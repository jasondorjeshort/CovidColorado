package variants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.data.time.TimeSeries;

import covid.CalendarUtils;
import nwss.DaySewage;
import sewage.All;

public class VocSewage {

	public final sewage.Abstract sewage;
	public final boolean isMerger;
	public final int vocId;

	private final Voc voc;

	public VocSewage(sewage.Abstract sewage, Voc voc) {
		this.sewage = sewage;
		this.voc = voc;
		this.isMerger = voc.isMerger;
		this.vocId = voc.id;
		build();
	}

	private int lastInflection = CalendarUtils.dateToDay("2-25-2024");
	// private int lastInflection = CalendarUtils.dateToDay("2-27-2024");
	// private int lastInflection = CalendarUtils.dateToDay("3-6-2024");

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

	public HashMap<Variant, Double> getCumulativePrevalence(ArrayList<Variant> variantList) {
		variantList.addAll(variants);
		variantList.sort((v1, v2) -> -Double.compare(cumulativePrevalence.get(v1), cumulativePrevalence.get(v2)));
		return cumulativePrevalence;
	}

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
				number += Math.exp(fits.get(variant).predict(day));
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
				if (s != null) {
					double number = Math.exp(fits.get(variant).predict(day));
					number += cFit.get(s);
					cFit.put(s, number);
				}
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
	 * rounding error of subtractions can cause "0" to show as really low.
	 * Usually like E-16 but nothing lower than about E-4 should be possible
	 * given sequencing counts. Actually the lowest in the US for any reasonable
	 * lineage is 0.0025. Anything zero needs to be ignored for both graphing or
	 * regression, since they'll bork an exponential fit or graph.
	 */
	static final double MINIMUM = 1E-8;

	private Variant findLineageToRemove() {
		if (variants.size() <= 1) {
			return null;
		}
		ArrayList<Variant> v = new ArrayList<>(variants);
		v.removeIf(variant -> variant.lineage == null);
		v.sort((v1, v2) -> -Integer.compare(v1.lineage.getFull().length(), v2.lineage.getFull().length()));

		for (Variant variant : v) {
			int fitStartDay = findFitStartDay(variant);
			SimpleRegression fit = makeFit(variant, fitStartDay);
			double slope = fit.getSlope();
			if (!Double.isFinite(slope)) {
				return variant;
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

			if (numDays < 10 || number < 0.001) {
				return variant;
			}
		}
		return null;
	}

	private int findFitStartDay(Variant variant) {
		SimpleRegression fit = makeFit(variant, lastInflection);

		double lowestSlope = fit.getSlope();
		int fitStartDay = lastInflection;

		/*
		 * Continue to go backwards SO LONG AS it makes the slope lower.
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
		Variant others = null;
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

			System.out.println("Merged variant " + deletion.name + " into " + merge.name);
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
		 * Build (part of) collective fit
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
		if (fit != null) {
			double num = 100 * Math.exp(fit.predict(currentDay)) / getCollectiveFit(currentDay);
			double lastNum = 100 * Math.exp(fit.predict(last)) / getCollectiveFit(last);
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
			series.add(CalendarUtils.dayToDay(day), 100 * Math.exp(fit.predict(day)) / getCollectiveFit(day));
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

			series.add(CalendarUtils.dayToDay(day), 100 * number / entry.getSewage());
		}
		if (fit != null) {
			/* For relative we can go past the model last day */
			for (int day = getLastDay() + 1; day <= last; day++) {
				series.add(CalendarUtils.dayToDay(day), 100 * Math.exp(fit.predict(day)) / getCollectiveFit(day));
			}
		}
		return series;
	}

	public synchronized TimeSeries makeRelativeSeries(Strain strain, boolean doFit) {
		TimeSeries series = new TimeSeries(strain.getName());
		if (doFit) {
			int day = getFirstDay() - 42;
			series.add(CalendarUtils.dayToDay(day), 100.0 * getCollectiveFit(strain, day) / getCollectiveFit(day));
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

			series.add(CalendarUtils.dayToDay(day), number);
		}
		if (doFit) {
			for (int day = getLastDay() + 1; day <= relativeLastDay; day++) {
				double num = getCollectiveFit(strain, day) / getCollectiveFit(day);
				if (num < 0) {
					new Exception("Uh oh.").printStackTrace();
				}
				series.add(CalendarUtils.dayToDay(day), 100.0 * num);
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
		if (fit != null) {
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
			series.add(CalendarUtils.dayToDay(day), Math.exp(fit.predict(day)));
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

			series.add(CalendarUtils.dayToDay(day), number);
		}
		if (fit != null) {
			for (int day = getLastDay() + 1; day <= absoluteLastDay; day++) {
				series.add(CalendarUtils.dayToDay(day), Math.exp(fit.predict(day)));
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
}
