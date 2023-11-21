package charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import covid.CalendarUtils;
import sewage.Abstract;
import sewage.All;
import variants.Voc;
import variants.VocSewage;

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
	public static final String REGIONS = "regions";
	public static final String COUNTIES = "counties";
	public static final String STATES_FOLDER = SEWAGE_FOLDER + "\\" + STATES;
	public static final String REGIONS_FOLDER = SEWAGE_FOLDER + "\\" + REGIONS;
	public static final String COUNTIES_FOLDER = SEWAGE_FOLDER + "\\" + COUNTIES;
	public static final String PLANT_FOLDER = SEWAGE_FOLDER + "\\" + PLANTS;
	public static final String LL = "LL";
	public static final String LL_FOLDER = SEWAGE_FOLDER + "\\" + LL;
	public static final String VARIANTS = "variants";
	public static final String VARIANTS_FOLDER = SEWAGE_FOLDER + "\\" + VARIANTS;

	public static void mkdirs() {
		new File(Charts.FULL_FOLDER).mkdir();
		new File(SEWAGE_FOLDER).mkdir();
		new File(PLANT_FOLDER).mkdir();
		new File(STATES_FOLDER).mkdir();
		new File(REGIONS_FOLDER).mkdir();
		new File(COUNTIES_FOLDER).mkdir();
		new File(LL_FOLDER).mkdir();
		new File(VARIANTS_FOLDER).mkdir();
	}

	public static void reportState(String state) {
		new File(COUNTIES_FOLDER + "\\" + state).mkdir();
	}

	public static BufferedImage buildSewageTimeseriesChart(Abstract sewage, boolean log, Integer maxChildren) {

		if (sewage.getTotalSewage() <= 0) {
			return null;
		}

		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		collection.addSeries(sewage.makeTimeSeries(null));
		renderer.setSeriesStroke(seriesCount, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, Color.BLUE);
		renderer.setSeriesFillPaint(seriesCount, Color.BLUE.darker());
		seriesCount++;

		TimeSeries series2 = sewage.makeFitSeries(28);
		if (series2 != null) {
			collection.addSeries(series2);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, Color.RED);
			renderer.setSeriesFillPaint(seriesCount, Color.RED.darker());
			seriesCount++;
		}

		if (sewage instanceof sewage.Multi) {
			for (Abstract child : ((sewage.Multi) sewage).getChildren(maxChildren)) {
				collection.addSeries(child.makeTimeSeries(null));
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		}
		// dataset.addSeries("Cases", series);

		String fileName = sewage.getChartFilename();
		String title = "Covid in sewage, " + CalendarUtils.dayToDate(sewage.getLastDay()) + "\n";
		title += sewage.getTitleLine();
		title += "\nSource: CDC/NWSS";
		fileName += "-" + (log ? "log" : "cart");
		String verticalAxis = All.SCALE_NAME;
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		for (ValueMarker marker : sewage.getMarkers()) {
			plot.addDomainMarker(marker);
		}
		plot.setRenderer(renderer);

		if (log) {
			LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
			plot.setRangeAxis(yAxis);
			double lowerBound = 0.01;
			if (yAxis.getLowerBound() < lowerBound) {
				yAxis.setLowerBound(lowerBound);
			}

			// plot.getDomainAxis().setLowerBound(CalendarUtils.dateToTime("5-1-2023"));
		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);

		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		if (sewage instanceof sewage.All || sewage instanceof sewage.Geo
				|| sewage.getName().equalsIgnoreCase("Colorado")) {
			// library.OpenImage.openImage(fileName);
			// library.OpenImage.open();
		}

		// System.out.println("Created : " + sewage.id + " for " +
		// series.getItemCount() + " => " + fileName);
		return image;
	}

	public static BufferedImage buildAbsolute(VocSewage vocSewage, String targetVariant) {

		if (vocSewage.sewage.getTotalSewage() <= 0) {
			return null;
		}
		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;
		TimeSeries series;

		Voc voc = vocSewage.voc;
		series = vocSewage.makeCollectiveTS();
		if (series != null) {
			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			seriesCount++;
		}

		series = vocSewage.sewage.makeTimeSeries("Actual sewage");
		collection.addSeries(series);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		seriesCount++;

		for (String variant : vocSewage.getVariantsByCount()) {
			if (targetVariant != null && !targetVariant.equalsIgnoreCase(variant)) {
				continue;
			}
			/*
			 * if (fit) { series = vocSewage.makeRegressionTS(variant);
			 * collection.addSeries(series);
			 * renderer.setSeriesStroke(seriesCount, new BasicStroke(1.0f,
			 * BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); seriesCount++; }
			 */
			series = vocSewage.makeAbsoluteSeries(variant);
			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount,
					new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			seriesCount++;
		}

		// dataset.addSeries("Cases", series);

		String fileName = vocSewage.sewage.getChartFilename();
		String title = vocSewage.sewage.getTitleLine();
		// (fit ? "-fit" : "") +(exact ? "-exact" : "") +
		fileName += "-" + voc.id + "-abslog" + (voc.isMerger ? "-merger" : "");
		if (targetVariant == null) {
			fileName += "-all";
		} else {
			fileName += ("-" + targetVariant);
		}
		title += "\nSource: CDC/NWSS, Cov-Spectrum";
		String verticalAxis = All.SCALE_NAME;

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
		plot.setRangeAxis(yAxis);
		// yAxis.setUpperBound(1000);
		yAxis.setLowerBound(0.01);
		/*
		 * double bound = yAxis.getUpperBound() / 10000.0; if
		 * (yAxis.getLowerBound() < bound) { yAxis.setLowerBound(bound); }
		 */

		ValueAxis xAxis = plot.getDomainAxis();
		double bound = CalendarUtils.dayToTime(voc.getFirstDay());
		if (xAxis.getLowerBound() < bound) {
			xAxis.setLowerBound(bound);
		}

		bound = CalendarUtils.dayToTime(vocSewage.getModelLastDay());
		bound = Math.min(bound, xAxis.getUpperBound());
		xAxis.setUpperBound(bound);

		ValueMarker marker = new ValueMarker(CalendarUtils.dayToTime(vocSewage.getLastDay() + 1));
		marker.setPaint(Color.black);
		marker.setLabel("Data cutoff");
		marker.setStroke(Charts.stroke);
		marker.setLabelFont(Charts.font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		plot.addDomainMarker(marker);

		marker = new ValueMarker(System.currentTimeMillis());
		marker.setPaint(Color.black);
		marker.setLabel("Today");
		marker.setStroke(Charts.stroke);
		marker.setLabelFont(Charts.font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		plot.addDomainMarker(marker);

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT * 3 / 2);
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);

		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		// library.OpenImage.openImage(fileName);
		// library.OpenImage.open();

		// System.out.println("Created : " + sewage.id + " for " +
		// series.getItemCount() + " => " + fileName);
		return image;
	}

	public static BufferedImage buildRelative(VocSewage vocSewage, String targetVariant) {
		if (vocSewage.sewage.getTotalSewage() <= 0) {
			return null;
		}
		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;
		TimeSeries series;

		Voc voc = vocSewage.voc;

		for (String variant : vocSewage.getVariantsByCount()) {
			if (targetVariant != null && !targetVariant.equalsIgnoreCase(variant)) {
				continue;
			}
			/*
			 * if (fit) { series = vocSewage.makeRegressionTS(variant);
			 * collection.addSeries(series);
			 * renderer.setSeriesStroke(seriesCount, new BasicStroke(1.0f,
			 * BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); seriesCount++; }
			 */
			series = vocSewage.makeRelativeSeries(variant);
			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount,
					new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			seriesCount++;
		}

		// dataset.addSeries("Cases", series);

		String fileName = vocSewage.sewage.getChartFilename();
		String title = vocSewage.sewage.getTitleLine();
		// (fit ? "-fit" : "") +(exact ? "-exact" : "") +
		fileName += "-" + voc.id + "-rel" + (voc.isMerger ? "-merger" : "");
		if (targetVariant == null) {
			fileName += "-all";
		} else {
			fileName += ("-" + targetVariant);
		}
		title += "\nSource: CDC/NWSS, Cov-Spectrum";
		String verticalAxis = "Relative percentage";

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
		plot.setRangeAxis(yAxis);
		// yAxis.setUpperBound(1000);
		yAxis.setLowerBound(0.1);
		/*
		 * double bound = yAxis.getUpperBound() / 10000.0; if
		 * (yAxis.getLowerBound() < bound) { yAxis.setLowerBound(bound); }
		 */

		ValueAxis xAxis = plot.getDomainAxis();
		double bound = CalendarUtils.dayToTime(voc.getFirstDay());
		if (xAxis.getLowerBound() < bound) {
			xAxis.setLowerBound(bound);
		}

		bound = CalendarUtils.dayToTime(vocSewage.getModelLastDay());
		bound = Math.min(bound, xAxis.getUpperBound());
		xAxis.setUpperBound(bound);

		ValueMarker marker = new ValueMarker(CalendarUtils.dayToTime(vocSewage.getLastDay() + 1));
		marker.setPaint(Color.black);
		marker.setLabel("Data cutoff");
		marker.setStroke(Charts.stroke);
		marker.setLabelFont(Charts.font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		plot.addDomainMarker(marker);

		marker = new ValueMarker(System.currentTimeMillis());
		marker.setPaint(Color.black);
		marker.setLabel("Today");
		marker.setStroke(Charts.stroke);
		marker.setLabelFont(Charts.font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		plot.addDomainMarker(marker);

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT * 3 / 2);
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);

		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		// library.OpenImage.openImage(fileName);
		// library.OpenImage.open();

		// System.out.println("Created : " + sewage.id + " for " +
		// series.getItemCount() + " => " + fileName);
		return image;
	}

	public static BufferedImage buildSewageCumulativeChart(VocSewage vocSewage) {
		if (vocSewage.sewage.getTotalSewage() <= 0) {
			return null;
		}
		ArrayList<String> variants = new ArrayList<>();
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		Voc voc = vocSewage.voc;

		Map<String, Double> prev = vocSewage.getCumulativePrevalence(variants);

		for (String variant : variants) {
			double prevalence = prev.get(variant);
			if (prevalence <= 0) {
				if (prevalence < 0) {
					System.out.println("Prevalence " + prevalence + " for " + variant);
				}
				continue;
			}
			double logPrevalence = Math.log(prevalence) / Math.log(10);
			String name = String.format("%s %.0f (%+.0f weekly)", variant.replaceAll("nextcladePangoLineage:", ""),
					prevalence, vocSewage.getGrowth(variant));
			dataset.addValue(logPrevalence, name, "Prevalence");
		}

		JFreeChart chart = ChartFactory.createBarChart("Cumulative prevalence", null, "Combined sewage (powers of 10)",
				dataset, PlotOrientation.HORIZONTAL, true, true, false);

		// https://stackoverflow.com/questions/7155294/jfreechart-bar-graph-labels
		/*
		 * StackedBarRenderer renderer = new StackedBarRenderer(false);
		 * renderer.setBaseItemLabelGenerator(new
		 * StandardCategoryItemLabelGenerator());
		 * renderer.setBaseItemLabelsVisible(true);
		 * chart.getCategoryPlot().setRenderer(renderer);`
		 */
		CategoryItemRenderer renderer = chart.getCategoryPlot().getRenderer();
		// CategoryItemLabelGenerator labelGenerator =
		// renderer.getDefaultItemLabelGenerator();

		if (false) {
			CategoryItemLabelGenerator generator = new StandardCategoryItemLabelGenerator("{2}",
					NumberFormat.getInstance());
			renderer.setDefaultItemLabelGenerator(generator);
			renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.PLAIN, 12));
			renderer.setDefaultItemLabelsVisible(true);
			renderer.setDefaultPositiveItemLabelPosition(
					new ItemLabelPosition(ItemLabelAnchor.CENTER, TextAnchor.CENTER, TextAnchor.CENTER, -0 / 2));
		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT * 3 / 2);

		String fileName = vocSewage.sewage.getChartFilename() + "-" + voc.id + "-cumulative"
				+ (vocSewage.voc.isMerger ? "-merger" : "");
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);
		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		// library.OpenImage.openImage(fileName);
		// library.OpenImage.open();

		return image;
	}

	public static void createSewage(Abstract sewage, Integer maxChildren) {
		// buildSewageTimeseriesChart(sewage, false);
		buildSewageTimeseriesChart(sewage, true, maxChildren);
	}

	public static void buildVocSewageCharts(VocSewage vocSewage) {
		ChartSewage.buildAbsolute(vocSewage, null);
		ChartSewage.buildRelative(vocSewage, null);
		if (false) {
			ChartSewage.buildAbsolute(vocSewage, "Others");
		}
		ChartSewage.buildSewageCumulativeChart(vocSewage);
	}

}
