package library;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This emulates aspects of ScheduledExecutorService and ExecutorService.
 * 
 * I have two different full thread pools: one that can grow without limit, but
 * should only be invoked for real-time or user/blocking actions (userpool), and
 * one that you can throw as much code as you want over to, but the code in it
 * should never block for IO because the thread pool is limited.
 * 
 * I might refine this more in future, perhaps even creating my own executor
 * service that can create threads based on the CPU usage.
 * 
 * @author jdorje@gmail.com
 */
public class MyExecutor {

	private static final int processors = Runtime.getRuntime().availableProcessors();
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

	public static <T> Future<T> submitCode(Runnable task) {
		// surely there's a better way? Do we care?
		return codePool.submit(new Callable<T>() {
			@Override
			public T call() {
				task.run();
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

	public static void shutdown() {
		webPool.shutdown();
		codePool.shutdown();
		schedule.shutdown();
	}

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
			System.out.println("Pool forced down.");
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}

		new Exception("Pool did not shutdown.").printStackTrace();
		return false;
	}
}
