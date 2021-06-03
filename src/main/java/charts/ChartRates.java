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
import covid.NumbersType;
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
	public Chart buildChart(int dayOfData) {
		Smoothing smoothing = new Smoothing(30, Smoothing.Type.AVERAGE, Smoothing.Timing.SYMMETRIC);

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		int seriesCount = 0;

		int height = 0;

		int firstDayOfChart = stats.getVeryFirstDay();

		boolean needCaveat = false;
		for (Rate rate : rates) {
			needCaveat |= (rate.numerator == NumbersType.HOSPITALIZATIONS || rate.numerator == NumbersType.DEATHS)
					&& (timing == NumbersTiming.INFECTION || timing == NumbersTiming.ONSET);

			IncompleteNumbers nNumbers = stats.getNumbers(rate.numerator, timing);
			IncompleteNumbers dNumbers = stats.getNumbers(rate.denominator, timing);

			YIntervalSeries series = new YIntervalSeries(rate.description);

			height = Math.max(height, rate.highestValue);

			for (int dayOfType = firstDayOfChart; dayOfType <= dayOfData; dayOfType++) {
				long time = CalendarUtils.dayToTime(dayOfType);

				double numerator = nNumbers.getNumbers(dayOfData, dayOfType, smoothing);
				double denominator = dNumbers.getNumbers(dayOfData, dayOfType, smoothing);
				if (numerator == 0 || denominator == 0) {
					continue;
				}

				DescriptiveStatistics statistics = new DescriptiveStatistics();
				int actualDelay = dayOfData - dayOfType;
				for (int oldDayOfType = dayOfType - DELAY - INTERVAL; oldDayOfType < dayOfType
						- DELAY; oldDayOfType++) {
					double n1 = nNumbers.getNumbers(oldDayOfType + actualDelay, oldDayOfType);
					double n2 = nNumbers.getNumbers(oldDayOfType + actualDelay + DELAY, oldDayOfType);

					double d1 = dNumbers.getNumbers(oldDayOfType + actualDelay, oldDayOfType);
					double d2 = dNumbers.getNumbers(oldDayOfType + actualDelay + DELAY, oldDayOfType);

					if (n1 == 0 || n2 == 0 || d1 == 0 || d2 == 0) {
						continue;
					}
					statistics.addValue((numerator / denominator) * (n2 / d2) / (n1 / d1));
				}

				double upperBound = statistics.getPercentile(topRange);
				double lowerBound = statistics.getPercentile(bottomRange);
				double median = statistics.getPercentile(50);

				series.add(time, 100 * Charts.value(numerator / denominator, median), 100 * lowerBound,
						100 * upperBound);
			}

			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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
		title.append(" by day of ");
		title.append(timing.lowerName);
		title.append(", ");
		title.append(smoothing.getDescription());
		title.append(String.format("\n(central %.0f%% interval for value in %d days based on prev %d days)", confidence,
				DELAY, INTERVAL));
		if (needCaveat) {
			title.append(String.format("\n(onset/infection timings are shifted starting on Jan 1, 2021)", confidence,
					DELAY, INTERVAL));
		}
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date of Infection", "Rate (%)",
				collection);

		// chart.getXYPlot().setRangeAxis(new LogarithmicAxis("Cases"));

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		DateAxis xAxis = new DateAxis("Date");
		xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(firstDayOfChart));
		xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(Charts.getLastDayForChartDisplay(stats)));
		plot.setDomainAxis(xAxis);

		ValueAxis yAxis = plot.getRangeAxis();
		yAxis.setLowerBound(0);
		yAxis.setUpperBound(height);

		plot.addDomainMarker(Charts.getTodayMarker(dayOfData));

		Event.addEvents(plot);

		String fileName = CalendarUtils.dayToFullDate(dayOfData, '-');
		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), getPngName(dayOfData));

		if (timing == NumbersTiming.INFECTION && dayOfData == stats.getLastDay() && rates.size() == 1) {
			c.addFileName(Charts.TOP_FOLDER + "\\" + getName() + ".png");
			c.open();
		}
		c.saveAsPNG();

		return c;
	}

	@Override
	public String getName() {
		return Rate.name(rates, "-") + "-" + timing.lowerName;
	}

	@Override
	public boolean hasData() {
		return true;
	}
}
