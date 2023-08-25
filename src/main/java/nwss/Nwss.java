package nwss;

import java.io.File;
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

public class Nwss {

	private static final String CSV_NAME = "C:\\Users\\jdorj\\Downloads\\"
			+ "NWSS_Public_SARS-CoV-2_Concentration_in_Wastewater_Data.csv";

	private static final String CSV2_NAME = "C:\\Users\\jdorj\\Downloads\\"
			+ "NWSS_Public_SARS-CoV-2_Wastewater_Metric_Data.csv";

	private final Charset charset = Charset.forName("US-ASCII");

	private HashMap<String, Sewage> plantSewage = new HashMap<>();
	private HashMap<String, Sewage> stateSewage = new HashMap<>();
	private Sewage countrySewage = new Sewage("United States");

	private Sewage getPlantSewage(String plant) {
		synchronized (plantSewage) {
			Sewage sewage = plantSewage.get(plant);
			if (sewage == null) {
				sewage = new Sewage(plant);
				plantSewage.put(plant, sewage);
			}
			return sewage;
		}
	}

	private Sewage getStateSewage(String state) {
		synchronized (stateSewage) {
			Sewage sewage = stateSewage.get(state);
			if (sewage == null) {
				sewage = new Sewage(state);
				sewage.setState(state);
				stateSewage.put(state, sewage);
			}
			return sewage;
		}
	}

	double scaleFactor = 1E6;

	public void readSewage() {
		URL url = null;
		try {
			url = new URL("https://data.cdc.gov/api/views/g653-rqe2/rows.csv?accessType=DOWNLOAD");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		double maxNumber = 0;
		try (CSVParser csv = CSVParser.parse(url, charset, CSVFormat.DEFAULT)) {
			for (CSVRecord line : csv) {
				String plant = line.get(0);
				if (plant.equalsIgnoreCase("key_plot_id")) {
					continue;
				}
				String date = line.get(1);
				int day = CalendarUtils.dateToDay(date);
				String num = line.get(2);
				if (num.equals("")) {
					continue;
				}
				Double number = Double.valueOf(num);
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

		try (CSVParser csv = CSVParser.parse(f, charset, CSVFormat.DEFAULT)) {
			int lines = 0;
			for (CSVRecord line : csv) {
				if (lines++ == 0) {
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

	public void read() {
		ASync<Chart> build = new ASync<>();
		build.execute(() -> readSewage());
		build.execute(() -> readLocations());
		build.complete();

		plantSewage.forEach((plantId, sewage) -> {
			getStateSewage(sewage.getState()).includeSewage(sewage);
			countrySewage.includeSewage(sewage);
		});
	}

	public void build() {
		ASync<Chart> build = new ASync<>();
		ChartSewage sew = new ChartSewage();
		plantSewage.forEach((plantId, sewage) -> build.execute(() -> sew.createSewage(sewage)));
		stateSewage.forEach((stateId, sewage) -> build.execute(() -> sew.createSewage(sewage)));
		sew.createSewage(countrySewage);
		build.complete();
		library.OpenImage.open();
	}

}
