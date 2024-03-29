package library;

import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Run some things asynchronously, then wait on them.
 * 
 * @author jdorje@gmail.com
 */
public class ASync<V> {

	private final LinkedList<Future<V>> exec = new LinkedList<>();
	private int executions = 0;

	/**
	 * Start executing the given code immediately.
	 * 
	 * @param func
	 *            The code.
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
	 * Start executing the given code immediately.
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
	 * @return The number of executions done.
	 */
	public int getExecutions() {
		synchronized (this) {
			return executions;
		}
	}

	/**
	 * Waits until all code that has been executed is completed.
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

	/**
	 * Waits until the next item completes, and returns it in FIFO order.
	 * Returns null if there is nothing pending.
	 */
	public V get() {
		Future<V> future;
		synchronized (this) {
			future = exec.poll();
		}
		if (future == null) {
			return null;
		}

		V value;
		try {
			value = future.get();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		if (value == null) {
			return get();
		}
		return value;
	}

}
