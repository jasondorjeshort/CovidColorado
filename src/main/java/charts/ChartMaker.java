package charts;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Set;
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
import covid.Smoothing;
import library.ASync;

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
		if (dayOfData == stats.getLastDay()) {
			// c.open();
		}
		return c;
	}

	public Chart buildNewTimeseriesChart(IncompleteNumbers numbers, int dayOfData) {
		String by = "new-" + numbers.getType().lowerName + "-" + numbers.getTiming().lowerName;
		if (!numbers.hasData()) {
			return null;
		}
		return buildCasesTimeseriesChart(by, CalendarUtils.dayToFullDate(dayOfData), dayOfData,
				dayOfType -> (double) numbers.getNewNumbers(dayOfData, dayOfType), null, by, "?", false, true, 0,
				false);
	}

	public int getFirstDayForAnimation() {
		return Math.max(Charts.getFirstDayForCharts(stats), stats.getVeryFirstDay());
	}

	public void buildNewTimeseriesCharts(IncompleteNumbers numbers) {
		if (!numbers.hasData()) {
			return;
		}
		for (int dayOfData = getFirstDayForAnimation(); dayOfData <= stats.getLastDay(); dayOfData++) {
			buildNewTimeseriesChart(numbers, dayOfData);
		}
	}

	public void buildAgeTimeseriesChart(IncompleteNumbers numbers, int finalDay) {
		if (!numbers.hasData()) {
			return;
		}
		String by = "age-" + numbers.getType().lowerName + "-" + numbers.getTiming().lowerName;
		buildCasesTimeseriesChart(by, CalendarUtils.dayToFullDate(finalDay), finalDay,
				dayOfData -> numbers.getAverageAgeOfNewNumbers(dayOfData, 14), null, by, "?", false, false, 0, false);
	}

	public void createCumulativeStats() {
		Smoothing smoothing = new Smoothing(7, Smoothing.Type.AVERAGE, Smoothing.Timing.TRAILING);
		String format = "Colorado %s, daily numbers\n(" + smoothing.getDescription() + ")";
		buildCasesTimeseriesChart("cumulative", "cases", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.CASES).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "cases"), "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "hospitalizations", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.HOSPITALIZATIONS).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "hospitalizations"), "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "deaths", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.DEATHS).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "deaths"), "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "deaths (confirmed)", stats.getLastDay(),
				dayOfCases -> stats.confirmedDeaths.getNumbers(dayOfCases, smoothing), null,
				String.format(format, "deaths (final)"), "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "people tested", stats.getLastDay(),
				dayOfCases -> stats.peopleTested.getNumbers(dayOfCases, smoothing), null,
				String.format(format, "people tested"), "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "tests", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.TESTS).getNumbers(dayOfCases, smoothing), null,
				String.format(format, "test encounters"), "count", false, false, 0, false);
		buildCasesTimeseriesChart("cumulative", "positivity", stats.getLastDay(),
				dayOfCases -> stats.getNumbers(NumbersType.CASES).getNumbers(dayOfCases, smoothing)
						/ stats.getNumbers(NumbersType.TESTS).getNumbers(dayOfCases, smoothing),
				null, "positivity", "count", false, false, 0, false);
	}

	public void buildCharts() {
		// folders must be at very top
		new File(Charts.TOP_FOLDER).mkdir();
		new File(Charts.FULL_FOLDER).mkdir();
		ChartCounty county = new ChartCounty(stats);
		ChartIncompletes incompletes = new ChartIncompletes(stats);
		Set<NumbersType> fullTypes = NumbersType.getSet();
		Set<NumbersType> noTests = NumbersType.getSet(NumbersType.CASES, NumbersType.DEATHS,
				NumbersType.HOSPITALIZATIONS);

		long buildStarted = System.currentTimeMillis();

		ASync<Void> build = new ASync<>();

		if (false) {
			build.execute(() -> ChartRates.buildGIF(stats, "CFR", "Colorado rates by day of infection, ", true, false,
					false, false));
			build.execute(() -> incompletes.buildGIF(fullTypes, NumbersTiming.INFECTION, true));
			build.complete();
			return;
		}

		build.execute(() -> createCumulativeStats());

		/* These are just ordered from slowest to fastest */

		build.execute(() -> ChartRates.buildGIF(stats, "rates", "Colorado rates by day of infection, ", true, true,
				true, true));
		build.execute(() -> ChartRates.buildGIF(stats, "CFR", "Colorado CFR by day of infection, ", true, false, false,
				false));
		build.execute(() -> ChartRates.buildGIF(stats, "CHR", "Colorado CHR by day of infection, ", false, true, false,
				false));
		build.execute(() -> ChartRates.buildGIF(stats, "HFR", "Colorado HFR by day of infection, ", false, false, true,
				false));
		build.execute(() -> ChartRates.buildGIF(stats, "Positivity", "Colorado positivity by day of infection, ", false,
				false, false, true));

		for (NumbersTiming timing : NumbersTiming.values()) {
			build.execute(() -> incompletes.buildGIF(noTests, timing, true));
			build.execute(() -> incompletes.buildGIF(noTests, timing, false));
			build.execute(() -> incompletes.buildGIF(fullTypes, timing, true));
			build.execute(() -> incompletes.buildGIF(fullTypes, timing, false));

			for (NumbersType type : NumbersType.values()) {
				Set<NumbersType> types = NumbersType.getSet(type);
				IncompleteNumbers numbers = stats.getNumbers(type, timing);
				build.execute(() -> incompletes.buildGIF(types, timing, true));
				build.execute(() -> incompletes.buildGIF(types, timing, false));
				build.execute(() -> buildNewTimeseriesCharts(numbers));
				build.execute(() -> buildAgeTimeseriesChart(numbers, stats.getLastDay()));
			}
		}

		stats.getCounties().forEach((key, value) -> build.execute(() -> county.createCountyStats(value)));
		build.complete();
		System.out.println("Built charts in " + (System.currentTimeMillis() - buildStarted) + " ms with "
				+ build.getExecutions() + " executions.");
	}
}
