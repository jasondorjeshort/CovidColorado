package charts;

import java.awt.image.BufferedImage;
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

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import covid.ColoradoStats;
import covid.Date;
import covid.Event;
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
public class ChartIncompletes {
	public ChartIncompletes(ColoradoStats stats) {
		this.stats = stats;
	}

	private final ColoradoStats stats;

	private BufferedImage buildCasesTimeseriesChart(String folder, String fileName, int dayOfData,
			Function<Integer, Double> getCasesForDay, Function<Integer, Double> getProjectedCasesForDay, String title,
			String verticalAxis, boolean log, boolean showAverage, int daysToSkip, boolean showEvents) {

		folder = Charts.TOP_FOLDER + "\\" + folder;

		TimeSeries series = new TimeSeries("Cases");
		TimeSeries projectedSeries = new TimeSeries("Projected");
		Integer incomplete = null;
		for (int d = Math.max(showAverage ? dayOfData - 30 : 0, stats.getFirstDay()); d <= dayOfData
				- daysToSkip; d++) {
			Day ddd = Date.dayToDay(d);

			double cases = getCasesForDay.apply(d);

			if (!log || cases > 0) {
				series.add(ddd, cases);
			}

			if (getProjectedCasesForDay != null) {
				double projected = getProjectedCasesForDay.apply(d);
				if (!log || projected > 0) {
					projectedSeries.add(ddd, projected);
				}

				if (incomplete == null && cases > 0) {
					// 10% inaccuracy is huge, but anything less seems to go WAY
					// back due to changes in really old data
					if (projected / cases > 1.1) {
						incomplete = d;
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

		XYPlot plot = chart.getXYPlot();
		if (log) {
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			yAxis.setLowerBound(1);
			yAxis.setUpperBound(100000);
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");

			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay() + 14));

			plot.setDomainAxis(xAxis);

			ValueMarker marker = Charts.getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);

			if (showEvents) {
				Event.addEvents(plot);
			}
		}

		if (incomplete != null) {
			plot.addDomainMarker(Charts.getIncompleteMarker(incomplete));
		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(folder, fileName, image);
		return image;
	}

	private BufferedImage buildTimeseriesChart(NumbersType type, NumbersTiming timing, int dayOfData, boolean log) {
		String title = String.format("Colorado %s by %s date as of %s\n(%s%s)", type.lowerName, timing.lowerName,
				Date.dayToDate(dayOfData), type.smoothing.description, log ? ", logarithmic" : "");

		String fileName = type.lowerName + "-" + timing.lowerName + (log ? "-log" : "-cart");
		BufferedImage bi = buildCasesTimeseriesChart(fileName, Date.dayToFullDate(dayOfData), dayOfData,
				dayOfOnset -> stats.getNumbers(type, timing).getNumbers(dayOfData, dayOfOnset, false, type.smoothing),
				dayOfOnset -> stats.getNumbers(type, timing).getNumbers(dayOfData, dayOfOnset, true, type.smoothing),
				title, type.capName, log, false, 0, log && timing == NumbersTiming.INFECTION);

		if (timing == NumbersTiming.INFECTION && log && dayOfData == stats.getLastDay()) {
			// hack on name here
			library.OpenImage
					.openImage(Charts.TOP_FOLDER + "\\" + fileName + "\\" + Date.dayToFullDate(dayOfData) + ".png");
		}
		return bi;
	}

	public String buildTimeseriesCharts(NumbersType type, NumbersTiming timing, boolean log) {
		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String fileName = type.lowerName + "-" + timing.lowerName + (log ? "-log" : "-cart");
		String name = Charts.TOP_FOLDER + "\\" + fileName + ".gif";
		gif.start(name);
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
			BufferedImage bi = buildTimeseriesChart(type, timing, dayOfData, log);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(bi);
		}
		gif.finish();
		return name;
	}

}
