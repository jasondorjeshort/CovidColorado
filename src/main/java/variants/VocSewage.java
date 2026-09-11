package variants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.data.time.TimeSeries;

import covid.CalendarUtils;
import nwss.DaySewage;
import sewage.All;

/**
 * One {@link Voc} against one sewage series: the Voc's variants merged down to
 * a legend, a fit of log(sewage times prevalence) for each, and the series the
 * lineage charts in charts/ChartSewage.java draw from them.
 * docs/reference/lineages.txt owns the merge rules and where a fit starts.
 * <p>
 * nwss/Nwss.java build() makes one per Voc on the national series, and a second
 * on the Colorado state series for the LAPIS Voc or a single-variant export.
 * Both are aggregates, whose normalizer is 1, and the relative series rely on
 * that: they divide sewage times normalizer times prevalence by the raw reading
 * to get the prevalence back.
 * <p>
 * The constructor does all the work, on duplicates of the Voc's variants, so
 * one Voc can back several of these. After it returns, the only state that
 * changes is the two collective-fit caches, each under its own lock, and the
 * overflowed set, which is concurrent; everything else is only read, which is
 * what lets the chart tasks share an instance. Those tasks are queued after
 * the constructor returns, and the executor hand-off publishes its writes.
 */
public class VocSewage {

	public final sewage.Abstract sewage;
	public final boolean isMerger;
	public final int vocId;

	private final Voc voc;

	/*
	 * The seed each variant's fit start searches back from: the sewage series'
	 * own latest peak or valley, clamped by the constructor to leave
	 * MIN_FIT_DAYS. Used to be a hand-updated date. The last thing build()
	 * does is overwrite it with the latest of the variants' own fit starts,
	 * which is what getLastInflection(null) returns and where the collective
	 * fit is drawn from.
	 */
	private int lastInflection;

	/* A fit needs some days no matter how recent the inflection is. */
	private static final int MIN_FIT_DAYS = 28;

	public VocSewage(sewage.Abstract sewage, Voc voc) {
		this.sewage = sewage;
		this.voc = voc;
		this.isMerger = voc.isMerger;
		this.vocId = voc.id;

		Long inflection = sewage.getLastInflection();
		lastInflection = inflection == null ? getFirstDay() : CalendarUtils.timeToDay(inflection);
		lastInflection = Math.max(getFirstDay(), Math.min(lastInflection, getLastDay() - MIN_FIT_DAYS));

		build();
	}

	/**
	 * Where a fit starts, for the charts' "Fit start" marker: the variant's own
	 * start, or for null the latest of every variant's. Despite the name this
	 * is the sewage series' inflection only where a fit kept its seed. The
	 * variant must come from {@link #getVariants()}.
	 */
	public int getLastInflection(Variant variant) {
		if (variant == null) {
			return lastInflection;
		}
		return fitStartDays.get(variant);
	}

	public int getFirstDay() {
		return Math.max(sewage.getFirstDay(), voc.getFirstDay());
	}

	public int getLastDay() {
		return Math.min(sewage.getLastDay(), voc.getLastDay());
	}

	/**
	 * Appends every variant to variantList, sorts that list by cumulative
	 * sewage-weighted prevalence, largest first, and returns the map of those
	 * totals itself, not a copy.
	 */
	public HashMap<Variant, Double> getCumulativePrevalence(ArrayList<Variant> variantList) {
		variantList.addAll(variants);
		variantList.sort((v1, v2) -> -Double.compare(cumulativePrevalence.get(v1), cumulativePrevalence.get(v2)));
		return cumulativePrevalence;
	}

	/*
	 * Both getCollectiveFit overloads report into this, and they hold different
	 * locks, so a plain HashSet could be corrupted by two chart threads.
	 */
	private final Set<Variant> overflowed = ConcurrentHashMap.newKeySet();

