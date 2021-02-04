package charts;

import java.awt.Color;
import java.io.File;
import java.util.Set;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import covid.CalendarUtils;
import covid.ColoradoStats;
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
public class Reproductive {
	/*
	 * Preliminary here.
	 * 
	 * This charts the estimated R(t).
	 */

	final ColoradoStats stats;
	final String FOLDER = Charts.FULL_FOLDER + "\\" + "R";

	public Reproductive(ColoradoStats stats) {
		this.stats = stats;
		new File(FOLDER).mkdir();
	}

	private static final NumbersTiming TIMING = NumbersTiming.INFECTION;
	private static final boolean SHOW_EVENTS = true;

	private static final int FIRST_DAY = CalendarUtils.dateToDay("2-14-2020");

	/**
	 * @param types
	 * @param dayOfData
	 * @return
	 */
	private Chart buildReproductiveChart(Set<NumbersType> types, int dayOfData) {

		XYSeriesCollection collection = new XYSeriesCollection();

		for (NumbersType type : NumbersType.values()) {
			if (!types.contains(type)) {
				continue;
			}

			IncompleteNumbers numbers = stats.getNumbers(type, TIMING);
			if (!numbers.hasData()) {
				throw new RuntimeException("UH OH");
			}

			XYSeries series = new XYSeries("Based on " + type.capName);

			for (int dayOfType = FIRST_DAY; dayOfType <= dayOfData; dayOfType++) {
				long time = CalendarUtils.dayToTime(dayOfType);

				Double upperBound = numbers.getBigR(dayOfData, dayOfType, true);
				Double lowerBound = numbers.getBigR(dayOfData, dayOfType, false);
				if (upperBound == null || !Double.isFinite(upperBound) || lowerBound == null
						|| !Double.isFinite(lowerBound)) {
					continue;
				}

				series.add(time, upperBound);
				series.add(time + 1, lowerBound);
			}
			collection.addSeries(series);
		}

		// dataset.addSeries("Cases", series);

		String title = "Colorado COVID reproductive rate\nthrough " + CalendarUtils.dayToDate(dayOfData);

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", "R(t)", collection);

		XYPlot plot = chart.getXYPlot();
		ValueAxis yAxis = plot.getRangeAxis();
		yAxis.setLowerBound(0);
		yAxis.setUpperBound(4);

		ValueMarker marker = new ValueMarker(1.0);
		marker.setPaint(Color.black);
		plot.addRangeMarker(marker);

		if (SHOW_EVENTS) {
			Event.addEvents(plot);
		}

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT),
				FOLDER + "\\" + NumbersType.name(types, "-") + ".png");
		if (dayOfData == stats.getLastDay() && types.size() > 1) {
			c.addFileName(Charts.TOP_FOLDER + "\\R.png");
			c.saveAsPNG();
			c.open();
		} else {
			c.saveAsPNG();
		}
		return c;
	}

	/*
	 * public Chart buildReproductiveCharts(Set<NumbersType> types, int
	 * dayOfData) { String by = "R-" + numbers.getType().lowerName + "-" +
	 * numbers.getTiming().lowerName; return buildCasesTimeseriesChart(by,
	 * CalendarUtils.dayToFullDate(dayOfData), dayOfData, dayOfType ->
	 * numbers.getBigR(dayOfData, dayOfType), null, by, "?R?", false, 0, false);
	 * }
	 */

	public void buildReproductiveChart(Set<NumbersType> types) {
		buildReproductiveChart(types, stats.getLastDay());
	}

	public int getFirstDayForAnimation() {
		return Math.max(Charts.getFirstDayForCharts(stats), stats.getVeryFirstDay());
	}

	public void buildReproductiveCharts(Set<NumbersType> types) {
		for (int dayOfData = getFirstDayForAnimation(); dayOfData <= stats.getLastDay(); dayOfData++) {
			buildReproductiveChart(types, dayOfData);
		}
	}
}
