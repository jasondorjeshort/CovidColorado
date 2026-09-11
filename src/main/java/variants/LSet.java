package variants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;

import covid.CalendarUtils;

/**
 * A set of lineages and a date window, printed as cov-spectrum links for the
 * manual export path (docs/reference/lineages.txt, THE MANUAL PATH): the
 * maintainer opens a link, exports its CSV to the downloads folder, and Voc
 * create() reads it. Its output is console only. Its two callers, Nwss read()
 * for each LEnum and VocSewage getLink(), use nothing it returns beyond what it
 * prints.
 * <p>
 * Each lineage is queried as Lineage.getQuery(), inclusive of its descendants:
 * the form Variant(String) resolves back to a lineage, and the one Voc
 * build()'s child subtraction expects.
 */
public class LSet {

	private final HashSet<Lineage> lineages = new HashSet<>();

	private final String startDate;
	private final String endDate;

	/**
	 * The machine's local date, offset by whole days, as YYYY-MM-DD. Being the
	 * local date it is the same day CalendarUtils.today() gives, rather than the
	 * UTC day CalendarUtils.timeToDay(now) rolls over to at 18:00.
	 */
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

	/** A null, or a lineage already in the set, prints a stack trace and is skipped. */
	public void addLineage(Lineage lineage) {
		if (lineage == null) {
			new Exception("Null lineage.").printStackTrace();
			return;
		}
		if (lineages.contains(lineage)) {
			new Exception("Duplicate lineages " + lineage.getAlias()).printStackTrace();
			return;
		}
		lineages.add(lineage);

	}

	public void addLineage(String lName) {
		Lineage lineage = Lineage.get(lName);
		if (lineage == null) {
			new Exception("Can't read lineage " + lName + ".").printStackTrace();
			return;
		}
		addLineage(lineage);
	}

	/**
	 * Dates are YYYY-MM-DD and go into the URL as given; a null endDate is
	 * TODAY(-10), the lag Lapis LAG_DAYS was set to match. A name Lineage.get
	 * cannot resolve prints a stack trace and is left out of every link, the
	 * reverse one included, so its sequences fall in the reverse query's set.
	 */
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

	/**
	 * Comparison URLs over the United States and this set's window, one
	 * variantQuery per lineage, printed and returned. A long set is split
	 * across several URLs, each numbering its queries from variantQuery again;
	 * each gives its own export, and Voc create() reads the numbered exports
	 * of one name into a single Voc. Lineages come in HashSet order, which
	 * differs between runs, and so does the split.
	 */
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
			sb.append("variantQuery");
			if (n > 0) {
				sb.append(String.valueOf(n));
			}
			sb.append("=");
			sb.append(lineage.getQuery());
			sb.append("&");

			/*
			 * A URL is closed once past this, so it may run one query over.
			 * 5500 replaced 3000 in January 2024 and no reason was recorded.
			 */
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

	/**
	 * Not a link: the query for every sequence outside all of this set's
	 * lineages, {@code !A*&!B*...}, printed and returned for pasting into
	 * cov-spectrum's query box. It carries no location or dates; those come
	 * from the page it is pasted into. Its set is the complement of the union
	 * of the forward links' queries.
	 */
	public String getCovSpectrumReverseLink() {
		StringBuilder sb = new StringBuilder();
		// sb.append("https://cov-spectrum.org/explore/United%20States/AllSamples/");
		// sb.append("from=" + startDate + "%26to=" + endDate);
		// sb.append("/variants?variantQuery=");
		for (Lineage lineage : lineages) {
			/*
			 * Between terms only: LAPIS's variant-query parser rejects a
			 * trailing '&' ("mismatched input '<EOF>'", probed on the open
			 * endpoint on 2026-09-10).
			 */
			if (sb.length() > 0) {
				sb.append("&");
				// sb.append("%26");
			}
			sb.append("!");
			sb.append(lineage.getQuery());
		}

		System.out.println(sb.toString());

		return sb.toString();
	}
}
