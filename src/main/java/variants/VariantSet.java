package variants;

import java.util.Arrays;
import java.util.Calendar;

import covid.CalendarUtils;

public class VariantSet {

	private final String[] variants;
	private String[] variantsFull;
	private String[] variantQueries;
	private String[] variantDisplay;

	private final String startDate;
	private final String endDate;

	public static final String TODAY() {
		Calendar cal = CalendarUtils.timeToCalendar(System.currentTimeMillis());
		return String.format("%d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
				cal.get(Calendar.DAY_OF_MONTH));
	}

	public VariantSet(VariantEnum vEnum) {
		this(vEnum.startDate, vEnum.endDate, vEnum.variants);
	}

	public VariantSet(String startDate, String endDate, String... variants) {
		this.startDate = startDate;
		if (endDate == null) {
			endDate = TODAY();
		}
		this.endDate = endDate;
		this.variants = variants;
		variantsFull = new String[variants.length];
		variantQueries = new String[variants.length];
		variantDisplay = new String[variants.length];

		System.out.println("Variants: " + variants.length);

		Arrays.sort(variants, (s1, s2) -> s1.compareTo(s2));
		boolean exit = false;
		for (int i = 0; i < variants.length; i++) {
			variants[i] = variants[i].toLowerCase();
			if (variants[i].contains("*")) {
				System.out.println("Illegal " + variants[i]);
			}

			variantsFull[i] = variants[i];
			for (String prefix : Aliases.aliases.keySet()) {
				if (variantsFull[i].startsWith(prefix)) {
					variantsFull[i] = variantsFull[i].replaceAll(prefix, Aliases.aliases.get(prefix));
				}
			}

			if (!variantsFull[i].startsWith("x") & !variantsFull[i].startsWith("b.")
					& !variantsFull[i].startsWith("a.")) {
				System.out.println("Missing: " + variantsFull[i]);
				exit = true;
			}

			variantQueries[i] = variants[i] + "*";
			variantDisplay[i] = variants[i];
		}

		if (exit) {
			System.exit(0);
		}

		for (int i = 0; i < variants.length; i++) {
			String parent = variantsFull[i];
			for (int j = 0; j < variants.length; j++) {
				if (i == j) {
					continue;
				}
				if (variantsFull[i].equalsIgnoreCase(variantsFull[j])) {
					new Exception("Duplicate variants " + i + " and " + j + " : " + variants[i] + " and " + variants[j])
							.printStackTrace();
				}
				String child = variantsFull[j];
				if (i != j && child.startsWith(parent)) {
					variantQueries[i] += "%26!nextcladePangoLineage:" + variants[j] + "*";

					if (!variantDisplay[i].contains("(")) {
						variantDisplay[i] += " except ";
					} else {
						variantDisplay[i] += ",";
					}
					variantDisplay[i] += variants[j];
				}
			}
		}
	}

	public String getCovSpectrumLink() {
		StringBuilder sb = new StringBuilder();
		sb.append("https://cov-spectrum.org/explore/United%20States/AllSamples/");
		sb.append("from=" + startDate + "%26to=" + endDate);
		sb.append("/variants?analysisMode=CompareEquals&");

		for (int i = 0; i < variants.length; i++) {
			// System.out.println(variants[i] + " => " + variantsFull[i] + " =>
			// " + variantQueries[i]);
			sb.append("variantQuery");
			if (i > 0) {
				sb.append(String.valueOf(i));
			}
			sb.append("=nextcladePangoLineage:");
			sb.append(variantQueries[i]);
			sb.append("&");
		}

		System.out.println(sb.toString());

		return sb.toString();
	}

	public String getCovSpectrumLink2(boolean all) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < variants.length; i++) {
			if (sb.length() > 0) {
				sb.append("&");
			}
			sb.append("!nextcladePangoLineage:");
			sb.append(variants[i]);
			if (all) {
				sb.append("*");
			}
		}

		System.out.println(sb.toString());

		return sb.toString();
	}
}
