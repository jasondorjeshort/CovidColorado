package charts;

import java.io.File;
import java.util.Set;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

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
public class ChartIncompletes {
	public ChartIncompletes(ColoradoStats stats) {
		this.stats = stats;
	}

	private final ColoradoStats stats;

	public Chart buildChart(String folder, String fileName, int dayOfData, Set<NumbersType> types, NumbersTiming timing,
			boolean logarithmic) {
		TimeSeriesCollection collection = new TimeSeriesCollection();
		String verticalAxis = null;
		StringBuilder title = new StringBuilder();

		boolean multi = (types.size() > 1);
		int firstDayOfChart = Integer.MAX_VALUE;

		title.append("Colorado ");
		if (multi) {
			title.append("COVID numbers");
		} else {
			for (NumbersType type : types) {
				title.append(type.lowerName);
			}
		}
		title.append(" by ");
		title.append(timing.lowerName);
		title.append(" date as of ");
		title.append(CalendarUtils.dayToDate(dayOfData));
		if (logarithmic || !multi) {
			title.append("\n(");
			if (logarithmic) {
				title.append("logarithmic ");
			}
			if (types.size() == 1) {
				for (NumbersType type : types) {
					title.append(type.lowerName + " ");
				}
			}
			title.delete(title.length() - 1, title.length());
			title.append(")");
		}

		for (NumbersType type : NumbersType.values()) {
			if (!types.contains(type)) {
				continue;
			}
			IncompleteNumbers numbers = stats.getNumbers(type, timing);
			if (!numbers.hasData()) {
				continue;
			}
			TimeSeries lower = new TimeSeries(type.capName + " (lower bound)");
			TimeSeries upper = new TimeSeries(type.capName + " (upper bound)");

			firstDayOfChart = Math.min(firstDayOfChart, numbers.getFirstDayOfType());
			for (int d = numbers.getFirstDayOfType(); d <= dayOfData; d++) {
				Day ddd = CalendarUtils.dayToDay(d);

				double lowerBound = numbers.getNumbers(dayOfData, d, IncompleteNumbers.Form.LOWER, type.smoothing);
				if (!logarithmic || lowerBound > 0) {
					lower.add(ddd, lowerBound);
				}

				double upperBound = numbers.getNumbers(dayOfData, d, IncompleteNumbers.Form.UPPER, type.smoothing);
				if (!logarithmic || upperBound > 0) {
					upper.add(ddd, upperBound);
				}
			}

			collection.addSeries(lower);
			collection.addSeries(upper);

			if (verticalAxis == null) {
				verticalAxis = type.capName;
			} else {
				verticalAxis = verticalAxis + " / " + type.capName;
			}
		}

		if (firstDayOfChart >= stats.getLastDay()) {
			throw new RuntimeException(
					"Chart don't exist: " + types + " ... " + timing + " ... " + CalendarUtils.dayToDate(dayOfData));
		}

		// dataset.addSeries("Cases", series);

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		if (logarithmic) {
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			yAxis.setLowerBound(1);
			yAxis.setUpperBound(NumbersType.getHighest(types));
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");
			xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(firstDayOfChart));
			xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(Charts.getLastDayForChartDisplay(stats)));
			plot.setDomainAxis(xAxis);

			ValueMarker marker = Charts.getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);
		}

		if (timing == NumbersTiming.INFECTION) {
			Event.addEvents(plot);
		}

		if (fileName == null) {
			fileName = CalendarUtils.dayToFullDate(dayOfData, '-');
		}
		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), folder + "\\" + fileName + ".png");
		if (timing == NumbersTiming.INFECTION && types.size() == 3 && logarithmic && dayOfData == stats.getLastDay()) {
			c.addFileName(Charts.TOP_FOLDER + "\\" + NumbersType.name(types, "-") + "-infection"
					+ (logarithmic ? "-log" : "-cart") + ".png");
			c.saveAsPNG();
			c.open();
		} else {
			c.saveAsPNG();

		}
		return c;
	}

	public String buildChart(Set<NumbersType> types, NumbersTiming timing) {
		String fileName = NumbersType.name(types, "-") + "-" + timing.lowerName;
		Chart c = buildChart(Charts.FULL_FOLDER, fileName, stats.getLastDay(), types, timing, true);
		return c.getFileName();
	}

	public int getFirstDayForAnimation(NumbersTiming timing) {
		return Math.max(Charts.getFirstDayForCharts(stats), stats.getFirstDayOfTiming(timing));
	}

	public void buildGIF(Set<NumbersType> types, NumbersTiming timing, boolean logarithmic) {
		boolean hasData = false;
		for (NumbersType type : types) {
			hasData |= stats.getNumbers(type, timing).hasData();
		}
		if (!hasData) {
			return;
		}
		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String name = NumbersType.name(types, "-") + "-" + timing.lowerName + (logarithmic ? "-log" : "-cart");
		String folder = Charts.FULL_FOLDER + "\\" + name;
		String gifName = Charts.FULL_FOLDER + "\\" + name + ".gif";
		new File(folder).mkdir();
		gif.start(gifName);
		for (int dayOfData = getFirstDayForAnimation(timing); dayOfData <= stats.getLastDay(); dayOfData++) {
			Chart c = buildChart(folder, null, dayOfData, types, timing, logarithmic);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(c.getImage());
		}
		gif.finish();
	}

	public void buildGIF(NumbersType type, NumbersTiming timing, boolean logarithmic) {
		buildGIF(NumbersType.getSet(type), timing, logarithmic);
	}

}
