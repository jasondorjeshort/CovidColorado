package nwss;

import java.io.InputStream;
import java.util.HashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Which region each jurisdiction's plants are aggregated into, read from
 * {@code src/main/resources/regions.csv}: no header, one
 * {@code full name,region} row per jurisdiction. Names must match what
 * {@link StateNames} produces exactly, case and spacing included, since
 * nothing is trimmed or folded. The five regions are this program's own
 * grouping, not the Census Bureau's four.
 *
 * {@code nwss/Nwss.java} {@code read()} is the only user: it runs
 * {@link #load()} as one task on its parallel pool and calls
 * {@link #getRegion} only after that pool's {@code complete()}, which is what
 * makes the unsynchronized map safe to read.
 */
public class Regions {

	private final HashMap<String, String> map = new HashMap<>();

	/**
	 * The region for a jurisdiction's full name, never null. A name with no
	 * row, or null, gets "Other", which is then a region like the rest and
	 * gets its own chart.
	 */
	public String getRegion(String state) {
		String region = map.get(state);

		if (region == null) {
			return "Other";
		}

		return region;
	}

	/**
	 * Fills the map from regions.csv; a later row for the same name wins. Never
	 * throws: a missing resource, or a row with fewer than two fields, is
	 * printed and abandons the load with the map as far as it got, and every
	 * name it did not reach falls to "Other" for the rest of the run.
	 */
	public void load() {
		try (InputStream is = Regions.class.getClassLoader().getResourceAsStream("regions.csv");
				CSVParser csv = CSVParser.parse(is, Nwss.CHARSET, CSVFormat.DEFAULT)) {
			for (CSVRecord line : csv) {
				map.put(line.get(0), line.get(1));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
