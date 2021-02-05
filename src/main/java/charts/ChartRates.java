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
public class ChartRates extends AbstractChart {

	private static final NumbersTiming timing = NumbersTiming.INFECTION;

	public static final String RATES_FOLDER = Charts.FULL_FOLDER + "\\rates";
	public final Set<Rate> rates;

	public ChartRates(ColoradoStats stats, Set<Rate> rates) {
		super(RATES_FOLDER, stats);
		this.rates = rates;
	}

	@Override
	public Chart buildChart(int dayOfData) {
		Smoothing smoothing = new Smoothing(13, Smoothing.Type.AVERAGE, Smoothing.Timing.TRAILING);

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		int seriesCount = 0;

		int height = 0;

		int firstDayOfChart = stats.getVeryFirstDay();

		for (Rate rate : rates) {
			IncompleteNumbers nNumbers = stats.getNumbers(rate.numerator, timing);
			IncompleteNumbers dNumbers = stats.getNumbers(rate.denominator, timing);

			YIntervalSeries series = new YIntervalSeries(rate.description);

			height = Math.max(height, rate.highestValue);

			for (int dayOfInfection = firstDayOfChart; dayOfInfection <= dayOfData; dayOfInfection++) {
				Double nUpper = nNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
				Double nLower = nNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);
				Double nProj = nNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
						smoothing);
				if (nUpper == null || nLower == null || nProj == null) {
					continue;
				}

				Double dUpper = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.UPPER, smoothing);
				Double dLower = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.LOWER, smoothing);
				Double dProj = dNumbers.getNumbers(dayOfData, dayOfInfection, IncompleteNumbers.Form.PROJECTED,
						smoothing);
				if (dUpper == null || dLower == null || dProj == null) {
					continue;
				}

				long time = CalendarUtils.dayToTime(dayOfInfection);

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
		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT),
				getSubfolder() + "\\" + fileName + ".png");

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

	@Override
	public String getName() {
		return Rate.name(rates, "-");
	}
}
