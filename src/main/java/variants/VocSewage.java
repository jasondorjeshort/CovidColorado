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

	public double getCollectiveFit(int day) {
		synchronized (collectiveFit) {
			Double n = collectiveFit.get(day);
			if (n != null) {
				return n;
			}

			double number = 0.0;
			for (String variant : variantsByCount) {
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

	public TimeSeries makeCollectiveTS() {
		build();

		TimeSeries series = new TimeSeries(String.format("Collective fit"));
		for (int day = getFirstDay(); day <= modelLastDay; day++) {
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

	private boolean built = false;
	private int numVariants;
	private ArrayList<String> variantsByGrowth, variantsByCount, variantsByCumulative;
	private int currentDay, modelLastDay;
	private HashMap<String, SimpleRegression> fits;
	private final HashMap<String, Double> cumulativePrevalence = new HashMap<>();
	private double cumulative = 0;
	private final HashMap<Integer, Double> collectiveFit = new HashMap<>();

	public int getModelLastDay() {
		build();
		return modelLastDay;
	}

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

	public double getFit(String variant, int day) {
		return fits.get(variant).predict(day);
	}

	public synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		fits = new HashMap<>();
		variantsByGrowth = voc.getVariantNames();
		variantsByCount = voc.getVariantNames();
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
		currentDay = CalendarUtils.timeToDay(System.currentTimeMillis());
		modelLastDay = currentDay + 30;
		variantsByCount.sort(
				(v1, v2) -> -Double.compare(fits.get(v1).predict(modelLastDay), fits.get(v2).predict(modelLastDay)));

		variantsByCumulative = voc.getVariantNames();

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

	public synchronized TimeSeries makeRelativeSeries(String variant, boolean doFit) {
		build();
		String name = variant.replaceAll("nextcladePangoLineage:", "");
		SimpleRegression fit = null;
		if (doFit) {
			synchronized (this) {
				fit = fits.get(variant);
			}
		}
		if (fit != null) {
			double num = 100 * Math.exp(fit.predict(currentDay)) / getCollectiveFit(currentDay);
			double lastNum = 100 * Math.exp(fit.predict(modelLastDay)) / getCollectiveFit(modelLastDay);
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
		if (fit != null) {
			int day = getFirstDay() - 42;
			series.add(CalendarUtils.dayToDay(day), 100 * Math.exp(fit.predict(day)) / getCollectiveFit(day));
		}
		for (int day = Math.max(getFirstDay(), voc.getFirstDay()); day <= getLastDay(); day++) {
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
				// fit data before or after will fill for it
				continue;
			}

			series.add(CalendarUtils.dayToDay(day), 100 * number / entry.getSewage());
		}
		if (fit != null) {
			for (int day = getLastDay() + 1; day <= modelLastDay; day++) {
				series.add(CalendarUtils.dayToDay(day), 100 * Math.exp(fit.predict(day)) / getCollectiveFit(day));
			}
		}
		return series;
	}

	public synchronized TimeSeries makeAbsoluteSeries(String variant, boolean doFit) {
		build();
		String name = variant.replaceAll("nextcladePangoLineage:", "");
		SimpleRegression fit = null;
		if (doFit) {
			synchronized (this) {
				fit = fits.get(variant);
			}
		}
		if (fit != null) {
			String cap = variantsByGrowth.get(variantsByGrowth.size() - 1);
			String apex = "";
			double num = Math.exp(fit.predict(currentDay));
			if (variant.equalsIgnoreCase(cap)) {
				apex = ", 365d+";
				int n = num > getCollectiveFit(currentDay) * 0.5 ? -1 : 1;
				for (int day = currentDay; day < currentDay + 364; day += n) {
					if (Math.exp(fit.predict(day)) > getCollectiveFit(day) * 0.5) {
						apex = ", " + (day - currentDay) + "d";
						break;
					}
				}
			}
			if (num > 1) {
				name = String.format("%s (%.1f%s%s)", name, num, slopeToWeekly(fit), apex);
			} else if (num > 0.1) {
				name = String.format("%s (%.2f%s%s)", name, num, slopeToWeekly(fit), apex);
			} else {
				name = String.format("%s (%.3f%s%s)", name, num, slopeToWeekly(fit), apex);
			}
		}
		TimeSeries series = new TimeSeries(name);
		if (fit != null) {
			int day = getFirstDay() - 42;
			series.add(CalendarUtils.dayToDay(day), Math.exp(fit.predict(day)));
		}
		for (int day = Math.max(getFirstDay(), voc.getFirstDay()); day <= getLastDay(); day++) {
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
				// fit data before or after will fill for it
				continue;
			}

			series.add(CalendarUtils.dayToDay(day), number);
		}
		if (fit != null) {
			for (int day = getLastDay() + 1; day <= modelLastDay; day++) {
				series.add(CalendarUtils.dayToDay(day), Math.exp(fit.predict(day)));
			}
		}
		return series;
	}

	public ArrayList<String> getVariantsByCount() {
		build();
		return variantsByCount;
	}
}
