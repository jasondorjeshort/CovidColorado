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

/**
 * One sewage series, a plant's or an aggregate's: one {@link DaySewage} per
 * day index, over the range {@link DailyTracker} keeps. Every sewage chart is
 * drawn from one of these (docs/reference/charts.txt, THE NAMING RULE).
 * <p>
 * A plant's entries are in its own units, and {@link #getNormalizer} puts them
 * on the national scale, a percentage of the pandemic peak. An aggregate's are
 * on that scale already and its normalizer is 1; {@code sewage/Plant.java} is
 * the only override. The methods that read an entry's raw value instead of the
 * normalized one say so.
 * <p>
 * {@link #build} runs once, from the first method that needs the inflections,
 * so the entries must be complete by then; nothing re-runs it. The entry map is
 * guarded by this object's monitor. DailyTracker's lock and each DaySewage's
 * are taken inside it and never the other way round.
 */
public abstract class Abstract extends DailyTracker {

	/*
	 * A plant's served population, set by nwss/Nwss.java, or null when the
	 * plant has none, and then no aggregate includes it. An aggregate's is its
	 * "line pop": the largest undamped population summed on any one day.
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

	/*
	 * The first day after day, and no later than lastDay, that has a reading,
	 * or -1 when there is none.
	 */
	private int nextReadingDay(int day, int lastDay) {
		for (int next = day + 1; next <= lastDay; next++) {
			if (getNormalized(next) != null) {
				return next;
			}
		}
		return -1;
	}

	/*
	 * The peaks and valleys, used as the chart markers, as the bound on how far
	 * back makeFitSeries reaches, as how far back a recent chart reaches when
	 * the last one is over 180 days old, and as VocSewage's fit seed.
	 * docs/reference/wastewater.txt, under INFLECTIONS AND FIT LINES, has the
	 * design.
	 *
	 * Readings are compared with each other, not with the calendar day before
	 * them: a plant reporting every few days has its turns found the same way a
	 * daily one does. Comparing consecutive calendar days instead left nine
	 * plants in ten with no inflection at all, since most never report two days
	 * running.
	 */
	private synchronized void buildInflections() {
		int firstDay = Math.max(getFirstDay(), CalendarUtils.dateToDay("9/1/2020")), lastDay = getLastDay();

		/*
		 * build() only promises a positive total somewhere in the day range,
		 * not two readings on or after 2020-09-01, so a series that ended
		 * before then, or that reported on one day only, has no direction to
		 * start from and gets no inflections.
		 */
		int turnDay = nextReadingDay(firstDay - 1, lastDay);
		if (turnDay < 0) {
			return;
		}
		int day = nextReadingDay(turnDay, lastDay);
		if (day < 0) {
			return;
		}
		boolean rising = getNormalized(day) >= getNormalized(turnDay);

		/*
		 * A turn is a reading whose next reading goes against the current
		 * direction, ties counting as rising. It is kept only if no reading in
		 * the four weeks after it, up to 27 days past it and not past the last
		 * day, is back on the old side of that next reading; otherwise the scan
		 * resumes after the reading that went back. The inflection is the turn
		 * day itself, the peak or valley. The window is what spreads the markers
		 * out: it was 30 days, then 21 ("3 week gap between inflections I
		 * guess?"), then 28 ("Go back to 4 week min interval between
		 * inflections", c135ff9). It is not a hard minimum, since the next turn
		 * is sought from the next reading on.
		 *
		 * No turn is sought in the last 14 days, so each is confirmed over at
		 * least 13 days, and none is within two weeks of the last day. The 14
		 * came with the first version and has no recorded reason.
		 *
		 * Both windows are counted in days rather than in readings so that a
		 * plant reporting every few days confirms a turn over the same four
		 * weeks, and keeps clear of the same last two weeks, as a daily plant
		 * does. Counted in readings, a weekly plant's confirmation window would
		 * run for half a year.
		 */
		while (turnDay <= lastDay - 14) {
			double val2 = getNormalized(day);
			boolean stillRising = val2 >= getNormalized(turnDay);

			if (stillRising != rising) {
				int windowEnd = Math.min(lastDay, turnDay + 27);
				int flipDay = -1;
				for (int d = nextReadingDay(day, windowEnd); d >= 0; d = nextReadingDay(d, windowEnd)) {
					if ((getNormalized(d) >= val2) == rising) {
						flipDay = d;
						break;
					}
				}

				if (flipDay < 0) {
					Inflection inflection = new Inflection();
					inflection.day = turnDay;
					inflection.peak = rising;
					inflections.add(inflection);

					rising = !rising;
				} else {
					turnDay = nextReadingDay(flipDay, lastDay);
					if (turnDay < 0) {
						return;
					}
					day = nextReadingDay(turnDay, lastDay);
					if (day < 0) {
						return;
					}
					continue;
				}
			}

			turnDay = day;
			day = nextReadingDay(day, lastDay);
			if (day < 0) {
				return;
			}
		}
	}

