package sewage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

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
	 * Counts of the two things buildInflections passes over, summed across every
	 * series a run builds and reported once by printInflectionSummary. Neither
	 * is an error -- a sparse series legitimately has gaps -- but they happen
	 * thousands of times a run, so they are counted rather than printed, the way
	 * nwss/Nwss.java readSewage counts skipped plants and dropped spike days.
	 * Atomic because the chart tasks build their series on the code pool.
	 */
	private static final AtomicInteger startDaysBumped = new AtomicInteger();
	private static final AtomicInteger seriesSkipped = new AtomicInteger();

	/**
	 * Prints one line covering every series the run built inflections for: how
	 * many leading days with no reading were stepped over, and how many series
	 * got no inflections at all. Call it once, after everything that draws has
	 * built.
	 */
	public static void printInflectionSummary() {
		System.out.println("Built inflections, bumped " + startDaysBumped.get()
				+ " leading days with no reading, skipped " + seriesSkipped.get()
				+ " series with no reading the day after their first.");
	}

	/*
	 * The peaks and valleys, used as the chart markers, as the bound on how far
	 * back makeFitSeries reaches, as how far back a recent chart reaches when
	 * the last one is over 180 days old, and as VocSewage's fit seed.
	 * docs/reference/wastewater.txt, under INFLECTIONS AND FIT LINES, has the
	 * design.
	 *
	 * Only readings on consecutive days are compared, so a series whose first
	 * reading from 2020-09-01 on has no reading the next day returns with none,
	 * which is most plants; see
	 * docs/active/findings/2026-09-10-most-plants-never-get-an-inflection.md.
	 */
	private synchronized void buildInflections() {
		int firstDay = Math.max(getFirstDay(), CalendarUtils.dateToDay("9/1/2020")), lastDay = getLastDay();
		/*
		 * build() only promises a positive total somewhere in the day range, not
		 * a reading on or after 2020-09-01, so a series that ended before then
		 * would walk this loop forever without the bound on lastDay. Past the
		 * last day getNormalized is null, so such a series takes the return
		 * below.
		 */
		while (firstDay <= lastDay && getNormalized(firstDay) == null) {
			startDaysBumped.incrementAndGet();
			firstDay++;
		}
		if (getNormalized(firstDay + 1) == null) {
			seriesSkipped.incrementAndGet();
			return;
		}
		boolean rising = getNormalized(firstDay + 1) >= getNormalized(firstDay);

		/*
		 * A turn is a day whose next day's reading goes against the current
		 * direction, ties counting as rising. It is kept only if no reading in
		 * the four weeks after it, day + 2 through day + 27 and not past the
		 * last day, is back on the old side of that next day's reading;
		 * otherwise the scan resumes after the reading that went back. The
		 * inflection is the turn day itself, the peak or valley. The window is
		 * what spreads the markers out: it was 30 days, then 21 ("3 week gap
		 * between inflections I guess?"), then 28 ("Go back to 4 week min
		 * interval between inflections", c135ff9). It is not a hard minimum,
		 * since the next turn is sought from the day after.
		 *
		 * No turn is sought in the last 14 days, so each is confirmed over at
		 * least 13 days, and none is within two weeks of the last day. The 14
		 * came with the first version and has no recorded reason.
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
	 * machine's local date, {@link covid.CalendarUtils#today}), the sum of the
	 * raw entries in the trailing window divided by its length. That is neither
	 * normalized nor an average of the days with a reading; see
	 * docs/active/findings/2026-09-10-a-plants-smoothed-lines-and-peak-labels-are-in-its-own-units.md
	 * and
	 * docs/active/findings/2026-09-10-a-smoothed-line-counts-a-day-without-a-reading-as-zero.md.
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
				for (int day2 = day; day2 > day - daysAveraged; day2--) {
					DaySewage entry = getEntry(day2);
					if (entry != null) {
						number += entry.getSewage();
					}
				}
				number /= daysAveraged;
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
	 * interval, and the walk stops at the first that does. The inflection bound
	 * wins over {@code numDays}: an inflection can be as late as 14 days before
	 * the last day, which leaves eight days to fit.
	 * <p>
	 * The legend gives the interval as weekly growth and the value predicted
	 * for today. SimpleRegression gives a NaN interval below three readings and
	 * a NaN line below two, and a zero reading makes the fit NaN; see
	 * docs/active/findings/2026-09-10-a-zero-reading-in-a-plants-fit-window-makes-its-fit-nan.md.
	 * Today here is the machine's local date, {@link covid.CalendarUtils#today},
	 * as in makeTimeSeries.
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
	 * and value, green for a valley. The value is the entry's raw one, which
	 * for a plant is in its own units, not the axis's; see
	 * docs/active/findings/2026-09-10-a-plants-smoothed-lines-and-peak-labels-are-in-its-own-units.md.
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

	/**
	 * The first day from {@code startDay} on with no entry or a raw reading of
	 * zero or less, or the last day + 1 when there is none. Multi takes it as
	 * the end of a plant's run for its fade-out. A zero reading counts, see
	 * docs/active/findings/2026-09-10-a-zero-reading-is-weighted-out-of-every-aggregate.md,
	 * and so does every gap between samples, see
	 * docs/active/findings/2026-09-10-every-gap-between-samples-restarts-a-plants-fade-in.md.
	 */
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
