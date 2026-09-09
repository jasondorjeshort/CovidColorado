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
			/*
			 * Merge stderr into stdout: git writes progress there, and if nobody
			 * drains it the pipe fills up and git blocks forever. Also never let
			 * git prompt for credentials, since there is no terminal to answer.
			 */
			ProcessBuilder pb = new ProcessBuilder("git", "pull", "--no-progress", "--no-rebase", "origin");
			pb.directory(f);
			pb.redirectErrorStream(true);
			pb.environment().put("GIT_TERMINAL_PROMPT", "0");
			process = pb.start();

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
