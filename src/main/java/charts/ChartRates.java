package charts;

import java.awt.image.BufferedImage;
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

import covid.ColoradoStats;
import covid.Date;
import covid.Event;
import covid.IncompleteNumbers;
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
public class ChartRates {

	private static BufferedImage buildRates(ColoradoStats stats, int dayOfData, String fileName, String title,
			boolean useCFR, boolean useCHR, boolean useHFR, boolean usePositivity, Integer age, Integer fixedHeight) {

		int INTERVAL = 7;

		TimeSeries cfr = new TimeSeries("CFR (deaths / cases)");
		TimeSeries cfrProjected = new TimeSeries("CFR (projected)");
		TimeSeries chr = new TimeSeries("CHR (hospitalizations / cases)");
		TimeSeries chrProjected = new TimeSeries("CHR (projected)");
		TimeSeries hfr = new TimeSeries("HFR (deaths / hospitalizations)");
		TimeSeries hfrProjected = new TimeSeries("HFR (projected)");
		TimeSeries pos = new TimeSeries("Positivity");
		TimeSeries posProjected = new TimeSeries("Positivity (projected)");

		IncompleteNumbers tNumbers = stats.getNumbers(NumbersType.TESTS, NumbersTiming.INFECTION);
		IncompleteNumbers cNumbers = stats.getNumbers(NumbersType.CASES, NumbersTiming.INFECTION);
		IncompleteNumbers hNumbers = stats.getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION);
		IncompleteNumbers dNumbers = stats.getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION);

		for (int dayOfInfection = (age == null || fixedHeight != null ? 0
				: stats.getLastDay() - age); dayOfInfection <= stats.getLastDay(); dayOfInfection++) {
			double tests = tNumbers.getNumbers(dayOfData, dayOfInfection, false, INTERVAL);
			double testsProjected = tNumbers.getNumbers(dayOfData, dayOfInfection, true, INTERVAL);

			double cases = cNumbers.getNumbers(dayOfData, dayOfInfection, false, INTERVAL);
			double casesProjected = cNumbers.getNumbers(dayOfData, dayOfInfection, true, INTERVAL);

			double hosp = hNumbers.getNumbers(dayOfData, dayOfInfection, false, INTERVAL);
			double hospProjected = hNumbers.getNumbers(dayOfData, dayOfInfection, true, INTERVAL);

			double deaths = dNumbers.getNumbers(dayOfData, dayOfInfection, false, INTERVAL);
			double deathsProjected = dNumbers.getNumbers(dayOfData, dayOfInfection, true, INTERVAL);

			if (!Double.isFinite(cases) || cases == 0) {
				continue;
			}

			Day ddd = Date.dayToDay(dayOfInfection);

			if (Double.isFinite(cases) && cases > 0) {
				cfr.add(ddd, 100.0 * deaths / cases);
			}
			if (Double.isFinite(casesProjected) && casesProjected > 0) {
				cfrProjected.add(ddd, 100.0 * deathsProjected / casesProjected);
			}
			if (Double.isFinite(cases) && cases > 0) {
				chr.add(ddd, 100.0 * hosp / cases);
			}
			if (Double.isFinite(casesProjected) && casesProjected > 0) {
				chrProjected.add(ddd, 100.0 * hospProjected / casesProjected);
			}
			if (Double.isFinite(hosp) && hosp > 0) {
				hfr.add(ddd, 100.0 * deaths / hosp);
			}
			if (Double.isFinite(hospProjected) && hospProjected > 0) {
				hfrProjected.add(ddd, 100.0 * deathsProjected / hospProjected);
			}

			if (Double.isFinite(tests) && tests > 0) {
				pos.add(ddd, 100.0 * cases / tests);
			}
			if (Double.isFinite(testsProjected) && testsProjected > 0) {
				posProjected.add(ddd, 100.0 * casesProjected / testsProjected);
			}
		}

		TimeSeriesCollection collection = new TimeSeriesCollection();
		if (useCFR) {
			collection.addSeries(cfr);
			if (!usePositivity) {
				collection.addSeries(cfrProjected);
			}
		}
		if (useHFR) {
			collection.addSeries(hfr);
			if (!usePositivity) {
				collection.addSeries(hfrProjected);
			}
		}
		if (useCHR) {
			collection.addSeries(chr);
			if (!usePositivity) {
				collection.addSeries(chrProjected);
			}
		}
		if (usePositivity) {
			collection.addSeries(pos);
			if (!useCFR) {
				collection.addSeries(posProjected);
			}
		}

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title + "\n(" + INTERVAL + "-day running average)",
				"Date of Infection", "Rate (%)", collection);

		// chart.getXYPlot().setRangeAxis(new LogarithmicAxis("Cases"));

		XYPlot plot = chart.getXYPlot();
		plot.addDomainMarker(Charts.getIncompleteMarker(dayOfData - 35));

		if (fixedHeight != null) {
			DateAxis xAxis = (DateAxis) plot.getDomainAxis();
			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay()));

			ValueAxis yAxis = plot.getRangeAxis();
			yAxis.setLowerBound(0);
			yAxis.setUpperBound(fixedHeight);

			plot.addDomainMarker(Charts.getTodayMarker(dayOfData));

			Event.addEvents(plot);
		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(Charts.TOP_FOLDER + "\\rates", fileName, image);
		return image;
	}

	public static String buildRates(ColoradoStats stats, String prefix, String title, boolean useCFR, boolean useCHR,
			boolean useHFR, boolean usePositivity) {

		new File(Charts.TOP_FOLDER + "\\rates").mkdir();

		AnimatedGifEncoder gif = new AnimatedGifEncoder();

		String fileName = Charts.TOP_FOLDER + "\\" + prefix + ".gif";
		gif.start(fileName);
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {

			String day = Date.dayToDate(dayOfData);
			String full = Date.dayToFullDate(dayOfData, '-');
			int fixedHeight = 0;

			if (useCFR) {
				fixedHeight = Math.max(fixedHeight, 10);
			}
			if (useCHR) {
				fixedHeight = Math.max(fixedHeight, 16);
			}
			if (useHFR) {
				fixedHeight = Math.max(fixedHeight, 50);
			}
			if (usePositivity) {
				fixedHeight = Math.max(fixedHeight, 25);
			}

			BufferedImage bi = buildRates(stats, dayOfData, prefix + "-" + full, title + day, useCFR, useCHR, useHFR,
					usePositivity, null, fixedHeight);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(bi);
		}

		gif.finish();
		return fileName;
	}
}
