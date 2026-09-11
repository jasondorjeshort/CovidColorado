package sewage;

import java.io.File;
import java.util.HashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import nwss.Nwss;

/**
 * County FIPS code to name, latitude and longitude, read from a third-party
 * CSV of US counties.
 * <p>
 * Nothing in the program uses this class. Its one construction site, in
 * {@code nwss.Nwss.read()}, was commented out in 2024 and is still there as a
 * comment, and nothing has ever called {@link #getCounty}. It came in with
 * {@link Geo} and the FIPS ids stored on {@link Plant}, in a 2023 commit whose
 * message calls that geographic aggregation undecided.
 */
public class Fips {

	public static class County {
		int fipsId;
		String name;
		double lat, lon;
	}

	private final HashMap<Integer, County> fips = new HashMap<>();

	private static final String URL = "https://gist.githubusercontent.com/russellsamora/12be4f9f574e92413ea3f92ce1bc58e6/raw/3f18230058afd7431a5d394dab7eeb0aafd29d81/us_county_latlng.csv";
	private static final String CSV = System.getProperty("java.io.tmpdir") + "\\" + Nwss.FOLDER + "\\"
			+ "us_county_latlng.csv";

	public Fips() {
		File f = Nwss.ensureFileUpdated(CSV, URL, 168);

		try (CSVParser csv = CSVParser.parse(f, Nwss.CHARSET, CSVFormat.DEFAULT)) {
			int records = 0;
			for (CSVRecord line : csv) {
				if (records++ == 0) {
					continue;
				}

				try {
					County county = new County();
					county.fipsId = Integer.valueOf(line.get(0));
					county.name = line.get(1);
					county.lon = Double.valueOf(line.get(2));
					county.lat = Double.valueOf(line.get(3));
					fips.put(county.fipsId, county);
				} catch (Exception e) {
					// continue
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public County getCounty(int fipsId) {
		return fips.get(fipsId);
	}

}
