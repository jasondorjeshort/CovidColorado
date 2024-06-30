package variants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import covid.CalendarUtils;

public class LSet {

	private final String[] variants;
	private String[] variantsFull;
	private String[] variantQueries;
	private String[] variantDisplay;

	private final String startDate;
	private final String endDate;

	public static final String TODAY(int offset) {
		Calendar cal = CalendarUtils.timeToCalendar(System.currentTimeMillis() + offset * 24l * 60l * 60l * 1000l);
		return String.format("%d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
				cal.get(Calendar.DAY_OF_MONTH));
	}

	public static final String TODAY() {
		return TODAY(0);
	}

	public LSet(LEnum vEnum) {
		this(vEnum.startDate, vEnum.endDate, vEnum.variants);
	}

	public LSet(String startDate, String endDate, String... variants) {
		this.startDate = startDate;
		if (endDate == null) {
			endDate = TODAY(-10);
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
			variants[i] = Aliases.simplify(variants[i]);
			if (variants[i].contains("*")) {
				System.out.println("Illegal " + variants[i]);
				exit = true;
			}

			variantsFull[i] = Aliases.expand(variants[i]);
			exit |= variantsFull[i] == null;

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

	public ArrayList<String> getCovSpectrumLink() {
		ArrayList<String> list = new ArrayList<>();
		StringBuilder sb = null;
		int n = 0;

		for (int i = 0; i < variants.length; i++) {
			if (sb == null) {
				sb = new StringBuilder();
				sb.append("https://cov-spectrum.org/explore/United%20States/AllSamples/");
				sb.append("from=" + startDate + "%26to=" + endDate);
				sb.append("/variants?analysisMode=CompareEquals&");
				n = 0;
			}
			// System.out.println(variants[i] + " => " + variantsFull[i] + " =>
			// " + variantQueries[i]);
			sb.append("variantQuery");
			if (n > 0) {
				sb.append(String.valueOf(n));
			}
			sb.append("=nextcladePangoLineage:");
			sb.append(variants[i]);
			sb.append("*");
			sb.append("&");

			if (sb.length() > 5500) {
				list.add(sb.toString());
				sb = null;
			}
			n++;
		}

		if (sb != null) {
			list.add(sb.toString());
		}

		sb = new StringBuilder();
		sb.append("\n");
		for (String s2 : list) {
			sb.append(s2);
			sb.append("\n");
		}
		System.out.println(sb.toString());

		return list;
	}

	public String getCovSpectrumReverseLink(boolean all) {
		StringBuilder sb = new StringBuilder();
		// sb.append("https://cov-spectrum.org/explore/United%20States/AllSamples/");
		// sb.append("from=" + startDate + "%26to=" + endDate);
		// sb.append("/variants?variantQuery=");
		for (int i = 0; i < variants.length; i++) {
			if (i > 0) {
				sb.append("&");
				// sb.append("%26");
			}
			sb.append("!nextcladePangoLineage:");
			sb.append(variants[i]);
			if (all) {
				sb.append("*");
				// sb.append("%2A");
			}
		}

		System.out.println(sb.toString());

		return sb.toString();
	}
}
