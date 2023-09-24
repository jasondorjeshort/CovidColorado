package sewage;

import java.util.HashMap;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.data.time.TimeSeries;

import covid.CalendarUtils;
import covid.DailyTracker;
import nwss.DaySewage;

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
