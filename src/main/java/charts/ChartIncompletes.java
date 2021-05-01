package charts;

import java.awt.BasicStroke;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;

import covid.CalendarUtils;
import covid.ColoradoStats;
import covid.Event;
import covid.IncompleteNumbers;
import covid.NumbersTiming;
import covid.NumbersType;
import covid.Smoothing;

/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 * 
 * @author jdorje@gmail.com
 */
public class ChartIncompletes extends AbstractChart {
	public final Set<NumbersType> types;
	public final NumbersTiming timing;
	public final boolean logarithmic;
	public final boolean useSmoothing;

	public ChartIncompletes(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing, boolean logarithmic,
			boolean useSmoothing) {
		super(stats, Charts.FULL_FOLDER + "\\" + "numbers");
		this.types = types;
		this.timing = timing;
		this.logarithmic = logarithmic;
		this.useSmoothing = useSmoothing;
	}

	@Override
	public Chart buildChart(int dayOfData) {
		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		StringBuilder title = new StringBuilder();

		// https://www.jfree.org/forum/viewtopic.php?t=20396
		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		boolean multi = (types.size() > 1);
		int firstDayOfChart = Integer.MAX_VALUE;

		title.append("Colorado ");
		if (multi) {
			title.append("COVID numbers");
		} else {
			for (NumbersType type : types) {
				title.append(type.lowerName);
			}
		}
		title.append(" by ");
		title.append(timing.lowerName);
		title.append(" date as of ");
		title.append(CalendarUtils.dayToDate(dayOfData));
		if (logarithmic) {
			title.append(", logarithmic");
		} else {
			title.append(", cartesian");
		}
		if (useSmoothing) {
			title.append(", smoothed");
		} else {
			title.append(", exact");
		}
		title.append(String.format("\n(central %.0f%% interval for value in %d days based on prev %d days)", confidence,
				DELAY, INTERVAL));

		for (NumbersType type : types) {
			IncompleteNumbers numbers = stats.getNumbers(type, timing);
			if (!numbers.hasData()) {
				continue;
			}
			Smoothing smoothing = useSmoothing ? type.smoothing : Smoothing.NONE;
			YIntervalSeries series = new YIntervalSeries(type.capName + " (" + smoothing.getDescription() + ")");

			firstDayOfChart = Math.min(firstDayOfChart, numbers.getFirstDayOfType());
			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= dayOfData; dayOfType++) {
				long time = CalendarUtils.dayToTime(dayOfType);

				double number = numbers.getNumbers(dayOfData, dayOfType, smoothing);

				if (number == 0.0) {
					continue;
				}

				DescriptiveStatistics statistics = new DescriptiveStatistics();
				int actualDelay = dayOfData - dayOfType;
				for (int oldDayOfType = dayOfType - DELAY - INTERVAL; oldDayOfType < dayOfType
						- DELAY; oldDayOfType++) {
					double n1 = numbers.getNumbers(oldDayOfType + actualDelay, oldDayOfType);
					double n2 = numbers.getNumbers(oldDayOfType + actualDelay + DELAY, oldDayOfType);

					if (n1 == 0 || n2 == 0) {
						continue;
					}
					statistics.addValue(Math.log(n2 / n1));
				}

				double upperBound, lowerBound;
				if (true) {
					/* Use the actual 2.5% and 97.5% values */
					upperBound = statistics.getPercentile(topRange);
					lowerBound = statistics.getPercentile(bottomRange);
				} else {
					/*
					 * Apply a log-normal distribution to smooth these bounds
					 * out. This assumes log-normal is correct (it probably is)
					 * but will give a much more consistent result.
					 * 
					 * This also hard-codes 2 standard deviations (essentially
					 * 95%).
					 * 
					 * One issue (so maybe normal isn't the right distribution)
					 * is that the upper 97.5% discrete bound often jumps way up
					 * after old numbers are increased, but rarely drops. It may
					 * be asymmetrical.
					 */
					double mean = statistics.getMean();
					double sd = statistics.getStandardDeviation();
					upperBound = mean + 2 * sd;
					lowerBound = mean - 2 * sd;
				}

				upperBound = number * Math.exp(upperBound);
				lowerBound = number * Math.exp(lowerBound);

				if (!logarithmic || (lowerBound > 0 && number > 0 && upperBound > 0)) {
					series.add(time, number, lowerBound, upperBound);
				}
			}

			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, type.color);
			renderer.setSeriesFillPaint(seriesCount, type.color.darker());
			seriesCount++;
		}

		if (firstDayOfChart >= stats.getLastDay()) {
			throw new RuntimeException(
					"Chart don't exist: " + types + " ... " + timing + " ... " + CalendarUtils.dayToDate(dayOfData));
		}

		// dataset.addSeries("Cases", series);

		String verticalAxis = NumbersType.name(types, " / ");
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		if (logarithmic) {
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			yAxis.setLowerBound(1);
			yAxis.setUpperBound(NumbersType.getHighest(types));
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");
			xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(firstDayOfChart));
			xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(Charts.getLastDayForChartDisplay(stats)));
			plot.setDomainAxis(xAxis);

			ValueMarker marker = Charts.getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);
		}

		if (timing == NumbersTiming.INFECTION) {
			Event.addEvents(plot);
		}

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), getPngName(dayOfData));
		if (timing == NumbersTiming.INFECTION && types.size() == 3 && logarithmic && dayOfData == stats.getLastDay()) {
			c.addFileName(Charts.TOP_FOLDER + "\\" + getName() + ".png");
			c.open();
		}
		c.saveAsPNG();

		return c;
	}

	@Override
	public String getName() {
		return NumbersType.name(types, "-") + "-" + timing.lowerName + (logarithmic ? "-log" : "-cart")
				+ (useSmoothing ? "-smooth" : "-exact");
	}

	/**
	 * Some combinations here have no data and this is the easiest way to find
	 * that out.
	 */
	@Override
	public boolean hasData() {
		for (NumbersType type : types) {
			IncompleteNumbers n = stats.getNumbers(type, timing);
			if (n.hasData()) {
				return true;
			}
		}
		return false;
	}

}