	public double getCollectiveFit(int day) {
		synchronized (collectiveFit) {
			Double n = collectiveFit.get(day);
			if (n != null) {
				return n;
			}

			double number = 0.0;
			for (Variant variant : variants) {
				if (fits.get(variant) == null) {
					System.out.println("Impossible variant : " + variant);
					continue;
				}
				/*
				 * Not finite: a projection that overflows, or NaN from a fit of
				 * under two points. Only a variant with no lineage, such as
				 * Others, can have the second, since the floor removes every
				 * lineage whose slope is not finite; the message calls both an
				 * overflow.
				 */
				double term = Math.exp(fits.get(variant).predict(day));
				if (!Double.isFinite(term)) {
					if (overflowed.add(variant)) {
						System.out.println("Fit overflow on " + variant.name + ", first at " + CalendarUtils.dayToDate(day));
					}
					continue;
				}
				number += term;
			}
			collectiveFit.put(day, number);
			return number;
		}
	}

	public double getCollectiveFit(Strain strain, int day) {
		synchronized (collectiveStrainFit) {
			HashMap<Strain, Double> cFit = collectiveStrainFit.get(day);

			if (cFit != null) {
				return cFit.get(strain);
			}

			cFit = new HashMap<>();
			collectiveStrainFit.put(day, cFit);

			for (Strain s : Strain.values()) {
				cFit.put(s, 0.0);
			}
			for (Variant variant : variants) {
				Strain s = Strain.findStrain(variant.lineage);
				if (s == null) {
					continue;
				}
				if (fits.get(variant) == null) {
					System.out.println("Impossible variant : " + variant);
					continue;
				}
				/*
				 * Same guard as the collective total above, and it has to be
				 * the same one: the relative strain series divides this by
				 * that, so a term dropped from one and kept in the other turns
				 * a ratio into +Infinity.
				 */
				double term = Math.exp(fits.get(variant).predict(day));
				if (!Double.isFinite(term)) {
					if (overflowed.add(variant)) {
						System.out.println("Fit overflow on " + variant.name + ", first at " + CalendarUtils.dayToDate(day));
					}
					continue;
				}
				cFit.put(s, cFit.get(s) + term);
			}

			return cFit.get(strain);
		}

	}

	public TimeSeries makeAbsoluteCollectiveTS() {
		int today = CalendarUtils.timeToDay(System.currentTimeMillis());
		TimeSeries series = new TimeSeries(String.format("Collective fit (today=%.1f)", getCollectiveFit(today)));
		for (int day = Math.max(getFirstDay(), lastInflection); day <= absoluteLastDay; day++) {
			series.add(CalendarUtils.dayToDay(day), getCollectiveFit(day));
		}

		return series;
	}

	public static double slopeToWeekly(double slope) {
		return 100.0 * (Math.exp(7.0 * slope) - 1);
	}

	public static String slopeToWeekly(SimpleRegression fit) {
		// double min = slopeToWeekly(fit.getSlope() -
		// fit.getSlopeConfidenceInterval());
		// double max = slopeToWeekly(fit.getSlope() +
		// fit.getSlopeConfidenceInterval());
		double act = slopeToWeekly(fit.getSlope());
		return String.format("%+.0f%%/week", act);
	}

	private int currentDay, absoluteLastDay, relativeLastDay;
	private final HashMap<Variant, SimpleRegression> fits = new HashMap<>();
	private final HashMap<Variant, Integer> fitStartDays = new HashMap<>();
	private final HashMap<Variant, Double> cumulativePrevalence = new HashMap<>();
	private final HashMap<Strain, Double> cumulativeStrainPrevalence = new HashMap<>();
	private double cumulative = 0;
	private final HashMap<Integer, Double> collectiveFit = new HashMap<>();
	private final HashMap<Integer, HashMap<Strain, Double>> collectiveStrainFit = new HashMap<>();
	private final HashSet<Variant> variants = new HashSet<>();
	private final HashMap<Lineage, Variant> lineageMap = new HashMap<>();

	/*
	 * Merge target for a lineage with no parent. Null until build() finds or
	 * creates it.
	 */
	private Variant others;

