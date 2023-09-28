package nwss;

import java.io.InputStream;
import java.util.HashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class Regions {

	public final HashMap<String, String> map = new HashMap<>();

	public String getRegion(String state) {
		String region = map.get(state);

		if (region == null) {
			return "Other";
		}

		return region;
	}

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
