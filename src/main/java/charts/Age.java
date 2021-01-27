package charts;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Function;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import covid.CalendarUtils;
import covid.ColoradoStats;
import covid.Event;
import covid.IncompleteNumbers;
import covid.NumbersTiming;
import covid.NumbersType;

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
public class Age {

	/*
	 * Charts the age of some incomplete numbers.
	 * 
	 * Each day a new set of numbers are added, each with an associated day in
	 * the past. The most useful of these is onset date (or subtract 5 to get
	 * infection date). So "today" we added 100 new cases, whose onset dates
	 * were distributed across the last 10 days. The average "age" since onset
	 * today can therefore be calculated easily.
	 * 
	 * A caveat of the use of this data, though, is that it may still be
	 * incomplete. An onset date may be a placeholder on the first day, and then
	 * be moved to a more accurate value later. This would mean that initial age
	 * is a placeholder, and the age delta is a trivial part of the age of the
	 * new day. So long as we take rolling averages within the window of change,
	 * this should all work out fine though.
	 */

	final ColoradoStats stats;

	public Age(ColoradoStats stats) {
		this.stats = stats;
	}

	public Chart buildCasesTimeseriesChart(String folder, String fileName, int dayOfData,
			Function<Integer, Double> getCasesForDay, Function<Integer, Double> getProjectedCasesForDay, String title,
			String verticalAxis, boolean log, boolean showAverage, int daysToSkip, boolean showEvents) {

		folder = Charts.FULL_FOLDER + "\\" + folder;
		new File(folder).mkdir();

		TimeSeries series = new TimeSeries("Cases");
		TimeSeries projectedSeries = new TimeSeries("Projected");
		for (int d = Math.max(showAverage ? dayOfData - 30 : 0, stats.getVeryFirstDay()); d <= dayOfData
				- daysToSkip; d++) {
			Day ddd = CalendarUtils.dayToDay(d);

			double cases = getCasesForDay.apply(d);

			if (Double.isFinite(cases)) {
				if (!log || cases > 0) {
					series.add(ddd, cases);
				}
			}

			if (getProjectedCasesForDay != null) {

				double projected = getProjectedCasesForDay.apply(d);
				if (Double.isFinite(projected)) {
					if (!log || projected > 0) {
						projectedSeries.add(ddd, projected);
					}
				}
			}
		}

		// dataset.addSeries("Cases", series);

		TimeSeriesCollection collection = new TimeSeriesCollection();
		collection.addSeries(series);
		if (getProjectedCasesForDay != null) {
			collection.addSeries(projectedSeries);
		}
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		if (log) {
			XYPlot plot = chart.getXYPlot();
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			yAxis.setLowerBound(1);
			yAxis.setUpperBound(100000);
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");

			xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(stats.getVeryFirstDay()));
			xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(stats.getLastDay() + 14));

			plot.setDomainAxis(xAxis);

			ValueMarker marker = Charts.getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);

			if (showEvents) {
				Event.addEvents(plot);
			}

		}

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), folder + "\\" + fileName + ".png");
		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		c.saveAsPNG();
		return c;
	}

	public void buildChart(IncompleteNumbers numbers, int finalDay) {
		if (!numbers.hasData()) {
			return;
		}
		String by = "age-" + numbers.getType().lowerName + "-" + numbers.getTiming().lowerName;
		Chart c = buildCasesTimeseriesChart(by, CalendarUtils.dayToFullDate(finalDay), finalDay,
				dayOfData -> numbers.getAverageAgeOfNewNumbers(dayOfData, 14), null, by, "?", false, false, 0, false);

		if (finalDay == stats.getLastDay() && numbers.getType() == NumbersType.CASES
				&& numbers.getTiming() == NumbersTiming.INFECTION) {
			c.open();
		}
	}

	public void buildChart(IncompleteNumbers numbers) {
		buildChart(numbers, stats.getLastDay());
	}

}