	/* Why findLineageToRemove picked its last answer, for build()'s merge line. */
	private String removalReason;

	/*
	 * The line between a value and rounding residue, applied to prevalence and
	 * to sewage times prevalence alike; Variant uses it too. The child
	 * subtraction leaves residues around 1E-16 where a prevalence should be 0,
	 * while a real prevalence is at least one sequence in a smoothing window's
	 * total, which even a window of ten thousand sequences puts at 1E-4. 1E-8
	 * sits well clear of both, and a sewage factor within a few orders of 1
	 * leaves each on its side. A value at or under it is kept out of every fit
	 * and series: log(0) wrecks a fit, and 0 is undrawable on either axis.
	 */
	static final double MINIMUM = 1E-8;

	/*
	 * Past this many variants, Others included, the cost tier keeps merging
	 * even when the cheapest merge changes the forecast. The LAPIS input
	 * carries every lineage with at least 20 US sequences, and the floor alone
	 * leaves 100 to 200 of them on a chart, which is more colours than a
	 * reader can tell apart; ten is about the most a legend can carry
	 * distinctly. A target, not a guarantee: the cost tier never merges a
	 * pinned lineage, or one with anything in the last RECENT_DAYS and no
	 * ancestor left on the chart, to reach it.
	 */
	private static final int MAX_VARIANTS = 10;

	/*
	 * Standard errors a slope difference must clear before it counts, about a
	 * 95% interval. The open GenBank data is roughly one sequence a week per
	 * lineage, so most raw slope differences are noise, and a lineage whose
	 * growth cannot be told apart from its reference's should fold whatever
	 * its size.
	 */
	private static final double SLOPE_NOISE_SIGMAS = 2.0;

	/*
	 * Merge cost weighs lineages by their size over these last days: the
	 * recent stretch is what decides what the chart projects forward. It is
	 * the minimum fit length, so every fit covers the whole window.
	 */
	private static final int RECENT_DAYS = MIN_FIT_DAYS;

	/*
	 * The next variant for build() to fold away, or null when the chart is
	 * done. A variant failing the floor goes first, deepest lineage first -- by
	 * expanded-name length, which puts every descendant before its ancestors;
	 * only when none does is the cheapest merge by forecast change taken, and
	 * then only while there are more than MAX_VARIANTS or the merge is free.
	 */
	private Variant findLineageToRemove() {
		if (variants.size() <= 1) {
			return null;
		}
		ArrayList<Variant> v = new ArrayList<>(variants);
		v.removeIf(variant -> variant.lineage == null);
		v.sort((v1, v2) -> -Integer.compare(v1.lineage.getFull().length(), v2.lineage.getFull().length()));

		HashMap<Variant, SimpleRegression> passFits = new HashMap<>();
		for (Variant variant : v) {
			int fitStartDay = findFitStartDay(variant);
			SimpleRegression fit = makeFit(variant, fitStartDay);
			passFits.put(variant, fit);
			double slope = fit.getSlope();
			/*
			 * A fit from two or three points can be steep enough that the
			 * projection overflows; it is as unusable as no slope at all.
			 */
			if (!Double.isFinite(slope) || !Double.isFinite(Math.exp(fit.predict(getLastDay() + 60)))) {
				removalReason = Double.isFinite(slope) ? "projection overflows" : "no finite slope";
				return variant;
			}
			/* Pinned in LEnum: keep it separate no matter how small it is. */
			if (voc.pinned.contains(variant.lineage)) {
				continue;
			}

			double number = 0, numDays = 0;
			for (int day = Math.max(fitStartDay, getFirstDay()); day <= getLastDay(); day++) {
				Double s = sewage.getSewageNormalized(day);
				if (s == null) {
					continue;
				}
				double prev = variant.getPrevalence(day);
				number += s * prev;
				numDays += prev > MINIMUM ? 1 : 0;
			}

			/*
			 * Both counted from this variant's fit start, not over the whole
			 * window. Ten days is what two sequences three days apart make
			 * under the 7-day smoothing, as the disabled pre-filter in Voc
			 * build() notes. 77c991c lowered both from 22 and 0.1 and recorded
			 * no reason.
			 */
			if (numDays < 10) {
				removalReason = String.format("present on only %.0f days", numDays);
				return variant;
			}
			if (number < 0.001) {
				removalReason = String.format("sewage-weighted total %.2g since fit start", number);
				return variant;
			}
		}
		return findCheapestMerge(v, passFits);
	}

