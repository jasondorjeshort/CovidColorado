package variants;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import nwss.Nwss;

/**
 * The designated-lineage list: every lineage named in pango-designation's
 * lineages.csv, which has one "taxon,lineage" row per designated sequence
 * (2,672,224 rows naming 6,006 lineages on 2026-09-10). {@link #build} interns a
 * {@link Lineage} for each and numbers them by first appearance through
 * Lineage.setOrdering().
 * <p>
 * Nothing reads that number yet: Lineage.getOrdering() has no caller. So what a
 * run gets from this class is the printed count and a warm Lineage cache,
 * which changes nothing either, since Lineage.get() builds the same instance
 * on demand. That is also why it can run as one of nwss/Nwss.java read()'s
 * pool tasks while the Voc and LAPIS load and the LEnum loop call
 * Lineage.get() on other threads: get() interns under one lock, and no reader
 * waits on this list. A reader of the ordering added later would have to wait
 * for the pool, since a lineage this has not reached yet reads zero.
 */
public class Lineages {

	/*
	 * In the checkout Nwss.read() git-pulls, and read only after that pull and
	 * after Aliases.build() has loaded alias_key.json from it. A lineages.csv
	 * newer than the loaded aliases could name an alias they lack, and build()
	 * exits on that.
	 */
	private static final String LINEAGES_FILE = Nwss.GIT_LOCATION + "\\lineages.csv";

	private static boolean built = false;

	/**
	 * Reads lineages.csv, once; later calls return immediately, and a second
	 * caller during the first waits for it.
	 * <p>
	 * A missing or unreadable file, or a row with fewer than two fields, prints
	 * a stack trace, keeps what was read before it and lets the run go on. A
	 * name Lineage.get() cannot resolve instead prints a stack trace and exits
	 * the JVM with status 0, taking every other pool task with it; see
	 * docs/active/findings/ for the open entry on this.
	 */
	public static synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		/*
		 * commons-csv reads a File through an InputStreamReader, which replaces
		 * a byte outside the charset rather than throwing, so a non-ASCII taxon
		 * cannot stop the read; the lineage column is ASCII.
		 */
		Charset charset = Charset.forName("US-ASCII");

		File f = new File(LINEAGES_FILE);

		HashSet<Lineage> lineages = new HashSet<>();

		try (CSVParser csv = CSVParser.parse(f, charset, CSVFormat.DEFAULT)) {

			for (CSVRecord line : csv) {
				String lineage = line.get(1);
				String sequence = line.get(0);

				if (sequence.equals("taxon") && lineage.equals("lineage")) {
					continue;
				}

				Lineage l = Lineage.get(lineage);
				if (l == null) {
					new Exception("Null lineage for " + lineage).printStackTrace();
					System.exit(0);
				}
				if (lineages.contains(l)) {
					continue;
				}
				lineages.add(l);

				/*
				 * Ordering can't cleanly come from this data source. The
				 * sequence ID has a year on it, usually, but that's not enough.
				 * The file is in chronological order, mostly, which is what we
				 * use. But not entirely: on 2026-09-10 its first data row was
				 * XBZ, a 2023 recombinant, ahead of A; after that it ran
				 * roughly in designation order (B.1.1.7 466th, JN.1 3,401st),
				 * ending on the two lineages the checkout's newest commit
				 * designated.
				 *
				 * We'd like to have an ordering because we'd like to prioritize
				 * recent lineages for inclusion. Or even ignore lineages that
				 * are too recent because the Gisaid source (cov-spectrum) won't
				 * have them yet.
				 */
				l.setOrdering(lineages.size());

				// System.out.println("New lineage: " + l.getAlias());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Total lineage count: " + lineages.size());
	}

}
