package CovidColorado;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultXYDataset;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import CovidColorado.CovidStats.County;
import library.MyExecutor;

import org.jfree.chart.ui.TextAnchor;

public class ChartMaker {

	int built = 0;

	private static final String TOP_FOLDER = "H:\\CovidColorado";

	private static final double halfLifeRatio = Math.pow(0.5, 1 / 7.0);

	public static class Event {
		public final String name;
		public final long time;

		public Event(String name, String date) {
			this.name = name;
			this.time = Date.dateToTime(date);
		}
	}

	private final BasicStroke stroke = new BasicStroke(2);

	public Event[] events = new Event[] { new Event("SaH", "3-26-2020"), new Event("Bars", "06-30-2020"),
			new Event("Masks", "7-16-2020"), new Event("Snow", "9-9-2020"), new Event("CU/DPS", "8-24-2020"),
			new Event("Intervention", "11-05-2020") };

	public void saveBufferedImageAsPNG(String folder, String name, BufferedImage bufferedImage) {

		new File(folder).mkdir();
		File file = new File(folder + "\\" + name + ".png");

		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
			EncoderUtil.writeBufferedImage(bufferedImage, ImageFormat.PNG, out);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public BufferedImage buildCasesTimeseriesChart(CovidStats stats, String folder, int dayOfData,
			Function<Integer, Double> getCasesForDay, Function<Integer, Double> getProjectedCasesForDay, String by,
			boolean log, boolean showZeroes, boolean showAverage, int daysToSkip, boolean showRollingAverage,
			boolean showEvents) {

		folder = TOP_FOLDER + "\\" + folder;

		DefaultXYDataset dataset = new DefaultXYDataset();
		TimeSeries series = new TimeSeries("Cases");
		TimeSeries projectedSeries = new TimeSeries("Projected");
		int totalCases = 0, totalDays = 0;
		double rollingAverage = 0;
		for (int d = Math.max(showAverage ? dayOfData - 30 : 0, stats.getFirstDay()); d <= dayOfData
				- daysToSkip; d++) {
			double cases = getCasesForDay.apply(d);
			Double projected = null;
			if (getProjectedCasesForDay != null) {
				projected = getProjectedCasesForDay.apply(d);
			}

			if (!Double.isFinite(cases)) {
				continue;
			}

			rollingAverage = rollingAverage * halfLifeRatio + cases * (1 - halfLifeRatio);

			Day ddd = Date.dayToDay(d);
			if (Double.isFinite(cases) && (showZeroes || cases > 0)) {
				series.add(ddd, cases);
				if (getProjectedCasesForDay != null) {
					projectedSeries.add(ddd, projected);
				}
				totalCases += cases;
				totalDays += cases * d;
			}
			if (!log && !showZeroes) {
				// System.out.println("Cases " + cases + " on day " + ddd);
			}
		}

		// dataset.addSeries("Cases", series);

		double averageAge = dayOfData - (double) totalDays / totalCases;
		TimeSeriesCollection collection = new TimeSeriesCollection();
		collection.addSeries(series);
		if (getProjectedCasesForDay != null) {
			collection.addSeries(projectedSeries);
		}
		JFreeChart chart = ChartFactory.createTimeSeriesChart(
				"Colorado cases (" + totalCases + ") by " + by + " date "
						+ (showAverage ? String.format("(avg age: %.02f) ", averageAge) : "") + "as of "
						+ Date.dayToDate(dayOfData) + (log ? " (logarithmic)" : ""),
				"Date", "Cases.", collection, false, false, false);

		if (log) {
			XYPlot plot = chart.getXYPlot();
			LogarithmicAxis yAxis = new LogarithmicAxis("Cases");
			if (showEvents) {
				yAxis.setLowerBound(1);
				yAxis.setUpperBound(10000);
			}
			plot.setRangeAxis(yAxis);

			DateAxis xAxis = new DateAxis("Date");

			if (showEvents) {
				xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
				xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay() + 14));
			}

			plot.setDomainAxis(xAxis);

			Font font = new Font("normal", 0, 12);

			ValueMarker marker = new ValueMarker(Date.dayToJavaDate(dayOfData).getTime());
			marker.setPaint(Color.black);
			marker.setLabel("Today");
			marker.setLabelFont(font);
			marker.setStroke(stroke);
			marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
			plot.addDomainMarker(marker);

			if (showEvents) {
				for (Event event : events) {
					marker = new ValueMarker(event.time);
					marker.setPaint(Color.green);
					marker.setLabel(event.name);
					marker.setStroke(stroke);
					marker.setLabelFont(font);
					marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
					plot.addDomainMarker(marker);
				}
			}

		}

