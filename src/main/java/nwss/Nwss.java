package nwss;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import charts.Chart;
import charts.ChartSewage;
import covid.CalendarUtils;
import library.ASync;
import library.GitUpdater;
import variants.Aliases;
import variants.LEnum;
import variants.Lapis;
import variants.LSet;
import variants.Lineages;
import variants.Voc;
import variants.VocSewage;

/**
 * The live program's wastewater intake and its driver:
 * {@code colorado/CovidColorado.java} main makes one Nwss and calls
 * {@link #read()}, then {@link #build()}. docs/reference/wastewater.txt owns
 * what the reader does to the CDC data and why.
 * <p>
 * read() runs the downloads and parsers as tasks on the code pool, waits for
 * them, and then, on the calling thread, runs the national baseline and wires
 * the plants into counties, states, regions and the nation. What the tasks
 * fill in (the plant map, through readSewage, {@code variants}, and the
 * table in {@code regionList}) is read only after that wait, and the
 * Future.get() inside ASync.complete() is what makes it visible. From build()
 * on, the maps are only read, by the main thread and the chart tasks alike.
 * <p>
 * Also home to the download cache the other readers share: {@link #FOLDER}
 * under the system temp directory, and {@link #ensureFileUpdated}.
 */
public class Nwss {

	public static final String FOLDER = "CovidBackend";

	/*
	 * Makes the download cache directory when the class initializes. The
	 * result is not checked: a directory that cannot be made shows up later as
	 * every download failing. FOLDER is a compile-time constant, so a class
	 * that only names it does not run this block; calling ensureFileUpdated
	 * does.
	 */
	static {
		new File(System.getProperty("java.io.tmpdir") + "\\" + FOLDER).mkdir();
	}

	/*
	 * CDC archived the old NWSS concentration + metric datasets on September
	 * 12, 2025. Their successor is a single per-sample table.
	 */
	public static final String CSV = System.getProperty("java.io.tmpdir") + "\\" + FOLDER + "\\"
			+ "CDC_Wastewater_Data_for_SARS-CoV-2.csv";
	public static final String URL = "https://data.cdc.gov/api/views/j9g8-acpt/rows.csv?accessType=DOWNLOAD";

	public static final Charset CHARSET = Charset.forName("US-ASCII");

	private HashMap<String, sewage.Plant> plants = new HashMap<>();
	private HashMap<String, sewage.County> counties = new HashMap<>();
	private HashMap<String, sewage.State> states = new HashMap<>();
	private HashMap<String, sewage.Region> regions = new HashMap<>();
	private sewage.All all = new sewage.All("United States");
	private sewage.Geo geo = new sewage.Geo(37.8921116, -106.0125575);

	// private Fips fips;
	private Regions regionList = new Regions();

	public static final long HOUR = 60 * 60 * 1000;

	/**
	 * Copies url to file, overwriting it. Never throws: on any failure the
	 * trace is printed and file is deleted. While the copy runs, the partial
	 * file sits under its final name with a current mtime; see
	 * docs/active/findings/2026-09-10-a-second-run-reads-a-half-written-download-as-a-fresh-cache.md.
	 * No connect or read timeout is set, so a server that stops sending blocks
	 * this, and the run, indefinitely; see
	 * docs/active/findings/2026-09-10-a-stalled-download-hangs-the-run-for-good.md.
	 */
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

	/**
	 * Returns the file at fileLoc, first fetching it from urlSource with
	 * {@link #download} if it is missing or was last modified more than hours
	 * ago. Freshness is judged by the mtime alone: the URL is not compared
	 * (docs/active/findings/2026-09-09-lapis-cache-keyed-by-filename-not-url.md),
	 * nor is the file checked for being complete.
	 * <p>
	 * A stale file is deleted before the fetch, so a failed fetch leaves no
	 * file at all rather than the stale one, and the File returned may not
	 * exist: the caller must check. A stale file that cannot be deleted is
	 * returned as it is, after the line saying it is being deleted. A
	 * urlSource that is not a URL ends the JVM with status 0.
	 */
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
				/*
				 * URL(String), deprecated since Java 20, is the call behind
				 * javac's deprecation note for this file. Its replacement,
				 * URI.create(urlSource).toURL(), parses more strictly and throws
				 * IllegalArgumentException, which this catch does not take, so
				 * the swap changes how a bad URL fails. The URLs passed today
				 * parse the same either way.
				 */
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

