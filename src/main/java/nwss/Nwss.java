package nwss;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import Variants.VariantSet;
import Variants.Voc;
import charts.Chart;
import charts.ChartSewage;
import covid.CalendarUtils;
import library.ASync;
import nwss.Sewage.Type;

public class Nwss {

	public static final String CSV1 = System.getProperty("java.io.tmpdir")
			+ "NWSS_Public_SARS-CoV-2_Concentration_in_Wastewater_Data.csv";
	public static final String URL1 = "https://data.cdc.gov/api/views/g653-rqe2/rows.csv?accessType=DOWNLOAD";

	public static final String CSV2 = System.getProperty("java.io.tmpdir")
			+ "NWSS_Public_SARS-CoV-2_Wastewater_Metric_Data.csv";
	public static final String URL2 = "https://data.cdc.gov/api/views/2ew6-ywp6/rows.csv?accessType=DOWNLOAD";

	private static final Charset CHARSET = Charset.forName("US-ASCII");

	private HashMap<String, Sewage> plantSewage = new HashMap<>();
	private HashMap<String, Sewage> countySewage = new HashMap<>();
	private HashMap<String, Sewage> stateSewage = new HashMap<>();
	private Sewage countrySewage = new Sewage(Type.COUNTRY, "United States");

	private static Sewage getIdSewage(Type type, String id, HashMap<String, Sewage> source) {
		synchronized (source) {
			Sewage sewage = source.get(id);
			if (sewage == null) {
				sewage = new Sewage(type, id);
				source.put(id, sewage);
			}
			return sewage;
		}
	}

	private static long HOUR = 60 * 60 * 1000;

	public static void download(URL url, File file) {
		try (BufferedInputStream in = new BufferedInputStream(url.openStream());
				FileOutputStream fileOutputStream = new FileOutputStream(file)) {
			byte dataBuffer[] = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
		} catch (Exception e) {
			file.delete();
			e.printStackTrace();
		}
	}

	public static File ensureFileUpdated(String fileLoc, String urlSource, int hours) {
		File f = new File(fileLoc);

		if (f.exists() && System.currentTimeMillis() - f.lastModified() > hours * HOUR) {
			System.out.println(
					"Deleting " + fileLoc + ", age " + (System.currentTimeMillis() - f.lastModified()) / HOUR + "h.");
			f.delete();
		}

		if (!f.exists()) {
			URL url = null;
			try {
				url = new URL(urlSource);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				System.exit(0);
			}

			System.out.println("Downloading " + fileLoc + ".");
			download(url, f);
		}

		return f;
	}

	private Sewage getPlantSewage(String plantId) {
		return getIdSewage(Type.PLANT, plantId, plantSewage);
	}

	private Sewage getCountySewage(String county, String state) {
		String countyId = state + "-" + county;
		Sewage s = getIdSewage(Type.COUNTY, countyId, countySewage);
		s.setCounty(county);
		s.setState(state);
		return s;
	}

	private Sewage getStateSewage(String stateId) {
		Sewage s = getIdSewage(Type.STATE, stateId, stateSewage);
		s.setState(stateId);
		return s;
	}

	double scaleFactor = 1E6;

	public void readSewage() {
		File f = ensureFileUpdated(CSV1, URL1, 4);

		double maxNumber = 0;
		try (CSVParser csv = CSVParser.parse(f, CHARSET, CSVFormat.DEFAULT)) {
			int records = 0;
			for (CSVRecord line : csv) {
				if (records++ == 0) {
					continue;
				}

				String plant = line.get(0);
				String date = line.get(1);
				int day = CalendarUtils.dateToDay(date);
				String num = line.get(2);
				if (num.equals("")) {
					continue;
				}
				Double number = Double.valueOf(num);
				if (number == null || number.isInfinite()) {
					continue;
				}
				if (number < 0) {
					number = 0.0;
				}
				if (number <= 0) {
					/*
					 * If using a geometric system we'd need to skip these,
					 * easiest place is here though hackery. If using algebraic
					 * then we do want to include the zeroes.
					 */
					// continue;
				}
				Sewage sewage = getPlantSewage(plant);
				sewage.setSmoothing(line.get(3));
				maxNumber = Math.max(number, maxNumber);
				sewage.addEntry(day, number / scaleFactor);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void readLocations() {
		File f = ensureFileUpdated(CSV2, URL2, 24);

		try (CSVParser csv = CSVParser.parse(f, CHARSET, CSVFormat.DEFAULT)) {
			int records = 0;
			for (CSVRecord line : csv) {
				if (records++ == 0) {
					continue;
				}

				String state = line.get(0);
				String plantIdString = line.get(1);
				String plant = line.get(5);
				String county = line.get(6);
				String popString = line.get(8);

				Sewage sewage = getPlantSewage(plant);
				sewage.setPlantId(Integer.valueOf(plantIdString));
				sewage.setState(state);
				sewage.setCounty(county);
				sewage.setPopulation(Integer.valueOf(popString));

				if (false && line.get(0).equalsIgnoreCase("colorado")) {
					System.out.println("CO => ");
					System.out.println("County => " + sewage.getCounty());
					System.out.println("Pop => " + sewage.getPopulation());
					System.out.println("Plant => " + sewage.id);
					return;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private Voc variants;

	public void read() {
		ASync<Chart> build = new ASync<>();
		build.execute(() -> readSewage());
		build.execute(() -> readLocations());
		build.execute(() -> variants = Voc.create());

		VariantSet vs = new VariantSet(VariantSet.JUNE_15, VariantSet.TODAY, VariantSet.JUNE_TO_SEPTEMBER_VARIANTS);
		vs.getCovSpectrumLink();
		vs.getCovSpectrumLink2(false);
		vs.getCovSpectrumLink2(true);

		build.complete();

		countrySewage.buildCountry(plantSewage.values());
		plantSewage.forEach((plantId, sewage) -> {
			String state = sewage.getState();
			if (state != null) {
				getStateSewage(state).includeSewage(sewage, 1.0);
				String c = sewage.getCounty();
				if (c != null) {
					String[] counties = sewage.getCounty().split(",");
					for (String county : counties) {
						getCountySewage(county, state).includeSewage(sewage, 1.0 / counties.length);
					}
				}
			}
		});
	}

	public void build() {
		ChartSewage.mkdirs();
		stateSewage.forEach((id, sewage) -> ChartSewage.reportState(id));
		ASync<Chart> build = new ASync<>();
		build.execute(() -> ChartSewage.createSewage(countrySewage));
		if (variants != null) {
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(countrySewage, variants, true, false));
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(countrySewage, variants, true, true));
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(countrySewage, variants, false, true));
			build.execute(() -> ChartSewage.buildSewageCumulativeChart(countrySewage, variants));
		}
		plantSewage.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		countySewage.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		stateSewage.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		build.complete();
		library.OpenImage.open();
	}

}
