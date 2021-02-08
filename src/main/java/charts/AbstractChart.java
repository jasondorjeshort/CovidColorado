package charts;

import java.io.File;

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

	public AbstractChart(ColoradoStats stats, String topFolder) {
		this.topFolder = topFolder;
		this.stats = stats;
		new File(topFolder).mkdir();
	}

	// checkFinite
	public double cF(double value) {
		if (!Double.isFinite(value)) {
			new Exception("Infinite value : " + value);
		}
		return value;
	}

	/**
	 * The (file) name of this chart is used for both the GIF and folder that
	 * stores the individual charts.
	 */
	public abstract String getName();

	public String getSubfolder() {
		return topFolder + "\\" + getName();
	}

	public abstract Chart buildChart(int dayOfChart);

	public int getFirstDayOfChart() {
		return stats.getFirstDayOfData();
	}

	public int getLastDayOfChart() {
		return stats.getLastDay();
	}

	private final int _getFirstDayOfChart() {
		return Math.max(Charts.getFirstDayForCharts(stats), getFirstDayOfChart());
	}

	public String getPngName(int dayOfChart) {
		return getSubfolder() + "\\" + CalendarUtils.dayToFullDate(dayOfChart, '-') + ".png";
	}

	public abstract boolean hasData();

	public void buildAllCharts() {
		if (!hasData()) {
			return;
		}
		AnimatedGifEncoder gif = new AnimatedGifEncoder();
		String fileName = topFolder + "\\" + getName() + ".gif";
		new File(getSubfolder()).mkdir();
		gif.start(fileName);
		for (int dayOfChart = _getFirstDayOfChart(); dayOfChart <= getLastDayOfChart(); dayOfChart++) {
			try {
				Chart c = buildChart(dayOfChart);
				Charts.setDelay(stats, dayOfChart, gif);
				gif.addFrame(c.getImage());
			} catch (Exception e) {
				System.out.println("Fail on " + getPngName(dayOfChart));
				e.printStackTrace();
				// just continue
			}
		}
		gif.finish();
	}

	public void buildChartsOnly(@SuppressWarnings("rawtypes") ASync async) {
		if (!hasData()) {
			return;
		}
		new File(getSubfolder()).mkdir();
		for (int dayOfData = _getFirstDayOfChart(); dayOfData <= getLastDayOfChart(); dayOfData++) {
			if (async == null) {
				buildChart(dayOfData);
			} else {
				int _dayOfData = dayOfData;
				async.execute(() -> buildChart(_dayOfData));
			}
		}
	}
}