		BufferedImage image = chart.createBufferedImage(WIDTH, HEIGHT);
		String name = by + "-" + (log ? "log-" : "cart-") + Date.dayToFullDate(dayOfData, '-');
		saveBufferedImageAsPNG(folder, name, image);
		return image;
	}

	public BufferedImage buildOnsetDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, "onset-" + (log ? "log" : "cart"), dayOfData,
				dayOfOnset -> (double) stats.getCasesByOnsetDay(dayOfData, dayOfOnset),
				dayOfOnset -> (double) stats.getProjectedCasesByOnsetDay(dayOfData, dayOfOnset),

				"onset", log, !log, false, 0, false, false);
	}

	public BufferedImage buildInfectionDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, "infection-" + (log ? "log" : "cart"), dayOfData,
				dayOfInfection -> stats.getCasesByInfectionDay(dayOfData, dayOfInfection),
				dayOfInfection -> stats.getProjectedCasesByInfectionDay(dayOfData, dayOfInfection), "infection", log,
				!log, false, 5, false, true);
	}

	public BufferedImage buildReportedDayTimeseriesChart(CovidStats stats, int dayOfData, boolean log) {
		return buildCasesTimeseriesChart(stats, "reported-" + (log ? "log" : "cart"), dayOfData,
				dayOfReporting -> (double) stats.getCasesByReportedDay(dayOfData, dayOfReporting),
				dayOfReporting -> (double) stats.getExactProjectedCasesByReportedDay(dayOfData, dayOfReporting),
				"reported", log, !log, false, 1, false, false);
	}

	public BufferedImage buildNewInfectionDayTimeseriesChart(CovidStats stats, int dayOfData) {
		return buildCasesTimeseriesChart(stats, "new-infection", dayOfData,
				dayOfOnset -> stats.getNewCasesByInfectionDay(dayOfData, dayOfOnset), null, "today's cases infection",
				false, false, true, 0, false, false);
	}

	public static final int WIDTH = 800, HEIGHT = 600;

	// this completely doesn't work.
	public BufferedImage buildOnsetReportedDayTimeseriesChart(CovidStats stats, int dayOfOnset) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		String folder = TOP_FOLDER + "\\onset_reported";
		new File(folder).mkdir();

		int range = 5, fullRange = range * 2 + 1;

		for (int daysToReporting = 0; daysToReporting < 60; daysToReporting++) {
			int dayOfData = dayOfOnset + daysToReporting;

			int cases = stats.getNewCasesByOnsetDay(dayOfData, dayOfOnset);
			double smoothed = 0;
			for (int i = -range; i <= range; i++) {
				smoothed += stats.getNewCasesByOnsetDay(dayOfData + i, dayOfOnset);
			}
			smoothed /= fullRange;

			dataset.addValue(cases, "cases", Integer.valueOf(daysToReporting));
			dataset.addValue(smoothed, "smoothed (" + fullRange + " days symmetric average)",
					Integer.valueOf(daysToReporting));
		}

		JFreeChart chart = ChartFactory.createLineChart(
				"Days from onset (" + Date.dayToFullDate(dayOfOnset, '-') + ") to test reporting", "Days", "Cases",
				dataset, PlotOrientation.VERTICAL, true, true, false);

		BufferedImage image = chart.createBufferedImage(WIDTH, HEIGHT);
		String name = "days_from_onset_to_reporting-";
		saveBufferedImageAsPNG(folder, name, image);
		return image;
	}

	public BufferedImage buildCaseAgeTimeseriesChart(CovidStats stats, int dayOfData) {
		return buildCasesTimeseriesChart(stats, "case-age", dayOfData,
				dayOfCases -> stats.getAverageAgeOfNewCases(dayOfCases), null, "age", false, true, false, 0, true,
				false);
	}

	private ArrayList<Future<BufferedImage>> infectionLog = new ArrayList<>();

	public String buildGIFs() {

		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String name = TOP_FOLDER + "\\infection-log.gif";
		gif.start(name);
		gif.setDelay(40);
		for (Future<BufferedImage> fbi : infectionLog) {
			BufferedImage bufferedImage;
			try {
				bufferedImage = fbi.get();
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			gif.addFrame(bufferedImage);
		}
		gif.finish();
		System.out.println("Wrote gif!");
		return name;
	}

	public void createCountyStats(CovidStats stats, County county, int dayOfData) {
		buildCasesTimeseriesChart(stats, "county-stats-14days", dayOfData,
				dayOfCases -> Double.valueOf(county.getCases(dayOfCases) - county.getCases(dayOfCases - 14)), null,
				county.name.replaceAll(" ", "_"), false, true, false, 0, true, false);
		buildCasesTimeseriesChart(stats, "county-stats-14days", dayOfData,
				dayOfCases -> Double
						.valueOf(Math.max(county.getCases(dayOfCases) - county.getCases(dayOfCases - 14), 1)),
				null, county.name.replaceAll(" ", "_"), true, true, false, 0, true, false);

	}

	public String buildCharts(CovidStats stats) {
		new File(TOP_FOLDER).mkdir();

		stats.getCounties().forEach(
				(key, value) -> MyExecutor.executeCode(() -> createCountyStats(stats, value, stats.getLastDay())));

		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
			int _dayOfData = dayOfData;

			MyExecutor.submitCode(() -> buildOnsetDayTimeseriesChart(stats, _dayOfData, false));
			MyExecutor.submitCode(() -> buildOnsetDayTimeseriesChart(stats, _dayOfData, true));

			MyExecutor.submitCode(() -> buildNewInfectionDayTimeseriesChart(stats, _dayOfData));

			MyExecutor.submitCode(() -> buildReportedDayTimeseriesChart(stats, _dayOfData, true));
			MyExecutor.submitCode(() -> buildReportedDayTimeseriesChart(stats, _dayOfData, false));

			MyExecutor.submitCode(() -> buildInfectionDayTimeseriesChart(stats, _dayOfData, false));
			infectionLog.add(MyExecutor.submitCode(() -> buildInfectionDayTimeseriesChart(stats, _dayOfData, true)));

			int dayOfOnset = dayOfData; // names...
			if (stats.getCasesByOnsetDay(stats.getLastDay(), dayOfOnset) > 0) {
				MyExecutor.submitCode(() -> buildOnsetReportedDayTimeseriesChart(stats, dayOfOnset));
			}

			MyExecutor.submitCode(() -> buildCaseAgeTimeseriesChart(stats, _dayOfData));
		}

		return buildGIFs();

	}
}
