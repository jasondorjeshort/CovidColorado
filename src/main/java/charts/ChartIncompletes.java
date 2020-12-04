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

import covid.ColoradoStats;
import covid.Date;
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
public class ChartIncompletes {
	public ChartIncompletes(ColoradoStats stats) {
		this.stats = stats;
	}

	private final ColoradoStats stats;

	private Chart buildChart(String baseName, int dayOfData, Set<NumbersType> types, NumbersTiming timing,
			boolean logarithmic) {
		TimeSeriesCollection collection = new TimeSeriesCollection();
		int incomplete = Integer.MAX_VALUE;
		String verticalAxis = null;
		StringBuilder title = new StringBuilder();

		boolean multi = (types.size() > 1);
		boolean useProjections = (types.size() == 1);
		boolean useExact = (types.size() == 1);

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
		title.append(Date.dayToDate(dayOfData));
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
			String desc;
			if (multi) {
				desc = type.capName + " (" + type.smoothing.description + ")";
			} else {
				desc = type.capName;
			}
			TimeSeries series = new TimeSeries(desc);
			TimeSeries pSeries = new TimeSeries(type.capName + " (projected)");
			TimeSeries exact = new TimeSeries("Exact");
			IncompleteNumbers numbers = stats.getNumbers(type, timing);

			for (int d = stats.getFirstDay(); d <= dayOfData; d++) {
				Day ddd = Date.dayToDay(d);

				double cases = numbers.getNumbers(dayOfData, d, false, type.smoothing);
				if (!logarithmic || cases > 0) {
					series.add(ddd, cases);
				}

				if (useExact) {
					double exactNumbers = numbers.getNumbers(dayOfData, d, false, Smoothing.NONE);
					if (!logarithmic || cases > 0) {
						exact.add(ddd, exactNumbers);
					}
				}

				double projected = numbers.getNumbers(dayOfData, d, true, type.smoothing);
				if (useProjections && (!logarithmic || projected > 0)) {
					pSeries.add(ddd, projected);
				}
				if (cases > 0 && projected / cases > 1.1) {
					incomplete = Math.min(incomplete, d);
				}
			}

			collection.addSeries(series);
			if (useProjections) {
				collection.addSeries(pSeries);
			}
			if (useExact) {
				collection.addSeries(exact);
			}

			if (verticalAxis == null) {
				verticalAxis = type.capName;
			} else {
				verticalAxis = verticalAxis + " / " + type.capName;
			}
		}

		// dataset.addSeries("Cases", series);

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		if (logarithmic) {
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			yAxis.setLowerBound(1);
			yAxis.setUpperBound(200000);
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");

			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay() + 14));

			plot.setDomainAxis(xAxis);

			ValueMarker marker = Charts.getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);
		}

		if (timing == NumbersTiming.INFECTION) {
			Event.addEvents(plot);
		}

		if (incomplete >= stats.getFirstDay() && incomplete <= stats.getLastDay()) {
			plot.addDomainMarker(Charts.getIncompleteMarker(incomplete));
		}

		Chart c = new Chart();
		c.image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		c.fileName = Charts.TOP_FOLDER + "\\" + baseName + "\\" + Date.dayToFullDate(dayOfData, '-') + ".png";
		c.saveAsPNG();
		if (timing == NumbersTiming.INFECTION && types.size() >= 4 && logarithmic && dayOfData == stats.getLastDay()) {
			c.open();
		}
		return c;
	}

	public String buildCharts(Set<NumbersType> types, NumbersTiming timing, boolean logarithmic) {
		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String baseName = NumbersType.name(types, "-") + "-" + timing.lowerName + (logarithmic ? "-log" : "-cart");
		String gifName = Charts.TOP_FOLDER + "\\" + baseName + ".gif";
		new File(Charts.TOP_FOLDER + "\\" + baseName).mkdir();
		gif.start(gifName);
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
			Chart c = buildChart(baseName, dayOfData, types, timing, logarithmic);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(c.image);
		}
		gif.finish();
		return gifName;
	}

	public String buildCharts(NumbersType type, NumbersTiming timing, boolean logarithmic) {
		return buildCharts(NumbersType.getSet(type), timing, logarithmic);
	}

}