	/*
	 * Cost of folding a lineage into its reference. Replacing two exponentials
	 * of recent sizes c and p and slopes a and b by one with the size-weighted
	 * slope moves the projection H days out by (H^2/2) * c*p/(c+p) * (a-b)^2 to
	 * leading order, so the cheapest merge is the one that changes the
	 * forecast least: lineages growing alike and tiny lineages are both cheap,
	 * big diverging ones expensive. H^2/2 is common to every merge and left
	 * out. The slope gap is first shrunk by its noise, and an unmeasurable
	 * standard error (too few points) counts as infinite noise.
	 */
	private Variant findCheapestMerge(ArrayList<Variant> deepestFirst, HashMap<Variant, SimpleRegression> passFits) {
		HashMap<Variant, Double> sizes = new HashMap<>();
		Variant best = null;
		double bestCost = Double.POSITIVE_INFINITY;
		String bestReason = null;

		for (Variant candidate : deepestFirst) {
			if (voc.pinned.contains(candidate.lineage)) {
				continue;
			}
			Variant reference = findReference(candidate);
			if (reference == null) {
				continue;
			}
			double c = sizes.computeIfAbsent(candidate, this::recentSize);
			/*
			 * With no ancestor left on the chart -- a root, or the top of a
			 * family whose ancestors were all folded away -- the reference is
			 * Others, which the LAPIS lineages leave nearly empty, so the
			 * arithmetic prices the merge at nothing while it throws away the
			 * last label the family has. So such a lineage with anything
			 * recent stays, and with more than MAX_VARIANTS of them live the
			 * cap is not reached.
			 */
			if (reference == others && c != 0) {
				continue;
			}
			SimpleRegression fc = passFits.get(candidate);
			SimpleRegression fp = passFits.computeIfAbsent(reference, r -> makeFit(r, null));
			double p = sizes.computeIfAbsent(reference, this::recentSize);

			double seC = fc.getSlopeStdErr(), seP = fp.getSlopeStdErr();
			double noise = SLOPE_NOISE_SIGMAS * Math.sqrt(seC * seC + seP * seP);
			double excess = Double.isFinite(noise) ? Math.max(0, Math.abs(fc.getSlope() - fp.getSlope()) - noise) : 0;
			double cost = c + p == 0 ? 0 : c * p / (c + p) * excess * excess;

			/*
			 * Strictly less, so a tie goes to the deeper lineage seen first.
			 * Between names of equal length it goes to the HashSet's order,
			 * which follows identity hashes and not the data.
			 */
			if (!Double.isFinite(cost) || cost >= bestCost) {
				continue;
			}
			best = candidate;
			bestCost = cost;
			bestReason = String.format("cost %.2g vs %s (sizes %.3g, %.3g; slope gap %.2g/day past noise)", cost,
					reference.name, c, p, excess);
		}

		if (best == null || (variants.size() <= MAX_VARIANTS && bestCost > 0)) {
			return null;
		}
		removalReason = bestReason;
		return best;
	}

	/*
	 * What a lineage's merge cost is measured against: its nearest ancestor
	 * still on the chart. This is not always the merge target. build() folds
	 * into the direct parent and manufactures it if absent, and a manufactured
	 * parent starts empty, so measuring against it would make every merge
	 * free and relabel every lineage upward. A lineage with no ancestor on
	 * the chart gets Others, which findCheapestMerge accepts only when that
	 * lineage has nothing recent.
	 */
	private Variant findReference(Variant variant) {
		for (Lineage l = variant.lineage.getParent(); l != null; l = l.getParent()) {
			Variant ancestor = lineageMap.get(l);
			if (ancestor != null) {
				return ancestor;
			}
		}
		return others;
	}

