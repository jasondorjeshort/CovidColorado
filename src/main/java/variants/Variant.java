package variants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import covid.CalendarUtils;

/**
 * One line on a lineage chart: a name, usually a {@link Lineage}, and that
 * lineage's prevalence by day as a fraction of the day's sequences.
 * <p>
 * Prevalence arrives inclusive -- a cov-spectrum "X*" query, or Lapis's roll-up
 * of every descendant into each ancestor -- and Voc build() makes it exclusive
 * by subtracting each child out of its ancestors. From then on a variant
 * stands for its lineage less the descendants that have variants of their own,
 * and {@link #add} is how one of those is folded back in.
 * <p>
 * Mutable, and compared by identity: VocSewage keys its fits and totals by the
 * instance. One Voc is charted against more than one sewage series, and a
 * merge mutates both variants it touches, so each VocSewage works on its own
 * {@link #duplicate()} of every Voc variant. Not thread-safe: all mutation is
 * done by the time the Voc or VocSewage holding a variant is constructed, and
 * chart threads only read.
 */
public class Variant {

	/**
	 * Name of the catch-all bucket, which {@link #add} accepts anything into.
	 * Compared ignoring case: Voc build() names its bucket "others" and
	 * VocSewage build() names the one it creates "Others".
	 */
	public static final String OTHERS = "Others";

	/**
	 * What the variant was built from: the query text for a LAPIS variant or for
	 * a parent VocSewage build() manufactures ("nextcladePangoLineage:JN.1*"),
	 * the variant column of a multi-variant export, or a bare label ("others",
	 * "Variant 1"). Identifies the Others bucket, and is what
	 * {@link #duplicate()} re-parses.
	 */
	public final String name;

	/**
	 * Legend label. With its star stripped it is also part of the per-variant
	 * chart's filename.
	 */
	public final String displayName;

	/**
	 * Null when the name is not a single-lineage query or its alias does not
	 * resolve. A variant with no lineage takes no part in the child
	 * subtraction, is never merged away, and has no strain.
	 */
	public final Lineage lineage;

	/**
	 * Sum of the daily prevalence over the day range {@link #computeTotals} was
	 * last given, and used only to weight averageDay. Not sewage-weighted;
	 * VocSewage keeps its own totals.
	 */
	public double cumulativePrevalence;

	/**
	 * Prevalence-weighted mean day, from {@link #computeTotals}, and NaN for a
	 * variant with no prevalence in the range. It orders the non-fit relative
	 * chart, which is why VocSewage build() recomputes it once merging has
	 * moved prevalence around rather than leaving Voc build()'s value.
	 */
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

	/**
	 * Takes a child's prevalence out of this ancestor's for one day. A result
	 * below zero is treated as no prevalence and the day is dropped: silently
	 * when it is within VocSewage.MINIMUM, the size of rounding the child
	 * subtraction leaves, and with a printed warning otherwise, since a real
	 * negative means the counts were not inclusive.
	 */
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

	/**
	 * Sets {@link #cumulativePrevalence} and {@link #averageDay} from the
	 * prevalence this variant holds over [firstDay, lastDay]. Voc build() calls
	 * it once the child subtraction has made every variant exclusive, and
	 * VocSewage build() again once its merge loop has moved prevalence between
	 * variants: that second pass is what gives a merge target, a manufactured
	 * parent and the Others bucket a real averageDay to be sorted by. A variant
	 * with nothing in the range gets a cumulativePrevalence of 0 and an
	 * averageDay of NaN.
	 */
	public void computeTotals(int firstDay, int lastDay) {
		cumulativePrevalence = 0;
		double totalDay = 0;
		for (int day = firstDay; day <= lastDay; day++) {
			double prev = getPrevalence(day);
			cumulativePrevalence += prev;
			totalDay += day * prev;
		}
		averageDay = totalDay / cumulativePrevalence;
	}

	public static String displayName(String name) {
		return name.replaceAll("nextcladePangoLineage:", "");
	}

	/** The name: several log lines print a variant by concatenating it. */
	@Override
	public String toString() {
		return name;
	}

	/**
	 * A variant named by query text. It gets a lineage only when the whole name
	 * is {@code nextcladePangoLineage:<alias>*}, the form Lineage.getQuery()
	 * emits.
	 */
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

	/** Strict ancestry by expanded name. Both variants must have a lineage. */
	public boolean isAncestor(Variant descendant) {
		return lineage.isAncestor(descendant.lineage);
	}

	/** Days in [firstDay, lastDay] with prevalence above VocSewage.MINIMUM. */
	public int getNumDays(int firstDay, int lastDay) {
		/* A copy: removeIf on the keySet() view would delete the days from daily. */
		Set<Integer> keys = new HashSet<>(daily.keySet());
		keys.removeIf(day -> daily.get(day) <= VocSewage.MINIMUM || day < firstDay || day > lastDay);
		return keys.size();
	}

	/**
	 * Folds a descendant's prevalence into this variant and empties the
	 * descendant, which the caller then drops. This must be the descendant's
	 * ancestor or the Others bucket; anything else prints a stack trace and
	 * merges anyway. A descendant with no lineage can only go to Others.
	 * Neither variant's cumulativePrevalence or averageDay is updated here;
	 * VocSewage build() recomputes both with {@link #computeTotals} once it has
	 * stopped merging.
	 */
	public void add(Variant descendant) {
		if (!name.equalsIgnoreCase(OTHERS) && (lineage == null || !lineage.isAncestor(descendant.lineage))) {
			new Exception("Uh oh.").printStackTrace();
		}

		descendant.daily.forEach((d, v) -> {
			if (daily.get(d) == null) {
				daily.put(d, v);
			} else {
				daily.put(d, daily.get(d) + v);
			}
		});
		descendant.daily.clear();
	}

	/**
	 * An independent copy, daily prevalence and totals included, for a
	 * VocSewage to merge without disturbing the Voc it came from. The copy's
	 * lineage is re-parsed from the name, which is faithful because the name is
	 * all any variant is built from; the check below reports a name that stops
	 * resolving to the same lineage.
	 */
	public Variant duplicate() {
		try {
			Variant dup = new Variant(this.name);
			if (lineage != dup.lineage) {
				System.out.println("Uh oh.");
			}
			dup.cumulativePrevalence = cumulativePrevalence;
			dup.averageDay = averageDay;
			daily.forEach((d, v) -> dup.daily.put(d, v));
			return dup;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

}