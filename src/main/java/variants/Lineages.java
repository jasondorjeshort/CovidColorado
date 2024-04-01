package variants;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import nwss.Nwss;

public class Lineages {

	// private static final String ALIAS_FILE =
	// System.getProperty("java.io.tmpdir") + "\\" + Nwss.FOLDER + "\\"
	// + "aliases.json";
	// private static final String ALIAS_URL =
	// "https://raw.githubusercontent.com/cov-lineages/pango-designation/master/pango_designation/alias_key.json";

	private static final String LINEAGES_FILE = Nwss.GIT_LOCATION + "\\lineages.csv";

	private static boolean built = false;

	public static synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		Charset charset = Charset.forName("US-ASCII");

		// File f = Nwss.ensureFileUpdated(ALIAS_FILE, ALIAS_URL, 168);
		File f = new File(LINEAGES_FILE);

		HashSet<Lineage> lineages = new HashSet<>();

		try (CSVParser csv = CSVParser.parse(f, charset, CSVFormat.DEFAULT)) {

			for (CSVRecord line : csv) {
				String lineage = line.get(1);
				String sequence = line.get(0);

				if (sequence.equals("taxon") && lineage.equals("lineage")) {
					continue;
				}

				// String[] sequenceSplit = sequence.split("/");
				// String sequenceYear = sequenceSplit[sequenceSplit.length -
				// 1];
				// sequenceYear = sequenceYear.split("-", 2)[0]; // "2021-12" =
				// december
				// int year = Integer.valueOf(sequenceYear);

				Lineage l = Lineage.get(lineage);
				if (lineages.contains(l)) {
					continue;
				}
				lineages.add(l);

				/*
				 * Ordering can't cleanly come from this data source. The
				 * sequence ID has a year on it, usually, but that's not enough.
				 * The file is in chronological order, mostly, which is what we
				 * use. But not entirely.
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
