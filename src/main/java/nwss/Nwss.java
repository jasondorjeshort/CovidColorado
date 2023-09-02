package nwss;

import java.io.File;
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

	public static final String CSV_NAME = "C:\\Users\\jdorj\\Downloads\\"
			+ "NWSS_Public_SARS-CoV-2_Concentration_in_Wastewater_Data.csv";

	public static final String CSV2_NAME = "C:\\Users\\jdorj\\Downloads\\"
			+ "NWSS_Public_SARS-CoV-2_Wastewater_Metric_Data.csv";

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
		URL url = null;
		File f = new File(CSV_NAME);

		try {
			if (f.exists()) {
				url = new File(CSV_NAME).toURI().toURL();
			} else {
				url = new URL("https://data.cdc.gov/api/views/g653-rqe2/rows.csv?accessType=DOWNLOAD");
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		double maxNumber = 0;
		try (CSVParser csv = CSVParser.parse(url, CHARSET, CSVFormat.DEFAULT)) {
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

		File f = new File(CSV2_NAME);
		URL url = null;
		try {
			if (f.exists()) {
				url = f.toURI().toURL();
			} else {
				url = new URL("https://data.cdc.gov/api/views/2ew6-ywp6/rows.csv?accessType=DOWNLOAD");
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		try (CSVParser csv = CSVParser.parse(url, CHARSET, CSVFormat.DEFAULT)) {
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
		VariantSet vs = new VariantSet(VariantSet.APRIL_1, VariantSet.TODAY, VariantSet.APRIL_TO_SEPTEMBER_VARIANTS);
		vs.getCovSpectrumLink();
		vs.getCovSpectrumLink2();

		ASync<Chart> build = new ASync<>();
		build.execute(() -> readSewage());
		build.execute(() -> readLocations());
		build.execute(() -> variants = Voc.create());
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
		// plantSewage.forEach((id, sewage) -> build.execute(() ->
		// ChartSewage.createSewage(sewage)));
		// countySewage.forEach((id, sewage) -> build.execute(() ->
		// ChartSewage.createSewage(sewage)));
		// stateSewage.forEach((id, sewage) -> build.execute(() ->
		// ChartSewage.createSewage(sewage)));
		build.complete();
		library.OpenImage.open();
	}

}
