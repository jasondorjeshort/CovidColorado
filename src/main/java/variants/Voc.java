package variants;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import covid.CalendarUtils;
import covid.DailyTracker;
import nwss.Nwss;

public class Voc extends DailyTracker {

	private static final String CSV_NAME1 = "C:\\Users\\jdorj\\Downloads\\" + "VariantComparisonTimeDistributionPlot";
	private static final String CSV_NAME2 = "C:\\Users\\jdorj\\Downloads\\" + "VariantTimeDistributionPlot";
	private static final Charset CHARSET = Charset.forName("US-ASCII");

	public static final String OTHERS = "others";

	public final boolean isMerger;
	public boolean exclusions = false;

	private static File csv1(int i) {
		return new File(CSV_NAME1 + (i == 0 ? "" : "(" + i + ")") + ".csv");
	}

	private static File csv2(int i) {
		return new File(CSV_NAME2 + (i == 0 ? "" : "(" + i + ")") + ".csv");
	}

	public static LinkedList<Voc> create() {
		LinkedList<Voc> vocs = new LinkedList<>();
		File f;
		LinkedList<File> files = new LinkedList<>();

		int i = 0;
		f = csv1(i);
		if (f.exists()) {
			files.add(f);
			while (true) {
				i++;
				File f2 = csv1(i);
				if (!f2.exists()) {
					break;
				}
				files.add(f2);
			}
			if (System.currentTimeMillis() - f.lastModified() > 8 * Nwss.HOUR) {
				System.out.println("Deleting " + f.getPath() + ", age "
						+ (System.currentTimeMillis() - f.lastModified()) / Nwss.HOUR + "h.");
				files.forEach(file -> file.delete());
			} else {
				vocs.add(new Voc(files, true));
			}
		}

		files.clear();
		i = 0;
		f = csv2(i);
		if (f.exists()) {
			files.add(f);
			while (true) {
				i++;
				File f2 = csv2(i);
				if (!f2.exists()) {
					break;
				}
				files.add(f2);
			}
			if (System.currentTimeMillis() - f.lastModified() > 8 * Nwss.HOUR) {
				System.out.println("Deleting " + f.getPath() + ", age "
						+ (System.currentTimeMillis() - f.lastModified()) / Nwss.HOUR + "h.");
				files.forEach(file -> file.delete());
			} else {
				vocs.add(new Voc(files, false));
			}
		}

		return vocs;
	}

	public static class DayVariants {
		final HashMap<String, Double> variants = new HashMap<>();
		final int day;

		public DayVariants(int day) {
			this.day = day;
		}

		public synchronized double getPrevalence(String variant) {
			Double v = variants.get(variant);
			if (v == null) {
				if (variant.equals(OTHERS)) {
					double others = 1.0;
					for (Double prev : variants.values()) {
						others -= prev;
					}
					if (others < 0) {
						/*
						 * Ideally others can just be zero, but due to rounding
						 * it could then be slightly negative.
						 */
						if (others < -1E-15) {
							new Exception("Big fail negative others " + others + " on " + CalendarUtils.dayToDate(day))
									.printStackTrace();
						}
						others = 0.0;
					}
					variants.put(OTHERS, others);
					return others;
				}
				return 0.0;
			}

			return v;
		}
	}

	private final HashMap<Integer, DayVariants> entries = new HashMap<>();

	private HashMap<String, Variant> variants = new HashMap<>();

	public Variant getVariant(String name) {
		synchronized (variants) {
			Variant var = variants.get(name);
			if (var == null) {
				var = new Variant(name);
				variants.put(name, var);
			}
			return var;
		}
	}

	public final int id;

	private static int nextId = 1;
	private static final Object nextIdLock = new Object();
	
	public final boolean multiVariant;