	private double recentSize(Variant variant) {
		double size = 0;
		for (int day = Math.max(getFirstDay(), getLastDay() - RECENT_DAYS + 1); day <= getLastDay(); day++) {
			Double s = sewage.getSewageNormalized(day);
			if (s == null) {
				continue;
			}
			size += s * variant.getPrevalence(day);
		}
		return size;
	}

	private int findFitStartDay(Variant variant) {
		SimpleRegression fit = makeFit(variant, lastInflection);

		double lowestSlope = fit.getSlope();
		int fitStartDay = lastInflection;

		/*
		 * Walks back over every day to the first and keeps whichever start
		 * gave the lowest slope. It does not stop at the first rise: the
		 * preliminary fd65897 did, and 26a93fc replaced it with this. A
		 * variant with under two points from the seed on starts from a NaN
		 * slope that no comparison beats, so it keeps the seed, and a lineage
		 * in that state fails the floor's first test.
		 */
		for (int day = lastInflection - 1; day >= getFirstDay(); day--) {
			DaySewage entry;
			entry = sewage.getEntry(day);
			if (entry == null) {
				continue;
			}

			double number = entry.getSewage();
			number *= sewage.getNormalizer();
			number *= variant.getPrevalence(day);
			if (number <= MINIMUM) {
				continue;
			}

			fit.addData(day, Math.log(number));
			double slope = fit.getSlope();
			if (slope < lowestSlope) {
				lowestSlope = slope;
				fitStartDay = day;
			}
		}

		return fitStartDay;
	}

	private SimpleRegression makeFit(Variant variant, Integer fitStartDay) {
		SimpleRegression fit = new SimpleRegression();
		if (fitStartDay == null) {
			fitStartDay = findFitStartDay(variant);
		}
		for (int day = fitStartDay; day <= getLastDay(); day++) {
			DaySewage entry;
			entry = sewage.getEntry(day);
			if (entry == null) {
				continue;
			}

			double number = entry.getSewage();
			number *= sewage.getNormalizer();
			number *= variant.getPrevalence(day);
			if (number <= MINIMUM) {
				continue;
			}

			fit.addData(day, Math.log(number));
		}
		fitStartDays.put(variant, fitStartDay);
		return fit;
	}

	private void build() {
		for (Variant v : voc.getVariants()) {
			v = v.duplicate();
			variants.add(v);
			if (v.name.equalsIgnoreCase("others")) {
				others = v;
			}
		}
		variants.forEach(variant -> {
			if (variant.lineage != null) {
				lineageMap.put(variant.lineage, variant);
			}
		});

		Variant deletion;
		while ((deletion = findLineageToRemove()) != null) {
			Lineage l = deletion.lineage;

			Lineage p = l.getParent();

			Variant merge;
			if (p == null) {
				if (others == null) {
					others = new Variant("Others");
					variants.add(others);
				}

				/*
				 * TODO: maybe per-strain others?
				 */
				merge = others;
			} else {
				Variant pv = lineageMap.get(p);
				if (pv == null) {
					pv = new Variant(p);
					System.out.println("Manufacturing " + p.getAlias());
					lineageMap.put(p, pv);
					variants.add(pv);
				}
				merge = pv;
			}

			merge.add(deletion);
			variants.remove(deletion);
			lineageMap.remove(l);

			System.out.println("Merged variant " + deletion.name + " into " + merge.name + ": " + removalReason);
		}

		/*
		 * Cumulative sewage only (should be part of sewage???)
		 */
		cumulative = 0;
		for (int day = getFirstDay(); day <= getLastDay(); day++) {
			Double prev = sewage.getSewageNormalized(day);
			if (prev == null) {
				continue;
			}
			cumulative += prev;
		}

		/*
		 * Cumulative sewage by variant
		 */
		for (Variant variant : variants) {
			double number = 0;
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				Double prev = sewage.getSewageNormalized(day);
				if (prev == null) {
					continue;
				}
				number += prev * variant.getPrevalence(day);
			}
			cumulativePrevalence.put(variant, number);
		}

