package library;

import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Queues tasks on {@link MyExecutor}'s code pool, then waits for all of them
 * with {@link #complete()}.
 *
 * MyExecutor prints and swallows any Exception a task throws, so that task's
 * Future holds null; nothing here throws for a failed task.
 *
 * Thread-safe, and a running task may add more tasks to the same instance:
 * {@code buildVocSewageCharts} in {@code charts/ChartSewage.java} calls
 * {@link #execute} on the ASync that {@code build()} in {@code nwss/Nwss.java}
 * is running it from. Do not call {@link #complete()} from a task running on the
 * code pool; see {@link MyExecutor} for why that can deadlock.
 *
 * @author jdorje@gmail.com
 */
public class ASync<V> {

	private final LinkedList<Future<V>> exec = new LinkedList<>();
	private int executions = 0;

	/**
	 * Queues the given code on the code pool and returns at once; it starts
	 * when a pool thread is free.
	 *
	 * @param func
	 *            The code.
	 * @return The task's Future; its value is null if the task threw.
	 */
	public Future<V> submit(Callable<V> func) {
		Future<V> future = MyExecutor.submitCode(func);
		synchronized (this) {
			exec.add(future);
			executions++;
		}
		return future;
	}

	/**
	 * Queues the given code on the code pool and returns at once; it starts
	 * when a pool thread is free. This is {@link #submit} for a Runnable: its
	 * Future's value is always null, and {@link #complete()} waits for it like
	 * any other.
	 *
	 * @param func
	 *            The code.
	 */
	public void execute(Runnable func) {
		Future<V> future = MyExecutor.submitCode(func);
		synchronized (this) {
			exec.add(future);
			executions++;
		}
	}

	/**
	 * @return The number of tasks ever added by submit or execute, including
	 *         ones still queued or running; not a count of completed tasks.
	 */
	public int getExecutions() {
		synchronized (this) {
			return executions;
		}
	}

	/**
	 * Waits for the added tasks in FIFO order until none are left. A task's
	 * Future completes only after the task returns, so anything a waited-on task
	 * added before returning is waited for too; {@code nwss/Nwss.java}
	 * {@code build()} depends on this. A task added from another thread after
	 * this has found the list empty is not waited for. The instance may be
	 * reused afterwards.
	 *
	 * Never throws: a failed wait is printed and skipped. Trap: if the waiting
	 * thread is interrupted, the task it was waiting on is dropped unwaited and
	 * the thread's interrupt status is lost.
	 */
	public void complete() {
		while (true) {
			Future<V> future;
			synchronized (this) {
				future = exec.poll();
			}

			if (future == null) {
				break;
			}

			try {
				future.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
