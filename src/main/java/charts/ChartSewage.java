package charts;

import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Objects;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import covid.CalendarUtils;
import covid.NumbersType;
import nwss.Sewage;
import nwss.Sewage.Type;

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

	public static final String SEWAGE_FOLDER = Charts.FULL_FOLDER + "\\nwss";
	public static final String PLANTS = "plants";
	public static final String STATES = "states";
	public static final String COUNTIES = "counties";
	public static final String STATES_FOLDER = SEWAGE_FOLDER + "\\" + STATES;
	public static final String COUNTIES_FOLDER = SEWAGE_FOLDER + "\\" + COUNTIES;
	public static final String PLANT_FOLDER = SEWAGE_FOLDER + "\\" + PLANTS;

	public static void mkdirs() {
		new File(Charts.FULL_FOLDER).mkdir();
		new File(SEWAGE_FOLDER).mkdir();
		new File(PLANT_FOLDER).mkdir();
		new File(STATES_FOLDER).mkdir();
		new File(COUNTIES_FOLDER).mkdir();
	}

	public static void reportState(String state) {
		new File(COUNTIES_FOLDER + "\\" + state).mkdir();
	}

	public static BufferedImage buildSewageTimeseriesChart(Sewage sewage, boolean log) {
		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		TimeSeries series = new TimeSeries("Sewage");
		sewage.makeTimeSeries(series, log);
		collection.addSeries(series);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, NumbersType.CASES.color);
		renderer.setSeriesFillPaint(seriesCount, NumbersType.CASES.color.darker());
		seriesCount++;

		// dataset.addSeries("Cases", series);

		String fileName = sewage.id;
		String title;
		switch (sewage.type) {
		case COUNTRY:
			title = String.format("%s, %s\n %,d line pop", sewage.id, CalendarUtils.dayToDate(sewage.getLastDay()),
					sewage.getPopulation());
			break;
		case COUNTY:
			title = String.format("%s county, %s\n%s / %,d line pop", sewage.getCounty(), sewage.getState(),
					CalendarUtils.dayToDate(sewage.getLastDay()), sewage.getPopulation());
			fileName = COUNTIES + "\\" + sewage.getState() + "\\" + sewage.getCounty();
			break;
		case PLANT:
			title = String.format("Plant %d, %s\n%s / %s county / %,d line pop", sewage.getPlantId(),
					CalendarUtils.dayToDate(sewage.getLastDay()), sewage.getState(), sewage.getCounty(),
					sewage.getPopulation());
			fileName = PLANTS + "\\" + sewage.id;
			break;
		case STATE:
			title = String.format("%s through %s\n%,d line pop", sewage.getState(),
					CalendarUtils.dayToDate(sewage.getLastDay()), sewage.getPopulation(), sewage.getState());
			fileName = STATES + "\\" + sewage.getState();
			break;
		default:
			title = null;
			break;
		}
		fileName += "-" + (log ? "log" : "cart");
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

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);

		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		if (sewage.id.equalsIgnoreCase("Colorado-Denver") || sewage.id.equalsIgnoreCase("Colorado")
				|| Objects.equals(sewage.plantId, 251) || Objects.equals(sewage.plantId, 252)
				|| sewage.type.equals(Type.COUNTRY)) {
			library.OpenImage.openImage(fileName);
		}

		// System.out.println("Created : " + sewage.id + " for " +
		// series.getItemCount() + " => " + fileName);
		return image;
	}

	public static void createSewage(Sewage sewage) {
		// buildSewageTimeseriesChart(sewage, false);
		buildSewageTimeseriesChart(sewage, true);
	}

}