		/*
		 * Build fits
		 */
		for (Variant variant : variants) {
			fits.put(variant, makeFit(variant, null));
		}

		/*
		 * The absolute projection runs 30 days past today, cut back to the last
		 * day the collective fit stays at or under the pandemic peak,
		 * All.SCALE_PEAK_RENORMALIZER (since 4c33f48), but never short of the
		 * day after today. The relative projection always runs the full 30.
		 * "Today" is the UTC date, a day ahead in the evening; see
		 * docs/active/findings/2026-09-10-a-date-parsed-in-the-evening-lands-on-the-wrong-day.md.
		 */
		currentDay = CalendarUtils.timeToDay(System.currentTimeMillis());
		absoluteLastDay = relativeLastDay = currentDay + 30;
		while (absoluteLastDay > currentDay + 1 && getCollectiveFit(absoluteLastDay) > All.SCALE_PEAK_RENORMALIZER) {
			absoluteLastDay--;
		}

		/*
		 * Build strain numbers
		 */
		for (Strain s : Strain.values()) {
			cumulativeStrainPrevalence.put(s, 0.0);
		}
		for (Variant variant : variants) {
			Strain s = Strain.findStrain(variant);
			if (s != null) {
				double sPrev = cumulativeStrainPrevalence.get(s) + cumulativePrevalence.get(variant);
				cumulativeStrainPrevalence.put(s, sPrev);
			}

		}

