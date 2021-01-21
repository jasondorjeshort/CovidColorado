package charts;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Callable;
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

import covid.CalendarUtils;
import covid.ColoradoStats;
import covid.Event;
import covid.IncompleteNumbers;
import covid.NumbersTiming;
import covid.NumbersType;
import library.MyExecutor;

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
public class ChartMaker {

	int built = 0;

	private final ColoradoStats stats;

	public ChartMaker(ColoradoStats stats) {
		this.stats = stats;
	}

	public BufferedImage buildCasesTimeseriesChart(String folder, String fileName, int dayOfData,
			Function<Integer, Double> getCasesForDay, Function<Integer, Double> getProjectedCasesForDay, String title,
			String verticalAxis, boolean log, boolean showAverage, int daysToSkip, boolean showEvents) {

		folder = Charts.FULL_FOLDER + "\\" + folder;

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

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(folder, fileName, image);
		return image;
	}

	public BufferedImage buildNewTimeseriesChart(NumbersType type, NumbersTiming timing, int dayOfData) {
		String by = "new-" + type.lowerName + "-" + timing.lowerName;
		return buildCasesTimeseriesChart(by, CalendarUtils.dayToFullDate(dayOfData), dayOfData,
				dayOfOnset -> (double) stats.getNewCasesByType(type, timing, dayOfData, dayOfOnset), null, by, "?",
				false, true, 0, false);
	}

	public static int getFirstDayForAnimation(ColoradoStats stats) {
		int day = 0;
		day = Math.max(day, stats.getLastDay() - 7);
		return Math.max(day, stats.getVeryFirstDay());
	}

	public String buildNewTimeseriesCharts(NumbersType type, NumbersTiming timing) {
		for (int dayOfData = getFirstDayForAnimation(stats); dayOfData <= stats.getLastDay(); dayOfData++) {
			buildNewTimeseriesChart(type, timing, dayOfData);
		}
		return null;
	}

	public String buildAgeTimeseriesChart(NumbersType type, NumbersTiming timing, int finalDay) {
		String by = "age-" + type.lowerName + "-" + timing.lowerName;
		IncompleteNumbers numbers = stats.getNumbers(type, timing);
		buildCasesTimeseriesChart(by, CalendarUtils.dayToFullDate(finalDay), finalDay,
				dayOfData -> numbers.getAverageAgeOfNewNumbers(dayOfData, 14), null, by, "?", false, false, 0, false);
		return null;
	}

	public void createCumulativeStats() {
		buildCasesTimeseriesChart("cumulative", "cases", stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.CASES).getCumulativeNumbers(dayOfCases), null,
				"cases", "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "hospitalizations", stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.HOSPITALIZATIONS).getCumulativeNumbers(dayOfCases),
				null, "hospitalizations", "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "deaths", stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.DEATHS).getCumulativeNumbers(dayOfCases), null,
				"deaths", "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "deaths (confirmed)", stats.getLastDay(),
				dayOfCases -> (double) stats.confirmedDeaths.getCumulativeNumbers(dayOfCases), null, "deaths (final)",
				"count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "people tested", stats.getLastDay(),
				dayOfCases -> (double) stats.peopleTested.getCumulativeNumbers(dayOfCases), null, "peopleTested",
				"count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "tests", stats.getLastDay(),
				dayOfCases -> (double) stats.getNumbers(NumbersType.TESTS).getCumulativeNumbers(dayOfCases), null,
				"testEncounters", "count", false, false, 0, false);

	}

	private long buildStarted = System.currentTimeMillis();
	private final LinkedList<Future<String>> background = new LinkedList<>();
	private int runs = 0;

	private void build(Callable<String> run) {
		background.add(MyExecutor.submitCode(run));
		runs++;
	}

	private void awaitBuild() {
		MyExecutor.executeWeb(new Runnable() {
			@Override
			public void run() {
				while (background.size() > 0) {
					try {
						background.pop().get();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				System.out.println("Built charts in " + (System.currentTimeMillis() - buildStarted) + " ms with " + runs
						+ " executions.");
			}
		});
	}

	public void buildCharts() {
		new File(Charts.TOP_FOLDER).mkdir();
		new File(Charts.FULL_FOLDER).mkdir();
		ChartCounty county = new ChartCounty(stats);
		ChartIncompletes incompletes = new ChartIncompletes(stats);
		Set<NumbersType> fullTypes = NumbersType.getSet();
		Set<NumbersType> noTests = NumbersType.getSet(NumbersType.CASES, NumbersType.DEATHS,
				NumbersType.HOSPITALIZATIONS);

		buildStarted = System.currentTimeMillis();

		if (false) {
			build(() -> ChartRates.buildGIF(stats, "CFR", "Colorado rates by day of infection, ", true, false, false,
					false));
			build(() -> incompletes.buildGIF(fullTypes, NumbersTiming.INFECTION, true));

			awaitBuild();
			return;
		}

		MyExecutor.executeCode(() -> createCumulativeStats());

		/* These are just ordered from slowest to fastest */

		build(() -> ChartRates.buildGIF(stats, "rates", "Colorado rates by day of infection, ", true, true, true,
				true));
		build(() -> ChartRates.buildGIF(stats, "CFR", "Colorado CFR by day of infection, ", true, false, false, false));
		build(() -> ChartRates.buildGIF(stats, "CHR", "Colorado CHR by day of infection, ", false, true, false, false));
		build(() -> ChartRates.buildGIF(stats, "HFR", "Colorado HFR by day of infection, ", false, false, true, false));
		build(() -> ChartRates.buildGIF(stats, "Positivity", "Colorado positivity by day of infection, ", false, false,
				false, true));

		for (NumbersTiming timing : NumbersTiming.values()) {
			build(() -> incompletes.buildGIF(noTests, timing, true));
			build(() -> incompletes.buildGIF(noTests, timing, false));
			build(() -> incompletes.buildGIF(fullTypes, timing, true));
			build(() -> incompletes.buildGIF(fullTypes, timing, false));

			for (NumbersType type : NumbersType.values()) {
				Set<NumbersType> types = NumbersType.getSet(type);
				build(() -> incompletes.buildGIF(types, timing, true));
				build(() -> incompletes.buildGIF(types, timing, false));
				build(() -> buildNewTimeseriesCharts(type, timing));
				build(() -> buildAgeTimeseriesChart(type, timing, stats.getLastDay()));
			}
		}

		stats.getCounties().forEach((key, value) -> build(() -> county.createCountyStats(value)));

		awaitBuild();
	}
}
