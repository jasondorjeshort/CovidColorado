package charts;

import java.awt.BasicStroke;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;

import covid.CalendarUtils;
import covid.ColoradoStats;
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
public class FullDelayChart extends AbstractChart {

	/*
	 * Preliminary here.
	 * 
	 * This charts the distribution of case reporting over time.
	 */

	private final Set<NumbersType> types;
	private final NumbersTiming timing;

	private final int interval = 60;

	public FullDelayChart(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing) {
		super(stats, Charts.FULL_FOLDER + "\\" + "full-delay");
		this.types = types;
		this.timing = timing;
	}

	public static double sumArray(double[] array) {
		double total = 0.0;
		for (double value : array) {
			total += value;
		}
		return total;
	}

	boolean cumulative = true;

	@Override
	public Chart buildChart(int lastDayOfData) {

		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		String category = (types.size() > 1) ? "numbers" : NumbersType.name(types, "");

		for (NumbersType type : types) {
			IncompleteNumbers numbers = stats.getNumbers(type, timing);

			DescriptiveStatistics[] desc = new DescriptiveStatistics[interval + 1];
			for (int i = 0; i < desc.length; i++) {
				desc[i] = new DescriptiveStatistics();
			}

			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= numbers.getLastDay()
					- interval; dayOfType++) {
				double total = numbers.getNumbers(lastDayOfData, dayOfType);
				double total2 = 0;
				for (int dayOfData = dayOfType; dayOfData < dayOfType + interval
						&& dayOfData <= lastDayOfData; dayOfData++) {

					int delay = dayOfData - dayOfType;
					double n1 = numbers.getNumbers(dayOfData, dayOfType);
					double n2 = numbers.getNumbers(dayOfData - 1, dayOfType);
					double pct = ((cumulative ? total2 : 0) + n1 - n2) / total;
					desc[delay].addValue(pct);
					total2 += n1 - n2;
				}

				double n1 = numbers.getNumbers(lastDayOfData, dayOfType);
				double n2 = numbers.getNumbers(dayOfType + interval - 1, dayOfType);
				double pct = ((cumulative ? total2 : 0) + n1 - n2) / total;
				desc[interval].addValue(pct);
				total2 += n1 - n2;

				if (total != total2) {
					// new Exception("Fail totals: " + total + " vs " +
					// total2).printStackTrace();
				}
			}

			YIntervalSeries series = new YIntervalSeries(type.capName);
			for (int delay = 0; delay <= interval; delay++) {
				/*
				 * System.out .println("VALUES for " + delay + " out of " +
				 * desc[delay].getN() + ": " + desc[delay].getMean() + "[" +
				 * desc[delay].getPercentile(25) + " - " +
				 * desc[delay].getPercentile(75));
				 */
				series.add(delay, 100 * desc[delay].getPercentile(50), 100 * desc[delay].getPercentile(25),
						100 * desc[delay].getPercentile(75));
			}

			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, type.color);
			renderer.setSeriesFillPaint(seriesCount, type.color.darker());
			seriesCount++;
		}

		StringBuilder title = new StringBuilder();
		title.append("Time from " + timing.lowerName + " to release of all ");
		title.append(category);
		title.append(";\n data through ");
		title.append(CalendarUtils.dayToDate(lastDayOfData));
		JFreeChart chart = ChartFactory.createXYLineChart(title.toString(), "Days of delay",
				"Percentage of " + category + (cumulative ? " through" : " on") + " this day", collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), getPngName(lastDayOfData));
		if (timing == NumbersTiming.INFECTION && lastDayOfData == stats.getLastDay() && types.size() >= 3) {
			c.addFileName(Charts.TOP_FOLDER + "\\delay-" + timing.lowerName + ".png");
			c.open();
		}
		c.saveAsPNG();
		return c;
	}

	@Override
	public String getName() {
		return NumbersType.name(types, "-") + "-" + timing.lowerName;
	}

	/**
	 * Some combinations here have no data and this is the easiest way to find
	 * that out.
	 */
	@Override
	public boolean hasData() {
		for (NumbersType type : types) {
			IncompleteNumbers n = stats.getNumbers(type, timing);
			if (n.hasData()) {
				return true;
			}
		}
		return false;
	}
}
