package charts;

import java.io.File;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import covid.CalendarUtils;
import covid.ColoradoStats;
import covid.Event;
import covid.IncompleteNumbers;
import covid.NumbersTiming;
import covid.NumbersType;
import covid.Smoothing;

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
public class ChartRates {

	private static final double incompleteCutoff = 1.21;

	private static final NumbersTiming timing = NumbersTiming.INFECTION;

	public static final String RATES_FOLDER = Charts.FULL_FOLDER + "\\rates";

	public static int count(boolean... booleans) {
		int count = 0;
		for (boolean b : booleans) {
			if (b) {
				count++;
			}
		}
		return count;
	}

	private static Chart buildRates(ColoradoStats stats, int dayOfData, String fileName, String title, boolean useCFR,
			boolean useCHR, boolean useHFR, boolean usePositivity, Integer fixedHeight) {

		Smoothing smoothing = new Smoothing(13, Smoothing.Type.AVERAGE, Smoothing.Timing.TRAILING);

		TimeSeries cfrUpper = new TimeSeries("CFR (deaths / cases) upper bound");
		TimeSeries cfrLower = new TimeSeries("CFR lower bound");
		TimeSeries chrUpper = new TimeSeries("CHR (hospitalizations / cases) upper bound");
		TimeSeries chrLower = new TimeSeries("CHR lower bound");
		TimeSeries hfrUpper = new TimeSeries("HFR (deaths / hospitalizations) upper bound");
		TimeSeries hfrLower = new TimeSeries("HFR lower bound");
		TimeSeries posUpper = new TimeSeries("Positivity (cases / tests) upper bound");
		TimeSeries posLower = new TimeSeries("Positivity lower bound");

		IncompleteNumbers tNumbers = stats.getNumbers(NumbersType.TESTS, timing);
		IncompleteNumbers cNumbers = stats.getNumbers(NumbersType.CASES, timing);
		IncompleteNumbers hNumbers = stats.getNumbers(NumbersType.HOSPITALIZATIONS, timing);
		IncompleteNumbers dNumbers = stats.getNumbers(NumbersType.DEATHS, timing);

		int firstDayOfChart = stats.getVeryFirstDay();

		for (int dayOfInfection = firstDayOfChart; dayOfInfection <= dayOfData; dayOfInfection++) {
			double testsUpper = tNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double testsLower = tNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			double casesUpper = cNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double casesLower = cNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			double hospUpper = hNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double hospLower = hNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			double deathUpper = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double deathLower = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			Day ddd = CalendarUtils.dayToDay(dayOfInfection);

			if (Double.isFinite(casesUpper) && casesUpper > 0) {
				cfrUpper.add(ddd, 100.0 * deathUpper / casesLower);
				cfrLower.add(ddd, 100.0 * deathLower / casesUpper);

				chrUpper.add(ddd, 100.0 * hospUpper / casesLower);
				chrLower.add(ddd, 100.0 * hospLower / casesUpper);
			}
			if (Double.isFinite(hospUpper) && hospUpper > 0) {
				hfrUpper.add(ddd, 100.0 * deathUpper / hospLower);
				hfrLower.add(ddd, 100.0 * deathLower / hospUpper);
			}

			if (Double.isFinite(testsUpper) && testsUpper > 0) {
				posUpper.add(ddd, 100.0 * casesUpper / testsLower);
				posLower.add(ddd, 100.0 * casesLower / testsUpper);
			}
		}

		TimeSeriesCollection collection = new TimeSeriesCollection();
		if (useCFR) {
			collection.addSeries(cfrUpper);
			collection.addSeries(cfrLower);
		}
		if (useHFR) {
			collection.addSeries(hfrUpper);
			collection.addSeries(hfrLower);
		}
		if (useCHR) {
			collection.addSeries(chrUpper);
			collection.addSeries(chrLower);
		}
		if (usePositivity) {
			collection.addSeries(posUpper);
			collection.addSeries(posLower);
		}

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title + "\n(" + smoothing.getDescription() + ")",
				"Date of Infection", "Rate (%)", collection);

		// chart.getXYPlot().setRangeAxis(new LogarithmicAxis("Cases"));

		XYPlot plot = chart.getXYPlot();

		if (fixedHeight != null) {
			DateAxis xAxis = new DateAxis("Date");
			xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(firstDayOfChart));
			xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(Charts.getLastDayForChartDisplay(stats)));
			plot.setDomainAxis(xAxis);

			ValueAxis yAxis = plot.getRangeAxis();
			yAxis.setLowerBound(0);
			yAxis.setUpperBound(fixedHeight);

			plot.addDomainMarker(Charts.getTodayMarker(dayOfData));

			Event.addEvents(plot);
		}

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT),
				RATES_FOLDER + "\\" + fileName + ".png");

		if (timing == NumbersTiming.INFECTION && dayOfData == stats.getLastDay()
				&& count(useCFR, useHFR, useCHR, usePositivity) == 1) {
			String name = Charts.TOP_FOLDER + "\\";
			if (useCFR) {
				name += "CFR-infection.png";
			} else if (useHFR) {
				name += "HFR-infection.png";
			} else if (useCHR) {
				name += "CHR-infection.png";
			} else if (usePositivity) {
				name += "Positivity-infection.png";
			} else {
				throw new RuntimeException("...");
			}
			c.addFileName(name);
			c.saveAsPNG();
			c.open();
		} else {
			c.saveAsPNG();
		}
		return c;
	}

	public static int getFirstDayForAnimation(ColoradoStats stats) {
		return Math.max(Charts.getFirstDayForCharts(stats), stats.getFirstDayOfTiming(timing));
	}

	public static void buildGIF(ColoradoStats stats, String prefix, String title, boolean useCFR, boolean useCHR,
			boolean useHFR, boolean usePositivity) {

		new File(RATES_FOLDER).mkdir();

		AnimatedGifEncoder gif = new AnimatedGifEncoder();

		String fileName = Charts.FULL_FOLDER + "\\" + prefix + ".gif";
		gif.start(fileName);
		for (int dayOfData = getFirstDayForAnimation(stats); dayOfData <= stats.getLastDay(); dayOfData++) {

			String day = CalendarUtils.dayToDate(dayOfData);
			String full = CalendarUtils.dayToFullDate(dayOfData, '-');
			int fixedHeight = 0;

			if (useCFR) {
				fixedHeight = Math.max(fixedHeight, 10);
			}
			if (useCHR) {
				fixedHeight = Math.max(fixedHeight, 40);
			}
			if (useHFR) {
				fixedHeight = Math.max(fixedHeight, 100);
			}
			if (usePositivity) {
				fixedHeight = Math.max(fixedHeight, 25);
			}

			Chart c = buildRates(stats, dayOfData, prefix + "-" + full, title + day, useCFR, useCHR, useHFR,
					usePositivity, fixedHeight);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(c.getImage());
		}

		gif.finish();
	}
}