	/**
	 * Which column of the new dataset a plant's series is taken from. Each
	 * plant must use exactly one so that its values are comparable across
	 * days; the normalizer against the national baseline takes care of the
	 * differing units between plants.
	 */
	private enum Normalization {
		/* Concentration scaled by flow and population; the old "flow-population". */
		FLOW_POPULATION("pcr_target_flowpop_lin"),
		/* Concentration divided by a human fecal indicator; the old "microbial". */
		MICROBIAL("pcr_target_mic_lin"),
		/* Raw concentration, only when nothing normalized is available. */
		RAW("pcr_target_avg_conc_lin");

		final String column;

		Normalization(String column) {
			this.column = column;
		}
	}

	/** Everything read for one plant, before deciding which column to use. */
	private static class PlantRows {
		final EnumMap<Normalization, TreeMap<Integer, double[]>> byNorm = new EnumMap<>(Normalization.class);
		int metadataDay = -1;
		String state, counties, fips, site;
		Integer population;

		PlantRows() {
			for (Normalization norm : Normalization.values()) {
				byNorm.put(norm, new TreeMap<>());
			}
		}

		Normalization choose() {
			int most = 0;
			for (Normalization norm : Normalization.values()) {
				most = Math.max(most, byNorm.get(norm).size());
			}
			/*
			 * Prefer the better normalization unless it covers noticeably fewer
			 * days than another column does, or its values are not believable.
			 * The coverage bar is set by the best-covered column even when that
			 * column fails isSane, so a plant whose only sane column is
			 * out-covered by insane ones gets null and is counted as having no
			 * usable values; see
			 * docs/active/findings/2026-09-10-a-plant-whose-sane-column-is-outcovered-is-dropped.md.
			 */
			for (Normalization norm : Normalization.values()) {
				if (byNorm.get(norm).size() >= 0.9 * most && isSane(byNorm.get(norm))) {
					return norm;
				}
			}
			return null;
		}

		private static ArrayList<Double> positiveValues(TreeMap<Integer, double[]> days) {
			ArrayList<Double> values = new ArrayList<>();
			days.forEach((day, sumCount) -> {
				double v = sumCount[0] / sumCount[1];
				if (v > 0) {
					values.add(v);
				}
			});
			Collections.sort(values);
			return values;
		}

		/**
		 * A handful of sites report a column in different units over time, so
		 * their series spans ten decades. Real sewage levels do not: reject a
		 * column whose 10th-90th percentile spread is more than 10,000x. Fewer
		 * than 10 positive values are too few for percentiles to mean anything,
		 * so such a column passes if it has any positive value at all.
		 */
		private static boolean isSane(TreeMap<Integer, double[]> days) {
			ArrayList<Double> values = positiveValues(days);
			if (values.size() < 10) {
				return !values.isEmpty();
			}
			double p10 = values.get(values.size() / 10), p90 = values.get(values.size() * 9 / 10);
			return p90 / p10 < 1E4;
		}

		/**
		 * Single days hundreds of times above the plant's own median are data
		 * errors, not covid. A few hundred of them (out of 600,000 plant-days)
		 * were each large enough to be the entire national total for that day,
		 * which is what sets the "pandemic peak" everything is scaled to.
		 */
		private static final double SPIKE_CAP = 300;

		static int dropSpikes(TreeMap<Integer, double[]> days) {
			ArrayList<Double> values = positiveValues(days);
			if (values.isEmpty()) {
				return 0;
			}
			double limit = SPIKE_CAP * values.get(values.size() / 2);
			int before = days.size();
			days.values().removeIf(sumCount -> sumCount[0] / sumCount[1] > limit);
			return before - days.size();
		}
	}

	private static Double parseValue(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		double d;
		try {
			d = Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return null;
		}
		if (Double.isNaN(d) || Double.isInfinite(d)) {
			return null;
		}
		return Math.max(d, 0.0);
	}

