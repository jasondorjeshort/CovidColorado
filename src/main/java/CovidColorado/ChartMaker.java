package CovidColorado;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import library.MyExecutor;

public class ChartMaker {

	int built = 0;

	private static final String directory = "H:\\PNG";

	public String buildCasesTimeseriesChart(CovidStats stats, int dayOfData, Function<Integer, Double> getCasesForDay,
			String by, boolean log, boolean showZeroes, boolean showAverage, int daysToSkip) {
		TimeSeries series = new TimeSeries("Data");
		int totalCases = 0, totalDays = 0;
		for (int d = Math.max(showAverage ? dayOfData - 30 : 0, stats.getFirstDay()); d <= dayOfData
				- daysToSkip; d++) {
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
		JFreeChart chart = ChartFactory.createTimeSeriesChart(
				"Colorado cases (" + totalCases + ") by " + by + " date "
						+ (showAverage ? String.format("(avg age: %.02f) ", averageAge) : "") + "as of "
						+ Date.dayToDate(dayOfData) + (log ? " (logarithmic)" : ""),
				"Date", "Cases", new TimeSeriesCollection(series), false, false, false);

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

		}

		File file = new File(
				directory + "\\" + by + "-" + (log ? "log-" : "cart-") + Date.dayToFullDate(dayOfData, '-') + ".png");
		try {
			ChartUtils.saveChartAsPNG(file, chart, 1920, 1080);
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
		return buildCasesTimeseriesChart(stats, dayOfData,
				dayOfOnset -> (double) stats.getCasesByOnsetDay(dayOfData, dayOfOnset), "onset", log, !log, false, 10);
	}

	public String buildInfectionDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, dayOfData,
				dayOfOnset -> stats.getCasesByInfectionDay(dayOfData, dayOfOnset), "infection", log, !log, false, 15);
	}

	public String buildNewInfectionDayTimeseriesChart(CovidStats stats, int dayOfData) {
		return buildCasesTimeseriesChart(stats, dayOfData,
				dayOfOnset -> stats.getCasesByInfectionDay(dayOfData, dayOfOnset)
						- stats.getCasesByInfectionDay(dayOfData - 1, dayOfOnset),
				"today's cases infection", false, false, true, 0);
	}

	// this completely doesn't work.
	public String buildInfectionReportedDayTimeseriesChart(CovidStats stats, int dayOfInfection) {
		return buildCasesTimeseriesChart(stats, dayOfInfection,
				dayOfReporting -> stats.getCasesByInfectionDay(dayOfReporting, dayOfInfection)
						- stats.getCasesByInfectionDay(dayOfReporting - 1, dayOfInfection),
				"reported day of infections", false, false, false, 0);
	}

	public String buildReportedDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, dayOfData,
				dayOfOnset -> (double) stats.getCasesByReportedDay(dayOfData, dayOfOnset), "reported", log, !log, false,
				0);
	}

	public String buildCaseAgeTimeseriesChart(CovidStats stats, int dayOfData) {
		return buildCasesTimeseriesChart(stats, dayOfData, dayOfCases -> stats.getAverageAgeOfNewCases(dayOfCases),
				"age", false, true, false, 0);
	}

	public String buildCharts(CovidStats stats) {
		Future<String> fname = null;
		new File(directory).mkdir();
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
			int _dayOfData = dayOfData;
			fname = MyExecutor.submitCode(() -> buildOnsetDayTimeseriesChart(stats, _dayOfData, false));
			fname = MyExecutor.submitCode(() -> buildOnsetDayTimeseriesChart(stats, _dayOfData, true));

			fname = MyExecutor.submitCode(() -> buildNewInfectionDayTimeseriesChart(stats, _dayOfData));

			fname = MyExecutor.submitCode(() -> buildReportedDayTimeseriesChart(stats, _dayOfData, true));
			fname = MyExecutor.submitCode(() -> buildReportedDayTimeseriesChart(stats, _dayOfData, false));

			fname = MyExecutor.submitCode(() -> buildCaseAgeTimeseriesChart(stats, _dayOfData));

			fname = MyExecutor.submitCode(() -> buildInfectionDayTimeseriesChart(stats, _dayOfData, false));
			fname = MyExecutor.submitCode(() -> buildInfectionDayTimeseriesChart(stats, _dayOfData, true));

			fname = MyExecutor.submitCode(() -> buildInfectionReportedDayTimeseriesChart(stats, _dayOfData));
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
