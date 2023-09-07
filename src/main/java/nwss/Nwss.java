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

import charts.Chart;
import charts.ChartSewage;
import covid.CalendarUtils;
import library.ASync;
import variants.VariantSet;
import variants.Voc;

public class Nwss {

	public static final String CSV1 = System.getProperty("java.io.tmpdir")
			+ "NWSS_Public_SARS-CoV-2_Concentration_in_Wastewater_Data.csv";
	public static final String URL1 = "https://data.cdc.gov/api/views/g653-rqe2/rows.csv?accessType=DOWNLOAD";

	public static final String CSV2 = System.getProperty("java.io.tmpdir")
			+ "NWSS_Public_SARS-CoV-2_Wastewater_Metric_Data.csv";
	public static final String URL2 = "https://data.cdc.gov/api/views/2ew6-ywp6/rows.csv?accessType=DOWNLOAD";

	private static final Charset CHARSET = Charset.forName("US-ASCII");

	private HashMap<String, sewage.Plant> plants = new HashMap<>();
	private HashMap<String, sewage.County> counties = new HashMap<>();
	private HashMap<String, sewage.State> states = new HashMap<>();
	private sewage.All all = new sewage.All("United States");

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

	private sewage.Plant getPlantSewage(String plantId) {
		synchronized (plants) {
			sewage.Plant sew = plants.get(plantId);
			if (sew == null) {
				sew = new sewage.Plant(plantId);
				plants.put(plantId, sew);
			}
			return sew;
		}
	}

	private sewage.County getCountySewage(String county, String state) {
		synchronized (counties) {
			String countyId = state + "-" + county;
			sewage.County sew = counties.get(countyId);
			if (sew == null) {
				sew = new sewage.County(county, state);
				counties.put(countyId, sew);
			}
			return sew;
		}
	}

	private sewage.State getStateSewage(String state) {
		synchronized (states) {
			sewage.State sew = states.get(state);
			if (sew == null) {
				sew = new sewage.State(state);
				states.put(state, sew);
			}
			return sew;
		}
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
				sewage.Plant sewage = getPlantSewage(plant);
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

				sewage.Plant sewage = getPlantSewage(plant);
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
		long time = System.currentTimeMillis();
		ASync<Chart> build = new ASync<>();
		build.execute(() -> readSewage());
		build.execute(() -> readLocations());
		build.execute(() -> variants = Voc.create());

		VariantSet vs = new VariantSet(VariantSet.JUNE_15, VariantSet.TODAY, VariantSet.JUNE_TO_SEPTEMBER_VARIANTS);
		vs.getCovSpectrumLink();
		vs.getCovSpectrumLink2(false);
		vs.getCovSpectrumLink2(true);

		build.complete();

		System.out.println("Read stuff in " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();

		all.build(plants.values());
		plants.forEach((plantId, sewage) -> {
			String state = sewage.getState();
			if (state != null) {
				getStateSewage(state).includeSewage(sewage, 1.0);
				String c = sewage.getCounty();
				if (c != null) {
					String[] countyNames = sewage.getCounty().split(",");
					for (String county : countyNames) {
						getCountySewage(county, state).includeSewage(sewage, 1.0 / countyNames.length);
					}
				}
			}
		});

		System.out.println("Built combos in " + (System.currentTimeMillis() - time) / 1000 + "s.");
	}

	public void build() {
		long time = System.currentTimeMillis();
		ChartSewage.mkdirs();
		states.forEach((id, sewage) -> ChartSewage.reportState(id));

		System.out.println("Built dirs in " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();

		ASync<Chart> build = new ASync<>();
		build.execute(() -> ChartSewage.createSewage(all));
		if (variants != null) {
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(all, variants, true, false));
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(all, variants, true, true));
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(all, variants, false, true));
			build.execute(() -> ChartSewage.buildSewageCumulativeChart(all, variants));
		}
		plants.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		counties.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		states.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		build.complete();
		System.out.println("Built charts " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();
		library.OpenImage.open();
	}

}
