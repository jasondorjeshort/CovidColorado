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
		for (int d = Math.max(showAverage ? dayOfData - 30 : 0, stats.getFirstDay()); d <= dayOfData
				- daysToSkip; d++) {
			Day ddd = Date.dayToDay(d);

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

			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay() + 14));

			plot.setDomainAxis(xAxis);

			ValueMarker marker = Charts.getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);

			if (showEvents) {
				Event.addEvents(plot);
			}

		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(folder, fileName, image);
		return image;
	}



	private BufferedImage buildTimeseriesChart(NumbersType type, NumbersTiming timing, int dayOfData, boolean log) {
		String title = String.format("Colorado %s by %s date as of %s\n(%s%s)", type.lowerName, timing.lowerName,
				Date.dayToDate(dayOfData), type.smoothing.description, log ? ", logarithmic" : "");

		String fileName = type.lowerName + "-" + timing.lowerName + (log ? "-log" : "-cart");
		return buildCasesTimeseriesChart(fileName, Date.dayToFullDate(dayOfData), dayOfData,
				dayOfOnset -> stats.getNumbers(type, timing).getNumbers(dayOfData, dayOfOnset, false, type.smoothing),
				dayOfOnset -> stats.getNumbers(type, timing).getNumbers(dayOfData, dayOfOnset, true, type.smoothing),
				title, type.capName, log, false, 0, log && timing == NumbersTiming.INFECTION);
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
