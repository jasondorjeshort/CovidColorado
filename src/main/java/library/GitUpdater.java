package library;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class GitUpdater {

	private final String path;

	public GitUpdater(String path) {
		this.path = path;
	}

	/*
	 * Found this code on the internet - moves strings from a stream (i.e.
	 * process output) to a consumer (i.e. println)
	 */
	private static class StreamGobbler implements Runnable {
		private InputStream inputStream;
		private Consumer<String> consumer;

		public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
			this.inputStream = inputStream;
			this.consumer = consumer;
		}

		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
		}
	}

	public void update() {
		Process process = null;
		try {
			File f = new File(path);
			String cmd = "git.exe pull --progress -v --no-rebase \"origin\"";
			process = Runtime.getRuntime().exec(cmd, null, f);

			try (InputStream is = process.getInputStream()) {
				StreamGobbler streamGobbler = new StreamGobbler(is, s -> System.out.println("PangoLineage => " + s));
				streamGobbler.run();
			}

			process.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
