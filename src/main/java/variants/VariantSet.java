package variants;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;

import covid.CalendarUtils;

public class VariantSet {

	private HashMap<String, String> aliases = new HashMap<>() {
		private static final long serialVersionUID = -5540557963380925809L;

		{
			put("eg", "xbb.1.9.2");
			put("fl", "xbb.1.9.1");
			put("hv", "xbb.1.9.2.5.1.6");
			put("gj", "xbb.2.3.3");
			put("fu", "xbb.1.16.1");
			put("hh", "XBB.2.3.2");
			put("fd", "XBB.1.5.15");
			put("fe", "XBB.1.18.1");
			put("ge", "XBB.2.3.10");
			put("he", "XBB.1.18.1.1.1.1");
			put("hf", "XBB.1.16.13");
			put("hz", "XBB.1.5.68");
			put("hn", "XBB.1.9.1.1.5.1");
			put("bq", "B.1.1.529.5.3.1.1.1.1");
			put("fk", "B.1.1.529.2.75.3.4.1.1.1.1.17");
			put("ch", "B.1.1.529.2.75.3.4.1.1");
			put("hk", "XBB.1.9.2.5.1.1");
			put("gk", "XBB.1.5.70");
			put("gw", "XBB.1.19.1");
			put("dv", "B.1.1.529.2.75.3.4.1.1.1.1.1");
			put("fy", "xbb.1.22.1");
			put("gy", "XBB.1.16.2");
			put("eu", "XBB.1.5.26");
			put("ba", "b.1.1.529");
			put("gn", "XBB.1.5.73");

			for (String key : keySet()) {
				put(key, get(key).toLowerCase());
			}
		}
	};

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
			for (String prefix : aliases.keySet()) {
				if (variantsFull[i].startsWith(prefix)) {
					variantsFull[i] = variantsFull[i].replaceAll(prefix, aliases.get(prefix));
				}
			}

			if (!variantsFull[i].startsWith("xbb") & !variantsFull[i].startsWith("b.")) {
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
