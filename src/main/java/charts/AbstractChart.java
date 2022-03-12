package charts;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.Future;

import org.jfree.chart.JFreeChart;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import covid.CalendarUtils;
import covid.ColoradoStats;
import library.ASync;

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
public abstract class AbstractChart {

	public static final int DELAY = 28; // multiple of 7
	public static final int INTERVAL = 160; // best work with the 95%
	public static final double confidence = 95;
	public static final double bottomRange = 50 - confidence / 2;
	public static final double topRange = 50 + confidence / 2;
	private final HashSet<Flag> flags = new HashSet<>();

	public boolean logarithmic() {
		return flags.contains(Flag.LOGARITHMIC);
	}

	public String logName() {
		return (logarithmic() ? "-log" : "-cart");
	}

	public boolean smoothed() {
		return flags.contains(Flag.SMOOTHED);
	}

	public String smoothName() {
		return (smoothed() ? "-smooth" : "-exact");
	}

	public boolean oldYears() {
		return flags.contains(Flag.OLD_YEARS);
	}

	public String yearsName() {
		return (oldYears() ? "-years" : "-year");
	}

	/*
	 * An "abstract" chart graphs something over all days of the pandemic for
	 * one day of data. The simplest example is cases by day of infection: we
	 * can make a graph for every day of all the data available up through that
	 * day. It can also work the other way: graphing days of data over every day
	 * of infection.
	 * 
	 * We can then animate this, or only make the latest graphs.
	 */

	/**
	 * The "top" folder is a misnamer as a tier 2 folder presumably under the
	 * Charts.TOP_FOLDER. But then we make a new folder within that based on
	 * type/timing, and put a bunch of day-of-data charts into it.
	 */
	public final String topFolder;

	public final ColoradoStats stats;

	public AbstractChart(ColoradoStats stats, String topFolder, Flag... flags) {
		this.topFolder = topFolder;
		this.stats = stats;
		new File(topFolder).mkdir();
		for (Flag flag : flags) {
			this.flags.add(flag);
		}
	}

	// checkFinite
	public static double cF(double value) {
		if (!Double.isFinite(value)) {
			new Exception("Infinite value : " + value).printStackTrace();
		}
		return value;
	}

	private static final int GIF_DAYS = 21;

	/**
	 * The (file) name of this chart is used for both the GIF and folder that
	 * stores the individual charts.
	 */
	public abstract String getName();

	public String getSubfolder() {
		return topFolder + "\\" + getName() + "-" + DELAY + "-" + INTERVAL;
	}

	public abstract JFreeChart buildChart(int dayOfChart);

	public int getFirstDayOfChart() {
		return stats.getFirstDayOfData();
	}

	public int getLastDayOfChart() {
		return stats.getLastDay();
	}

	@SuppressWarnings("static-method")
	private int lastChartsDay(int lastDay) {
		lastDay -= lastDay % 7;
		lastDay += 30;
		return lastDay;
	}

	public int getFirstDayForChart() {
		int last = stats.getLastDay();
		if (lastChartsDay(last) == lastChartsDay(last - 1)) {
			return stats.getLastDay() - GIF_DAYS;
		}
		return 0;
	}

	public int getLastDayForChartDisplay() {
		return lastChartsDay(stats.getLastDay());
	}

	private final int _getFirstDayOfChart() {
		return Math.max(getFirstDayForChart(), getFirstDayOfChart());
	}

	public String getPngName(int dayOfChart) {
		return getSubfolder() + "\\" + CalendarUtils.dayToFullDate(dayOfChart, '-') + ".png";
	}

	public abstract boolean hasData();

	public abstract boolean dayHasData(int dayOfChart);

	@SuppressWarnings({ "static-method", "unused" })
	public boolean publish(int dayOfChart) {
		return false;
	}

	private Chart fullBuildChart(int dayOfChart) {
		JFreeChart chart = buildChart(dayOfChart);
		if (chart == null) {
			// new Exception("Null chart for " +
			// getPngName(dayOfChart)).printStackTrace();
			return null;
		}
		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), dayOfChart, getPngName(dayOfChart));
		if (publish(dayOfChart)) {
			c.addFileName(Charts.TOP_FOLDER + "\\" + getName() + ".png");
			c.open();
		}
		c.saveAsPNG();

		return c;
	}

	public void buildAllCharts() {
		if (!hasData()) {
			return;
		}
		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String fileName = topFolder + "\\" + getName() + ".gif";
		new File(getSubfolder()).mkdir();
		gif.start(fileName);
		int last = getLastDayOfChart();
		for (int dayOfChart = _getFirstDayOfChart(); dayOfChart <= last; dayOfChart++) {
			if (!dayHasData(dayOfChart)) {
				continue;
			}
			try {
				Chart c = fullBuildChart(dayOfChart);
				if (dayOfChart + GIF_DAYS > last) {
					Charts.setDelay(stats, dayOfChart, gif);
					gif.addFrame(c.getImage());
				}
			} catch (Exception e) {
				System.out.println("Fail on " + getPngName(dayOfChart));
				e.printStackTrace();
				// just continue
			}
		}
		gif.finish();
	}

	public void buildChartsOnly(ASync<Chart> async) {
		if (!hasData()) {
			return;
		}
		if (async == null) {
			buildAllCharts();
			return;
		}
		new File(getSubfolder()).mkdir();
		LinkedList<Future<Chart>> gifs = new LinkedList<>();
		int last = getLastDayOfChart();
		for (int dayOfChart = _getFirstDayOfChart(); dayOfChart <= last; dayOfChart++) {
			if (!dayHasData(dayOfChart)) {
				continue;
			}
			int _dayOfChart = dayOfChart;
			Future<Chart> future = async.submit(() -> fullBuildChart(_dayOfChart));
			if (dayOfChart + GIF_DAYS > last) {
				gifs.add(future);
			}
		}
		async.execute(() -> {
			AnimatedGifEncoder gif = new AnimatedGifEncoder();
			String fileName = topFolder + "\\" + getName() + ".gif";
			gif.start(fileName);

			for (Future<Chart> future : gifs) {
				Chart c;
				try {
					c = future.get();
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
				Charts.setDelay(stats, c.dayOfChart, gif);
				gif.addFrame(c.getImage());
			}
			gif.finish();
		});
	}

}
