package charts;

import java.awt.image.BufferedImage;
import java.io.File;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import covid.CalendarUtils;
import covid.ColoradoStats;
import covid.CountyStats;
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
public class ChartCounty {
	public ChartCounty(ColoradoStats stats) {
		this.stats = stats;
		new File(COUNTY_FOLDER).mkdir();
	}

	private final ColoradoStats stats;

	public static final String COUNTY_FOLDER = Charts.FULL_FOLDER + "\\county";

	public BufferedImage buildCountyTimeseriesChart(CountyStats c, boolean log, Smoothing smoothing) {

		TimeSeries cSeries = new TimeSeries("Cases"), dSeries = new TimeSeries("Deaths");
		c.getCases().makeTimeSeries(cSeries, smoothing, log);
		c.getDeaths().makeTimeSeries(dSeries, smoothing, log);

		// dataset.addSeries("Cases", series);

		TimeSeriesCollection collection = new TimeSeriesCollection();
		collection.addSeries(cSeries);
		collection.addSeries(dSeries);
		String title = String.format("%s County, %s\n(%s%s)", c.getName(), CalendarUtils.dayToDate(stats.getLastDay()),
				smoothing.getDescription(), (log ? ", logarithmic" : ""));
		String verticalAxis = smoothing.getDescription();
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		if (log) {
			XYPlot plot = chart.getXYPlot();
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			plot.setRangeAxis(yAxis);

			// DateAxis xAxis = new DateAxis("Date");
			// xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(stats.getFirstDayOfCumulative()));
			// xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(stats.getLastDay()));
			// plot.setDomainAxis(xAxis);
		}

		String fileName = c.getName() + "-" + (log ? "log" : "cart");
		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(COUNTY_FOLDER, fileName, image);
		return image;
	}

	public void createCountyStats(CountyStats county) {
		buildCountyTimeseriesChart(county, false, Smoothing.TOTAL_7_DAY);
		buildCountyTimeseriesChart(county, true, Smoothing.TOTAL_7_DAY);
	}

}
