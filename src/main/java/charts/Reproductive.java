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
	private static final boolean SHOW_EVENTS = false;

	private Chart buildReproductiveChart(Set<NumbersType> types, int dayOfData) {

		TimeSeriesCollection collection = new TimeSeriesCollection();

		for (NumbersType type : NumbersType.values()) {
			if (!types.contains(type)) {
				continue;
			}

			IncompleteNumbers numbers = stats.getNumbers(type, TIMING);
			if (!numbers.hasData()) {
				throw new RuntimeException("UH OH");
			}

			TimeSeries series = new TimeSeries("Based on " + type.capName);

			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= dayOfData; dayOfType++) {
				Day ddd = CalendarUtils.dayToDay(dayOfType);
				if (ddd == null) {
					new Exception("Uh oh: " + dayOfType).printStackTrace();
					continue;
				}

				Double R = numbers.getBigR(dayOfData, dayOfType);
				if (R == null || !Double.isFinite(R)) {
					continue;
				}

				series.add(ddd, R);
			}
			collection.addSeries(series);
		}

		// dataset.addSeries("Cases", series);

		String title = "Colorado COVID reproductive rate\nthrough " + CalendarUtils.dayToDate(dayOfData);

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", "R(t)", collection);

		XYPlot plot = chart.getXYPlot();
		/*
		 * LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
		 * yAxis.setLowerBound(1); yAxis.setUpperBound(100000);
		 * plot.setRangeAxis(yAxis);
		 * 
		 * DateAxis xAxis = new DateAxis("Date");
		 * 
		 * xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(stats.
		 * getVeryFirstDay()));
		 * xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(stats.getLastDay() +
		 * 14));
		 * 
		 * plot.setDomainAxis(xAxis);
		 * 
		 * ValueMarker marker = Charts.getTodayMarker(dayOfData);
		 * plot.addDomainMarker(marker);
		 */

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
