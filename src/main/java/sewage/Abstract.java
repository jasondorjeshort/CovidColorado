package sewage;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.data.time.TimeSeries;

import covid.CalendarUtils;
import covid.DailyTracker;
import nwss.DaySewage;
import variants.Voc;

public abstract class Abstract extends DailyTracker {

	/*
	 * If population is included it applies to the entire day set. If null then
	 * each day set should have its own.
	 */
	private Integer population;

	private final HashMap<Integer, DaySewage> entries = new HashMap<>();

	public abstract String getTSName();

	public abstract String getChartFilename();

	public abstract String getTitleLine();

	public synchronized TimeSeries makeTimeSeries(String name) {
		if (name == null) {
			name = getTSName();
		}
		TimeSeries series = new TimeSeries(name);
		Integer popo = getPopulation();
		for (int day = getFirstDay(); day <= getLastDay(); day++) {
			DaySewage entry;
			synchronized (this) {
				entry = entries.get(day);
			}
			if (entry == null) {
				continue;
			}

			Double pop = entry.getPop();
			if (pop != null && popo != null && pop < popo / 4.0 && day > getLastDay() - 21) {
				break;
			}

			double number = entry.getSewage();
			number *= getNormalizer();
			if (number <= 0) {
				number = 1E-6;
			}
			series.add(CalendarUtils.dayToDay(day), number);
		}
		return series;
	}

	@SuppressWarnings("static-method")
	public double getNormalizer() {
		return 1.0;
	}

	public synchronized TimeSeries makeFitSeries(int fFirstDay) {
		final SimpleRegression fit = new SimpleRegression();
		fFirstDay = Math.max(fFirstDay, getFirstDay());
		int fLastDay = getLastDay();
		if (fFirstDay >= fLastDay) {
			return null;
		}
		for (int day = fFirstDay; day <= fLastDay; day++) {
			DaySewage entry;
			synchronized (this) {
				entry = entries.get(day);
			}
			if (entry == null) {
				continue;
			}

			double number = entry.getSewage();
			number *= getNormalizer();
			fit.addData(day, Math.log(number));
		}

		TimeSeries series = new TimeSeries(
				String.format("%s (%+.0f%%/week)", "Fit", 100.0 * (Math.exp(7.0 * fit.getSlope()) - 1)));
		try {
			series.add(CalendarUtils.dayToDay(fFirstDay), Math.exp(fit.predict(fFirstDay)));
			series.add(CalendarUtils.dayToDay(fLastDay), Math.exp(fit.predict(fLastDay)));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("First: " + fFirstDay);
			System.out.println("Last: " + fLastDay);
		}
		return series;
	}

	public synchronized void makeTimeSeries(TimeSeries series, Voc voc, String variant) {
		for (int day = Math.max(getFirstDay(), voc.getFirstDay()); day <= getLastDay()
				&& day <= voc.getLastDay(); day++) {
			DaySewage entry;
			synchronized (this) {
				entry = entries.get(day);
			}
			if (entry == null) {
				continue;
			}

			Double pop = entry.getPop();

			double number = entry.getSewage();
			number *= getNormalizer();
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

	public synchronized TimeSeries makeRegressionTS(Voc voc, String variant) {
		final SimpleRegression fit = new SimpleRegression();
		final int fitLastDay = Math.min(getLastDay(), voc.getLastDay());
		final int fitFirstDay = Math.max(getFirstDay(), voc.getFirstDay());
		for (int day = fitFirstDay; day <= fitLastDay; day++) {
			DaySewage entry;
			synchronized (this) {
				entry = entries.get(day);
			}
			if (entry == null) {
				continue;
			}

			double number = entry.getSewage();
			number *= getNormalizer();

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

	public int getFirstDay(Voc voc) {
		return Math.max(getFirstDay(), voc.getFirstDay());
	}

	public int getLastDay(Voc voc) {
		return Math.min(getLastDay(), voc.getLastDay());
	}

	public synchronized HashMap<String, Double> getCumulativePrevalence(Voc voc, ArrayList<String> variants) {
		HashMap<String, Double> prevalence = new HashMap<>();

		int vFirstDay = getFirstDay(voc), vLastDay = getLastDay(voc);
		for (String variant : variants) {
			double number = 0;
			for (int day = vFirstDay; day <= vLastDay; day++) {
				Double prev = getSewageNormalized(day);
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

	public Double getSewageNormalized(int day) {
		DaySewage entry;
		double n;
		synchronized (this) {
			entry = entries.get(day);
			n = getNormalizer();
		}
		if (entry == null) {
			return null;
		}
		return entry.getSewage() * n;
	}

	public void addEntry(int day, double value) {
		DaySewage entry = new DaySewage(value);
		synchronized (this) {
			entries.put(day, entry);
		}
		includeDay(day);
	}

	public synchronized DaySewage getOrCreateMultiEntry(int day) {
		DaySewage ds = getEntry(day);
		if (ds == null) {
			ds = new DaySewage();
			entries.put(day, ds);
			includeDay(day);
		}
		return ds;
	}

	public synchronized DaySewage getEntry(int day) {
		return entries.get(day);
	}

	public synchronized void clear() {
		entries.clear();
	}

	public synchronized TimeSeries makeRegressionTS(Voc voc, ArrayList<String> variants) {
		final int fitFirstDay = getFirstDay(voc);
		final int fitLastDay = getLastDay(voc);
		HashMap<String, SimpleRegression> fits = new HashMap<>();
		HashMap<String, Double> finals = new HashMap<>();
		int tsLastDay = getLastDay() + 28;
		for (String variant : variants) {
			final SimpleRegression fit = new SimpleRegression();
			for (int day = fitFirstDay; day <= fitLastDay; day++) {
				Double number = getSewageNormalized(day);
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

	public synchronized Integer getPopulation() {
		return population;
	}

	public synchronized void setPopulation(int population) {
		this.population = population;
	}

	public synchronized int getNextZero(int startDay) {
		int lastDay = getLastDay();
		for (int day = startDay; day <= lastDay; day++) {
			DaySewage ds = entries.get(day);
			if (ds == null || ds.getSewage() <= 0) {
				return day;
			}
		}
		return lastDay + 1;
	}

	public synchronized double getTotalSewage(int first, int last) {
		double sewage = 0.0;
		for (int day = first; day <= last; day++) {
			DaySewage ds = entries.get(day);
			if (ds != null) {
				sewage += ds.getSewage();
			}
		}
		return sewage;
	}

}
