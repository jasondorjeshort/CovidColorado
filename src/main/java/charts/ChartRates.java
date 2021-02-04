package charts;

import java.awt.BasicStroke;
import java.io.File;
import java.util.Set;

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
import covid.Rate;
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

	private final ColoradoStats stats;

	public ChartRates(ColoradoStats stats) {
		new File(RATES_FOLDER).mkdir();
		this.stats = stats;
	}

	private Chart buildRates(int dayOfData, Set<Rate> rates, String folder) {

		Smoothing smoothing = new Smoothing(13, Smoothing.Type.AVERAGE, Smoothing.Timing.TRAILING);

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		int seriesCount = 0;

		int height = 0;

		int firstDayOfChart = stats.getVeryFirstDay();

		for (Rate rate : rates) {
			IncompleteNumbers nNumbers = stats.getNumbers(rate.numerator, timing);
			IncompleteNumbers dNumbers = stats.getNumbers(rate.denominator, timing);

			System.out.println("Building " + rate.allCapsName + " as " + rate.numerator + " / " + rate.denominator);

			YIntervalSeries series = new YIntervalSeries(rate.description);

			height = Math.max(height, rate.highestValue);

			for (int dayOfInfection = firstDayOfChart; dayOfInfection <= dayOfData; dayOfInfection++) {
				double nUpper = nNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
				double nLower = nNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);
				double nProj = nNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
						smoothing);

				double dUpper = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
				double dLower = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);
				double dProj = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
						smoothing);

				long time = CalendarUtils.dayToTime(dayOfInfection);

				if (!Double.isFinite(dUpper) || !Double.isFinite(dLower) || !Double.isFinite(dProj)) {
					continue;
				}

				if (dUpper <= 0 || dLower <= 0 || dProj <= 0) {
					continue;
				}

				series.add(time, 100 * nProj / dProj, 100 * nLower / dUpper, 100 * nUpper / dLower);
			}

			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, rate.color);
			renderer.setSeriesFillPaint(seriesCount, rate.color.darker());
			seriesCount++;
		}

		StringBuilder title = new StringBuilder();
		title.append("Colorado ");
		if (rates.size() > 1) {
			title.append("rates");
		} else {
			title.append(Rate.allCapsName(rates, ", "));
		}
		title.append(" by day of ");
		title.append(timing.lowerName);
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title + "\n(" + smoothing.getDescription() + ")",
				"Date of Infection", "Rate (%)", collection);

		// chart.getXYPlot().setRangeAxis(new LogarithmicAxis("Cases"));

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		DateAxis xAxis = new DateAxis("Date");
		xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(firstDayOfChart));
		xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(Charts.getLastDayForChartDisplay(stats)));
		plot.setDomainAxis(xAxis);

		ValueAxis yAxis = plot.getRangeAxis();
		yAxis.setLowerBound(0);
		yAxis.setUpperBound(height);

		plot.addDomainMarker(Charts.getTodayMarker(dayOfData));

		Event.addEvents(plot);

		String fileName = CalendarUtils.dayToFullDate(dayOfData, '-');
		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), folder + "\\" + fileName + ".png");

		if (timing == NumbersTiming.INFECTION && dayOfData == stats.getLastDay() && rates.size() == 1) {
			String name = Charts.TOP_FOLDER + "\\" + Rate.allCapsName(rates, "-") + "-" + timing.lowerName + ".png";
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

	public void buildGIF(Set<Rate> rates) {

		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String thisName = Rate.name(rates, "-");
		String fileName = RATES_FOLDER + "\\" + thisName + ".gif";
		String folder = RATES_FOLDER + "\\" + thisName;
		new File(folder).mkdir();
		gif.start(fileName);
		for (int dayOfData = getFirstDayForAnimation(stats); dayOfData <= stats.getLastDay(); dayOfData++) {
			Chart c = buildRates(dayOfData, rates, folder);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(c.getImage());
		}

		gif.finish();
	}
}
