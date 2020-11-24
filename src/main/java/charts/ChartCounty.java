package charts;

import java.awt.image.BufferedImage;
import java.io.File;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import covid.ColoradoStats;
import covid.CountyStats;
import covid.Date;
import covid.Smoothing;

public class ChartCounty {
	public ChartCounty(ColoradoStats stats) {
		this.stats = stats;
	}

	private final ColoradoStats stats;

	public BufferedImage buildCountyTimeseriesChart(CountyStats c, final String folder, boolean log) {

		TimeSeries cSeries = new TimeSeries("Cases");
		TimeSeries dSeries = new TimeSeries("Deaths");
		for (int day = stats.getFirstDay(); day <= stats.getLastDay(); day++) {
			Day ddd = Date.dayToDay(day);

			double cases = c.getCases().getNumbers(day, Smoothing.TOTAL_14_DAY);
			double deaths = c.getDeaths().getNumbers(day, Smoothing.TOTAL_14_DAY);

			if (!Double.isFinite(cases)) {
				throw new RuntimeException("Uh oh.");
			}
			if (!Double.isFinite(deaths)) {
				throw new RuntimeException("Uh oh.");
			}
			if (!log || cases > 0) {
				cSeries.add(ddd, cases);
			}

			if (!log || deaths > 0) {
				dSeries.add(ddd, deaths);
			}
		}

		// dataset.addSeries("Cases", series);

		TimeSeriesCollection collection = new TimeSeriesCollection();
		collection.addSeries(cSeries);
		collection.addSeries(dSeries);
		String title = c.getName() + " County, " + Date.dayToDate(stats.getLastDay()) + "\n(14-day totals"
				+ (log ? ", logarithmic" : "") + ")";
		String verticalAxis = "14-day totals";
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		if (log) {
			XYPlot plot = chart.getXYPlot();
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");
			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay()));
			plot.setDomainAxis(xAxis);
		}

		String fileName = c.getName() + "-" + (log ? "log" : "cart");
		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(folder, fileName, image);
		return image;
	}

	public void createCountyStats(CountyStats county) {
		System.out.println("Creating stats for " + county.getName());
		String folder = Charts.TOP_FOLDER + "\\county";
		new File(folder).mkdir();
		buildCountyTimeseriesChart(county, folder, false);
		buildCountyTimeseriesChart(county, folder, true);
	}

}
