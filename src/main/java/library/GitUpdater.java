package library;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * Brings a git checkout up to date with its origin by running {@code git pull}
 * in it. nwss/Nwss.java read() uses it on the pango-designation checkout,
 * before anything reads that checkout.
 */
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

	/**
	 * Runs {@code git pull --no-rebase origin} in the checkout and blocks until
	 * git exits, printing everything git writes, stdout and stderr together,
	 * with a "PangoLineage =>" prefix. nwss/Nwss.java read() relies on the
	 * blocking: it is what keeps every reader of the checkout behind the pull.
	 * <p>
	 * A failed pull does not stop the run. No network, a stalled transfer, a
	 * directory that is not a repository, or local changes the merge would
	 * overwrite each print git's message and then its exit status, and leave
	 * the files as they were, so alias_key.json and lineages.csv are still from
	 * one revision. A directory that does not exist, or no git to run, prints a
	 * stack trace instead. A merge that conflicts, which needs the checkout's
	 * history to have diverged from origin's, is the exception: git leaves
	 * conflict markers in the files, and the run reads them.
	 */
	public void update() {
		Process process = null;
		try {
			File f = new File(path);
			/*
			 * Merge stderr into stdout, so the one reader below drains both: git
			 * writes errors, and some progress even under --no-progress, to
			 * stderr, and a pipe nobody drains fills up and blocks git forever.
			 * Never let git prompt for credentials, since there is no terminal
			 * to answer.
			 *
			 * Git applies no low-speed limit by default, so a server that
			 * accepts the connection and then sends nothing holds the pull, and
			 * the run, indefinitely and in silence. One byte a second over a
			 * minute trips only on a transfer that has stopped; a healthy pull
			 * of this checkout takes seconds. A pull cut off this way fails like
			 * any other. This covers HTTP transfers only.
			 */
			ProcessBuilder pb = new ProcessBuilder("git", "pull", "--no-progress", "--no-rebase", "origin");
			pb.directory(f);
			pb.redirectErrorStream(true);
			pb.environment().put("GIT_TERMINAL_PROMPT", "0");
			pb.environment().put("GIT_HTTP_LOW_SPEED_LIMIT", "1");
			pb.environment().put("GIT_HTTP_LOW_SPEED_TIME", "60");
			process = pb.start();

			try (InputStream is = process.getInputStream()) {
				StreamGobbler streamGobbler = new StreamGobbler(is, s -> System.out.println("PangoLineage => " + s));
				streamGobbler.run();
			}

			/*
			 * Git's own last line is not always its error: an aborted merge can
			 * end on "Updating a..b", which reads like success.
			 */
			int status = process.waitFor();
			if (status != 0) {
				System.out.println("PangoLineage => git pull exited with status " + status
						+ "; continuing with the checkout as git left it.");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