	/**
	 * Reads the CDC CSV, fetched first if it is missing or over 4 hours old,
	 * into one sewage/Plant per plant id. Runs as one of read()'s pool tasks,
	 * and is the only writer of the plant map. Any exception while parsing,
	 * including a missing file after a failed fetch and a column the dataset
	 * has renamed, prints a trace, deletes the CSV so the next run fetches it
	 * again, and ends the JVM with status 0, so the run draws nothing and
	 * reports success; see
	 * docs/active/findings/2026-09-10-an-unreadable-cdc-download-ends-the-run-as-a-success.md.
	 */
	public void readSewage() {
		System.out.println(CSV);
		File f = ensureFileUpdated(CSV, URL, 4);

		HashMap<String, PlantRows> rows = new HashMap<>();

		CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
		try (CSVParser csv = CSVParser.parse(f, CHARSET, format)) {
			for (CSVRecord line : csv) {
				String source = line.get("source").toUpperCase();
				String state = line.get("state_territory");
				String site = line.get("site");
				String location = line.get("sample_location");
				String matrix = line.get("sample_matrix");
				/* Same shape as the old key_plot_id. */
				String plantId = source + "_" + state + "_" + site + "_" + location + "_" + matrix;

				int day = CalendarUtils.dateToDay(line.get("sample_collect_date"));

				PlantRows plant = rows.computeIfAbsent(plantId, id -> new PlantRows());

				for (Normalization norm : Normalization.values()) {
					Double value = parseValue(line.get(norm.column));
					if (value == null) {
						continue;
					}
					/* Several gene targets or samples on one day get averaged. */
					double[] sumCount = plant.byNorm.get(norm).computeIfAbsent(day, d -> new double[2]);
					sumCount[0] += value;
					sumCount[1]++;
				}

				/* Metadata drifts over time; keep whatever is most recent. */
				if (day > plant.metadataDay) {
					plant.metadataDay = day;
					plant.site = site;
					plant.state = StateNames.get(state);
					plant.counties = line.get("counties_served").replace(", ", ",");
					plant.fips = line.get("county_fips").replace(", ", ",");
					try {
						plant.population = Integer.valueOf(line.get("population_served"));
					} catch (NumberFormatException e) {
						plant.population = null;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			f.delete();
			System.exit(0);
		}

		int skipped = 0, spikes = 0;
		for (Map.Entry<String, PlantRows> entry : rows.entrySet()) {
			PlantRows plant = entry.getValue();
			Normalization norm = plant.choose();
			if (norm == null) {
				skipped++;
				continue;
			}
			spikes += PlantRows.dropSpikes(plant.byNorm.get(norm));

			sewage.Plant sewage = getPlantSewage(entry.getKey());
			sewage.setSmoothing(norm.column);
			try {
				sewage.setPlantId(Integer.valueOf(plant.site));
			} catch (NumberFormatException e) {
				/* Site ids look numeric so far, but nothing promises it. */
			}
			sewage.setState(plant.state);
			sewage.setCounties(plant.counties);
			sewage.setFipsIds(plant.fips);
			if (plant.population != null) {
				sewage.setPopulation(plant.population);
			}

			plant.byNorm.get(norm).forEach((day, sumCount) -> {
				sewage.addEntry(day, sumCount[0] / sumCount[1] / scaleFactor);
			});
		}
		System.out.println("Read " + rows.size() + " plants, skipped " + skipped + " with no usable values, dropped "
				+ spikes + " spike days.");
	}

	private Collection<Voc> variants;

	public static final String GIT_LOCATION = "C:\\Users\\jdorj\\Downloads\\pango-designation";

	/**
	 * Fetches and parses everything, then builds the baseline and the
	 * hierarchy; the class comment describes the threading. An exception in a
	 * pool task is printed and swallowed by the pool, so a reader that fails
	 * without exiting the JVM itself leaves its data missing rather than
	 * failing this call.
	 */
	public void read() {

		long time = System.currentTimeMillis();

		/*
		 * Pull before anything runs in parallel. Aliases loads alias_key.json
		 * lazily on the first Lineage.get(), and when the pull ran in the pool
		 * a lookup could load the stale alias file while Lineages.build() read
		 * the freshly pulled lineages.csv, leaving any alias new to that pull
		 * unknown. The pull is synchronous, and that is what orders it;
		 * Aliases.build() here only loads the aliases on this thread.
		 */
		new GitUpdater(GIT_LOCATION).update();
		Aliases.build();

		ASync<Chart> build = new ASync<>();
		build.execute(() -> Lineages.build());

		build.execute(() -> readSewage());
		build.execute(() -> {
			variants = Voc.create();
			variants.addAll(Lapis.create());
		});
		// build.execute(() -> fips = new Fips());
		build.execute(() -> regionList.load());
		build.execute(() -> {
			for (LEnum vEnum : LEnum.values()) {
				System.out.println(vEnum);
				LSet vs = new LSet(vEnum);
				vs.getCovSpectrumLink();
				vs.getCovSpectrumReverseLink();
				System.out.println();
			}
		});

		build.complete();

		System.out.println("Read stuff in " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();

		all.build(plants.values());

		/*
		 * Each plant is included directly into its region, its state and each
		 * of its counties; no aggregate is summed from another. A county's
		 * plants are added as its children here, but every aggregate is added
		 * as a child only below, after every include, because Multi.addChild
		 * sorts by population on insertion and never again.
		 */
		plants.forEach((plantId, sewage) -> {
			String state = sewage.getState();
			if (state != null) {
				String region = regionList.getRegion(state);
				getRegionSewage(region).includeSewage(sewage, 1.0);
				getStateSewage(state).includeSewage(sewage, 1.0);
				String c = sewage.getCounties();
				if (c != null) {
					/*
					 * A blank counties_served splits into one empty name, which
					 * becomes a county named ""; see
					 * docs/active/findings/2026-09-10-a-blank-counties-served-makes-a-county-with-no-name.md.
					 */
					String[] countyNames = c.split(",");
					for (String county : countyNames) {
						sewage.County cSew = getCountySewage(county, state);
						cSew.includeSewage(sewage, 1.0 / countyNames.length);
						cSew.addChild(sewage);
					}
				}
			}
			geo.includeSewage(sewage, 1.0);
		});

		regions.values().forEach(sewage -> all.addChild(sewage));
		states.forEach((stateId, sewage) -> {
			String r = regionList.getRegion(stateId);
			sewage.Multi rSew = getRegionSewage(r);
			rSew.addChild(sewage);
		});
		counties.forEach((countyId, sewage) -> {
			sewage.State sSew = getStateSewage(sewage.getState());
			sSew.addChild(sewage);
		});

		System.out.println("Built combos in " + (System.currentTimeMillis() - time) / 1000 + "s.");
	}

	/**
	 * Draws every chart from what read() built, as tasks on the code pool,
	 * then shows the charts queued with library/OpenImage.java once all are
	 * written. The lineage task queues its own charts on the same ASync while
	 * it runs, and complete() waits for those too.
	 */
	public void build() {
		long time = System.currentTimeMillis();
		ChartSewage.mkdirs();
		states.forEach((id, sewage) -> ChartSewage.reportState(id));

		System.out.println("Built dirs in " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();

		ASync<Chart> build = new ASync<>();

		// build.execute(() -> ChartSewage.createSewage(geo));
		build.execute(() -> ChartSewage.createSewage(all, null));
		if (variants != null) {
			build.execute(() -> {
				for (Voc voc : variants) {
					try {
						VocSewage vocSewage = new VocSewage(all, voc);
						ChartSewage.buildVocSewageCharts(vocSewage, build);
						vocSewage.getLink();

						if (!voc.multiVariant || voc.lapis) {
							sewage.State colorado = states.get("Colorado");
							if (colorado != null) {
								ChartSewage.buildVocSewageCharts(new VocSewage(colorado, voc), build);
							}
						}
					} catch (Exception e) {
						/*
						 * Ends the run with status 0 while other chart tasks
						 * are still drawing; see
						 * docs/active/findings/2026-09-10-an-unreadable-cdc-download-ends-the-run-as-a-success.md.
						 */
						e.printStackTrace();
						System.exit(0);
					}
				}
			});
		}
		plants.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage, null)));
		counties.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage, null)));
		states.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage, 5)));
		regions.forEach((id, sewage) -> build.execute(() -> ChartSewage.createSewage(sewage, null)));
		build.complete();
		System.out.println("Built charts " + (System.currentTimeMillis() - time) / 1000 + "s.");
		time = System.currentTimeMillis();
		library.OpenImage.open();
	}

}
