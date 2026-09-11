package library;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Process-wide static pools: a code pool and a web pool, each a fixed pool of
 * {@code threads} threads, and a single-thread scheduler.
 *
 * The live program uses only the code pool, through {@link ASync}, plus
 * {@link #awaitTermination} and {@link #shutdown}, which
 * {@code CovidColorado.main} calls at the end of a run. Nothing calls the web
 * pool, the scheduler, {@code executeCode} or the web and schedule methods;
 * they date from a web-server experiment (commit 3fe8992).
 *
 * The code pool does run blocking IO: {@code Nwss.read()} puts the CDC and
 * LAPIS downloads on it, which only makes other tasks queue. The trap is that a
 * code-pool task must not wait on another code-pool task, for example by
 * calling {@code ASync.complete()} or {@code Future.get()} from inside a task:
 * with a fixed pool that deadlocks once every thread is waiting on work still
 * queued behind it. Today only the main thread waits.
 *
 * Every task is wrapped by {@code catchWrapper}, or by the copy of it written
 * inline in {@code submitCode(Runnable)}: an {@code Exception} is printed and
 * swallowed, so a {@code Callable} that throws completes its {@code Future}
 * with null, and {@code Future.get()} never throws {@code ExecutionException}
 * for an {@code Exception}. An {@code Error} is not caught.
 *
 * The pool threads are non-daemon (the JDK default thread factory), so once a
 * task has run the JVM does not exit until the pools are shut down. That is
 * why {@code main} ends with {@link #awaitTermination}.
 *
 * @author jdorje@gmail.com
 */
public class MyExecutor {

	private static final int processors = Runtime.getRuntime().availableProcessors();
	// Half the processors, to leave the other half of the machine free for
	// whatever else it is doing while a run goes; never fewer than one.
	private static final int threads = Math.max(processors / 2, 1);

	private static final ExecutorService webPool = Executors.newFixedThreadPool(threads);
	private static final ExecutorService codePool = Executors.newFixedThreadPool(threads);
	private static final ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor();

	static Runnable catchWrapper(Runnable command) {
		return new Runnable() {
			@Override
			public void run() {
				try {
					command.run();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
	}

	static <T> Callable<T> catchWrapper(Callable<T> task) {
		return new Callable<T>() {
			@Override
			public T call() {
				try {
					return task.call();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		};
	}

	public static void executeWeb(Runnable command) {
		webPool.execute(catchWrapper(command));
	}

	public static void executeCode(Runnable command) {
		codePool.execute(catchWrapper(command));
	}

	public static <T> Future<T> submitCode(Callable<T> task) {
		return codePool.submit(catchWrapper(task));
	}

	/**
	 * Queues {@code task} on the code pool, with the same exception handling as
	 * {@code catchWrapper}. The returned {@code Future}'s value is always null;
	 * the method is generic in {@code T} only so {@code ASync<V>.execute} can
	 * queue the {@code Future} alongside the ones {@code submit} returns.
	 */
	public static <T> Future<T> submitCode(Runnable task) {
		return codePool.submit(new Callable<T>() {
			@Override
			public T call() {
				try {
					task.run();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}

		});
	}

	public static <T> Future<T> submitWeb(Callable<T> task) {
		return webPool.submit(catchWrapper(task));
	}

	public static void scheduleWeb(Runnable command, long delay, TimeUnit unit) {
		schedule.schedule(catchWrapper(command), delay, unit);
	}

	public static ScheduledFuture<?> scheduleWebWithFixedDelay(Runnable command, long initialDelay, long delay,
			TimeUnit unit) {
		return schedule.scheduleWithFixedDelay(() -> webPool.execute(catchWrapper(command)), initialDelay, delay, unit);
	}

	/** Refuses new work; tasks already on the code and web pools still run. */
	public static void shutdown() {
		webPool.shutdown();
		codePool.shutdown();
		schedule.shutdown();
	}

	/**
	 * Calls {@link #shutdown} first, so no new work can be submitted once this
	 * starts, then waits up to {@code timeout} for the code pool and, once that
	 * has terminated, up to {@code timeout} again for the web pool. If either is
	 * still running, it interrupts both ({@code shutdownNow}) and waits the same
	 * way again. A pool that terminated in the first round returns at once in
	 * the second, so the worst case is three times {@code timeout}. The
	 * scheduler is shut down but not awaited.
	 *
	 * @return true once both pools have terminated; false if they did not, or if
	 *         the waiting thread was interrupted, with a stack trace printed in
	 *         either case
	 */
	public static boolean awaitTermination(long timeout, TimeUnit unit) {
		shutdown();
		try {
			if (codePool.awaitTermination(timeout, unit) && webPool.awaitTermination(timeout, unit)) {
				return true;
			}
			webPool.shutdownNow();
			codePool.shutdownNow();
			if (codePool.awaitTermination(timeout, unit) && webPool.awaitTermination(timeout, unit)) {
				System.out.println("Successfully forced termination.");
				return true;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}

		new Exception("Pool did not shutdown.").printStackTrace();
		return false;
	}
}
