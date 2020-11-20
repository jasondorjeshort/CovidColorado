package covid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import library.MyExecutor;

public class ChartMaker {

	int built = 0;

	private static final String TOP_FOLDER = "H:\\CovidColorado";

	private static final double halfLifeRatio = Math.pow(0.5, 1 / 7.0);

	private final BasicStroke stroke = new BasicStroke(2);
	private final Font font = new Font("normal", 0, 12);
	private final ColoradoStats stats;

	public ChartMaker(ColoradoStats stats) {
		this.stats = stats;
	}

	public static void saveBufferedImageAsPNG(String folder, String name, BufferedImage bufferedImage) {

		new File(folder).mkdir();
		File file = new File(folder + "\\" + name + ".png");

		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
			EncoderUtil.writeBufferedImage(bufferedImage, ImageFormat.PNG, out);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private ValueMarker getTodayMarker(int dayOfData) {
		ValueMarker marker = new ValueMarker(Date.dayToJavaDate(dayOfData).getTime());
		marker.setPaint(Color.black);
		marker.setLabel("Today");
		marker.setLabelFont(font);
		marker.setStroke(stroke);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		return marker;
	}

	public void addEvents(XYPlot plot) {
		for (Event event : Event.events) {
			ValueMarker marker = new ValueMarker(event.time);
			marker.setPaint(Color.green);
			marker.setLabel(event.name);
			marker.setStroke(stroke);
			marker.setLabelFont(font);
			marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
			plot.addDomainMarker(marker);
		}
	}

	public BufferedImage buildCasesTimeseriesChart(String folder, int dayOfData,
			Function<Integer, Double> getCasesForDay, Function<Integer, Double> getProjectedCasesForDay, String by,
			boolean log, boolean showZeroes, boolean showAverage, int daysToSkip, boolean showEvents) {

		folder = TOP_FOLDER + "\\" + folder;

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
		JFreeChart chart = ChartFactory.createTimeSeriesChart("Colorado cases (" + totalCases + ") by " + by + " date "
				+ (showAverage ? String.format("(avg age: %.02f) ", averageAge) : "") + "as of "
				+ Date.dayToDate(dayOfData) + (log ? " (logarithmic)" : ""), "Date", "Cases.", collection);

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

			ValueMarker marker = getTodayMarker(dayOfData);
			plot.addDomainMarker(marker);

			if (showEvents) {
				addEvents(plot);
			}

		}

		BufferedImage image = chart.createBufferedImage(WIDTH, HEIGHT);
		String name = by + "-" + (log ? "log-" : "cart-") + Date.dayToFullDate(dayOfData, '-');
		saveBufferedImageAsPNG(folder, name, image);
		return image;
	}

	public BufferedImage buildOnsetDayTimeseriesChart(int dayOfData, boolean log) {
		return buildCasesTimeseriesChart("onset-" + (log ? "log" : "cart"), dayOfData,
				dayOfOnset -> (double) stats.getCasesByType(CaseType.ONSET_TESTS, dayOfData, dayOfOnset),
				dayOfOnset -> stats.getSmoothedProjectedCasesByType(CaseType.ONSET_TESTS, dayOfData, dayOfOnset),

				"onset", log, !log, false, 0, false);
	}

