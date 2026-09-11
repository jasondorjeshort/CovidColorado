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
import org.jfree.chart.renderer.category.BarRenderer;
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
 * Draws every chart the live program makes: createSewage for one sewage
 * series, buildVocSewageCharts for the lineage charts of one VocSewage.
 * {@code nwss/Nwss.java} build() is the only caller. It runs mkdirs() and
 * reportState() first, then queues createSewage for every series onto its
 * build pool, and from a task on that pool calls buildVocSewageCharts for each
 * Voc. docs/reference/charts.txt owns the naming rule and the axis bounds.
 * <p>
 * The builders run on that pool, many at once. The only static state here is
 * the folder names. What the builders share is the sewage and VocSewage
 * objects, which do their own locking -- a plant's series is read for its own
 * charts and for its county's, possibly at the same moment -- and the queue in
 * {@code library/OpenImage.java}, which is synchronized.
 * <p>
 * Each builder returns its image, or null when it saved nothing, and no caller
 * reads it. {@code isMerger} is false for every Voc today (see
 * {@code variants/Voc.java}), so each merger test here takes the non-merger
 * side and no filename carries "-merger". The lineage charts are half again as
 * tall as the sewage ones, since 2434d59, which does not say why.
 * <p>
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

	/**
	 * Makes the output tree, each folder after its parent: File.mkdir is not
	 * recursive, and {@code Charts.saveBufferedImageAsPNG} makes only the folder
	 * it is handed, so a subfolder named in a chart's filename has to exist
	 * before anything is saved. Must run before any chart is queued. LL is the
	 * never-drawn Geo aggregate's.
	 */
	public static void mkdirs() {
		new File(Charts.TOP_FOLDER).mkdir();
		new File(Charts.FULL_FOLDER).mkdir();
		new File(SEWAGE_FOLDER).mkdir();
		new File(PLANT_FOLDER).mkdir();
		new File(STATES_FOLDER).mkdir();
		new File(REGIONS_FOLDER).mkdir();
		new File(COUNTIES_FOLDER).mkdir();
		new File(LL_FOLDER).mkdir();
		new File(VARIANTS_FOLDER).mkdir();
	}

	/**
	 * Makes the state's folder under counties, which its counties' chart
	 * filenames name and nothing else creates. Nwss build() calls it after
	 * mkdirs() for every state, and every county's state is one of them.
	 */
	public static void reportState(String state) {
		new File(COUNTIES_FOLDER + "\\" + state).mkdir();
	}

	/**
	 * Draws one sewage series, saved under SEWAGE_FOLDER as its chart filename
	 * plus "-recent" or "-all" and, when smoothed, "-avg" and the window. The
	 * series is the thick blue line and its fit the red one; an aggregate's
	 * children are the thin lines, the {@code maxChildren} most populous or all
	 * of them when that is null; the peak and valley markers are the series'
	 * own. Queued for opening when the series is the nation's or is named
	 * Colorado.
	 *
	 * @param latest
	 *            the recent chart, whose x-axis runs from 180 days back, or from
	 *            the last inflection if that is earlier, to now
	 * @param daysAveraged
	 *            1 for the daily readings, else the smoothing window in days
	 * @return the image, or null, with nothing saved, when the series has no
	 *         sewage
	 */
	public static BufferedImage buildSewageTimeseriesChart(Abstract sewage, Integer maxChildren, boolean latest,
			int daysAveraged) {

		if (sewage.getTotalSewage() <= 0) {
			return null;
		}

		TimeSeriesCollection collection = new TimeSeriesCollection();

		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		collection.addSeries(sewage.makeTimeSeries(null, daysAveraged));
		renderer.setSeriesStroke(seriesCount, new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		renderer.setSeriesPaint(seriesCount, Color.BLUE);
		renderer.setSeriesFillPaint(seriesCount, Color.BLUE.darker());
		seriesCount++;

		/*
		 * The fit is of the daily readings whatever the window, so it is the same
		 * line on every chart that has one. 30 only separates the 28-day chart
		 * from the 365-day one, which 112320a kept without a fit as its yearly
		 * flag had been. 28 is makeFitSeries's numDays, the days it takes before
		 * the confidence interval may stop it; no commit says why 28. A zero
		 * reading in a plant's window makes the fit NaN; see
		 * docs/active/findings/2026-09-10-a-zero-reading-in-a-plants-fit-window-makes-its-fit-nan.md.
		 */
		if (daysAveraged < 30) {
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
				collection.addSeries(child.makeTimeSeries(null, daysAveraged));
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		}

		String fileName = sewage.getChartFilename();
		String title = "Covid in sewage, " + CalendarUtils.dayToDate(sewage.getLastDay());
		if (daysAveraged > 1) {
			title += " smoothed:" + daysAveraged;
		}
		title += "\n";
		title += sewage.getTitleLine();
		title += "\nSource: CDC/NWSS";
		fileName += "-" + (latest ? "recent" : "all");
		if (daysAveraged > 1) {
			fileName += "-avg" + daysAveraged;
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
		/*
		 * A floor, not a fixed bottom: 0.01% of the pandemic peak, four decades
		 * under it, since 65259c7 made the axis a percentage of that peak (it had
		 * been a thousandth of the chart's own top). No commit says why four. The
		 * 1E-6 that sewage/Abstract.java draws for a zero lands below it.
		 */
		double lowerBound = 0.01;
		if (yAxis.getLowerBound() < lowerBound) {
			yAxis.setLowerBound(lowerBound);
		}
		/*
		 * Not cosmetic. A fit extrapolated far enough overflows exp() to
		 * infinity, and LogarithmicAxis counts its ticks by casting log10 of
		 * each bound to an int: an infinite top becomes Integer.MAX_VALUE
		 * decades of ten ticks each, allocated until the heap runs out. A finite
		 * range costs ten ticks a decade however wide it is.
		 */
		double upperBound = 1E6;
		if (yAxis.getUpperBound() > upperBound) {
			yAxis.setUpperBound(upperBound);
		}

		if (latest) {
			/*
			 * The y-axis was sized to the whole series when it was set on the
			 * plot, and narrowing the x-axis does not resize it: XYPlot
			 * reconfigures its range axes on a dataset or renderer change, not on
			 * a domain axis change. So a recent chart keeps the all-time y range.
			 * At least six months back since 0e276eb; before that the chart began
			 * at the last inflection, however recent.
			 */
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
		}

		return image;
	}

	/**
	 * Draws a VocSewage's lineages as sewage times prevalence, on a log axis.
	 * With {@code targetVariant} null it draws all of them, under SEWAGE_FOLDER
	 * and named by the sewage's chart filename; given one, it draws that lineage
	 * alone, under VARIANTS_FOLDER and named by the sewage's getName()
	 * (docs/reference/charts.txt, LINEAGE CHARTS). With {@code strains} it draws
	 * a line per Strain in place of the lineages, and only with a legend.
	 * <p>
	 * {@code fit} extends each line along its fit to the absolute last day,
	 * adds the lineages' summed fit to a chart that is not of one lineage when
	 * the VocSewage has more than one, and orders the legend by fit on that day
	 * rather than by cumulative prevalence. The Fit start, Data cutoff and Today
	 * markers are drawn either way. The all-lineage and strain charts with a fit
	 * and a legend are queued for opening.
	 * <p>
	 * The lower bound is 0.01 whatever the data, where on the sewage charts it
	 * is only a floor, and there is no cap: the guard against an infinite point
	 * is on the data instead, in VocSewage.
	 *
	 * @return the image, or null, with nothing saved, for a strain chart without
	 *         a legend or a sewage series with no sewage
	 */
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

		series = vocSewage.sewage.makeTimeSeries("Actual sewage", 1);
		collection.addSeries(series);
		renderer.setSeriesStroke(seriesCount, new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		seriesCount++;

		if (strains) {
			for (Strain strain : Strain.values()) {
				/*
				 * In the cumulative's units, percent of pandemic peak times share
				 * summed over the days; 5fa85c5 set it, here and in buildRelative,
				 * without saying why 0.1.
				 */
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
				series = vocSewage.makeAbsoluteSeries(variant, fit);
				collection.addSeries(series);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		}

		String folder, fileName;
		String title = vocSewage.sewage.getTitleLine();
		if (targetVariant == null) {
			folder = SEWAGE_FOLDER;
			fileName = vocSewage.sewage.getChartFilename() + "-" + vocSewage.vocId + "-absolute"
					+ (fit ? "-fit" : "-old") + (vocSewage.isMerger ? "-merger" : "") + "-all";
		} else {
			folder = VARIANTS_FOLDER;
			String n = targetVariant.displayName;
			n = n.replaceAll("\\*", "");
			/* The same Voc is charted against several sewages; keep the files apart. */
			fileName = vocSewage.sewage.getName() + "-" + n + "-" + vocSewage.vocId + "-absolute" + (fit ? "-fit" : "")
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
		yAxis.setLowerBound(0.01);

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
		/*
		 * The exit cannot fire for a failed write, which the save catches and
		 * prints itself, so the run goes on; see
		 * docs/active/findings/2026-09-10-a-failed-lineage-chart-write-cannot-reach-its-exit.md.
		 */
		try {
			Charts.saveBufferedImageAsPNG(folder, fileName, image);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		fileName = folder + "\\" + fileName + ".png";

		if (!vocSewage.isMerger && targetVariant == null && legend && fit) {
			library.OpenImage.openImage(fileName);
		}

		return image;
	}

	/**
	 * Draws each lineage's share of the VocSewage's total on a logit axis, or
	 * with {@code strains} each strain's, which is drawn only with a fit and a
	 * legend. {@code targetVariant}, a variant's name, would draw that one alone;
	 * every caller passes null. {@code fit} extends each line along its fit to
	 * the relative last day and orders the legend by fit on that day; without
	 * it the x-axis ends at the last day of data. The chart with a fit and a
	 * legend, of lineages or of strains, is queued for opening.
	 *
	 * @return the image, or null, with nothing saved, for a strain chart without
	 *         a fit or a legend, a sewage series with no sewage, or a VocSewage
	 *         with fewer than two variants
	 */
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
			/*
			 * Others and every manufactured parent keep an averageDay of 0, so
			 * they lead; see
			 * docs/active/findings/2026-09-10-a-manufactured-parent-loses-its-star-and-leads-the-legend.md.
			 */
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
				series = vocSewage.makeRelativeSeries(variant, fit);
				collection.addSeries(series);
				renderer.setSeriesStroke(seriesCount,
						new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				seriesCount++;
			}
		}

		String fileName = vocSewage.sewage.getChartFilename();
		String title = vocSewage.sewage.getTitleLine();
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
		/*
		 * Set whatever the data, so a share under it runs off the bottom. 0.1%
		 * since 49e095f, the first relative chart; dfa3d82 made it 1% when the
		 * auto-ranged top is past 99%, and neither says why.
		 */
		double upper = yAxis.getUpperBound();
		double lower = upper > 99 ? 1.0 : 0.1;
		yAxis.setLowerBound(lower);

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
		}

		return image;
	}

	/**
	 * Draws each lineage's cumulative prevalence, or with {@code strains} each
	 * strain's, as a horizontal bar of its log10: sewage as a percentage of the
	 * pandemic peak times the share, summed over the VocSewage's days. Lineages
	 * come largest first, each labelled with its fit's weekly growth. One whose
	 * cumulative is not positive is left off, and printed if it is negative.
	 * Every chart saved here is queued for opening.
	 * <p>
	 * As it stands the title names no series, so the national and Colorado
	 * charts look alike; see
	 * docs/active/findings/2026-09-10-the-cumulative-chart-does-not-say-which-sewage-it-is.md.
	 *
	 * @return the image, or null, with nothing saved, when the sewage series
	 *         has no sewage
	 */
	public static BufferedImage buildSewageCumulativeChart(VocSewage vocSewage, boolean strains) {
		if (vocSewage.sewage.getTotalSewage() <= 0) {
			return null;
		}
		ArrayList<Variant> variants = new ArrayList<>();
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		double minLog = Double.POSITIVE_INFINITY;

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
				minLog = Math.min(minLog, logPrevalence);
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
				minLog = Math.min(minLog, logPrevalence);
				String name = String.format("%s (%+.0f%%/w)", variant.displayName, vocSewage.getGrowth(variant));
				dataset.addValue(logPrevalence, name, "Prevalence");
			}
		}

		JFreeChart chart = ChartFactory.createBarChart("Cumulative prevalence", null, "Combined sewage (powers of 10)",
				dataset, PlotOrientation.HORIZONTAL, true, true, false);

		// createBarChart builds the plot's renderer as a BarRenderer.
		// https://stackoverflow.com/questions/7155294/jfreechart-bar-graph-labels
		BarRenderer renderer = (BarRenderer) chart.getCategoryPlot().getRenderer();

		/*
		 * Each bar is a log10, so a cumulative under 1 is negative and from the
		 * default base of zero would draw leftward and get longer the smaller
		 * it is, which is the opposite of what a bar length means; a cumulative
		 * of exactly 1 would get no bar at all. Basing them at the largest
		 * whole power of ten strictly below the smallest value makes every bar
		 * grow rightward from one floor, so a longer bar means a larger
		 * cumulative again. The base is included in the range axis (BarRenderer's
		 * includeBaseInRange, on by default), so the axis starts there too.
		 * With no positive cumulative nothing was added and the default base
		 * stands over an empty chart.
		 */
		if (Double.isFinite(minLog)) {
			renderer.setBase(Math.ceil(minLog) - 1);
		}

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
		}

		return image;
	}

	/**
	 * Draws a sewage series' charts, the recent one and then the all-time ones
	 * at each smoothing window; 112320a added the 7, 14 and 28-day windows and
	 * made the yearly chart a 365-day one, and does not say why those. A
	 * failure abandons the series' charts not yet drawn.
	 */
	public static void createSewage(Abstract sewage, Integer maxChildren) {
		try {
			buildSewageTimeseriesChart(sewage, maxChildren, true, 1);
			buildSewageTimeseriesChart(sewage, maxChildren, false, 1);
			buildSewageTimeseriesChart(sewage, maxChildren, false, 7);
			buildSewageTimeseriesChart(sewage, maxChildren, false, 14);
			buildSewageTimeseriesChart(sewage, maxChildren, false, 28);
			buildSewageTimeseriesChart(sewage, maxChildren, false, 365);
		} catch (Throwable e) {
			/*
			 * One bad series should not hide which one it was. Deliberately
			 * wider than RuntimeException: the failure this exists for is the
			 * OutOfMemoryError from LogarithmicAxis allocating ticks across an
			 * infinite range, and an Error would otherwise go past unlabelled.
			 * Rethrown, so this catch swallows nothing, but the pool does:
			 * MyExecutor prints and drops an Exception, and an Error comes back
			 * through ASync.complete() as an ExecutionException that is printed
			 * and dropped too. Either way the run goes on.
			 */
			System.out.println("Chart failed for " + sewage.getChartFilename() + ": " + e);
			throw e;
		}
	}

	/**
	 * Queues a VocSewage's lineage charts onto {@code build} and returns
	 * without waiting for them: the absolute and relative charts with and
	 * without a fit, a legend and strains (three of the relative calls are
	 * combinations buildRelative refuses, and return at once), one absolute
	 * chart per variant, and the two cumulative ones. Nwss build() calls it from
	 * a task already on that pool, which ASync allows.
	 */
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
		System.out.println("Queued voc charts in " + time + " ms.");
	}

}
