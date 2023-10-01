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
		makeFits();
		variants.addAll(variantsByCumulative);
		return prevalence;
	}

	double getSlopeByGrowth(int n) {
		String v = variantsByGrowth.get(n);
		return fits.get(v).getSlope();
	}

	public VocSewage merge(int variantCount) {
		makeFits();

		if (variantCount >= variantsByGrowth.size()) {
			return this;
		}

		final int count = 5;
		final double[] targetSlopes = new double[count];
		@SuppressWarnings("unchecked")
		final LinkedList<String>[] variants = new LinkedList[count];
		targetSlopes[0] = getSlopeByGrowth(Math.max((numVariants - 1) / 10, 1));
		targetSlopes[count - 1] = getSlopeByGrowth(numVariants - 1) * 0.33 + getSlopeByGrowth(numVariants - 2) * 0.67;
		double gap = (targetSlopes[count - 1] - targetSlopes[0]) / (count - 1);
		for (int i = 0; i < count - 1; i++) {
			targetSlopes[i] = targetSlopes[0] + i * gap;
		}
		for (int i = 0; i < count; i++) {
			variants[i] = new LinkedList<>();
		}

		for (String variant : variantsByGrowth) {
			double slope = fits.get(variant).getSlope();

			int closest = 0;
			double closestDist = Math.abs(slope - targetSlopes[0]);
			// This is O(mn), could be O(m) with a better algorithm but only n=5
			for (int i = 1; i < count; i++) {
				double dist = Math.abs(slope - targetSlopes[i]);
				if (dist < closestDist) {
					closest = i;
					closestDist = dist;
				}
			}
			variants[closest].add(variant);
		}

		Voc voc2 = new Voc(voc, variants);
		return new VocSewage(sewage, voc2);
	}

	public synchronized void makeTimeSeries(TimeSeries series, String variant) {
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
		final int fitFirstDay = getFirstDay();
		final int fitLastDay = getLastDay();
		HashMap<String, SimpleRegression> fits = new HashMap<>();
		HashMap<String, Double> finals = new HashMap<>();
		int tsLastDay = getLastDay() + 28;
		for (String variant : variants) {
			final SimpleRegression fit = new SimpleRegression();
			for (int day = fitFirstDay; day <= fitLastDay; day++) {
				Double number = sewage.getSewageNormalized(day);
				if (number == null) {
					continue;
				}
				number *= voc.getPrevalence(day, variant);
				if (number <= 0) {
					continue;
				}

				fit.addData(day, Math.log(number));
			}

			fits.put(variant, fit);
			finals.put(variant, Math.exp(fits.get(variant).predict(tsLastDay)));
		}

		variants.sort((v1, v2) -> -Double.compare(finals.get(v1), finals.get(v2)));

		TimeSeries series = new TimeSeries(String.format("Collective fit"));
		for (int day = fitFirstDay; day <= tsLastDay; day++) {
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

	private int numVariants;
	private int fitLastDay;
	private ArrayList<String> variantsByGrowth, variantsByCount, variantsByCumulative;
	private HashMap<String, SimpleRegression> fits;
	private HashMap<String, Double> prevalence = new HashMap<>();

	public synchronized void makeFits() {
		if (fits != null) {
			return;
		}

		fits = new HashMap<>();
		variantsByGrowth = voc.getVariants();
		variantsByCount = voc.getVariants();
		numVariants = variantsByGrowth.size();
		fitLastDay = getLastDay() + 28;

		final int fitFirstDay = getFirstDay();
		for (String variant : variantsByGrowth) {
			SimpleRegression fit = new SimpleRegression();
			for (int day = fitFirstDay; day <= fitLastDay; day++) {
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
		variantsByCount
				.sort((v1, v2) -> Double.compare(fits.get(v1).predict(fitLastDay), fits.get(v2).predict(fitLastDay)));

		variantsByCumulative = voc.getVariants();
		int vFirstDay = getFirstDay(), vLastDay = getLastDay();
		for (String variant : variantsByCumulative) {
			double number = 0;
			for (int day = vFirstDay; day <= vLastDay; day++) {
				Double prev = sewage.getSewageNormalized(day);
				if (prev == null) {
					continue;
				}
				number += prev * voc.getPrevalence(day, variant);
			}
			prevalence.put(variant, number);
		}

		variantsByCumulative.sort((v1, v2) -> -Double.compare(prevalence.get(v1), prevalence.get(v2)));
	}

	public TimeSeries makeRegressionTS(String variant) {
		makeFits();
		SimpleRegression fit;
		synchronized (this) {
			fit = fits.get(variant);
		}
		if (fit == null) {
			return null;
		}
		int fitFirstDay = Math.max(getFirstDay(), voc.getFirstDay());
		String name = variant.replaceAll("nextcladePangoLineage:", "");
		TimeSeries series = new TimeSeries(String.format("%s %s", name, slopeToWeekly(fit)));
		series.add(CalendarUtils.dayToDay(fitFirstDay), Math.exp(fit.predict(fitFirstDay)));

		series.add(CalendarUtils.dayToDay(fitLastDay), Math.exp(fit.predict(fitLastDay)));
		return series;
	}
}