	public BufferedImage buildRates(int dayOfData, String fileName, String title, boolean useCFR, boolean useCHR,
			boolean useHFR, boolean usePositivity, Integer age, Integer fixedHeight) {

		int INTERVAL = 7;

		TimeSeries cfr = new TimeSeries("CFR (deaths / cases)");
		TimeSeries chr = new TimeSeries("CHR (hospitalizations / cases)");
		TimeSeries hfr = new TimeSeries("HFR (deaths / hospitalizations)");
		TimeSeries pos = new TimeSeries("Positivity (daily numbers, not by infection date)");

		for (int dayOfInfection = (age == null || fixedHeight != null ? 0
				: stats.getLastDay() - age); dayOfInfection <= stats.getLastDay(); dayOfInfection++) {
			double cases = stats.getCasesInInterval(CaseType.INFECTION_TESTS, dayOfData, dayOfInfection, INTERVAL);
			double hosp = stats.getCasesInInterval(CaseType.INFECTION_HOSP, dayOfData, dayOfInfection, INTERVAL);
			double deaths = stats.getCasesInInterval(CaseType.INFECTION_DEATH, dayOfData, dayOfInfection, INTERVAL);

			if (!Double.isFinite(cases) || cases == 0) {
				continue;
			}

			Day ddd = Date.dayToDay(dayOfInfection);

			if (Double.isFinite(cases) && cases > 0) {
				cfr.add(ddd, 100.0 * deaths / cases);
			}
			if (Double.isFinite(cases) && cases > 0) {
				chr.add(ddd, 100.0 * hosp / cases);
			}
			if (Double.isFinite(hosp) && hosp > 0) {
				hfr.add(ddd, 100.0 * deaths / hosp);
			}

			int dayOfNumbers = dayOfInfection;
			pos.add(ddd, 100 * stats.getPositivity(dayOfNumbers, 1));
		}

		TimeSeriesCollection collection = new TimeSeriesCollection();
		if (useCFR) {
			collection.addSeries(cfr);
		}
		if (useHFR) {
			collection.addSeries(hfr);
		}
		if (useCHR) {
			collection.addSeries(chr);
		}
		if (usePositivity) {
			collection.addSeries(pos);
		}

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title + "\n(" + INTERVAL + "-day running average)",
				"Date of Infection", "Rate (%)", collection);

		// chart.getXYPlot().setRangeAxis(new LogarithmicAxis("Cases"));

		XYPlot plot = chart.getXYPlot();
		IntervalMarker marker = new IntervalMarker(Date.dayToTime(dayOfData - 35),
				Date.dayToTime(stats.getLastDay() + 30));
		marker.setPaint(Color.black);
		// marker.setLabel("Incomplete");
		// marker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
		marker.setStroke(stroke);
		marker.setAlpha(0.25f);
		marker.setLabelFont(font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		plot.addDomainMarker(marker);

		if (fixedHeight != null) {
			DateAxis xAxis = (DateAxis) plot.getDomainAxis();
			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay()));

			ValueAxis yAxis = plot.getRangeAxis();
			yAxis.setLowerBound(0);
			yAxis.setUpperBound(fixedHeight);

			plot.addDomainMarker(getTodayMarker(dayOfData));

