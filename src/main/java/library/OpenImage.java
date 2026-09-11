package library;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import nwss.Nwss;

/**
 * Collects the charts worth looking at during a run and shows them all at the
 * end, in one IrfanView thumbnail window. Call sites queue a chart with
 * {@link #openImage(String)} as they save it; {@link #open()} is called once
 * when the run has finished building. IrfanView's install path is hard-coded
 * here.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * @author jdorje@gmail.com
 */
public class OpenImage {

	private static final String IRFANVIEW = "C:\\Program Files\\IrfanView\\i_view64.exe";

	/*
	 * Handed to IrfanView as /filelist= rather than putting the charts on the
	 * command line, which IrfanView caps at 4096 characters. A fixed name,
	 * overwritten each run, so nothing accumulates in the cache. Its whole
	 * path must stay free of spaces, java.io.tmpdir included: ProcessBuilder
	 * quotes a whole argument containing one, and IrfanView's parsing of a
	 * quoted /filelist= argument is not something to depend on.
	 */
	private static final File LIST_FILE = new File(new File(System.getProperty("java.io.tmpdir"), Nwss.FOLDER),
			"open-charts.txt");

	private static final HashSet<String> files = new HashSet<>();

	/**
	 * Queues a chart, by its absolute path with extension, to be shown by the
	 * next {@link #open()}; IrfanView wants full paths in the list. A null, or a
	 * path already queued, is ignored. Safe to call from the build threads. The
	 * one live open() is at the end of {@code build()} in {@code nwss/Nwss.java},
	 * after the pool drains, so a chart queued after that is never shown.
	 *
	 * @param fileName
	 *            File name
	 */
	public static synchronized void openImage(String fileName) {
		if (fileName == null) {
			return;
		}
		files.add(fileName);
	}

	/**
	 * Opens every queued chart in a single IrfanView thumbnail window and
	 * empties the queue. Does nothing if nothing is queued. If the list cannot
	 * be written or IrfanView cannot be started, as when it is not installed at
	 * the path above, the IOException's stack trace is printed and the queue is
	 * emptied all the same.
	 */
	public static synchronized void open() {
		if (files.isEmpty()) {
			return;
		}
		/*
		 * Charts are queued from the build threads in whatever order they
		 * finish, so sort for a list that reads the same every run: fewest
		 * backslashes first, which puts the national charts in the nwss folder
		 * itself ahead of those in its subfolders, such as Colorado's under
		 * states\. Whether the thumbnail grid keeps the list's order is up to
		 * IrfanView.
		 */
		List<String> names = new ArrayList<>(files);
		files.clear();
		names.sort(Comparator.<String> comparingLong(n -> n.chars().filter(c -> c == '\\').count())
				.thenComparing(String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.naturalOrder()));

		try {
			/*
			 * The cache directory is made by Nwss's static block, but Nwss.FOLDER
			 * is a compile-time constant, so referencing it here does not run
			 * that block.
			 */
			LIST_FILE.getParentFile().mkdirs();
			/*
			 * UTF-8 without a BOM. The chart paths are ASCII, which reads the
			 * same whichever encoding IrfanView assumes, and a BOM could be
			 * taken as part of the first file name.
			 */
			Files.write(LIST_FILE.toPath(), names);
			new ProcessBuilder(IRFANVIEW, "/filelist=" + LIST_FILE.getAbsolutePath(), "/thumbs").start();
			System.out.println("Opened " + names.size() + " charts from " + LIST_FILE.getAbsolutePath() + ".");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
