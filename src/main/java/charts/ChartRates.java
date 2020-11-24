package charts;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import covid.ColoradoStats;
import covid.Date;
import covid.Event;
import covid.NumbersTiming;
import covid.NumbersType;

public class ChartRates {

	private static BufferedImage buildRates(ColoradoStats stats, int dayOfData, String fileName, String title,
			boolean useCFR, boolean useCHR, boolean useHFR, boolean usePositivity, Integer age, Integer fixedHeight) {

		int INTERVAL = 7;

		TimeSeries cfr = new TimeSeries("CFR (deaths / cases)");
		TimeSeries cfrProjected = new TimeSeries("CFR (projected)");
		TimeSeries chr = new TimeSeries("CHR (hospitalizations / cases)");
		TimeSeries chrProjected = new TimeSeries("CHR (projected)");
		TimeSeries hfr = new TimeSeries("HFR (deaths / hospitalizations)");
		TimeSeries hfrProjected = new TimeSeries("HFR (projected)");
		TimeSeries pos = new TimeSeries("Positivity");
		TimeSeries posProjected = new TimeSeries("Positivity (projected)");

		for (int dayOfInfection = (age == null || fixedHeight != null ? 0
				: stats.getLastDay() - age); dayOfInfection <= stats.getLastDay(); dayOfInfection++) {
			double cases = stats.getCasesInInterval(NumbersType.CASES, NumbersTiming.INFECTION, dayOfData,
					dayOfInfection, INTERVAL);
			double hosp = stats.getCasesInInterval(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION, dayOfData,
					dayOfInfection, INTERVAL);
			double deaths = stats.getCasesInInterval(NumbersType.DEATHS, NumbersTiming.INFECTION, dayOfData,
					dayOfInfection, INTERVAL);
			double casesProjected = stats.getProjectedCasesInInterval(NumbersType.CASES, NumbersTiming.INFECTION,
					dayOfData, dayOfInfection, INTERVAL);
			double hospProjected = stats.getProjectedCasesInInterval(NumbersType.HOSPITALIZATIONS,
					NumbersTiming.INFECTION, dayOfData, dayOfInfection, INTERVAL);
			double deathsProjected = stats.getProjectedCasesInInterval(NumbersType.DEATHS, NumbersTiming.INFECTION,
					dayOfData, dayOfInfection, INTERVAL);
			double tests = stats.getCasesInInterval(NumbersType.TESTS, NumbersTiming.INFECTION, dayOfData,
					dayOfInfection, INTERVAL);
			double testsProjected = stats.getCasesInInterval(NumbersType.TESTS, NumbersTiming.INFECTION, dayOfData,
					dayOfInfection, INTERVAL);

			if (!Double.isFinite(cases) || cases == 0) {
				continue;
			}

			Day ddd = Date.dayToDay(dayOfInfection);

			if (Double.isFinite(cases) && cases > 0) {
				cfr.add(ddd, 100.0 * deaths / cases);
			}
			if (Double.isFinite(casesProjected) && casesProjected > 0) {
				cfrProjected.add(ddd, 100.0 * deathsProjected / casesProjected);
			}
			if (Double.isFinite(cases) && cases > 0) {
				chr.add(ddd, 100.0 * hosp / cases);
			}
			if (Double.isFinite(casesProjected) && casesProjected > 0) {
				chrProjected.add(ddd, 100.0 * hospProjected / casesProjected);
			}
			if (Double.isFinite(hosp) && hosp > 0) {
				hfr.add(ddd, 100.0 * deaths / hosp);
			}
			if (Double.isFinite(hospProjected) && hospProjected > 0) {
				hfrProjected.add(ddd, 100.0 * deathsProjected / hospProjected);
			}

			if (Double.isFinite(tests) && tests > 0) {
				pos.add(ddd, 100.0 * cases / tests);
			}
			if (Double.isFinite(testsProjected) && testsProjected > 0) {
				posProjected.add(ddd, 100.0 * casesProjected / testsProjected);
			}
		}

		TimeSeriesCollection collection = new TimeSeriesCollection();
		if (useCFR) {
			collection.addSeries(cfr);
			if (!usePositivity) {
				collection.addSeries(cfrProjected);
			}
		}
		if (useHFR) {
			collection.addSeries(hfr);
			if (!usePositivity) {
				collection.addSeries(hfrProjected);
			}
		}
		if (useCHR) {
			collection.addSeries(chr);
			if (!usePositivity) {
				collection.addSeries(chrProjected);
			}
		}
		if (usePositivity) {
			collection.addSeries(pos);
			if (!useCFR) {
				collection.addSeries(posProjected);
			}
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
		marker.setStroke(Charts.stroke);
		marker.setAlpha(0.25f);
		marker.setLabelFont(Charts.font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		plot.addDomainMarker(marker);

		if (fixedHeight != null) {
			DateAxis xAxis = (DateAxis) plot.getDomainAxis();
			xAxis.setMinimumDate(Date.dayToJavaDate(stats.getFirstDay()));
			xAxis.setMaximumDate(Date.dayToJavaDate(stats.getLastDay()));

			ValueAxis yAxis = plot.getRangeAxis();
			yAxis.setLowerBound(0);
			yAxis.setUpperBound(fixedHeight);

			plot.addDomainMarker(Charts.getTodayMarker(dayOfData));

			Event.addEvents(plot);
		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(Charts.TOP_FOLDER + "\\rates", fileName, image);
		return image;
	}

	public static String buildRates(ColoradoStats stats, String prefix, String title, boolean useCFR, boolean useCHR,
			boolean useHFR, boolean usePositivity) {

		new File(Charts.TOP_FOLDER + "\\rates").mkdir();

		AnimatedGifEncoder gif = new AnimatedGifEncoder();

		String fileName = Charts.TOP_FOLDER + "\\" + prefix + ".gif";
		gif.start(fileName);
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {

			String day = Date.dayToDate(dayOfData);
			String full = Date.dayToFullDate(dayOfData, '-');
			BufferedImage bi = buildRates(stats, dayOfData, prefix + "-" + full, title + day, useCFR, useCHR, useHFR,
					usePositivity, null, 50);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(bi);
		}

		gif.finish();
		return fileName;
	}
}