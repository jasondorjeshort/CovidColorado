package colorado;

import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.File;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import charts.Charts;
import covid.CalendarUtils;

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

	public BufferedImage buildCountyTimeseriesChart(CountyStats c, boolean log) {
		Smoothing s0 = Smoothing.NONE;
		Smoothing s1 = Smoothing.AVERAGE_7_DAY;
		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		TimeSeries cSeries0 = new TimeSeries("Cases (" + s0.getDescription() + ")");
		c.getCases().makeTimeSeries(cSeries0, s0, log);
		collection.addSeries(cSeries0);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, NumbersType.CASES.color);
		renderer.setSeriesFillPaint(seriesCount, NumbersType.CASES.color.darker());
		seriesCount++;

		TimeSeries cSeries = new TimeSeries("Cases (" + s1.getDescription() + ")");
		c.getCases().makeTimeSeries(cSeries, s1, log);
		collection.addSeries(cSeries);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, NumbersType.CASES.color);
		renderer.setSeriesFillPaint(seriesCount, NumbersType.CASES.color.darker());
		seriesCount++;

		TimeSeries dSeries = new TimeSeries("Deaths (" + s1.getDescription() + ")");
		c.getDeaths().makeTimeSeries(dSeries, s1, log);
		collection.addSeries(dSeries);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, NumbersType.DEATHS.color);
		renderer.setSeriesFillPaint(seriesCount, NumbersType.DEATHS.color.darker());
		seriesCount++;

		// dataset.addSeries("Cases", series);

		String title = String.format("%s County, %s%s", c.getName(), CalendarUtils.dayToDate(stats.getLastDay()),
				(log ? " (logarithmic)" : ""));
		String verticalAxis = "Daily cases";
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		if (log) {
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
		buildCountyTimeseriesChart(county, false);
		buildCountyTimeseriesChart(county, true);
	}

}
