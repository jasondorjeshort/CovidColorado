package variants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.data.time.TimeSeries;

import covid.CalendarUtils;
import nwss.DaySewage;

public class VocSewage {

	public final sewage.Abstract sewage;
	public final Voc voc;

	public VocSewage(sewage.Abstract sewage, Voc voc) {
		this.sewage = sewage;
		this.voc = voc;
	}

	public int getFirstDay() {
		return Math.max(sewage.getFirstDay(), voc.getFirstDay());
	}

	public int getLastDay() {
		return Math.min(sewage.getLastDay(), voc.getLastDay());
	}

	public HashMap<String, Double> getCumulativePrevalence(ArrayList<String> variants) {
		build();
		variants.addAll(variantsByCumulative);
		variants.sort((v1, v2) -> -Double.compare(cumulativePrevalence.get(v1), cumulativePrevalence.get(v2)));
		return cumulativePrevalence;
	}

	double getSlopeByGrowth(int n) {
		String v = variantsByGrowth.get(n);
		return fits.get(v).getSlope();
	}

	/**
	 * Todo: this should just be merged into the new VocSewage call from the end
	 * 
	 * Actually it should be a new VocSewage(VocSewage parent, int
	 * variantCount)?
	 */
	public VocSewage merge(int variantCount) {
		build();

		if (variantCount >= variantsByGrowth.size()) {
			return this;
		}

		final double[] targetSlopes = new double[variantCount];
		@SuppressWarnings("unchecked")
		final LinkedList<String>[] variants = new LinkedList[variantCount];
		targetSlopes[0] = getSlopeByGrowth(Math.max((numVariants - 1) / 10, 1));
		targetSlopes[variantCount - 1] = getSlopeByGrowth(numVariants - 1) * 0.33
				+ getSlopeByGrowth(numVariants - 2) * 0.67;
		double gap = (targetSlopes[variantCount - 1] - targetSlopes[0]) / (variantCount - 1);
		for (int i = 0; i < variantCount - 1; i++) {
			targetSlopes[i] = targetSlopes[0] + i * gap;
		}
		for (int i = 0; i < variantCount; i++) {
			variants[i] = new LinkedList<>();
		}

		for (String variant : variantsByGrowth) {
			double slope = fits.get(variant).getSlope();

			int closest = 0;
			double closestDist = Math.abs(slope - targetSlopes[0]);
			// This is O(mn), could be O(m) with a better algorithm but only n=5
			for (int i = 1; i < variantCount; i++) {
				double dist = Math.abs(slope - targetSlopes[i]);
				if (dist < closestDist) {
					closest = i;
					closestDist = dist;
				}
			}
			variants[closest].add(variant);
		}

		Voc voc2 = new Voc(this, variants);
		return new VocSewage(sewage, voc2);
	}

	public synchronized void makeTimeSeries(TimeSeries series, String variant) {
		build();
		for (int day = Math.max(getFirstDay(), voc.getFirstDay()); day <= getLastDay()
				&& day <= voc.getLastDay(); day++) {
			DaySewage entry;
			entry = sewage.getEntry(day);
			if (entry == null) {
				continue;
			}

			// Double pop = entry.getPop();

			double number = entry.getSewage();
			number *= sewage.getNormalizer();
			if (number <= 0) {
				number = 1E-6;
			}

			number *= voc.getPrevalence(day, variant);
			if (number <= 0) {
				number = 1E-12;
			}

			series.add(CalendarUtils.dayToDay(day), number);
		}
	}

	public synchronized TimeSeries makeRegressionTS(ArrayList<String> variants) {
		build();
		HashMap<String, Double> sorter = new HashMap<>();
		int tsLastDay = CalendarUtils.timeToDay(System.currentTimeMillis()) + 30;
		for (String variant : variants) {
			sorter.put(variant, Math.exp(fits.get(variant).predict(tsLastDay)));
		}

		variants.sort((v1, v2) -> -Double.compare(sorter.get(v1), sorter.get(v2)));

		if (!variants.contains(Voc.OTHERS)) {
			return null;
		}
		TimeSeries series = new TimeSeries(String.format("Collective fit"));
		for (int day = getFirstDay(); day <= tsLastDay; day++) {
			double number = 0.0;
			for (String variant : variants) {
				if (fits.get(variant) == null) {
					System.out.println("Impossible variant : " + variant);
					continue;
				}
				number += Math.exp(fits.get(variant).predict(day));
			}
			series.add(CalendarUtils.dayToDay(day), number);
		}

		return series;
	}

