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
import sewage.Fips;
import variants.VariantEnum;
import variants.VariantSet;
import variants.Voc;
import variants.VocSewage;

public class Nwss {

	public static final String FOLDER = "CovidBackend";

	static {
		new File(System.getProperty("java.io.tmpdir") + "\\" + FOLDER).mkdir();
	}

	public static final String CSV1 = System.getProperty("java.io.tmpdir") + "\\" + FOLDER + "\\"
			+ "NWSS_Public_SARS-CoV-2_Concentration_in_Wastewater_Data.csv";
	public static final String URL1 = "https://data.cdc.gov/api/views/g653-rqe2/rows.csv?accessType=DOWNLOAD";

	public static final String CSV2 = System.getProperty("java.io.tmpdir") + "\\" + FOLDER + "\\"
			+ "NWSS_Public_SARS-CoV-2_Wastewater_Metric_Data.csv";
	public static final String URL2 = "https://data.cdc.gov/api/views/2ew6-ywp6/rows.csv?accessType=DOWNLOAD";

	public static final Charset CHARSET = Charset.forName("US-ASCII");

	private HashMap<String, sewage.Plant> plants = new HashMap<>();
	private HashMap<String, sewage.County> counties = new HashMap<>();
	private HashMap<String, sewage.State> states = new HashMap<>();
	private HashMap<String, sewage.Region> regions = new HashMap<>();
	private sewage.All all = new sewage.All("United States");
	private sewage.Geo geo = new sewage.Geo(37.8921116, -106.0125575);

	private Fips fips;
	private Regions regionList = new Regions();

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

	private sewage.Region getRegionSewage(String region) {
		synchronized (regions) {
			sewage.Region sew = regions.get(region);
			if (sew == null) {
				sew = new sewage.Region(region);
				regions.put(region, sew);
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
				String fipsIds = line.get(7);
				String popString = line.get(8);

				sewage.Plant sewage = getPlantSewage(plant);
				sewage.setPlantId(Integer.valueOf(plantIdString));
				sewage.setState(state);
				sewage.setCounties(county);
				sewage.setFipsIds(fipsIds);
				if (county.contains(",") != fipsIds.contains(",")) {
					// System.out.println("Problem set");
					// System.out.println(county);
					// System.out.println(fipsIds);
				}
				sewage.setPopulation(Integer.valueOf(popString));
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
		build.execute(() -> fips = new Fips());
		build.execute(() -> regionList.load());

		for (VariantEnum vEnum : VariantEnum.values()) {
			System.out.println(vEnum);
			VariantSet vs = new VariantSet(vEnum);
			vs.getCovSpectrumLink();
			vs.getCovSpectrumLink2(false);
			vs.getCovSpectrumLink2(true);
			System.out.println();
		}

		build.complete();

		System.out.println("Read stuff in " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();

		all.build(plants.values());
		plants.forEach((plantId, sewage) -> {
			String state = sewage.getState();
			if (state != null) {
				String region = regionList.getRegion(state);
				getRegionSewage(region).includeSewage(sewage, 1.0);
				getStateSewage(state).includeSewage(sewage, 1.0);
				String c = sewage.getCounties();
				if (c != null) {
					String[] countyNames = c.split(",");
					for (String county : countyNames) {
						getCountySewage(county, state).includeSewage(sewage, 1.0 / countyNames.length);
					}
				}
			}
			geo.includeSewage(sewage, 1.0);
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
		build.execute(() -> ChartSewage.createSewage(geo));
		build.execute(() -> ChartSewage.createSewage(all));
		if (variants != null) {
			VocSewage vocSewage = new VocSewage(all, variants);
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(vocSewage, true, false));
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(vocSewage, true, true));
			build.execute(() -> ChartSewage.buildSewageTimeseriesChart(vocSewage, false, true));
			build.execute(() -> ChartSewage.buildSewageCumulativeChart(vocSewage));
		}
		plants.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		counties.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		states.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		regions.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage)));
		states.forEach((id, sewage) -> {
			System.out.println(id + "," + regionList.getRegion(id));
		});
		build.complete();
		System.out.println("Built charts " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();
		library.OpenImage.open();
	}

}