			addEvents(plot);
		}

		BufferedImage image = chart.createBufferedImage(WIDTH, HEIGHT);
		saveBufferedImageAsPNG(TOP_FOLDER + "\\rates", fileName, image);
		return image;
	}

	public BufferedImage buildInfectionDayTimeseriesChart(int dayOfData, boolean log) {
		return buildCasesTimeseriesChart("infection-" + (log ? "log" : "cart"), dayOfData,
				dayOfInfection -> (double) stats.getCasesByType(CaseType.INFECTION_TESTS, dayOfData, dayOfInfection),
				dayOfInfection -> stats.getSmoothedProjectedCasesByType(CaseType.INFECTION_TESTS, dayOfData,
						dayOfInfection),
				"infection", log, !log, false, 5, true);
	}

	public BufferedImage buildReportedDayTimeseriesChart(int dayOfData, boolean log) {
		return buildCasesTimeseriesChart("reported-" + (log ? "log" : "cart"), dayOfData,
				dayOfReporting -> (double) stats.getCasesByType(CaseType.REPORTED_TESTS, dayOfData, dayOfReporting),
				dayOfReporting -> stats.getExactProjectedCasesByType(CaseType.REPORTED_TESTS, dayOfData,
						dayOfReporting),
				"reported", log, !log, false, 1, false);
	}

	public BufferedImage buildNewInfectionDayTimeseriesChart(int dayOfData) {
		return buildCasesTimeseriesChart("new-infection", dayOfData,
				dayOfOnset -> (double) stats.getNewCasesByType(CaseType.INFECTION_TESTS, dayOfData, dayOfOnset), null,
				"today's cases infection", false, false, true, 0, false);
	}

	public static final int WIDTH = 800, HEIGHT = 600;

	// this completely doesn't work.
	public static BufferedImage buildOnsetReportedDayTimeseriesChart(ColoradoStats stats, int dayOfOnset) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		String folder = TOP_FOLDER + "\\onset_reported";
		new File(folder).mkdir();

		int range = 5, fullRange = range * 2 + 1;

		for (int daysToReporting = 0; daysToReporting < 60; daysToReporting++) {
			int dayOfData = dayOfOnset + daysToReporting;

			int cases = stats.getNewCasesByType(CaseType.ONSET_TESTS, dayOfData, dayOfOnset);
			double smoothed = 0;
			for (int i = -range; i <= range; i++) {
				smoothed += stats.getNewCasesByType(CaseType.ONSET_TESTS, dayOfData + i, dayOfOnset);
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

	public BufferedImage buildCaseAgeTimeseriesChart(int dayOfData) {
		return buildCasesTimeseriesChart("case-age", dayOfData,
				dayOfCases -> stats.getAverageAgeOfNewCases(CaseType.INFECTION_TESTS, dayOfCases), null, "age", false,
				true, false, 0, false);
	}

	private LinkedList<Future<BufferedImage>> infectionLog = new LinkedList<>();
	private LinkedList<Future<BufferedImage>> infectionLog14 = new LinkedList<>();
	private LinkedList<Future<BufferedImage>> cfrBI = new LinkedList<>();
	private LinkedList<Future<BufferedImage>> chrBI = new LinkedList<>();
	private LinkedList<Future<BufferedImage>> hfrBI = new LinkedList<>();
	private LinkedList<Future<BufferedImage>> rates = new LinkedList<>();

	public static String buildGIF(List<Future<BufferedImage>> images, String fileName, int delay) {

		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String name = TOP_FOLDER + "\\" + fileName + ".gif";
		gif.start(name);
		gif.setDelay(delay);

		for (Iterator<Future<BufferedImage>> it = images.iterator(); it.hasNext();) {
			Future<BufferedImage> fbi = it.next();
			if (!it.hasNext()) {
				delay *= 20;
				gif.setDelay(delay);
			}
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
		return name;
	}

	public void createCountyStats(CountyStats county, int dayOfData) {
		buildCasesTimeseriesChart("county", dayOfData,
				dayOfCases -> Double.valueOf(county.getCases().getCasesInInterval(dayOfCases, 14)),
				dayOfCases -> Double.valueOf(county.getDeaths().getCasesInInterval(dayOfCases, 14)),
				county.getDisplayName(), false, true, false, 0, false);
		buildCasesTimeseriesChart("county", dayOfData,
				dayOfCases -> Double.valueOf(Math.max(county.getCases().getCasesInInterval(dayOfCases, 14), 1)),
				dayOfCases -> Double.valueOf(Math.max(county.getDeaths().getCasesInInterval(dayOfCases, 14), 1)),
				county.getDisplayName(), true, true, false, 0, false);
	}

	public void createCumulativeStats() {
		buildCasesTimeseriesChart("cumulative", stats.getLastDay(),
				dayOfCases -> (double) stats.totalCases.getCases(dayOfCases), null, "cases", false, true, false, 0,
				false);
		buildCasesTimeseriesChart("cumulative", stats.getLastDay(),
				dayOfCases -> (double) stats.totalHospitalizations.getCases(dayOfCases), null, "hospitalizations",
				false, true, false, 0, false);
		buildCasesTimeseriesChart("cumulative", stats.getLastDay(),
				dayOfCases -> (double) stats.totalDeaths.getCases(dayOfCases), null, "deathsLB", false, true, false, 0,
				false);
		buildCasesTimeseriesChart("cumulative", stats.getLastDay(),
				dayOfCases -> (double) stats.totalDeathsPUI.getCases(dayOfCases), null, "deathsUB", false, true, false,
				0, false);
		buildCasesTimeseriesChart("cumulative", stats.getLastDay(),
				dayOfCases -> (double) stats.peopleTested.getCases(dayOfCases), null, "peopleTested", false, true,
				false, 0, false);
		buildCasesTimeseriesChart("cumulative", stats.getLastDay(),
				dayOfCases -> (double) stats.testEncounters.getCases(dayOfCases), null, "testEncounters", false, true,
				false, 0, false);

	}

	public String buildCharts() {
		new File(TOP_FOLDER).mkdir();

		stats.getCounties()
				.forEach((key, value) -> MyExecutor.executeCode(() -> createCountyStats(value, stats.getLastDay())));
		Future<BufferedImage> fbi;

		MyExecutor.executeCode(() -> buildRates(stats.getLastDay(), "Positivity",
				"Colorado positivity, " + Date.dayToDate(stats.getLastDay()), false, false, false, true, 180, 20));

		MyExecutor.executeCode(() -> createCumulativeStats());

		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
			int _dayOfData = dayOfData;

			String day = Date.dayToDate(dayOfData);
			String full = Date.dayToFullDate(dayOfData, '-');
			fbi = MyExecutor.submitCode(() -> buildRates(_dayOfData, "rates-" + full,
					"Colorado rates by day of infection, " + day, true, true, true, true, null, 50));
			if (dayOfData > stats.getLastDay() - 60) {
				rates.add(fbi);
			}
			fbi = MyExecutor.submitCode(() -> buildRates(_dayOfData, "CFR-" + full,
					"Colorado case fatality rate, " + day, true, false, false, false, 180, 10));
			if (dayOfData > stats.getLastDay() - 60) {
				cfrBI.add(fbi);
			}
			fbi = MyExecutor.submitCode(() -> buildRates(_dayOfData, "CHR-" + full,
					"Colorado case hospitalization rate, " + day, false, true, false, false, 180, 50));
			if (dayOfData > stats.getLastDay() - 60) {
				chrBI.add(fbi);
			}
			fbi = MyExecutor.submitCode(() -> buildRates(_dayOfData, "HFR-" + full,
					"Colorado hospitalization fatality rate, " + day, false, false, true, false, 180, 50));
			if (dayOfData > stats.getLastDay() - 60) {
				hfrBI.add(fbi);
			}

			MyExecutor.executeCode(() -> buildOnsetDayTimeseriesChart(_dayOfData, false));
			MyExecutor.executeCode(() -> buildOnsetDayTimeseriesChart(_dayOfData, true));

			MyExecutor.executeCode(() -> buildNewInfectionDayTimeseriesChart(_dayOfData));

			MyExecutor.executeCode(() -> buildReportedDayTimeseriesChart(_dayOfData, true));
			MyExecutor.executeCode(() -> buildReportedDayTimeseriesChart(_dayOfData, false));

			MyExecutor.executeCode(() -> buildInfectionDayTimeseriesChart(_dayOfData, false));
			fbi = MyExecutor.submitCode(() -> buildInfectionDayTimeseriesChart(_dayOfData, true));
			infectionLog.add(fbi);
			if (dayOfData > stats.getLastDay() - 14) {
				infectionLog14.add(fbi);
			}

			int dayOfOnset = dayOfData; // names...
			if (stats.getCasesByType(CaseType.ONSET_TESTS, stats.getLastDay(), dayOfOnset) > 0) {
				MyExecutor.executeCode(() -> buildOnsetReportedDayTimeseriesChart(stats, dayOfOnset));
			}

			MyExecutor.executeCode(() -> buildCaseAgeTimeseriesChart(_dayOfData));
		}

		MyExecutor.executeCode(() -> buildGIF(infectionLog, "infection-log", 40));
		MyExecutor.executeCode(() -> buildGIF(infectionLog14, "infection-log-14days", 200));
		MyExecutor.executeCode(() -> buildGIF(hfrBI, "hfr", 200));
		MyExecutor.executeCode(() -> buildGIF(chrBI, "chr", 200));
		MyExecutor.executeCode(() -> buildGIF(cfrBI, "cfr", 200));
		return buildGIF(rates, "rates", 200);
	}
}
