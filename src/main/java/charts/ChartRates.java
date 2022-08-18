package charts;

import java.awt.BasicStroke;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;

import covid.CalendarUtils;
import covid.ColoradoStats;
import covid.Event;
import covid.IncompleteNumbers;
import covid.NumbersTiming;
import covid.Rate;
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
public class ChartRates extends AbstractChart {

	public static final String RATES_FOLDER = Charts.FULL_FOLDER + "\\rates";
	public final Set<Rate> rates;
	private final NumbersTiming timing;

	public ChartRates(ColoradoStats stats, Set<Rate> rates, NumbersTiming timing) {
		super(stats, RATES_FOLDER);
		this.rates = rates;
		this.timing = timing;
	}

	@Override
	public JFreeChart buildChart(int dayOfData) {

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		int seriesCount = 0;

		int height = 0;

		int firstDayOfChart = stats.getVeryFirstDay();

		for (Rate rate : rates) {
			Smoothing smoothing = rate.smoothing;

			IncompleteNumbers nNumbers = stats.getNumbers(rate.numerator, timing);
			IncompleteNumbers dNumbers = stats.getNumbers(rate.denominator, timing);

			YIntervalSeries series2022 = new YIntervalSeries(
					rate.description + " (" + smoothing.getDescription() + ", 2022)");
			YIntervalSeries series2021 = new YIntervalSeries("2021");
			YIntervalSeries series2020 = new YIntervalSeries("2020");

			height = Math.max(height, rate.highestValue);

			for (int dayOfType = firstDayOfChart; dayOfType <= dayOfData; dayOfType++) {

				if (!nNumbers.dayHasData(dayOfData) || !dNumbers.dayHasData(dayOfData)) {
					// fairly rare to have one rate not have data while another
					// does
					continue;
				}
				double numerator = nNumbers.getNumbers(dayOfData, dayOfType, smoothing);
				double denominator = dNumbers.getNumbers(dayOfData, dayOfType, smoothing);
				if (numerator == 0 || denominator == 0) {
					continue;
				}

				DescriptiveStatistics statistics = new DescriptiveStatistics();
				int actualDelay = dayOfData - dayOfType;
				if (DELAY % 7 != 0) {
					throw new RuntimeException("You really want delay and interval to "
							+ "be weekly amounts to deal with day-of-week issues.");
				}
				for (int count = 0, oldDayOfType = dayOfType - DELAY; count < INTERVAL
						&& oldDayOfType >= stats.getVeryFirstDay(); oldDayOfType--) {
					if (!nNumbers.dayHasData(oldDayOfType + actualDelay)
							|| !nNumbers.dayHasData(oldDayOfType + actualDelay + DELAY)
							|| !dNumbers.dayHasData(oldDayOfType + actualDelay)
							|| !dNumbers.dayHasData(oldDayOfType + actualDelay + DELAY)) {
						continue;
					}
					double n1 = nNumbers.getNumbers(oldDayOfType + actualDelay, oldDayOfType);
					double n2 = nNumbers.getNumbers(oldDayOfType + actualDelay + DELAY, oldDayOfType);

					double d1 = dNumbers.getNumbers(oldDayOfType + actualDelay, oldDayOfType);
					double d2 = dNumbers.getNumbers(oldDayOfType + actualDelay + DELAY, oldDayOfType);

					if (n1 == 0 || n2 == 0 || d1 == 0 || d2 == 0) {
						continue;
					}
					statistics.addValue((numerator / denominator) * (n2 / d2) / (n1 / d1));
					count++;
				}

				double upperBound = statistics.getPercentile(topRange);
				double lowerBound = statistics.getPercentile(bottomRange);
				double median = statistics.getPercentile(50);

				double value = 100 * Charts.value(numerator / denominator, median);
				if (rate == Rate.POSITIVITY && timing == NumbersTiming.ONSET) {
					series2022.add(CalendarUtils.dayToTime(dayOfType), value, value, value);
				} else {
					series2022.add(CalendarUtils.dayToTime(dayOfType), value, 100 * lowerBound, 100 * upperBound);
					series2020.add(CalendarUtils.dayToTime(dayOfType + 2 * CalendarUtils.YEAR), value, value, value);
					series2021.add(CalendarUtils.dayToTime(dayOfType + CalendarUtils.YEAR), value, value, value);
				}
			}

			collection.addSeries(series2022);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, rate.color);
			renderer.setSeriesFillPaint(seriesCount, rate.color.darker());
			seriesCount++;

			collection.addSeries(series2021);
			renderer.setSeriesStroke(seriesCount,
					new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] { 3 }, 0));
			renderer.setSeriesPaint(seriesCount, rate.color);
			renderer.setSeriesFillPaint(seriesCount, rate.color.darker());
			seriesCount++;

			collection.addSeries(series2020);
			renderer.setSeriesStroke(seriesCount,
					new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] { 6 }, 0));
			renderer.setSeriesPaint(seriesCount, rate.color);
			renderer.setSeriesFillPaint(seriesCount, rate.color.darker());
			seriesCount++;
		}

		StringBuilder title = new StringBuilder();
		title.append("Colorado ");
		if (rates.size() > 1) {
			title.append("rates");
		} else {
			title.append(Rate.allCapsName(rates, ", "));
		}
		title.append(" by ");
		title.append(timing.lowerName);
		title.append(" date as of ");
		title.append(CalendarUtils.dayToDate(dayOfData));
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
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date of Infection", "Rate (%)",
				collection);

		// chart.getXYPlot().setRangeAxis(new LogarithmicAxis("Cases"));

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		DateAxis xAxis = new DateAxis("Date");
		double y2022 = CalendarUtils.dateToDay("1/1/2022");
		xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(y2022));
		xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(y2022 + CalendarUtils.YEAR));
		plot.setDomainAxis(xAxis);

		ValueAxis yAxis = plot.getRangeAxis();
		yAxis.setLowerBound(0);
		yAxis.setUpperBound(height);

		plot.addDomainMarker(Charts.getTodayMarker(dayOfData));

		Event.addEvents(plot);

		return chart;
	}

	@Override
	public boolean publish(int dayOfData) {
		return timing == NumbersTiming.ONSET && dayOfData == stats.getLastDay() && rates.size() == 1;
	}

	@Override
	public String getName() {
		return Rate.name(rates, "-") + "-" + timing.lowerName;
	}

	@Override
	public boolean hasData() {
		return true;
	}

	@Override
	public boolean dayHasData(int dayOfData) {
		for (Rate rate : rates) {
			IncompleteNumbers n = stats.getNumbers(rate.numerator, timing);
			IncompleteNumbers d = stats.getNumbers(rate.denominator, timing);
			if (n.dayHasData(dayOfData) || d.dayHasData(dayOfData)) {
				return true;
			}
		}
		return false;

	}
}
