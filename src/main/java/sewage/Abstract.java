package sewage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.TimeSeries;

import charts.Charts;
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

	public abstract String getName();

	public abstract String getChartFilename();

	public abstract String getTitleLine();

	class Inflection {
		int day;
		boolean peak; // vs valley
	}

	private boolean built = false;
	private final ArrayList<Inflection> inflections = new ArrayList<>();

	private synchronized void buildInflections() {
		int firstDay = Math.max(getFirstDay(), CalendarUtils.dateToDay("9/1/2020")), lastDay = getLastDay();
		boolean rising = getNormalized(firstDay + 1) >= getNormalized(firstDay);

		/*
		 * This logic is hackish and awful! We want to find inflection points
		 * that are spread-out enough from others.
		 */
		for (int day = firstDay + 1; day <= lastDay - 14; day++) {
			Double val = getNormalized(day);
			Double val2 = getNormalized(day + 1);
			if (val == null || val2 == null) {
				continue;
			}
			boolean stillRising = val2 >= val;

			if (stillRising == rising) {
				continue;
			}

			double baseVal = val2;
			for (int flipDay = day + 2; flipDay < day + 28 && flipDay <= lastDay; flipDay++) {
				val2 = getNormalized(flipDay);
				if (val2 == null) {
					continue;
				}
				stillRising = val2 >= baseVal;
				if (stillRising == rising) {
					val = val2;
					day = flipDay;

					break;
				}
			}

			if (stillRising == rising) {
				continue;
			}

			Inflection inflection = new Inflection();
			inflection.day = day;
			inflection.peak = rising;
			inflections.add(inflection);

			rising = !rising;
		}
	}

	protected synchronized final void build() {
		if (built) {
			return;
		}
		built = true;

		if (getTotalSewage() <= 0) {
			return;
		}
		if (getLastDay() > getFirstDay()) {
			buildInflections();
		}
		buildBackend();
	}

	protected void buildBackend() {

	}

	public synchronized TimeSeries makeTimeSeries(String name, boolean yearlyAverage) {
		build();
		if (name == null) {
			name = getTSName();
		}
		TimeSeries series = new TimeSeries(name);
		int today = CalendarUtils.timeToDay(System.currentTimeMillis());
		Integer popo = getPopulation();
		for (int day = getFirstDay(); day <= today; day++) {
			double number;
			if (yearlyAverage) {
				number = 0;
				for (int day2 = day; day2 > day - 365; day2--) {
					DaySewage entry = getEntry(day2);
					if (entry != null) {
						number += entry.getSewage();
					}
				}
				number /= 365.0;
			} else {
				DaySewage entry = getEntry(day);
				if (entry == null) {
					continue;
				}

				Double pop = entry.getPop();
				if (pop != null && popo != null && pop < popo / 4.0 && day > getLastDay() - 21) {
					break;
				}

				number = entry.getSewage();
				number *= getNormalizer();
			}
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

	public static double slopeToWeekly(double slope) {
		return 100.0 * (Math.exp(7.0 * slope) - 1);
	}

	public static String slopeToWeekly(SimpleRegression fit) {
		double min = slopeToWeekly(fit.getSlope() - fit.getSlopeConfidenceInterval());
		double max = slopeToWeekly(fit.getSlope() + fit.getSlopeConfidenceInterval());
		return String.format("[%+.1f%%,%+.1f%%]/week", min, max);
	}

	public synchronized Double getNormalized(int day) {
		DaySewage entry = getEntry(day);
		if (entry == null) {
			return null;
		}
		return entry.getSewage() * getNormalizer();
	}

	public synchronized TimeSeries makeFitSeries(int numDays) {
		build();
		final SimpleRegression fit = new SimpleRegression();
		Double confidence = null;
		int startDay = getFirstDay(), endDay = getLastDay();

		if (inflections.size() > 0) {
			startDay = Math.max(startDay, inflections.get(inflections.size() - 1).day + 7);
		}

		if (startDay == endDay) {
			return null;
		}

		for (int day = endDay; day >= startDay; day--) {
			DaySewage entry = getEntry(day);
			if (entry == null) {
				continue;
			}

			double number = entry.getSewage();
			number *= getNormalizer();
			double val = Math.log(number);
			fit.addData(day, val);

			if (day < endDay - numDays) {
				double newConfidence = fit.getSlopeConfidenceInterval();
				if (confidence != null && newConfidence > confidence) {
					fit.removeData(day, val);
					startDay = day + 1;
					break;
				}
				confidence = newConfidence;
			}
		}

		try {
			int today = CalendarUtils.timeToDay(System.currentTimeMillis());
			TimeSeries series = new TimeSeries(
					String.format("%s (%s, today=%.0f)", "Fit", slopeToWeekly(fit), Math.exp(fit.predict(today))));
			series.add(CalendarUtils.dayToDay(startDay), Math.exp(fit.predict(startDay)));
			series.add(CalendarUtils.dayToDay(today), Math.exp(fit.predict(today)));
			return series;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Error on " + getClass() + " - " + getName());
			return null;
		}
	}

	public synchronized LinkedList<ValueMarker> getMarkers() {
		build();
		LinkedList<ValueMarker> markers = new LinkedList<>();

		for (int i = 0; i < inflections.size(); i++) {
			Inflection inflection = inflections.get(i);
			long time = CalendarUtils.dayToTime(inflection.day);
			ValueMarker marker = new ValueMarker(time);
			marker.setPaint(inflection.peak ? Color.red : Color.green);
			if (inflection.peak) {
				double val = entries.get(inflection.day).getSewage();
				marker.setLabel(String.format("%s %.1f", CalendarUtils.dayToDate(inflection.day), val));
			}
			marker.setStroke(Charts.stroke);
			marker.setLabelFont(Charts.font);
			marker.setLabelTextAnchor(inflection.peak ? TextAnchor.TOP_CENTER : TextAnchor.HALF_ASCENT_CENTER);
			markers.add(marker);
		}

		return markers;
	}

	public Long getLastInflection() {
		if (inflections.size() == 0) {
			return null;
		}
		Inflection inflection = inflections.get(inflections.size() - 1);
		return CalendarUtils.dayToTime(inflection.day);
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

	public double getTotalSewage(int first, int last) {
		double totalSewage = 0.0;
		for (int day = first; day <= last; day++) {
			DaySewage ds = getEntry(day);
			if (ds != null) {
				totalSewage += ds.getSewage();
			}
		}
		return totalSewage;
	}

	public double getTotalSewage() {
		return getTotalSewage(getFirstDay(), getLastDay());
	}

	public Double getHighestSewage(int first, int last) {
		Double highestSewage = null;
		for (int day = first; day <= last; day++) {
			DaySewage ds = getEntry(day);
			if (ds != null) {
				double s = ds.getSewage();
				if (highestSewage == null || s > highestSewage) {
					highestSewage = s;
				}
			}
		}
		return highestSewage;
	}

}
