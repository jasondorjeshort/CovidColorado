package library;

import java.util.LinkedList;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Run some things asynchronously, then wait on them.
 * 
 * @author jdorje@gmail.com
 */
public class Async<V> {

	private final LinkedList<Future<V>> exec = new LinkedList<>();
	private int executions = 0;

	/**
	 * Start executing the given code immediately.
	 * 
	 * @param func
	 *            The code.
	 */
	public void exec(Supplier<V> func) {
		Future<V> future = MyExecutor.submitCode(() -> func.get());
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

}