		lastInflection = Integer.MIN_VALUE;
		for (Variant v : variants) {
			lastInflection = Math.max(lastInflection, fitStartDays.get(v));
		}

	}

	public int getAbsoluteLastDay() {
		return absoluteLastDay;
	}

	public int getRelativeLastDay() {
		return relativeLastDay;
	}

	public double getGrowth(Variant variant) {
		return slopeToWeekly(fits.get(variant).getSlope());
	}

	public double getCumulative(Variant variant) {
		return cumulativePrevalence.get(variant);
	}

	public double getCumulative(Strain strain) {
		return cumulativeStrainPrevalence.get(strain);
	}

	public double getCumulative() {
		return cumulative;
	}

	public double getPercentage(Variant variant) {
		return getCumulative(variant) / getCumulative();
	}

	public double getFit(Variant variant, int day) {
		return fits.get(variant).predict(day);
	}

	public int getNumVariants() {
		return variants.size();
	}

	public TimeSeries makeRegressionTS(Variant variant) {
		SimpleRegression fit;
		synchronized (this) {
			fit = fits.get(variant);
		}
		if (fit == null) {
			return null;
		}
		TimeSeries series = new TimeSeries(String.format("%s %s", variant.displayName, slopeToWeekly(fit)));
		int f = getFirstDay();
		int l = CalendarUtils.timeToDay(System.currentTimeMillis()) + 30;
		series.add(CalendarUtils.dayToDay(f), Math.exp(fit.predict(f)));
		series.add(CalendarUtils.dayToDay(l), Math.exp(fit.predict(l)));
		return series;
	}

	public synchronized TimeSeries makeRelativeSeries(Variant variant, boolean doFit) {
		String name = variant.displayName;
		SimpleRegression fit = null;
		int last = getRelativeLastDay();
		if (doFit) {
			synchronized (this) {
				fit = fits.get(variant);
			}
		}
		double num = 0, lastNum = 0;
		if (fit != null) {
			num = 100 * Math.exp(fit.predict(currentDay)) / getCollectiveFit(currentDay);
			lastNum = 100 * Math.exp(fit.predict(last)) / getCollectiveFit(last);
		}
		if (fit != null && Double.isFinite(num) && Double.isFinite(lastNum)) {
			if (num > 10 && lastNum > 10) {
				name = String.format("%s (%.0f%%->%.0f%%)", name, num, lastNum);
			} else if (num > 1 && lastNum > 1) {
				name = String.format("%s (%.1f%%->%.1f%%)", name, num, lastNum);
			} else if (num > 0.1 && lastNum > 0.1) {
				name = String.format("%s (%.2f%%->%.2f%%)", name, num, lastNum);
			} else {
				name = String.format("%s (%.3f%%->%.3f%%)", name, num, lastNum);
			}
		}
		TimeSeries series = new TimeSeries(name);
		if (fit != null && fit.getSlope() > 0) {
			int day = getFirstDay() - 42;
			addRelative(series, day, 100 * Math.exp(fit.predict(day)) / getCollectiveFit(day));
		}
		for (int day = Math.max(getFirstDay(), getFirstDay()); day <= getLastDay(); day++) {
			DaySewage entry;
			entry = sewage.getEntry(day);
			if (entry == null) {
				continue;
			}

			// Double pop = entry.getPop();

			double number = entry.getSewage();
			number *= sewage.getNormalizer();
			number *= variant.getPrevalence(day);
			if (number <= MINIMUM) {
				// fit data before or after will fill for it
				continue;
			}

			/*
			 * With the normalizer 1 (see the class comment) this is 100 times
			 * the prevalence. A zero reading never gets here: it makes number
			 * zero, which the test above skips.
			 */
			addRelative(series, day, 100 * number / entry.getSewage());
		}
		if (fit != null) {
			/* For relative we can go past the model last day */
			for (int day = getLastDay() + 1; day <= last; day++) {
				addRelative(series, day, 100 * Math.exp(fit.predict(day)) / getCollectiveFit(day));
			}
		}
		return series;
	}

	/*
	 * The relative chart is on a logit axis with a peak of 100: exactly 0 or
	 * 100 (a lone lineage on a thin day, or runaway extrapolations) is
	 * undrawable and takes the whole chart down with it.
	 */
	private static void addRelative(TimeSeries series, int day, double percent) {
		if (!Double.isFinite(percent) || percent <= 100 * MINIMUM || percent >= 100) {
			return;
		}
		series.add(CalendarUtils.dayToDay(day), percent);
	}

	/*
	 * The absolute chart's LogarithmicAxis sets a lower bound and no upper one,
	 * so a single infinite point makes JFreeChart allocate ticks across every
	 * decade until the heap is gone. findLineageToRemove drops a fit that
	 * overflows at getLastDay() + 60, but that only proves the fit is finite on
	 * that one day, and this draws to currentDay + 30, which is later whenever
	 * the data is more than 30 days stale.
	 */
	private static void addAbsolute(TimeSeries series, int day, double value) {
		if (!Double.isFinite(value) || value <= MINIMUM) {
			return;
		}
		series.add(CalendarUtils.dayToDay(day), value);
	}

	public synchronized TimeSeries makeRelativeSeries(Strain strain, boolean doFit) {
		TimeSeries series = new TimeSeries(strain.getName());
		if (doFit) {
			int day = getFirstDay() - 42;
			addRelative(series, day, 100.0 * getCollectiveFit(strain, day) / getCollectiveFit(day));
		}
		for (int day = Math.max(getFirstDay(), getFirstDay()); day <= getLastDay(); day++) {
			DaySewage entry;
			entry = sewage.getEntry(day);
			if (entry == null) {
				continue;
			}

			// Double pop = entry.getPop();

			double number = getPrevalence(strain, day);
			number *= 100.0;
			if (number <= MINIMUM) {
				// fit data before or after will fill for it
				continue;
			}

			addRelative(series, day, number);
		}
		if (doFit) {
			for (int day = getLastDay() + 1; day <= relativeLastDay; day++) {
				double num = getCollectiveFit(strain, day) / getCollectiveFit(day);
				if (num < 0) {
					new Exception("Uh oh.").printStackTrace();
				}
				addRelative(series, day, 100.0 * num);
			}
		}
		return series;
	}

	public synchronized TimeSeries makeAbsoluteSeries(Variant variant, boolean doFit) {
		SimpleRegression fit = null;
		if (doFit) {
			synchronized (this) {
				fit = fits.get(variant);
			}
		}
		String name = variant.displayName;
		if (fit != null && Double.isFinite(Math.exp(fit.predict(currentDay)))) {
			double num = Math.exp(fit.predict(currentDay));

			if (num > 1) {
				name = String.format("%s (%.1f%s)", variant.displayName, num, slopeToWeekly(fit));
			} else if (num > 0.1) {
				name = String.format("%s (%.2f%s)", variant.displayName, num, slopeToWeekly(fit));
			} else {
				name = String.format("%s (%.3f%s)", variant.displayName, num, slopeToWeekly(fit));
			}
		}
		TimeSeries series = new TimeSeries(name);
		if (fit != null && fit.getSlope() > 0) {
			int day = getFirstDay() - 42;
			addAbsolute(series, day, Math.exp(fit.predict(day)));
		}
		for (int day = Math.max(getFirstDay(), getFirstDay()); day <= getLastDay(); day++) {
			DaySewage entry;
			entry = sewage.getEntry(day);
			if (entry == null) {
				continue;
			}

			// Double pop = entry.getPop();

			double number = entry.getSewage();
			number *= sewage.getNormalizer();
			number *= variant.getPrevalence(day);
			if (number <= MINIMUM) {
				// fit data before or after will fill for it
				continue;
			}

			addAbsolute(series, day, number);
		}
		if (fit != null) {
			for (int day = getLastDay() + 1; day <= absoluteLastDay; day++) {
				addAbsolute(series, day, Math.exp(fit.predict(day)));
			}
		}
		return series;
	}

	public double getPrevalence(Strain strain, int day) {
		double number = 0;
		for (Variant variant : variants) {
			// TODO: cache this maybe? dunno
			if (variant.lineage == null) {
				continue;
			}
			if (Strain.findStrain(variant.lineage) == strain) {
				number += variant.getPrevalence(day);
			}
		}
		return number;
	}

	public Collection<Variant> getVariants() {
		return variants;
	}

	public synchronized TimeSeries makeAbsoluteSeries(Strain strain, boolean doFit) {
		TimeSeries series = new TimeSeries(strain.getName());
		if (doFit) {
			int day = getFirstDay() - 42;
			series.add(CalendarUtils.dayToDay(day), getCollectiveFit(strain, day));
		}
		for (int day = Math.max(getFirstDay(), getFirstDay()); day <= getLastDay(); day++) {
			DaySewage entry;
			entry = sewage.getEntry(day);
			if (entry == null) {
				continue;
			}

			// Double pop = entry.getPop();

			double number = getPrevalence(strain, day);

			number *= sewage.getNormalizer();
			number *= entry.getSewage();
			if (number <= MINIMUM) {
				// fit data before or after will fill for it
				continue;
			}

			series.add(CalendarUtils.dayToDay(day), number);
		}
		if (doFit) {
			for (int day = getLastDay() + 1; day <= absoluteLastDay; day++) {
				series.add(CalendarUtils.dayToDay(day), getCollectiveFit(strain, day));
			}
		}
		return series;
	}

	/**
	 * Prints cov-spectrum comparison links for the lineages left on this chart,
	 * for the manual export path. A variant with no lineage, Others included,
	 * is named and left out. The LSet is built empty, so its constructor's
	 * "Variants: 0" line comes first.
	 */
	public void getLink() {
		LSet lset = new LSet("2020-01-06", null);
		for (Variant variant : variants) {
			Lineage l = variant.lineage;

			if (l == null) {
				System.out.println("No vocSewage variant list " + variant.name);
				continue;
			}

			lset.addLineage(l);
		}

		/* getCovSpectrumLink prints the links itself. */
		System.out.println("VocSewage link : " + variants.size() + " : ");
		lset.getCovSpectrumLink();
	}
}