	/**
	 * Finds the inflections and then runs {@link #buildBackend}, the first time
	 * only. A series whose total is not positive gets neither, and a one-day
	 * series no inflections.
	 */
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

	/**
	 * A subclass's own once-only step, run under build()'s lock after the
	 * inflections, so a trim of the range it makes is not seen by them.
	 */
	protected void buildBackend() {

	}

	/**
	 * The series as a chart line, named {@code name}, or {@link #getTSName}
	 * when that is null. A value at or below zero is drawn as 1E-6, because the
	 * log axis cannot take it, and shows as a drop off the chart's bottom edge.
	 * <p>
	 * With {@code daysAveraged} 1: a point for each day with an entry,
	 * normalized. With more: a point for every day from the first to today (the
	 * machine's local date, {@link covid.CalendarUtils#today}) whose trailing
	 * window of that many days holds a reading, the mean of the normalized
	 * readings in that window. A window holding none gets no point at all, so
	 * the line ends at the last day with a reading instead of running on to
	 * today, and a gap wider than the window is spanned by one straight segment
	 * instead of dropping the line off the chart's bottom and back. Both kinds
	 * of point are on the national scale, so a plant's smoothed line, its daily
	 * line and its fit line share the axis.
	 * <p>
	 * The mean is over the days that have a reading, not over the window's
	 * length: most plants sample once or twice a week, and counting their other
	 * days as zero drew them at a fraction of their own level. The first
	 * windows reach back before the first day and so average only the days from
	 * it onwards -- except on an aggregate, where days trimmed off the front by
	 * {@code sewage/Multi.java} buildBackend keep their entries and so still
	 * feed them.
	 */
	public synchronized TimeSeries makeTimeSeries(String name, int daysAveraged) {
		build();
		if (name == null) {
			name = getTSName();
		}
		TimeSeries series = new TimeSeries(name);
		int today = CalendarUtils.today();
		Integer popo = getPopulation();
		for (int day = getFirstDay(); day <= today; day++) {
			double number;
			if (daysAveraged > 1) {
				number = 0;
				int daysRead = 0;
				for (int day2 = day; day2 > day - daysAveraged; day2--) {
					DaySewage entry = getEntry(day2);
					if (entry != null) {
						number += entry.getSewage();
						daysRead++;
					}
				}
				if (daysRead == 0) {
					continue;
				}
				number *= getNormalizer();
				number /= daysRead;
			} else {
				DaySewage entry = getEntry(day);
				if (entry == null) {
					continue;
				}

				/*
				 * An aggregate's daily line stops at the first day in its
				 * last three weeks whose undamped population is under a
				 * quarter of the largest it ever had, the line pop; a plant's
				 * entries carry no population, so a plant's line never stops
				 * early. Presumably against a tail carried by the few plants
				 * that have reported so far, but no commit says why. It went
				 * from skipping any day under half, to this cut at three
				 * quarters within 21 days (a38c69e, when the fade-out was also
				 * 21 days), to 1/20 (657a150), to a quarter (8a190e5).
				 */
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

	/**
	 * What an entry is multiplied by to put it on the national scale: 1 here,
	 * for an aggregate, whose entries are normalized as plants are added.
	 */
	@SuppressWarnings("static-method")
	public double getNormalizer() {
		return 1.0;
	}

	/** Percent change per week, for a slope of log(value) per day. */
	public static double slopeToWeekly(double slope) {
		return 100.0 * (Math.exp(7.0 * slope) - 1);
	}

	/**
	 * The fit's 95% confidence interval for its slope, as weekly percent
	 * change, formatted for a legend. NaN below three points.
	 */
	public static String slopeToWeekly(SimpleRegression fit) {
		double min = slopeToWeekly(fit.getSlope() - fit.getSlopeConfidenceInterval());
		double max = slopeToWeekly(fit.getSlope() + fit.getSlopeConfidenceInterval());
		return String.format("[%+.1f%%,%+.1f%%]/week", min, max);
	}

	/** The day's entry times {@link #getNormalizer}, or null when it has none. */
	public synchronized Double getNormalized(int day) {
		DaySewage entry = getEntry(day);
		if (entry == null) {
			return null;
		}
		return entry.getSewage() * getNormalizer();
	}

	/**
	 * A straight line fitted to log(normalized value), drawn from where the fit
	 * starts to today; null for a one-day series, or when the line cannot be
	 * built.
	 * <p>
	 * The fit walks backwards from the last day, never further back than a
	 * week after the last inflection. Every reading from {@code numDays} before
	 * the last day onwards is kept, and the first one before that; each earlier
	 * reading is kept only while it does not widen the slope's confidence
	 * interval, and the walk stops at the first that does. A reading that is
	 * not positive is left out of the fit altogether, log(0) being -Infinity.
	 * The inflection bound wins over {@code numDays}: an inflection can be as
	 * late as 14 days before the last day, which leaves eight days to fit.
	 * <p>
	 * The legend gives the interval as weekly growth and the value predicted
	 * for today. SimpleRegression gives a NaN interval below three readings and
	 * a NaN line below two. Today here is the machine's local date,
	 * {@link covid.CalendarUtils#today}, as in makeTimeSeries.
	 */
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
			/*
			 * A plant's own readings can be zero, and log(0) is -Infinity.
			 * commons-math's SimpleRegression keeps a running ybar, so one
			 * -Infinity poisons every point added after it and the slope, the
			 * confidence interval and predict() all come back NaN. The day is
			 * left out of the fit entirely, as makeTimeSeries floors it to 1E-6
			 * for drawing; the confidence walk below never sees it, so a skipped
			 * day does not count against numDays or move startDay.
			 */
			if (number <= 0) {
				continue;
			}
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
			int today = CalendarUtils.today();
			TimeSeries series = new TimeSeries(
					String.format("%s (%s, today=%.1f)", "Fit", slopeToWeekly(fit), Math.exp(fit.predict(today))));
			/*
			 * A plant that stopped reporting years ago with a falling trend
			 * extrapolates to exp(-huge) = 0, which the log axis refuses.
			 */
			series.add(CalendarUtils.dayToDay(startDay), Math.max(1E-6, Math.exp(fit.predict(startDay))));
			series.add(CalendarUtils.dayToDay(today), Math.max(1E-6, Math.exp(fit.predict(today))));
			return series;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Error on " + getClass() + " - " + getName());
			return null;
		}
	}

	/**
	 * A domain marker per inflection: red for a peak, labelled with its date
	 * and its {@link #getNormalized} value, green for a valley. The label is on
	 * the axis's scale, as the inflections themselves are.
	 */
	public synchronized LinkedList<ValueMarker> getMarkers() {
		build();
		LinkedList<ValueMarker> markers = new LinkedList<>();

		for (int i = 0; i < inflections.size(); i++) {
			Inflection inflection = inflections.get(i);
			long time = CalendarUtils.dayToTime(inflection.day);
			ValueMarker marker = new ValueMarker(time);
			marker.setPaint(inflection.peak ? Color.red : Color.green);
			if (inflection.peak) {
				double val = getNormalized(inflection.day);
				marker.setLabel(String.format("%s %.1f", CalendarUtils.dayToDate(inflection.day), val));
			}
			marker.setStroke(Charts.stroke);
			marker.setLabelFont(Charts.font);
			marker.setLabelTextAnchor(inflection.peak ? TextAnchor.TOP_CENTER : TextAnchor.HALF_ASCENT_CENTER);
			markers.add(marker);
		}

		return markers;
	}

	/**
	 * The last inflection as {@link CalendarUtils#dayToTime} milliseconds, or
	 * null when there is none.
	 */
	public Long getLastInflection() {
		build();
		if (inflections.size() == 0) {
			return null;
		}
		Inflection inflection = inflections.get(inflections.size() - 1);
		return CalendarUtils.dayToTime(inflection.day);
	}

	/** The same value as {@link #getNormalized}, holding the lock only for the lookup. */
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

	/**
	 * Stores a plant reading, in the plant's own units, replacing any entry for
	 * {@code day}.
	 */
	public void addEntry(int day, double value) {
		DaySewage entry = new DaySewage(value);
		synchronized (this) {
			entries.put(day, entry);
		}
		includeDay(day);
	}

	/** The aggregate accumulator for {@code day}, created empty if the day has no entry. */
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

	/**
	 * Empties the entries, and nothing else: the day range stays, and a build
	 * that has run is not undone. So it is for refilling before the first
	 * build, as {@link All}'s baseline loop does.
	 */
	public synchronized void clear() {
		entries.clear();
	}

	public synchronized Integer getPopulation() {
		return population;
	}

	public synchronized void setPopulation(int population) {
		this.population = population;
	}

	/*
	 * What counts as a stop in reporting: a run of more than this many days with
	 * no entry. Plants sample every one to seven days, a few every two weeks, so
	 * a fixed bound well past any routine sampling interval separates a pause in
	 * reporting from the plant's own cadence; a month is that bound, and it is a
	 * choice with no derivation beyond that.
	 *
	 * A reading of zero is a reading like any other and does not end a run; only
	 * a gap this long does.
	 */
	public static final int REPORTING_GAP_DAYS = 30;

	/**
	 * The first day from {@code startDay} on that begins a run of more than
	 * {@link #REPORTING_GAP_DAYS} days with no entry, or the last day + 1 when
	 * there is none. Multi takes it as the day this series stops reporting, and
	 * fades the plant out over the fortnight before it.
	 */
	public synchronized int getNextReportingGap(int startDay) {
		int lastDay = getLastDay();
		int gapStart = -1;
		for (int day = startDay; day <= lastDay; day++) {
			if (entries.get(day) != null) {
				gapStart = -1;
			} else if (gapStart < 0) {
				gapStart = day;
			} else if (day - gapStart + 1 > REPORTING_GAP_DAYS) {
				return gapStart;
			}
		}
		return lastDay + 1;
	}

	/** The sum of the raw entries over the inclusive range; 0 when it holds none. */
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

	/** The largest raw entry in the inclusive range, or null when it holds none. */
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
