package variants;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import covid.CalendarUtils;
import covid.DailyTracker;
import nwss.Nwss;

public class Voc extends DailyTracker {

	private static final String CSV_NAME1 = "C:\\Users\\jdorj\\Downloads\\" + "VariantComparisonTimeDistributionPlot"
			+ ".csv";
	private static final String CSV_NAME2 = "C:\\Users\\jdorj\\Downloads\\" + "VariantTimeDistributionPlot" + ".csv";
	private static final Charset CHARSET = Charset.forName("US-ASCII");

	public static String OTHERS = "others";

	public final boolean isMerger;

	public static LinkedList<Voc> create() {
		LinkedList<Voc> vocs = new LinkedList<>();
		File f;

		f = new File(CSV_NAME1);
		if (f.exists()) {
			if (System.currentTimeMillis() - f.lastModified() > 8 * Nwss.HOUR) {
				System.out.println("Deleting " + f.getPath() + ", age "
						+ (System.currentTimeMillis() - f.lastModified()) / Nwss.HOUR + "h.");
				f.delete();
			} else {
				vocs.add(new Voc(f, true));
			}
		}

		f = new File(CSV_NAME2);
		if (f.exists()) {
			if (System.currentTimeMillis() - f.lastModified() > 8 * Nwss.HOUR) {
				System.out.println("Deleting " + f.getPath() + ", age "
						+ (System.currentTimeMillis() - f.lastModified()) / Nwss.HOUR + "h.");
				f.delete();
			} else {
				vocs.add(new Voc(f, false));
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
	private HashMap<String, Double> variantTot = new HashMap<>();
	private final ArrayList<String> variantList = new ArrayList<>();
	public final int id;

	private static int nextId = 1;
	private static final Object nextIdLock = new Object();

	public Voc(File f, boolean multiVariant) {
		isMerger = false;
		synchronized (nextIdLock) {
			id = nextId++;
		}
		HashSet<String> variantSet = new HashSet<>();
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
					variant = "Variant";
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
				variantSet.add(variant);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
		if (multiVariant) {
			variantSet.add(OTHERS);
		}

		variantList.addAll(variantSet);
		makeVariantTot();
		variantList.sort((v1, v2) -> -Double.compare(variantTot.get(v1), variantTot.get(v2)));

		System.out.println(String.format("%,d total variants", variantList.size() - 1));
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		for (String variant : variantList) {
			if (variant.equals(Voc.OTHERS)) {
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
			sb2.append(variant.replaceAll("nextcladePangoLineage:", ""));
			sb2.append("\"");
		}
		System.out.println(sb.toString());
		System.out.println(sb2.toString());
	}

	private void makeVariantTot() {
		for (String variant : variantList) {
			double number = 0;
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				DayVariants entry = entries.get(day);
				if (entry != null) {
					number += entry.getPrevalence(variant);
				}
			}
			variantTot.put(variant, number);
		}
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
		for (int v = 0; v < vNames.length; v++) {
			vNames[v] = "Tier " + (v + 1);
			variantList.add(vNames[v]);
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

		makeVariantTot();
	}

	public synchronized ArrayList<String> getVariants() {
		return new ArrayList<>(variantList);
	}

	public synchronized int numVariants() {
		return variantList.size();
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
