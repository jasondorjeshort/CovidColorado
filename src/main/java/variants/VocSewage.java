package variants;

import java.util.ArrayList;
import java.util.HashMap;

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
		HashMap<String, Double> prevalence = new HashMap<>();

		int vFirstDay = getFirstDay(), vLastDay = getLastDay();
		for (String variant : variants) {
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

		variants.sort((v1, v2) -> -Double.compare(prevalence.get(v1), prevalence.get(v2)));

		return prevalence;
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

	public synchronized TimeSeries makeRegressionTS(String variant) {
		final SimpleRegression fit = new SimpleRegression();
		final int fitLastDay = Math.min(getLastDay(), voc.getLastDay());
		final int fitFirstDay = Math.max(getFirstDay(), voc.getFirstDay());
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

		String name = variant.replaceAll("nextcladePangoLineage:", "");
		TimeSeries series = new TimeSeries(
				String.format("%s (%+.0f%%/week)", name, 100.0 * (Math.exp(7.0 * fit.getSlope()) - 1)));
		int first = Math.max(getFirstDay(), voc.getFirstDay());
		series.add(CalendarUtils.dayToDay(first), Math.exp(fit.predict(first)));
		int last = getLastDay() + 28;
		series.add(CalendarUtils.dayToDay(last), Math.exp(fit.predict(last)));
		return series;
	}
}
