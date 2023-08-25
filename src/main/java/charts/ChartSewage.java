package charts;

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

import covid.CalendarUtils;
import covid.NumbersType;
import covid.Smoothing;
import nwss.Sewage;

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
public class ChartSewage {

	public ChartSewage() {
		new File(SEWAGE_FOLDER).mkdir();
	}

	public static final String SEWAGE_FOLDER = Charts.FULL_FOLDER + "\\nwss";

	public BufferedImage buildSewageTimeseriesChart(Sewage sewage, boolean log) {
		Smoothing s0 = Smoothing.NONE;
		Smoothing s1 = Smoothing.AVERAGE_7_DAY;
		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		TimeSeries cSeries0 = new TimeSeries("Sewage");
		sewage.makeTimeSeries(cSeries0, log);
		collection.addSeries(cSeries0);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, NumbersType.CASES.color);
		renderer.setSeriesFillPaint(seriesCount, NumbersType.CASES.color.darker());
		seriesCount++;

		// dataset.addSeries("Cases", series);

		String title = String.format("Plant %d, %s%s\n%s / %s county / %,d line pop", sewage.getPlantId(),
				CalendarUtils.dayToDate(sewage.getLastDay()), (log ? " (logarithmic)" : ""), sewage.getState(),
				sewage.getCounty(), sewage.getPopulation());
		String verticalAxis = "Millions of copies per mL";
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

		String fileName = sewage.getState() + "-" + sewage.getPlantId() + "-" + (log ? "log" : "cart");
		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);

		if (sewage.getPlantId() == 252 || sewage.id.equalsIgnoreCase("United States")) {
			library.OpenImage.openImage(SEWAGE_FOLDER + "//" + fileName + ".png");
		}

		System.out.println("Created : " + sewage.id + " => " + SEWAGE_FOLDER + fileName);
		return image;
	}

	public void createSewage(Sewage sewage) {
		// buildSewageTimeseriesChart(sewage, false);
		buildSewageTimeseriesChart(sewage, true);
	}

}
