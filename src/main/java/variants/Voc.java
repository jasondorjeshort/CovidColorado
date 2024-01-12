package variants;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

	private HashSet<Variant> variants = new HashSet<>();

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
		HashMap<String, Variant> variantMap = new HashMap<>();
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
					String variantName;
					if (multiVariant) {
						variantName = line.get(4);
					} else {
						variantName = "Variant " + fNumber;
					}
					int day = CalendarUtils.dateToDay(date);

					Variant variant = variantMap.get(variantName);
					if (variant == null) {
						variant = new Variant(variantName);
						variantMap.put(variantName, variant);
					}

					double prev = Double.valueOf(proportion);
					variant.setPrevalence(day, prev);
					includeDay(day);
					variants.add(variant);
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
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

			double prev = 0.0;
			for (Variant variant : variants) {
				prev += variant.getPrevalence(last);
			}
			if (prev > 0.0) {
				break;
			}
			dropLastDay();
		}

		/*
		 * Remove variants without enough prevalence.
		 * 
		 * TODO: this should go below the lineage subtraction, which then
		 * requires adding back on any removed lineages to the closest parent.
		 * 
		 * Current prevalence requirements are just 10 days of data. With weekly
		 * smoothing this requires at least 2 sequences separated by ~3 days.
		 */
		int num = variants.size();
		variants.removeIf(variant -> variant.getNumDays(getFirstDay(), getLastDay()) < 10);
		num -= variants.size();
		System.out.println("Removed " + num + " variants.");

		/*
		 * If lineages are known, lets subtract off child from parent.
		 */
		ArrayList<Variant> myList = new ArrayList<>(variants.size());
		for (Variant v : variants) {
			if (v.lineage != null) {
				myList.add(v);
			}
		}
		myList.sort((v1, v2) -> Integer.compare(v1.lineage.getFull().length(), v2.lineage.getFull().length()));
		// for (Variant v : myList) {
		// System.out.println(" ==> " + v.lineage.getFull() + " -> " +
		// v.lineage.getAlias());
		// }
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
					parent.subtractPrevalence(day, child.getPrevalence(day));
				}
				exclusions = true;
			}
		}

		/*
		 * Build the variant data directly: cumulative prevalence and average
		 * time
		 */
		for (Variant variant : variants) {
			variant.cumulativePrevalence = 0;
			double totalDay = 0;
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				double prev = variant.getPrevalence(day);
				variant.cumulativePrevalence += prev;
				totalDay += day * prev;
			}
			variant.averageDay = totalDay / variant.cumulativePrevalence;
		}

		/*
		 * Output (display)
		 */
		System.out.println(String.format("%,d total variants", variants.size() - 1));
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		for (Variant variant : variants) {
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

		if (variants.size() > 1) {
			Variant others = new Variant("others");
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				others.setPrevalence(day, 1.0);
				for (Variant variant : variants) {
					others.subtractPrevalence(day, variant.getPrevalence(day));
				}
			}
			variants.add(others);
		}
	}

	public Collection<Variant> getVariants() {
		return variants;
	}

	public synchronized int numVariants() {
		return variants.size();
	}
}
