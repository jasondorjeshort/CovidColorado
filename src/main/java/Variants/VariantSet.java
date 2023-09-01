package Variants;

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

			for (String key : keySet()) {
				put(key, get(key).toLowerCase());
			}
		}
	};

	public static final String[] APRIL_TO_SEPTEMBER_VARIANTS = new String[] { "xbb.1.5", "xbb.1.16", "xbb.1.16.6",
			"eg.5.1", "eg.5.1.1", "xbb.1.16.1", "xbb.1.22", "fl.1.5.1", "gj.1.2", "xbb.2.3", "xbb.1.5.10", "xbb.1.5.72",
			"fu.1", "xbb.1.16.11", "eg.6.1", "xbb.1.9.1", "eg.5.1.3", "ge.1", "xbb.2.3.2", "eg.5.1.4", "fl.4",
			"xbb.1.5.49", "xbb.1.42.2", "xbb.1.9.2", "eg.1", "hf.1", "hv.1", "fd.1.1", "fu.2.1", "HH.1", "fu.2",
			"fe.1.2", "he.1", "eg.5.2", "eg.5.1.6", "xbb.1.16.2", "fl.15", "xbb.1.5.77", "hz.1", "xbb.2.3.8", "fl.2",
			"bq.1", "xbb.1", "fk.1.1", "ch.1.1" };
	public static final String[] JUNE_TO_SEPTEMBER_VARIANTS = new String[] { "xbb.1.16", "eg.5.1.1", "hv.1", "xbb.1.5",
			"xbb.1.16.1", "eg.5.1", "xbb.1.16.6", "xbb.2.3", "gj.1.2", "xbb.1.5.10", "fl.1.5.1", "xbb.1.9.1", "fu.1",
			"xbb.1.5.72", "fl.4", "eg.5.1.3", "xbb.1.5.49", "eg.1", "xbb.1.16.11", "xbb.2.3.2", "ge.1", "eg.5.1.4",
			"xbb.1.42.2", "he.1", "fd.1.1", "fu.2", "hf.1", "eg.5.2", "fu.2.1", "xbb.1.22", "fl.2", "eg.6.1",
			"xbb.1.9.2", "hh.1", "fe.1.2", "xbb.1.16.2", "hz.1", "fl.15", "eg.5.1.6", "xbb.2.3.8", "xbb.1.5.77", "gk.2",
			"gy.5", "fk.1.1", "fy.5", "eg.5.2.1", "xbb.1.5.28", "xbb.2.3.3" };
	public static final String[] FLIP_VARIANTS = new String[] { "GK.2", "GK.1", "XBB.1.5.70", "GK.1.3", "GK.1.1",
			"GK.3", "DV.7.1", "GK.3.1", "HK.3", "GW.5" };

	public static final String[] APRIL_VARIANTS = { "xbb.1.5", "xbb.1.16", "xbb.1.9.1", "xbb.1.16.1", "xbb.1.5.49",
			"xbb.1.5.1", "fd.2", "xbb.1.5.15", "xbb.1.5.10", "xbb.1.5.13", "xbb.1.9.2", "xbb.2.3", "xbb.1.5.17", "fl.4",
			"eg.1", "xbb.1.5.4", "xbb.1.5.35", "fl.2", "fu.1", "xbb.1.5.32", "xbb.1.5.30", "xbb.1.5.37", "xbb.1.5.51",
			"xbb.1.5.20", "xbb.1.5.33", "xbb.2.3.2", "xbb.1.5.73", "xbb.1.16.2", "xbb.1.5.21", "eu.1.1", "xbb.1.5.66",
			"xbb.1.5.31", "xbb.1.5.95", "xbb.1.5.91", "xbb.1.5.48", "fl.5", "xbb.1.5.2", "xbb.1.5.52", "xbb.1.5.16",
			"xbb.1.5.77", "fd.4", "gj.1.2", "he.1", "eg.4", "eg.5.1", "xbb.1.5.28", "xbb.1.5.69", "xbb.1.5.100",
			"xbb.1.5.67" };

	public static final String APRIL_1 = "2023-04-01";
	public static final String JUNE_15 = "2023-06-15";

	private final String[] variants;
	private String[] variantsFull;
	private String[] variantQueries;
	private String[] variantDisplay;

	private final String startDay;
	private final String endDay;

	private static final Calendar TODAY_CAL = CalendarUtils.timeToCalendar(System.currentTimeMillis());
	public static final String TODAY = String.format("%d-%02d-%02d", TODAY_CAL.get(Calendar.YEAR),
			TODAY_CAL.get(Calendar.MONTH) + 1, TODAY_CAL.get(Calendar.DAY_OF_MONTH));

	public VariantSet(String startDay, String endDay, String... variants) {
		this.startDay = startDay;
		this.endDay = endDay;
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

			if (!variantsFull[i].startsWith("xbb.") & !variantsFull[i].startsWith("b.")) {
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
		sb.append("from=" + startDay + "%26to=" + endDay);
		sb.append("/variants?analysisMode=CompareEquals&");

		for (int i = 0; i < variants.length; i++) {
			System.out.println(variants[i] + " => " + variantsFull[i] + " => " + variantQueries[i]);
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

	public String getCovSpectrumLink2() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < variants.length; i++) {
			if (sb.length() > 0) {
				sb.append("&");
			}
			sb.append("!nextcladePangoLineage:");
			sb.append(variants[i]);
		}

		System.out.println(sb.toString());

		return sb.toString();
	}
}
