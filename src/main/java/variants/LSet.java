package variants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;

import covid.CalendarUtils;

public class LSet {

	private final HashSet<Lineage> lineages = new HashSet<>();

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
		this(vEnum.startDate, vEnum.endDate, vEnum.lineages);
	}

	public void addLineage(String lName) {
		Lineage lineage = Lineage.get(lName);
		if (lineage == null) {
			new Exception("Can't read lineage " + lName + ".").printStackTrace();
			return;
		}

		if (lineages.contains(lineage)) {
			new Exception("Duplicate lineages " + lineage.getAlias()).printStackTrace();
			return;
		}
		lineages.add(lineage);
	}

	public LSet(String startDate, String endDate, String... lNames) {
		this.startDate = startDate;
		if (endDate == null) {
			endDate = TODAY(-10);
		}
		this.endDate = endDate;
		for (String lName : lNames) {
			addLineage(lName);
		}

		System.out.println("Variants: " + lineages.size());
	}

	public ArrayList<String> getCovSpectrumLink() {
		ArrayList<String> list = new ArrayList<>();
		StringBuilder sb = null;
		int n = 0;

		for (Lineage lineage : lineages) {
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
			sb.append("=");
			sb.append(lineage.getQuery());
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

	public String getCovSpectrumReverseLink() {
		StringBuilder sb = new StringBuilder();
		// sb.append("https://cov-spectrum.org/explore/United%20States/AllSamples/");
		// sb.append("from=" + startDate + "%26to=" + endDate);
		// sb.append("/variants?variantQuery=");
		for (Lineage lineage : lineages) {
			sb.append("!");
			sb.append(lineage.getQuery());
			sb.append("&");
			// sb.append("%26");
		}

		System.out.println(sb.toString());

		return sb.toString();
	}
}
