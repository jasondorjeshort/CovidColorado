package colorado;

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

import charts.Chart;
import charts.Charts;
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
public class Finals {
	/*
	 * Preliminary here.
	 * 
	 * This charts final numbers, aka the daily released values.
	 */

	final ColoradoStats stats;
	final String FOLDER = Charts.FULL_FOLDER + "\\" + "cumulative";

	public Finals(ColoradoStats stats) {
		this.stats = stats;
		new File(FOLDER).mkdir();
	}

	private Chart buildCasesTimeseriesChart(String folder, String fileName, int dayOfData,
			Function<Integer, Double> getCasesForDay, Function<Integer, Double> getProjectedCasesForDay, String title,
			String verticalAxis, boolean log, int daysToSkip, boolean showEvents) {

		folder = Charts.FULL_FOLDER + "\\" + folder;
		new File(folder).mkdir();

		TimeSeries series = new TimeSeries("Cases");
		TimeSeries projectedSeries = new TimeSeries("Projected");
		for (int d = stats.getVeryFirstDay(); d <= dayOfData - daysToSkip; d++) {
			Day ddd = CalendarUtils.dayToDay(d);

			Double cases = getCasesForDay.apply(d);

			if (cases != null && Double.isFinite(cases)) {
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

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), dayOfData,
				folder + "\\" + fileName + ".png");
		c.saveAsPNG();
		if (dayOfData == stats.getLastDay()) {
			// c.open();
		}
		return c;
	}

	public void createCumulativeStats() {
		Smoothing smoothing = new Smoothing(7, Smoothing.Type.AVERAGE, Smoothing.Timing.TRAILING);
		String format = "Colorado %s, daily numbers\n(" + smoothing.getDescription() + ")";
		buildCasesTimeseriesChart("cumulative", "cases", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.CASES).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "cases"), "count", false, 0, false);
		buildCasesTimeseriesChart("cumulative", "hospitalizations", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.HOSPITALIZATIONS).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "hospitalizations"), "count", false, 0, false);
		buildCasesTimeseriesChart("cumulative", "deaths", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.DEATHS).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "deaths"), "count", false, 0, false);
		buildCasesTimeseriesChart("cumulative", "deaths (confirmed)", stats.getLastDay(),
				dayOfCases -> stats.confirmedDeaths.getNumbers(dayOfCases, smoothing), null,
				String.format(format, "deaths (final)"), "count", false, 0, false);
		buildCasesTimeseriesChart("cumulative", "people tested", stats.getLastDay(),
				dayOfCases -> stats.peopleTested.getNumbers(dayOfCases, smoothing), null,
				String.format(format, "people tested"), "count", false, 0, false);
		buildCasesTimeseriesChart("cumulative", "tests", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.TESTS).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "test encounters"), "count", false, 0, false);
		buildCasesTimeseriesChart("cumulative", "positivity", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.CASES).getNumbers(dayOfCases, smoothing)
						/ stats.getNumbers(NumbersType.TESTS).getNumbers(dayOfCases, smoothing),
				null, "positivity", "count", false, 0, false);
	}
}