	public static double slopeToWeekly(double slope) {
		return 100.0 * (Math.exp(7.0 * slope) - 1);
	}

	public static String slopeToWeekly(SimpleRegression fit) {
		double min = slopeToWeekly(fit.getSlope() - fit.getSlopeConfidenceInterval());
		double max = slopeToWeekly(fit.getSlope() + fit.getSlopeConfidenceInterval());
		return String.format("[%+.0f%%,%+.0f%%]/week", min, max);
	}

	private boolean built = false;
	private int numVariants;
	private ArrayList<String> variantsByGrowth, variantsByCount, variantsByCumulative;
	private HashMap<String, SimpleRegression> fits;
	private HashMap<String, Double> cumulativePrevalence = new HashMap<>();
	private double cumulative = 0;

	public double getGrowth(String variant) {
		return slopeToWeekly(fits.get(variant).getSlope());
	}

	public double getCumulative(String variant) {
		build();
		return cumulativePrevalence.get(variant);
	}

	public double getCumulative() {
		build();
		return cumulative;
	}

	public double getPercentage(String variant) {
		return getCumulative(variant) / getCumulative();
	}

	public synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		fits = new HashMap<>();
		variantsByGrowth = voc.getVariants();
		variantsByCount = voc.getVariants();
		numVariants = variantsByGrowth.size();

		for (String variant : variantsByGrowth) {
			SimpleRegression fit = new SimpleRegression();
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				DaySewage entry;
				entry = sewage.getEntry(day);
				if (entry == null) {
					continue;
				}

				double number = entry.getSewage();
				number *= sewage.getNormalizer();

				number *= voc.getPrevalence(day, variant);
				if (number <= 0) {
					continue;
				}

				fit.addData(day, Math.log(number));
			}

			fits.put(variant, fit);
		}

		variantsByGrowth.sort((v1, v2) -> Double.compare(fits.get(v1).getSlope(), fits.get(v2).getSlope()));
		variantsByCount.sort(
				(v1, v2) -> Double.compare(fits.get(v1).predict(getFirstDay()), fits.get(v2).predict(getLastDay())));

		variantsByCumulative = voc.getVariants();

		cumulative = 0;
		for (int day = getFirstDay(); day <= getLastDay(); day++) {
			Double prev = sewage.getSewageNormalized(day);
			if (prev == null) {
				continue;
			}
			cumulative += prev;
		}
		if (sewage instanceof sewage.All) {
			System.out.println("Cumulative " + sewage.getName() + " => " + cumulative);
		}

		for (String variant : variantsByCumulative) {
			double number = 0;
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				Double prev = sewage.getSewageNormalized(day);
				if (prev == null) {
					continue;
				}
				number += prev * voc.getPrevalence(day, variant);
			}
			cumulativePrevalence.put(variant, number);

			if (sewage instanceof sewage.All) {
				System.out.println("Prev on " + variant + " => " + number);
			}
		}

		variantsByCumulative
				.sort((v1, v2) -> -Double.compare(cumulativePrevalence.get(v1), cumulativePrevalence.get(v2)));
	}

	public TimeSeries makeRegressionTS(String variant) {
		build();
		SimpleRegression fit;
		synchronized (this) {
			fit = fits.get(variant);
		}
		if (fit == null) {
			return null;
		}
		String name = variant.replaceAll("nextcladePangoLineage:", "");
		TimeSeries series = new TimeSeries(String.format("%s %s", name, slopeToWeekly(fit)));
		int f = getFirstDay();
		int l = CalendarUtils.timeToDay(System.currentTimeMillis()) + 30;
		series.add(CalendarUtils.dayToDay(f), Math.exp(fit.predict(f)));
		series.add(CalendarUtils.dayToDay(l), Math.exp(fit.predict(l)));
		return series;
	}
}
