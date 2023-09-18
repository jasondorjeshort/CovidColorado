package sewage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class Geo extends Multi {

	private final double lat, lon;

	public Geo(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}

	private static final Charset CHARSET = Charset.forName("US-ASCII");

	public static LinkedList<Geo> readCsv(String fileName) {
		LinkedList<Geo> geos = new LinkedList<>();

		File f = new File(fileName);
		if (!f.exists()) {
			return geos;
		}

		try (CSVParser csv = CSVParser.parse(f, CHARSET, CSVFormat.DEFAULT)) {
			for (CSVRecord line : csv) {
				double lat, lon;
				try {
					lat = Double.valueOf(line.get(0));
					lon = Double.valueOf(line.get(1));
				} catch (Exception e) {
					continue;
				}
				geos.add(new Geo(lat, lon));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return geos;
	}

	public synchronized double getLat() {
		return lat;
	}

	public synchronized double getLon() {
		return lon;
	}

	@Override
	public String getChartFilename() {
		return charts.ChartSewage.LL + "\\" + String.format("lat%flon%f", getLat(), getLon());
	}

	@Override
	public void includeSewage(Plant sewage, double popMultiplier) {
		super.includeSewage(sewage, popMultiplier);
	}

	@Override
	public String getTitleLine() {
		return String.format("%f,%f", getLat(), getLon());
	}
}
