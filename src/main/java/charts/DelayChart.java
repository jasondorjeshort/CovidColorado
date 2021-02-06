package charts;

import java.util.Set;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

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
public class DelayChart extends AbstractChart {

	/*
	 * Preliminary here.
	 * 
	 * This charts the distribution of case reporting over time.
	 */

	private final Set<NumbersType> types;
	private final NumbersTiming timing;

	private final int interval = 60;

	public DelayChart(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing) {
		super(stats, Charts.FULL_FOLDER + "\\" + "new-numbers");
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

	public Chart buildFullChart(int lastDayOfData) {
		XYSeriesCollection collection = new XYSeriesCollection();

		for (NumbersType type : types) {

			double[] number = new double[interval + 1];
			IncompleteNumbers numbers = stats.getNumbers(type, timing);

			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= numbers.getLastDay(); dayOfType++) {
				for (int dayOfData = dayOfType; dayOfData < dayOfType + interval
						&& dayOfData <= lastDayOfData; dayOfData++) {
					int delay = dayOfData - dayOfType;
					double n1 = numbers.getNumbers(dayOfData, dayOfType);
					double n2 = numbers.getNumbers(dayOfData - 1, dayOfType);
					number[delay] += n1 - n2;
				}

				if (dayOfType + interval <= lastDayOfData) {
					double n1 = numbers.getNumbers(lastDayOfData, dayOfType);
					double n2 = numbers.getNumbers(dayOfType + interval - 1, dayOfType);
					number[interval] += n1 - n2;
				}
			}

			XYSeries series = new XYSeries(type.capName);
			double total = sumArray(number);
			for (int delay = 0; delay <= interval; delay++) {
				series.add(delay, number[delay] / total * 100.0);
			}
			collection.addSeries(series);
			System.out.println("Total " + type.lowerName + ": " + total);
		}

		StringBuilder title = new StringBuilder();
		title.append("Time from " + timing.lowerName + " to release of all ");
		if (types.size() > 1) {
			title.append("numbers");
		} else {
			for (NumbersType type : types) {
				title.append(type.lowerName);
			}
		}
		title.append(";\n data through ");
		title.append(CalendarUtils.dayToDate(lastDayOfData));
		JFreeChart chart = ChartFactory.createXYLineChart(title.toString(), "Date", "Percentage", collection);

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), getPngName(lastDayOfData));
		if (timing == NumbersTiming.INFECTION && types.size() == 3) {
			c.addFileName(Charts.TOP_FOLDER + "\\delay-" + timing.lowerName + ".png");
			c.saveAsPNG();
			c.open();
		} else {
			c.saveAsPNG();
		}
		return c;
	}

	@Override
	public Chart buildChart(int dayOfType) {

		if (dayOfType == stats.getLastDay()) {
			return buildFullChart(dayOfType);
		}

		boolean hasData = false;

		XYSeriesCollection collection = new XYSeriesCollection();

		for (NumbersType type : types) {
			IncompleteNumbers numbers = stats.getNumbers(type, timing);
			XYSeries series = new XYSeries(type.capName);

			for (int dayOfData = dayOfType; dayOfData < dayOfType + interval
					&& dayOfData <= stats.getLastDay(); dayOfData++) {
				int delay = dayOfData - dayOfType;
				double n1 = numbers.getNumbers(dayOfData, dayOfType);
				double n2 = numbers.getNumbers(dayOfData - 1, dayOfType);
				series.add(delay, n1 - n2);
			}

			if (dayOfType + interval <= stats.getLastDay()) {
				double n1 = numbers.getNumbers(stats.getLastDay(), dayOfType);
				double n2 = numbers.getNumbers(dayOfType + interval - 1, dayOfType);
				series.add(interval, n1 - n2);
			}

			if (series.getItemCount() > 0) {
				collection.addSeries(series);
				hasData = true;
			}
		}

		if (!hasData) {
			return null;
		}

		StringBuilder title = new StringBuilder();
		title.append("Time from " + timing.lowerName + " to release of\n");
		if (types.size() > 1) {
			title.append("numbers");
		} else {
			for (NumbersType type : types) {
				title.append(type.lowerName);
			}
		}
		title.append(" for " + timing.lowerName + " on ");
		title.append(CalendarUtils.dayToDate(dayOfType));
		JFreeChart chart = ChartFactory.createXYLineChart(title.toString(), "Date", "Count", collection);

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), getPngName(dayOfType));
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
