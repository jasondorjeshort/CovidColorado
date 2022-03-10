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
public class ChartIncompletes extends TypesTimingChart {

	private final int chartDays;

	public ChartIncompletes(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing, int chartDays,
			Flag... flags) {
		super(stats, Charts.FULL_FOLDER + "\\" + "numbers", types, timing, flags);
		this.chartDays = chartDays;
	}

	@Override
	public boolean publish(int dayOfData) {
		if (!logarithmic() || dayOfData != stats.getLastDay()) {
			return false;
		}
		if (timing == NumbersTiming.ONSET && types.size() == 3) {
			return true;
		}
		if (timing == NumbersTiming.DEATH && types.size() == 1 && smoothed()) {
			return true;
		}
		if (timing == NumbersTiming.ONSET && types.size() == 1 && types.contains(NumbersType.CASES) && smoothed()) {
			// return true;
		}
		return false;
	}

	@Override
	public JFreeChart buildChart(int dayOfData) {
		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		StringBuilder title = new StringBuilder();

		// https://www.jfree.org/forum/viewtopic.php?t=20396
		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		boolean multi = (types.size() > 1);

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
		if (logarithmic()) {
			title.append(", logarithmic");
		} else {
			title.append(", cartesian");
		}
		if (smoothed()) {
			title.append(", smoothed");
		} else {
			title.append(", exact");
		}
		title.append("\n(");
		if (Charts.useMedian()) {
			title.append("Median");
		} else {
			title.append("Current");
		}
		title.append(String.format(" and central %.0f%% interval for value in %d days based on prev %d days)",
				confidence, DELAY, INTERVAL));
		title.append("\n");
		title.append(Charts.valueDesc());

		for (NumbersType type : types) {
			IncompleteNumbers numbers = stats.getNumbers(type, timing);
			if (!numbers.hasData() || !numbers.dayHasData(dayOfData)) {
				continue;
			}
			Smoothing smoothing = smoothed() ? type.smoothing : Smoothing.NONE;
			YIntervalSeries series2022 = new YIntervalSeries(type.capName + " (2022)");
			YIntervalSeries series2021 = new YIntervalSeries("2021");
			YIntervalSeries series2020 = new YIntervalSeries("2020");

			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= dayOfData; dayOfType++) {

				double number = numbers.getNumbers(dayOfData, dayOfType, smoothing);

				if (number == 0.0) {
					continue;
				}

				DescriptiveStatistics statistics = new DescriptiveStatistics();
				int actualDelay = dayOfData - dayOfType;
				for (int oldDayOfType = dayOfType - DELAY - INTERVAL; oldDayOfType < dayOfType
						- DELAY; oldDayOfType++) {
					if (!numbers.dayHasData(oldDayOfType + actualDelay)
							|| !numbers.dayHasData(oldDayOfType + actualDelay + DELAY)) {
						continue;
					}
					double n1 = numbers.getNumbers(oldDayOfType + actualDelay, oldDayOfType);
					double n2 = numbers.getNumbers(oldDayOfType + actualDelay + DELAY, oldDayOfType);

					if (n1 == 0 || n2 == 0) {
						continue;
					}
					statistics.addValue(Math.log(n2 / n1));
				}

				double upperBound, lowerBound, median;
				if (true) {
					/* Use the actual 2.5% and 97.5% values */
					upperBound = statistics.getPercentile(topRange);
					median = statistics.getPercentile(50);
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

				if (smoothing != Smoothing.NONE && upperBound > 10 * lowerBound) {
					// Done with fish! This is a simple metric for when the data
					// is so uncertain as to not be worth showing.
					break;
				}
				median = number * Math.exp(median);

				if (!logarithmic() || (lowerBound > 0 && number > 0 && upperBound > 0)) {
					double value = Charts.value(number, median);
					long time = CalendarUtils.dayToTime(dayOfType);
					series2022.add(time, value, lowerBound, upperBound);

					long timeLY = CalendarUtils.dayToTime(dayOfType + 365.24);
					series2021.add(timeLY, value, value, value);

					long timeLLY = CalendarUtils.dayToTime(dayOfType + 365.24 * 2);
					series2020.add(timeLLY, value, value, value);
				}
			}

			collection.addSeries(series2022);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, type.color);
			renderer.setSeriesFillPaint(seriesCount, type.color.darker());
			seriesCount++;

			if (oldYears()) {
				collection.addSeries(series2021);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] { 9 }, 0));
				renderer.setSeriesPaint(seriesCount, type.color.darker());
				renderer.setSeriesFillPaint(seriesCount, type.color.darker().darker());
				seriesCount++;

				collection.addSeries(series2020);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] { 18 }, 0));
				renderer.setSeriesPaint(seriesCount, type.color.darker());
				renderer.setSeriesFillPaint(seriesCount, type.color.darker().darker());
				seriesCount++;
			}
		}

		// dataset.addSeries("Cases", series);

		String verticalAxis = NumbersType.name(types, " / ");
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		DateAxis xAxis = new DateAxis("Date");
		if (oldYears()) {
			xAxis.setMinimumDate(CalendarUtils.dateToJavaDate("1/1/2022"));
			xAxis.setMaximumDate(CalendarUtils.dateToJavaDate("1/1/2023"));
		} else {
			int last = getLastDayForChartDisplay();
			int first = last - chartDays;
			xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(first));
			xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(last));
		}
		plot.setDomainAxis(xAxis);

		if (logarithmic()) {
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			yAxis.setLowerBound(1);
			yAxis.setUpperBound(NumbersType.getHighest(types));
			plot.setRangeAxis(yAxis);

			ValueMarker marker = Charts.getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);
		}

		if (timing == NumbersTiming.INFECTION) {
			Event.addEvents(plot);
		}

		return chart;
	}

	@Override
	public String getName() {
		return NumbersType.name(types, "-") + "-" + chartDays + "-" + timing.lowerName + logName() + smoothName()
				+ yearsName();
	}

}
