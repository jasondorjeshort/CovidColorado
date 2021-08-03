package charts;

import java.awt.BasicStroke;
import java.util.Set;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
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
public class FullDelayChart extends TypesTimingChart {

	/*
	 * Preliminary here.
	 * 
	 * This charts the distribution of case reporting over time.
	 */

	private final boolean cumulative;
	private final int interval = 60;

	public FullDelayChart(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing, boolean cumulative) {
		super(stats, Charts.FULL_FOLDER + "\\" + "full-delay", types, timing);
		this.cumulative = cumulative;
	}

	public static double sumArray(double[] array) {
		double total = 0.0;
		for (double value : array) {
			total += value;
		}
		return total;
	}

	@Override
	public Chart buildChart(int lastDayOfData) {

		YIntervalSeriesCollection collection = new YIntervalSeriesCollection();
		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		String category = (types.size() > 1) ? "numbers" : NumbersType.name(types, "");

		double peak = 0;

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
					if (!numbers.dayHasData(dayOfData)) {
						continue;
					}
					int delay = dayOfData - dayOfType;
					double n1 = numbers.getNumbers(dayOfData, dayOfType);
					Integer prev = numbers.getPrevDayOfData(dayOfData);
					if (prev == null) {
						continue;
					}
					double n2 = numbers.getNumbers(prev, dayOfType);
					double pct = ((cumulative ? total2 : 0) + n1 - n2) / total;
					desc[delay].addValue(pct);
					total2 += n1 - n2;
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
				if (desc[delay].getN() == 0) {
					continue;
				}
				double middle = 100 * cF(desc[delay].getPercentile(50));
				double bottom = 100 * cF(desc[delay].getPercentile(25));
				double top = 100 * cF(desc[delay].getPercentile(75));
				peak = Math.max(peak, top);
				series.add(delay, middle, bottom, top);
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
		title.append(";\n");
		title.append(cumulative ? "Cumulative" : "Daily");
		title.append(" with median and interquartile ranges");
		title.append(";\n data through ");
		title.append(CalendarUtils.dayToDate(lastDayOfData));
		JFreeChart chart = ChartFactory.createXYLineChart(title.toString(), "Days of delay",
				"Percentage of " + category + (cumulative ? " through" : " on") + " this day", collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		// this actually does make empty graphs for which we need to set these
		// values by hand?
		ValueAxis xAxis = plot.getDomainAxis();
		xAxis.setLowerBound(0);
		xAxis.setUpperBound(interval);
		ValueAxis yAxis = plot.getRangeAxis();
		yAxis.setLowerBound(0);
		peak = Math.min(peak, 120);
		yAxis.setUpperBound(peak);

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), getPngName(lastDayOfData));
		if (timing == NumbersTiming.INFECTION && lastDayOfData == stats.getLastDay() && types.size() == 3
				&& cumulative) {
			c.addFileName(Charts.TOP_FOLDER + "\\" + getName() + ".png");
			c.open();
		}
		c.saveAsPNG();
		return c;
	}

	@Override
	public String getName() {
		return "delay-" + NumbersType.name(types, "-") + "-" + timing.lowerName + "-"
				+ (cumulative ? "cumulative" : "daily");
	}

}
