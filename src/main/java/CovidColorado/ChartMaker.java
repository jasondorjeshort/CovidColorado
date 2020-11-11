package CovidColorado;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Function;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class ChartMaker {

	public String buildCasesTimeseriesChart(CovidStats stats, int dayOfData, Function<Integer, Double> getCasesForDay,
			String by, boolean log, boolean showZeroes, boolean showAverage) {
		TimeSeries series = new TimeSeries("Data");
		int totalCases = 0, totalDays = 0;
		for (int d = Math.max(showAverage ? stats.getLastDay() - 30 : 0, stats.getFirstDay()); d <= stats
				.getLastDay(); d++) {
			double cases = getCasesForDay.apply(d);
			Day ddd = Date.dayToDay(d);
			if (showZeroes || cases > 0) {
				series.add(ddd, cases);
				totalCases += cases;
				totalDays += cases * d;
			}
			if (!log && !showZeroes) {
				// System.out.println("Cases " + cases + " on day " + ddd);
			}
		}

		double averageAge = dayOfData - (double) totalDays / totalCases;
		if (showAverage) {
			System.out.println("Average age on " + Date.dayToDay(dayOfData) + " is " + averageAge);
		}

		JFreeChart chart = ChartFactory.createTimeSeriesChart(
				"Colorado cases (" + totalCases + ") by " + by + " date "
						+ (showAverage ? String.format("(avg age: %.02f) ", averageAge) : "") + "as of "
						+ Date.dayToDate(dayOfData) + (log ? " (logarithmic)" : ""),
				"Date", "Cases", new TimeSeriesCollection(series), false, false, false);

		if (log) {
			XYPlot plot = chart.getXYPlot();
			LogarithmicAxis yAxis = new LogarithmicAxis("Cases");
			// yAxis.setUpperBound(100000);
			plot.setRangeAxis(yAxis);
		}

		File file = new File(by + "-" + (log ? "log-" : "cart-") + Date.dayToFullDate(dayOfData, '-') + ".png");
		try {
			ChartUtils.saveChartAsPNG(file, chart, 1000, 800);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return file.getAbsolutePath();
	}
	
	

	public String buildOnsetDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, dayOfData,
				dayOfOnset -> (double) stats.getCasesByOnsetDay(dayOfData, dayOfOnset), "onset", log, !log, false);
	}

	public String buildInfectionDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, dayOfData,
				dayOfOnset -> stats.getCasesByInfectionDay(dayOfData, dayOfOnset), "infection", log, !log, false);
	}

	public String buildNewInfectionDayTimeseriesChart(CovidStats stats, int dayOfData) {
		return buildCasesTimeseriesChart(stats, dayOfData,
				dayOfOnset -> stats.getCasesByInfectionDay(dayOfData, dayOfOnset)
						- stats.getCasesByInfectionDay(dayOfData - 1, dayOfOnset),
				"today's cases infection", false, false, true);
	}

	public String buildCharts(CovidStats stats) {
		String fname = null;
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
			fname = buildOnsetDayTimeseriesChart(stats, dayOfData, false);
			fname = buildInfectionDayTimeseriesChart(stats, dayOfData, false);
			fname = buildNewInfectionDayTimeseriesChart(stats, dayOfData);
			fname = buildOnsetDayTimeseriesChart(stats, dayOfData, true);
			fname = buildInfectionDayTimeseriesChart(stats, dayOfData, true);
		}
		return fname;

	}
}
