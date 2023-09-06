package colorado;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;

import covid.CalendarUtils;

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
public class Reproductive extends TypesTimingChart {
	/*
	 * Preliminary here.
	 * 
	 * This charts the estimated R(t).
	 */
	public Reproductive(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing) {
		super(stats, Charts.FULL_FOLDER + "\\" + "reproductive", types, timing);
	}

	private static final boolean SHOW_EVENTS = true;

	@Override
	public JFreeChart buildChart(int dayOfData) {
		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		for (NumbersType type : NumbersType.values()) {
			if (!types.contains(type)) {
				continue;
			}

			IncompleteNumbers numbers = stats.getNumbers(type, timing);
			if (!numbers.hasData()) {
				continue;
			}

			YIntervalSeries series = new YIntervalSeries("Based on " + type.capName + ", "
					+ (numbers.getReproductiveSmoothingInterval() * 2) + "-day smoothing");
			YIntervalSeries seriesLY = new YIntervalSeries("Last year");

			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= dayOfData; dayOfType++) {

				Double reproductive = numbers.getBigR(dayOfData, dayOfType);

				if (reproductive == null || reproductive == 0.0) {
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
					Double r1 = numbers.getBigR(oldDayOfType + actualDelay, oldDayOfType);
					Double r2 = numbers.getBigR(oldDayOfType + actualDelay + DELAY, oldDayOfType);

					if (r1 == null || r2 == null || r1 == 0 || r2 == 0) {
						continue;
					}
					statistics.addValue(reproductive * r2 / r1);
				}

				double upperBound = statistics.getPercentile(topRange);
				double lowerBound = statistics.getPercentile(bottomRange);

				double median = statistics.getPercentile(50);
				double value = Charts.value(reproductive, median);

				long time = CalendarUtils.dayToTime(dayOfType);
				series.add(time, value, lowerBound, upperBound);

				long timeLY = CalendarUtils.dayToTime(dayOfType + 365);
				seriesLY.add(timeLY, value, lowerBound, upperBound);
			}
			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, type.color);
			renderer.setSeriesFillPaint(seriesCount, type.color.darker());
			seriesCount++;

			if (types.size() == 1) {
				collection.addSeries(seriesLY);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				renderer.setSeriesPaint(seriesCount, type.color);
				renderer.setSeriesFillPaint(seriesCount, type.color.darker());
				seriesCount++;
			}
		}

		// dataset.addSeries("Cases", series);

		StringBuilder title = new StringBuilder();
		title.append(String.format("Colorado COVID growth rate per %.01f days\n", IncompleteNumbers.SERIAL_INTERVAL));
		title.append("\nthrough " + CalendarUtils.dayToDate(dayOfData));
		title.append(" ");
		title.append("by " + timing.lowerName + " date");
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

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", "R(t)", collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);
		ValueAxis yAxis = plot.getRangeAxis();
		yAxis.setLowerBound(0.50);
		yAxis.setUpperBound(2.00);

		DateAxis xAxis = new DateAxis("Date");
		int last = getLastDayForChartDisplay();
		int first = last - 180; // stats.getVeryFirstDay();
		xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(first));
		xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(last));
		plot.setDomainAxis(xAxis);

		ValueMarker marker = new ValueMarker(1.0);
		marker.setPaint(Color.black);
		plot.addRangeMarker(marker);
		plot.addDomainMarker(Charts.getTodayMarker(dayOfData));

		if (SHOW_EVENTS) {
			Event.addEvents(plot);
		}

		return chart;
	}

	@Override
	public boolean publish(int dayOfData) {
		return dayOfData == stats.getLastDay() && types.size() == 3 && types.contains(NumbersType.CASES)
				&& types.contains(NumbersType.HOSPITALIZATIONS) && types.contains(NumbersType.DEATHS)
				&& timing == NumbersTiming.ONSET;
	}

	@Override
	public String getName() {
		return "R-" + NumbersType.name(types, "-") + "-" + timing.lowerName;
	}
}
