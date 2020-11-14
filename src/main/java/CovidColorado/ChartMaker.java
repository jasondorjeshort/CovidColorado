package CovidColorado;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultXYDataset;

import library.MyExecutor;

public class ChartMaker {

	int built = 0;

	private static final String TOP_FOLDER = "H:\\CovidColorado";

	private static final double halfLifeRatio = Math.pow(0.5, 1 / 7.0);

	public String buildCasesTimeseriesChart(CovidStats stats, String folder, int dayOfData,
			Function<Integer, Double> getCasesForDay, Function<Integer, Double> getProjectedCasesForDay, String by,
			boolean log, boolean showZeroes, boolean showAverage, int daysToSkip, boolean showRollingAverage) {

		folder = TOP_FOLDER + "\\" + folder;
		new File(folder).mkdir();

		DefaultXYDataset dataset = new DefaultXYDataset();
		TimeSeries series = new TimeSeries("Cases");
		TimeSeries projectedSeries = new TimeSeries("Projected");
		int totalCases = 0, totalDays = 0;
		double rollingAverage = 0;
		for (int d = Math.max(showAverage ? dayOfData - 30 : 0, stats.getFirstDay()); d <= dayOfData
				- daysToSkip; d++) {
			double cases = getCasesForDay.apply(d);
			Double projected = null;
			if (getProjectedCasesForDay != null) {
				projected = getProjectedCasesForDay.apply(d);
			}

			if (!Double.isFinite(cases)) {
				continue;
			}

			rollingAverage = rollingAverage * halfLifeRatio + cases * (1 - halfLifeRatio);

			Day ddd = Date.dayToDay(d);
			if (Double.isFinite(cases) && (showZeroes || cases > 0)) {
				series.add(ddd, cases);
				if (getProjectedCasesForDay != null) {
					projectedSeries.add(ddd, projected);
				}
				totalCases += cases;
				totalDays += cases * d;
			}
			if (!log && !showZeroes) {
				// System.out.println("Cases " + cases + " on day " + ddd);
			}
		}

		// dataset.addSeries("Cases", series);

		double averageAge = dayOfData - (double) totalDays / totalCases;
		TimeSeriesCollection collection = new TimeSeriesCollection(series);
		if (getProjectedCasesForDay != null) {
			collection.addSeries(projectedSeries);
		}
		JFreeChart chart = ChartFactory.createTimeSeriesChart(
				"Colorado cases (" + totalCases + ") by " + by + " date "
						+ (showAverage ? String.format("(avg age: %.02f) ", averageAge) : "") + "as of "
						+ Date.dayToDate(dayOfData) + (log ? " (logarithmic)" : ""),
				"Date", "Cases.", collection, false, false, false);

		if (log) {
			XYPlot plot = chart.getXYPlot();
			LogarithmicAxis yAxis = new LogarithmicAxis("Cases");
			yAxis.setLowerBound(10);
			yAxis.setUpperBound(100000);
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");
			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(new java.util.Date(120, 11, 31));
			plot.setDomainAxis(xAxis);

			ValueMarker marker = new ValueMarker(Date.dayToJavaDate(dayOfData).getTime());
			marker.setPaint(Color.black);
			plot.addDomainMarker(marker);

		}

		File file = new File(
				folder + "\\" + by + "-" + (log ? "log-" : "cart-") + Date.dayToFullDate(dayOfData, '-') + ".png");
		try {
			ChartUtils.saveChartAsPNG(file, chart, 800, 600);
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*
		 * int tot; synchronized (this) { tot = ++built; }
		 * System.out.println("Built chart " + file.getAbsolutePath() + ", " +
		 * tot + " total.");
		 */
		return file.getAbsolutePath();
	}

	public String buildOnsetDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, "onset-" + (log ? "log" : "cart"), dayOfData,
				dayOfOnset -> (double) stats.getCasesByOnsetDay(dayOfData, dayOfOnset),
				dayOfOnset -> (double) stats.getProjectedCasesByOnsetDay(dayOfData, dayOfOnset),

				"onset", log, !log, false, 0, false);
	}

	public String buildInfectionDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, "infection-" + (log ? "log" : "cart"), dayOfData,
				dayOfOnset -> stats.getCasesByInfectionDay(dayOfData, dayOfOnset),
				dayOfOnset -> stats.getProjectedCasesByInfectionDay(dayOfData, dayOfOnset), "infection", log, !log,
				false, 5, false);
	}

	public String buildNewInfectionDayTimeseriesChart(CovidStats stats, int dayOfData) {
		return buildCasesTimeseriesChart(stats, "new-infection", dayOfData,
				dayOfOnset -> stats.getNewCasesByInfectionDay(dayOfData, dayOfOnset), null, "today's cases infection",
				false, false, true, 0, false);
	}

	// this completely doesn't work.
	public String buildOnsetReportedDayTimeseriesChart(CovidStats stats, int dayOfOnset) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		String folder = TOP_FOLDER + "\\onset_reported";
		new File(folder).mkdir();

		int range = 5, fullRange = range * 2 + 1;

		for (int daysToReporting = 0; daysToReporting < 60; daysToReporting++) {
			int dayOfData = dayOfOnset + daysToReporting;

			int cases = stats.getNewCasesByOnsetDay(dayOfData, dayOfOnset);
			double smoothed = 0;
			for (int i = -range; i <= range; i++) {
				smoothed += stats.getNewCasesByOnsetDay(dayOfData + i, dayOfOnset);
			}
			smoothed /= fullRange;

			dataset.addValue(cases, "cases", Integer.valueOf(daysToReporting));
			dataset.addValue(smoothed, "smoothed (" + fullRange + " days symmetric average)",
					Integer.valueOf(daysToReporting));
		}

		JFreeChart chart = ChartFactory.createLineChart(
				"Days from onset (" + Date.dayToFullDate(dayOfOnset, '-') + ") to test reporting", "Days", "Cases",
				dataset, PlotOrientation.VERTICAL, true, true, false);

		File file = new File(folder + "\\days_from_onset_to_reporting-" + Date.dayToFullDate(dayOfOnset, '-') + ".png");
		try {
			ChartUtils.saveChartAsPNG(file, chart, 1920, 1080);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return file.getAbsolutePath();
	}

	public String buildReportedDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, "reported-" + (log ? "log" : "cart"), dayOfData,
				dayOfOnset -> (double) stats.getCasesByReportedDay(dayOfData, dayOfOnset), null, "reported", log, !log,
				false, 0, false);
	}

	public String buildCaseAgeTimeseriesChart(CovidStats stats, int dayOfData) {
		return buildCasesTimeseriesChart(stats, "case-age", dayOfData,
				dayOfCases -> stats.getAverageAgeOfNewCases(dayOfCases), null, "age", false, true, false, 0, true);
	}

	public String buildCharts(CovidStats stats) {
		Future<String> fname = null;
		new File(TOP_FOLDER).mkdir();
		for (int dayOfData = stats.getLastDay(); dayOfData >= stats.getFirstDay(); dayOfData--) {
			int _dayOfData = dayOfData;

			MyExecutor.submitCode(() -> buildOnsetDayTimeseriesChart(stats, _dayOfData, false));
			MyExecutor.submitCode(() -> buildOnsetDayTimeseriesChart(stats, _dayOfData, true));

			MyExecutor.submitCode(() -> buildNewInfectionDayTimeseriesChart(stats, _dayOfData));

			MyExecutor.submitCode(() -> buildReportedDayTimeseriesChart(stats, _dayOfData, true));
			MyExecutor.submitCode(() -> buildReportedDayTimeseriesChart(stats, _dayOfData, false));

			MyExecutor.submitCode(() -> buildInfectionDayTimeseriesChart(stats, _dayOfData, false));
			Future<String> fname_ = MyExecutor
					.submitCode(() -> buildInfectionDayTimeseriesChart(stats, _dayOfData, true));

			int dayOfOnset = dayOfData; // names...
			if (stats.getCasesByOnsetDay(stats.getLastDay(), dayOfOnset) > 0) {
				MyExecutor.submitCode(() -> buildOnsetReportedDayTimeseriesChart(stats, dayOfOnset));
			}

			MyExecutor.submitCode(() -> buildCaseAgeTimeseriesChart(stats, _dayOfData));

			if (fname == null) {
				fname = fname_;
			}
		}

		if (fname != null) {
			try {
				return fname.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;

	}
}
