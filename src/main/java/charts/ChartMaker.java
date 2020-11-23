package charts;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.Future;
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
import covid.CountyStats;
import covid.Date;
import covid.Event;
import covid.NumbersTiming;
import covid.NumbersType;
import library.MyExecutor;

public class ChartMaker {

	int built = 0;

	private final ColoradoStats stats;

	public ChartMaker(ColoradoStats stats) {
		this.stats = stats;
	}

	public BufferedImage buildCasesTimeseriesChart(String folder, String fileName, int dayOfData,
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

	public BufferedImage buildTimeseriesChart(NumbersType type, NumbersTiming timing, int dayOfData, boolean log) {
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

	public BufferedImage buildNewTimeseriesChart(NumbersType type, NumbersTiming timing, int dayOfData) {
		String by = "new-" + type.lowerName + "-" + timing.lowerName;
		return buildCasesTimeseriesChart(by, Date.dayToFullDate(dayOfData), dayOfData,
				dayOfOnset -> (double) stats.getNewCasesByType(type, timing, dayOfData, dayOfOnset), null, by, "?",
				false, true, 0, false);
	}

	public void buildNewTimeseriesCharts(NumbersType type, NumbersTiming timing) {
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
			buildNewTimeseriesChart(type, timing, dayOfData);
		}
	}

	public BufferedImage buildAgeTimeseriesChart(NumbersType type, NumbersTiming timing, int dayOfData) {
		String by = "age-" + type.lowerName + "-" + timing.lowerName;
		return buildCasesTimeseriesChart(by, Date.dayToFullDate(dayOfData), dayOfData,
				dayOfCases -> stats.getAverageAgeOfNewCases(type, timing, dayOfCases), null, by, "?", false, false, 0,
				false);
	}

	public void createCountyStats(CountyStats county, int dayOfData) {
		buildCasesTimeseriesChart("county", Date.dayToFullDate(dayOfData), dayOfData,
				dayOfCases -> Double.valueOf(county.getCases().getNumbersInInterval(dayOfCases, 14)),
				dayOfCases -> Double.valueOf(county.getDeaths().getNumbersInInterval(dayOfCases, 14)),
				county.getDisplayName(), "Count", false, false, 0, false);
		buildCasesTimeseriesChart("county", Date.dayToFullDate(dayOfData), dayOfData,
				dayOfCases -> Double.valueOf(Math.max(county.getCases().getNumbersInInterval(dayOfCases, 14), 1)),
				dayOfCases -> Double.valueOf(Math.max(county.getDeaths().getNumbersInInterval(dayOfCases, 14), 1)),
				county.getDisplayName(), "count", true, false, 0, false);
	}

	public void createCumulativeStats() {
		String date = Date.dayToFullDate(stats.getLastDay());
		buildCasesTimeseriesChart("cumulative", date, stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.CASES).getCumulativeNumbers(dayOfCases), null,
				"cases", "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", date, stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.HOSPITALIZATIONS).getCumulativeNumbers(dayOfCases),
				null, "hospitalizations", "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", date, stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.DEATHS).getCumulativeNumbers(dayOfCases), null,
				"deaths", "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", date, stats.getLastDay(),
				dayOfCases -> (double) stats.confirmedDeaths.getCumulativeNumbers(dayOfCases), null, "deaths (final)",
				"count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", date, stats.getLastDay(),
				dayOfCases -> (double) stats.peopleTested.getCumulativeNumbers(dayOfCases), null, "peopleTested",
				"count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", date, stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.TESTS).getCumulativeNumbers(dayOfCases), null,
				"testEncounters", "count", false, false, 0, false);

		for (NumbersType type : NumbersType.values()) {
			for (NumbersTiming timing : NumbersTiming.values()) {
				buildAgeTimeseriesChart(type, timing, stats.getLastDay());
			}
		}

	}

	public String buildCharts() {
		new File(Charts.TOP_FOLDER).mkdir();

		if (false) {
			return buildTimeseriesCharts(NumbersType.TESTS, NumbersTiming.INFECTION, true);
		}

		MyExecutor.executeCode(() -> createCumulativeStats());
		stats.getCounties()
				.forEach((key, value) -> MyExecutor.executeCode(() -> createCountyStats(value, stats.getLastDay())));

		Future<BufferedImage> fbi;
		for (NumbersTiming timing : NumbersTiming.values()) {
			for (NumbersType type : NumbersType.values()) {
				MyExecutor.executeCode(() -> buildTimeseriesCharts(type, timing, true));
				MyExecutor.executeCode(() -> buildTimeseriesCharts(type, timing, false));
				MyExecutor.executeCode(() -> buildNewTimeseriesCharts(type, timing));
			}
		}

		MyExecutor.executeCode(() -> ChartRates.buildRates(stats, "rates", "Colorado rates by day of infection, ", true,
				true, true, true));
		MyExecutor.executeCode(() -> ChartRates.buildRates(stats, "CFR", "Colorado rates by day of infection, ", true,
				false, false, false));
		MyExecutor.executeCode(() -> ChartRates.buildRates(stats, "CHR", "Colorado rates by day of infection, ", false,
				true, false, false));
		MyExecutor.executeCode(() -> ChartRates.buildRates(stats, "HFR", "Colorado rates by day of infection, ", false,
				false, true, false));
		MyExecutor.executeCode(() -> ChartRates.buildRates(stats, "Positivity", "Colorado rates by day of infection, ",
				false, false, false, true));

		return null;
	}
}
