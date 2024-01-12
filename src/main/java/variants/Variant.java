package variants;

import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import covid.CalendarUtils;

/**
 * A variant is a lineage PLUS some data on the lineage prevalence. Direct daily
 * prevalence data is included in the Voc for now though.
 */
public class Variant {
	/** Index name. */
	public final String name;

	public final String displayName;

	/** Lineage for this variant data, or null for odd queries. */
	public final Lineage lineage;

	/** Cumulative prevalence (ASUs) over the time period */
	public double cumulativePrevalence;

	/** Weighted average of which day this variant was on. */
	public double averageDay;

	private final HashMap<Integer, Double> daily = new HashMap<>();

	public double getPrevalence(int day) {
		Double prev = daily.get(day);
		if (prev == null) {
			return 0;
		}
		return prev;
	}

	public void setPrevalence(int day, double prevalence) {
		daily.put(day, prevalence);
	}

	public void subtractPrevalence(int day, double subPrevalence) {
		if (subPrevalence == 0) {
			return;
		}
		double num = getPrevalence(day);
		num -= subPrevalence;
		if (num < 0) {
			if (Math.abs(num) > VocSewage.MINIMUM) {
				System.out
						.println("Negative prevalence " + num + " on " + name + " for " + CalendarUtils.dayToDate(day));
			}
			daily.remove(day);
			return;
		}
		daily.put(day, num);
	}

	public static String displayName(String name) {
		return name.replaceAll("nextcladePangoLineage:", "");
	}

	public Variant(String name) {
		this.name = name;
		this.displayName = displayName(name);

		Pattern p = Pattern.compile("nextcladePangoLineage:([A-Za-z]+[.0-9]*)\\*");
		Matcher m = p.matcher(name);
		if (m.matches()) {
			// may still be null
			lineage = Lineage.get(m.group(1));
		} else {
			lineage = null;
		}
		// System.out.println("Lineage for " + name + " is "
		// + (lineage == null ? "N/A" : lineage.getFull() + " / " +
		// lineage.getAlias()));
	}

	public boolean isAncestor(Variant descendant) {
		return lineage.isAncestor(descendant.lineage);
	}

	public int getNumDays(int firstDay, int lastDay) {
		Set<Integer> keys = daily.keySet();
		keys.removeIf(day -> daily.get(day) <= VocSewage.MINIMUM || day < firstDay || day > lastDay);
		return keys.size();
	}

}