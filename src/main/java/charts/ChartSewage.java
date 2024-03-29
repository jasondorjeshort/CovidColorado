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
import library.ASync;
import myjfreechart.LogitAxis;
import sewage.Abstract;
import sewage.All;
import variants.Strain;
import variants.Variant;
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

	public static BufferedImage buildSewageTimeseriesChart(Abstract sewage, Integer maxChildren, boolean latest,
			boolean yearlyAverage) {

		if (sewage.getTotalSewage() <= 0) {
			return null;
		}

		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		collection.addSeries(sewage.makeTimeSeries(null, yearlyAverage));
		renderer.setSeriesStroke(seriesCount, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, Color.BLUE);
		renderer.setSeriesFillPaint(seriesCount, Color.BLUE.darker());
		seriesCount++;

		if (!yearlyAverage) {
			TimeSeries series2 = sewage.makeFitSeries(28);
			if (series2 != null) {
				collection.addSeries(series2);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				renderer.setSeriesPaint(seriesCount, Color.RED);
				renderer.setSeriesFillPaint(seriesCount, Color.RED.darker());
				seriesCount++;
			}
		}

		if (sewage instanceof sewage.Multi) {
			for (Abstract child : ((sewage.Multi) sewage).getChildren(maxChildren)) {
				collection.addSeries(child.makeTimeSeries(null, yearlyAverage));
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
		fileName += "-" + (latest ? "recent" : "all");
		if (yearlyAverage) {
			fileName += "-yearly";
		}
		String verticalAxis = All.SCALE_NAME;
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);

		XYPlot plot = chart.getXYPlot();
		for (ValueMarker marker : sewage.getMarkers()) {
			plot.addDomainMarker(marker);
		}
		plot.setRenderer(renderer);

		LogarithmicAxis yAxis = new LogarithmicAxis(verticalAxis);
		plot.setRangeAxis(yAxis);
		double lowerBound = 0.01;
		if (yAxis.getLowerBound() < lowerBound) {
			yAxis.setLowerBound(lowerBound);
		}

		// plot.getDomainAxis().setLowerBound(CalendarUtils.dateToTime("5-1-2023"));

		if (latest) {
			ValueAxis xaxis = plot.getDomainAxis();

			long earliest = System.currentTimeMillis() - 180l * 24 * 60 * 60 * 1000;
			Long last = sewage.getLastInflection();
			if (last != null) {
				earliest = Math.min(earliest, last);
			}
			xaxis.setLowerBound(earliest);
			xaxis.setUpperBound(System.currentTimeMillis());
		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT);
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);

		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		if (sewage instanceof sewage.All || sewage instanceof sewage.Geo
				|| sewage.getName().equalsIgnoreCase("Colorado")) {
			library.OpenImage.openImage(fileName);
			library.OpenImage.open();
		}

		// System.out.println("Created : " + sewage.id + " for " +
		// series.getItemCount() + " => " + fileName);
		return image;
	}

	public static BufferedImage buildAbsolute(VocSewage vocSewage, Variant targetVariant, boolean fit, boolean legend,
			boolean strains) {
		if (strains && (!legend || vocSewage.isMerger)) {
			return null;
		}
		if (vocSewage.sewage.getTotalSewage() <= 0) {
			return null;
		}
		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;
		TimeSeries series;

		if (fit && targetVariant == null && vocSewage.getNumVariants() > 1) {
			series = vocSewage.makeAbsoluteCollectiveTS();
			if (series != null) {
				collection.addSeries(series);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		}

		series = vocSewage.sewage.makeTimeSeries("Actual sewage", false);
		collection.addSeries(series);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		seriesCount++;

		if (strains) {
			for (Strain strain : Strain.values()) {
				if (vocSewage.getCumulative(strain) < 0.1) {
					continue;
				}
				series = vocSewage.makeAbsoluteSeries(strain, fit);
				collection.addSeries(series);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		} else {
			int lastDay = vocSewage.getAbsoluteLastDay();
			ArrayList<Variant> variants = new ArrayList<>(vocSewage.getVariants());
			if (fit) {
				variants.sort(
						(v1, v2) -> -Double.compare(vocSewage.getFit(v1, lastDay), vocSewage.getFit(v2, lastDay)));
			} else {
				variants.sort((v1, v2) -> -Double.compare(vocSewage.getCumulative(v1), vocSewage.getCumulative(v2)));
			}
			for (Variant variant : variants) {
				if (targetVariant != null && targetVariant != variant) {
					continue;
				}
				/*
				 * if (fit) { series = vocSewage.makeRegressionTS(variant);
				 * collection.addSeries(series);
				 * renderer.setSeriesStroke(seriesCount, new BasicStroke(1.0f,
				 * BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				 * seriesCount++; }
				 */
				series = vocSewage.makeAbsoluteSeries(variant, fit);
				collection.addSeries(series);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		}

		// dataset.addSeries("Cases", series);

		String folder, fileName;
		String title = vocSewage.sewage.getTitleLine();
		// (exact ? "-exact" : "") +
		if (targetVariant == null) {
			folder = SEWAGE_FOLDER;
			fileName = vocSewage.sewage.getChartFilename() + "-" + vocSewage.vocId + "-absolute"
					+ (fit ? "-fit" : "-old") + (vocSewage.isMerger ? "-merger" : "") + "-all";
		} else {
			folder = VARIANTS_FOLDER;
			String n = targetVariant.displayName;
			n = n.replaceAll("\\*", "");
			fileName = n + "-" + vocSewage.vocId + "-absolute" + (fit ? "-fit" : "")
					+ (vocSewage.isMerger ? "-merger" : "");
		}
		fileName += strains ? "-strain" : "-variant";
		fileName += legend ? "-legend" : "-nolegend";
		// fileName += vocSewage.voc.exclusions ? "-exc" : "-nxc";
		title += "\nSource: CDC/NWSS, Cov-Spectrum";
		String verticalAxis = All.SCALE_NAME;

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);
		if (!legend) {
			chart.removeLegend();
		}

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
		double bound = CalendarUtils.dayToTime(vocSewage.getFirstDay());
		if (xAxis.getLowerBound() < bound) {
			xAxis.setLowerBound(bound);
		}

		bound = CalendarUtils.dayToTime(vocSewage.getAbsoluteLastDay());
		bound = Math.min(bound, xAxis.getUpperBound());
		xAxis.setUpperBound(bound);

		int fitStartDay = vocSewage.getLastInflection(targetVariant);
		ValueMarker marker = new ValueMarker(CalendarUtils.dayToTime(fitStartDay));
		marker.setPaint(Color.black);
		marker.setLabel("Fit start");
		marker.setStroke(Charts.stroke);
		marker.setLabelFont(Charts.font);
		marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
		plot.addDomainMarker(marker);

		marker = new ValueMarker(CalendarUtils.dayToTime(vocSewage.getLastDay()));
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
		try {
			Charts.saveBufferedImageAsPNG(folder, fileName, image);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		fileName = folder + "\\" + fileName + ".png";

		if (!vocSewage.isMerger && targetVariant == null && legend && fit) {
			library.OpenImage.openImage(fileName);
			library.OpenImage.open();
		}

		// System.out.println("Created : " + sewage.id + " for " +
		// series.getItemCount() + " => " + fileName);
		return image;
	}

	public static BufferedImage buildRelative(VocSewage vocSewage, String targetVariant, boolean fit, boolean legend,
			boolean strains) {
		if (strains && (!fit || !legend || vocSewage.isMerger)) {
			return null;
		}
		if (vocSewage.sewage.getTotalSewage() <= 0) {
			return null;
		}
		if (vocSewage.getNumVariants() <= 1) {
			return null;
		}
		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;
		TimeSeries series;

		int lastDay = vocSewage.getRelativeLastDay();
		ArrayList<Variant> variants = new ArrayList<>(vocSewage.getVariants());
		if (fit) {
			variants.sort((v1, v2) -> -Double.compare(vocSewage.getFit(v1, lastDay), vocSewage.getFit(v2, lastDay)));
		} else {
			variants.sort((v1, v2) -> Double.compare(v1.averageDay, v2.averageDay));
		}

		if (strains) {
			for (Strain strain : Strain.values()) {
				if (vocSewage.getCumulative(strain) < 0.1) {
					continue;
				}
				series = vocSewage.makeRelativeSeries(strain, fit);
				collection.addSeries(series);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		} else {
			for (Variant variant : variants) {
				if (targetVariant != null && !targetVariant.equalsIgnoreCase(variant.name)) {
					continue;
				}
				/*
				 * if (fit) { series = vocSewage.makeRegressionTS(variant);
				 * collection.addSeries(series);
				 * renderer.setSeriesStroke(seriesCount, new BasicStroke(1.0f,
				 * BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				 * seriesCount++; }
				 */
				series = vocSewage.makeRelativeSeries(variant, fit);
				collection.addSeries(series);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		}

		// dataset.addSeries("Cases", series);

		String fileName = vocSewage.sewage.getChartFilename();
		String title = vocSewage.sewage.getTitleLine();
		// (fit ? "-fit" : "") +(exact ? "-exact" : "") +
		fileName += (vocSewage.isMerger ? "-merger" : "") + "-" + vocSewage.vocId + "-relative";
		fileName += (fit ? "-fit" : "-old");
		fileName += strains ? "-strain" : "-variant";
		fileName += (legend ? "-legend" : "-noleg");
		if (targetVariant == null) {
			fileName += "-all";
		} else {
			fileName += ("-" + targetVariant);
		}
		// fileName += vocSewage.voc.exclusions ? "-exc" : "-nxc";
		title += "\nSource: CDC/NWSS, Cov-Spectrum";
		String verticalAxis = "Relative percentage";

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date", verticalAxis, collection);
		if (!legend) {
			chart.removeLegend();
		}

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		LogitAxis yAxis = new LogitAxis(verticalAxis, 100.0);
		plot.setRangeAxis(yAxis);
		// yAxis.setUpperBound(1000);
		double upper = yAxis.getUpperBound();
		double lower = upper > 99 ? 1.0 : 0.1;
		yAxis.setLowerBound(lower);
		/*
		 * double bound = yAxis.getUpperBound() / 10000.0; if
		 * (yAxis.getLowerBound() < bound) { yAxis.setLowerBound(bound); }
		 */

		ValueAxis xAxis = plot.getDomainAxis();
		double bound = CalendarUtils.dayToTime(vocSewage.getFirstDay());
		if (xAxis.getLowerBound() < bound) {
			xAxis.setLowerBound(bound);
		}

		bound = CalendarUtils.dayToTime(lastDay);
		bound = Math.min(bound, xAxis.getUpperBound());
		xAxis.setUpperBound(bound);

		if (fit) {
			ValueMarker marker = new ValueMarker(CalendarUtils.dayToTime(vocSewage.getLastInflection(null)));
			marker.setPaint(Color.black);
			marker.setLabel("Fit start");
			marker.setStroke(Charts.stroke);
			marker.setLabelFont(Charts.font);
			marker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
			plot.addDomainMarker(marker);

			marker = new ValueMarker(CalendarUtils.dayToTime(vocSewage.getLastDay()));
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
		} else {
			xAxis.setUpperBound(CalendarUtils.dayToTime(vocSewage.getLastDay()));
		}

		BufferedImage image = chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT * 3 / 2);
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);

		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		if (!vocSewage.isMerger && legend && fit) {
			library.OpenImage.openImage(fileName);
			library.OpenImage.open();
		}

		// System.out.println("Created : " + sewage.id + " for " +
		// series.getItemCount() + " => " + fileName);
		return image;
	}

	public static BufferedImage buildSewageCumulativeChart(VocSewage vocSewage, boolean strains) {
		if (vocSewage.sewage.getTotalSewage() <= 0) {
			return null;
		}
		ArrayList<Variant> variants = new ArrayList<>();
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		if (strains) {
			for (Strain strain : Strain.values()) {
				double prevalence = vocSewage.getCumulative(strain);
				if (prevalence <= 0) {
					if (prevalence < 0) {
						System.out.println("Prevalence " + prevalence + " for " + strain);
					}
					continue;
				}
				double logPrevalence = Math.log(prevalence) / Math.log(10);
				String name = String.format("%s", strain.getName());
				dataset.addValue(logPrevalence, name, "Prevalence");
			}
		} else {

			Map<Variant, Double> prev = vocSewage.getCumulativePrevalence(variants);
			for (Variant variant : variants) {
				double prevalence = prev.get(variant);
				if (prevalence <= 0) {
					if (prevalence < 0) {
						System.out.println("Prevalence " + prevalence + " for " + variant);
					}
					continue;
				}
				double logPrevalence = Math.log(prevalence) / Math.log(10);
				String name = String.format("%s (%+.0f%%/w)", variant.displayName, vocSewage.getGrowth(variant));
				dataset.addValue(logPrevalence, name, "Prevalence");
			}
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

		String fileName = vocSewage.sewage.getChartFilename() + "-" + vocSewage.vocId + "-cumulative"
				+ (vocSewage.isMerger ? "-merger" : "");
		fileName += strains ? "-strain" : "-variant";
		// fileName += vocSewage.voc.exclusions ? "-exc" : "-nxc";
		Charts.saveBufferedImageAsPNG(SEWAGE_FOLDER, fileName, image);
		fileName = SEWAGE_FOLDER + "\\" + fileName + ".png";

		if (!vocSewage.isMerger) {
			library.OpenImage.openImage(fileName);
			library.OpenImage.open();
		}

		return image;
	}

	public static void createSewage(Abstract sewage, Integer maxChildren) {
		// buildSewageTimeseriesChart(sewage, false);
		buildSewageTimeseriesChart(sewage, maxChildren, true, false);
		buildSewageTimeseriesChart(sewage, maxChildren, false, false);
		buildSewageTimeseriesChart(sewage, maxChildren, false, true);
	}

	public static void buildVocSewageCharts(VocSewage vocSewage, ASync<Chart> build) {
		long time = System.currentTimeMillis();
		build.execute(() -> ChartSewage.buildAbsolute(vocSewage, null, true, true, true));
		build.execute(() -> ChartSewage.buildAbsolute(vocSewage, null, false, true, true));
		build.execute(() -> ChartSewage.buildAbsolute(vocSewage, null, true, true, false));
		build.execute(() -> ChartSewage.buildAbsolute(vocSewage, null, false, true, false));
		build.execute(() -> ChartSewage.buildAbsolute(vocSewage, null, true, false, false));
		build.execute(() -> ChartSewage.buildAbsolute(vocSewage, null, false, false, false));

		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, true, true, true));
		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, false, true, true));
		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, true, false, true));
		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, false, false, true));
		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, true, true, false));
		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, false, true, false));
		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, true, false, false));
		build.execute(() -> ChartSewage.buildRelative(vocSewage, null, false, false, false));
		for (Variant variant : vocSewage.getVariants()) {
			build.execute(() -> ChartSewage.buildAbsolute(vocSewage, variant, true, true, false));
		}
		build.execute(() -> ChartSewage.buildSewageCumulativeChart(vocSewage, true));
		build.execute(() -> ChartSewage.buildSewageCumulativeChart(vocSewage, false));
		time = System.currentTimeMillis() - time;
		System.out.println("Built voc in " + time + " ms.");
	}

}
