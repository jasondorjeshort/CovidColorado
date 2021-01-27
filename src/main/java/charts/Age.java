package charts;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Set;
import java.util.function.Function;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.ValueAxis;
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
public class Age {

	/*
	 * Charts the age of some incomplete numbers.
	 * 
	 * Each day a new set of numbers are added, each with an associated day in
	 * the past. The most useful of these is onset date (or subtract 5 to get
	 * infection date). So "today" we added 100 new cases, whose onset dates
	 * were distributed across the last 10 days. The average "age" since onset
	 * today can therefore be calculated easily.
	 * 
	 * A caveat of the use of this data, though, is that it may still be
	 * incomplete. An onset date may be a placeholder on the first day, and then
	 * be moved to a more accurate value later. This would mean that initial age
	 * is a placeholder, and the age delta is a trivial part of the age of the
	 * new day. So long as we take rolling averages within the window of change,
	 * this should all work out fine though.
	 */

	final ColoradoStats stats;
	final String FOLDER = Charts.FULL_FOLDER + "\\" + "age";
	private final int DAYS = 14;

	public Age(ColoradoStats stats) {
		this.stats = stats;
		new File(FOLDER).mkdir();
	}

	public void buildChart(Set<NumbersType> types, NumbersTiming timing) {
		TimeSeriesCollection collection = new TimeSeriesCollection();
		StringBuilder title = new StringBuilder();

		boolean multi = (types.size() > 1);

		title.append("Colorado ");
		if (multi) {
			title.append("COVID ages");
		} else {
			for (NumbersType type : types) {
				title.append(type.lowerName);
			}
			title.append(" age");
		}
		title.append(" from ");
		title.append(timing.lowerName);
		title.append(" date as of ");
		title.append(CalendarUtils.dayToDate(stats.getLastDay()));
		title.append("\n(" + DAYS + "-day rolling average)");
		boolean hasData = false;

		double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
		boolean useExact = types.size() == 1;

		for (NumbersType type : NumbersType.values()) {
			if (!types.contains(type)) {
				continue;
			}
			IncompleteNumbers numbers = stats.getNumbers(type, timing);
			if (!numbers.hasData()) {
				continue;
			}
			hasData = true;
			TimeSeries ageSeries = new TimeSeries(type.capName);
			TimeSeries exactSeries = new TimeSeries(type.capName + " (exact)");

			for (int dayOfData = numbers.getFirstDayOfData(); dayOfData <= numbers.getLastDay(); dayOfData++) {
				Day ddd = CalendarUtils.dayToDay(dayOfData);

				double age = numbers.getAverageAgeOfNewNumbers(dayOfData, DAYS);
				if (Double.isFinite(age)) {
					// will be infinite if the sample size is 0
					ageSeries.add(ddd, age);
					min = Math.min(min, age);
					max = Math.max(max, age);
				}

				if (useExact) {
					double exact = numbers.getAverageAgeOfNewNumbers(dayOfData, 1);
					if (Double.isFinite(exact)) {
						exactSeries.add(ddd, exact);
					}
				}

			}

			collection.addSeries(ageSeries);
			if (useExact) {
				collection.addSeries(exactSeries);
			}
		}

		if (!hasData) {
			return;
		}

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", "Age (days)", collection);

		XYPlot plot = chart.getXYPlot();
		ValueAxis yAxis = plot.getRangeAxis();
		// TOOD: maybe???

		if (timing == NumbersTiming.INFECTION) {
			Event.addEvents(plot);
		}

		String fileName = NumbersType.name(types, "-") + "-" + timing.lowerName + ".png";
		String fullFileName = FOLDER + "\\" + fileName;
		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), fullFileName);
		if (false && timing == NumbersTiming.INFECTION && types.size() == 3) {
			c.addFileName(Charts.TOP_FOLDER + "\\" + fileName);
			c.saveAsPNG();
			c.open();
		} else {
			c.saveAsPNG();
		}
	}

}