	public Voc(List<File> files, boolean multiVariant) {
		isMerger = false;
		synchronized (nextIdLock) {
			id = nextId++;
		}
		int fNumber = 0;
		this.multiVariant = multiVariant;
		for (File f : files) {
			fNumber++;
			try (CSVParser csv = CSVParser.parse(f, CHARSET, CSVFormat.DEFAULT)) {
				int records = 0;
				for (CSVRecord line : csv) {
					if (records++ == 0) {
						continue;
					}

					String date = line.get(0);
					String proportion = line.get(1);
					if (proportion == null || proportion.equalsIgnoreCase("null")) {
						continue;
					}
					String variant;
					if (multiVariant) {
						variant = line.get(4);
					} else {
						variant = "Variant " + fNumber;
					}
					int day = CalendarUtils.dateToDay(date);

					DayVariants entry = entries.get(day);
					if (entry == null) {
						entry = new DayVariants(day);
						entries.put(day, entry);
					}
					double prev = Double.valueOf(proportion);
					if (entry.variants.get(variant) != null) {
						System.out.println("Duplicate variant " + variant + "?");
					}
					entry.variants.put(variant, prev);
					includeDay(day);
					getVariant(variant);
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}

		synchronized (variants) {
			if (variants.size() > 1) {
				getVariant(OTHERS);
			}
		}

		build();
	}

	boolean built = false;

	private synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		/*
		 * This is specifically for single-variant graphs. If there aren't any
		 * sequences for days near the end of the query, we want to ignore those
		 * days and show the data cutoff sooner.
		 */
		while (true) {
			int last = getLastDay();
			if (last < getFirstDay()) {
				break;
			}

			DayVariants entry = entries.get(last);
			if (entry != null) {
				double prev = 0.0;
				for (String variant : variants.keySet()) {
					if (variant.equalsIgnoreCase(OTHERS)) {
						continue;
					}
					prev += entry.getPrevalence(variant);
				}
				if (prev > 0.0) {
					break;
				}
			}
			dropLastDay();
		}

		/*
		 * If lineages are known, lets subtract off child from parent.
		 */
		ArrayList<Variant> myList = new ArrayList<>(variants.size());
		for (Variant v : variants.values()) {
			if (v.lineage != null) {
				myList.add(v);
			}
		}
		myList.sort((v1, v2) -> Integer.compare(v1.lineage.getFull().length(), v2.lineage.getFull().length()));
		for (Variant v : myList) {
			// System.out.println(" ==> " + v.lineage.getFull() + " -> " +
			// v.lineage.getAlias());
		}
		for (int i = myList.size() - 1; i >= 0; i--) {
			Variant child = myList.get(i);

			for (int j = 0; j < i; j++) {
				Variant parent = myList.get(j);
				if (!parent.isAncestor(child)) {

					// System.out.println("NO Child: " +
					// child.lineage.getAlias() + " <-> " +
					// parent.lineage.getAlias());
					continue;
				}

				for (int day = getFirstDay(); day <= getLastDay(); day++) {
					DayVariants entry = entries.get(day);
					if (entry == null) {
						continue;
					}

					double num = entry.variants.get(parent.name);
					num -= entry.variants.get(child.name);
					entry.variants.put(parent.name, num);
				}
				exclusions = true;
			}
		}
		// System.exit(0);

		/*
		 * Build the variant data directly: cumulative prevalence and average
		 * time
		 */
		for (Variant variant : variants.values()) {
			variant.cumulativePrevalence = 0;
			double totalDay = 0;
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				DayVariants entry = entries.get(day);
				if (entry != null) {
					double prev = entry.getPrevalence(variant.name);
					variant.cumulativePrevalence += prev;
					totalDay += day * prev;
				}
			}
			variant.averageDay = totalDay / variant.cumulativePrevalence;
		}

		/*
		 * Output (display)
		 */
		System.out.println(String.format("%,d total variants", variants.size() - 1));
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		for (Variant variant : variants.values()) {
			if (variant.name.equals(Voc.OTHERS)) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append("&");
			}
			sb.append("!(");
			sb.append(variant);
			sb.append(")");

			if (sb2.length() > 0) {
				sb2.append(",");
			}
			sb2.append("\"");
			sb2.append(variant.displayName);
			sb2.append("\"");

			// System.out.println(variant.name + " : " +
			// CalendarUtils.dayToDate(variant.averageDay));
		}
		System.out.println(sb.toString());
		System.out.println(sb2.toString());
	}

	public static String display(String variant) {
		return variant.replaceAll("nextcladePangoLineage:", "");
	}

	public Voc(VocSewage parent, LinkedList<String>[] variants) {
		isMerger = true;
		synchronized (nextIdLock) {
			id = nextId++;
		}
		includeDay(parent.getFirstDay());
		includeDay(parent.getLastDay());

		int num = variants.length;
		String[] vNames = new String[num];
		this.multiVariant = true;
		for (int v = 0; v < vNames.length; v++) {
			vNames[v] = "Tier " + (v + 1);
			getVariant(vNames[v]);
			System.out.println(String.format("%s (%d)", vNames[v], variants[v].size()));
			for (String var : variants[v]) {
				String n = display(var);
				System.out.println(String.format("  %s (%+f%%/week, %.2f%% prevalence)", n, parent.getGrowth(var),
						100.0 * parent.getPercentage(var)));
			}
		}

		// int debugDay = (getFirstDay() + getLastDay()) / 2;

		for (int day = getFirstDay(); day <= getLastDay(); day++) {
			DayVariants entry = parent.voc.entries.get(day);
			if (entry == null) {
				continue;
			}

			DayVariants entry2 = new DayVariants(day);
			entries.put(day, entry2);

			for (int v = 0; v < variants.length; v++) {
				LinkedList<String> vList = variants[v];
				String variant = vNames[v];
				double totalPrev = 0.0;
				for (String vParent : vList) {
					double prev = parent.voc.entries.get(day).getPrevalence(vParent);
					// if (day == debugDay) {
					// System.out.println("Parent prev for " + vParent + " is "
					// + prev);
					// }
					totalPrev += prev;
				}
				// if (day == debugDay) {
				// System.out.println("Total prev for " + variant + " is " +
				// totalPrev);
				// }
				entry2.variants.put(variant, totalPrev);
			}
		}

		build();
	}

	public synchronized ArrayList<Variant> getVariants() {
		return new ArrayList<>(variants.values());
	}

	public Collection<Variant> getVariantsInline() {
		return variants.values();
	}

	public synchronized ArrayList<String> getVariantNames() {
		return new ArrayList<>(variants.keySet());
	}

	public synchronized int numVariants() {
		return variants.size();
	}

	public double getPrevalence(int day, String variant) {
		DayVariants dv;

		synchronized (this) {
			dv = entries.get(day);
		}
		if (dv == null) {
			return variant.equalsIgnoreCase(OTHERS) ? 1.0 : 0.0;
		}
		Double prev = dv.getPrevalence(variant);
		return prev;
	}
}
