package charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.File;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;

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

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		YIntervalSeries cfr = new YIntervalSeries("CFR (deaths / cases)");
		YIntervalSeries chr = new YIntervalSeries("CHR (hospitalizations / cases)d");
		YIntervalSeries hfr = new YIntervalSeries("HFR (deaths / hospitalizations)");
		YIntervalSeries pos = new YIntervalSeries("Positivity (cases / tests)");

		IncompleteNumbers tNumbers = stats.getNumbers(NumbersType.TESTS, timing);
		IncompleteNumbers cNumbers = stats.getNumbers(NumbersType.CASES, timing);
		IncompleteNumbers hNumbers = stats.getNumbers(NumbersType.HOSPITALIZATIONS, timing);
		IncompleteNumbers dNumbers = stats.getNumbers(NumbersType.DEATHS, timing);

		int firstDayOfChart = stats.getVeryFirstDay();

		for (int dayOfInfection = firstDayOfChart; dayOfInfection <= dayOfData; dayOfInfection++) {
			double testsUpper = tNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double testsProj = tNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
					smoothing);
			double testsLower = tNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			double casesUpper = cNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double casesProj = cNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
					smoothing);
			double casesLower = cNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			double hospUpper = hNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double hospProj = hNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
					smoothing);
			double hospLower = hNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			double deathUpper = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
			double deathProj = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
					smoothing);
			double deathLower = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);

			long time = CalendarUtils.dayToTime(dayOfInfection);

			if (Double.isFinite(casesUpper) && casesProj > 0 && casesLower > 0 && casesUpper > 0) {
				cfr.add(time, 100.0 * deathProj / casesProj, 100.0 * deathLower / casesUpper,
						100.0 * deathUpper / casesLower);

				chr.add(time, 100.0 * hospProj / casesProj, 100.0 * hospLower / casesUpper,
						100.0 * hospUpper / casesLower);
			}
			if (Double.isFinite(hospUpper) && hospUpper > 0 && hospLower > 0 && hospProj > 0) {
				hfr.add(time, 100.0 * deathProj / hospProj, 100.0 * deathLower / hospUpper,
						100.0 * deathUpper / hospLower);
			}

			if (Double.isFinite(testsUpper) && testsProj > 0 && testsLower > 0 && testsUpper > 0) {
				pos.add(time, 100.0 * casesProj / testsProj, 100.0 * casesLower / testsUpper,
						100.0 * casesUpper / testsLower);
			}
		}

		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		if (useCFR) {
			collection.addSeries(cfr);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, Color.black);
			renderer.setSeriesFillPaint(seriesCount, Color.black.darker());
			seriesCount++;
		}
		if (useHFR) {
			collection.addSeries(hfr);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, Color.orange);
			renderer.setSeriesFillPaint(seriesCount, Color.orange.darker());
			seriesCount++;
		}
		if (useCHR) {
			collection.addSeries(chr);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, Color.red);
			renderer.setSeriesFillPaint(seriesCount, Color.red.darker());
			seriesCount++;
		}
		if (usePositivity) {
			collection.addSeries(pos);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, Color.blue);
			renderer.setSeriesFillPaint(seriesCount, Color.blue.darker());
			seriesCount++;
		}

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title + "\n(" + smoothing.getDescription() + ")",
				"Date of Infection", "Rate (%)", collection);

		// chart.getXYPlot().setRangeAxis(new LogarithmicAxis("Cases"));

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

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
		return Math.max(Charts.getFirstDayForCharts(stats), stats.getFirstDayOfData());
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
