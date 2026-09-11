package sewage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * An aggregate named for a point on the map, left unfinished: the coordinates
 * name it and its chart file and play no part in what it sums.
 * <p>
 * {@code nwss.Nwss} builds one, at fixed coordinates in southern Colorado, and
 * each run passes every plant in the country into it at a multiplier of 1.0,
 * so it is in effect a second national aggregate. Its chart call in
 * {@code Nwss.build()} has been commented out since 2023, and no longer
 * matches {@code ChartSewage.createSewage}'s signature; nothing else reads it,
 * so the result reaches no chart, log line or other aggregate.
 * {@link #readCsv} has no callers. The class came in with {@link Fips} and the
 * FIPS ids on {@link Plant}, in a commit whose message calls the geographic
 * handling undecided; plants carry no coordinates to weight by, since
 * {@code Plant.setLatLon} is never called.
 */
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

	/*
	 * Takes every plant at the caller's weight, wherever it is. Whatever
	 * selection or distance weighting the class is named for would go here, and
	 * none was written.
	 */
	@Override
	public void includeSewage(Plant sewage, double popMultiplier) {
		super.includeSewage(sewage, popMultiplier);
	}

	@Override
	public String getTitleLine() {
		return String.format("%f,%f", getLat(), getLon());
	}

	@Override
	public String getName() {
		return String.format("%.2f,%.2f", getLat(), getLon());
	}
}
