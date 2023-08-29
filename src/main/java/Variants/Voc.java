package Variants;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import covid.CalendarUtils;

public class Voc {

	private static final String CSV_NAME = "C:\\Users\\jdorj\\Downloads\\" + "VariantComparisonTimeDistributionPlot"
			+ ".csv";
	private static final Charset CHARSET = Charset.forName("US-ASCII");

	public static String OTHERS = "others";

	public Voc() {
		this(CSV_NAME);
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
						new Exception("Big fail negative others " + others + " on " + CalendarUtils.dayToDate(day))
								.printStackTrace();
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

	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;

	private synchronized void includeDay(int day) {
		firstDay = Math.min(day, firstDay);
		lastDay = Math.max(day, lastDay);
	}

	public synchronized int getFirstDay() {
		return firstDay;
	}

	public synchronized int getLastDay() {
		return lastDay;
	}

	public Voc(String csvName) {
		File f = new File(csvName);

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
				String variant = line.get(4);
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
		variantSet.add(OTHERS);

		for (String variant : variantSet) {
			double number = 0;
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				DayVariants entry = entries.get(day);
				if (entry != null) {
					number += entry.getPrevalence(variant);
				}
			}
			variantTot.put(variant, number);
		}

		variantList.addAll(variantSet);
		variantList.sort((v1, v2) -> -Double.compare(variantTot.get(v1), variantTot.get(v2)));

		System.out.println(String.format("%,d total variants", variantList.size() - 1));
		StringBuilder sb = new StringBuilder();
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
		}
		System.out.println(sb.toString());

	}

	public synchronized ArrayList<String> getVariants() {
		return variantList;
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
