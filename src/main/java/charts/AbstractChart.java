package charts;

import java.io.File;

import com.madgag.gif.fmsware.AnimatedGifEncoder;

import covid.CalendarUtils;
import covid.ColoradoStats;

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
	 * day.
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

	/**
	 * The (file) name of this chart is used for both the GIF and folder that
	 * stores the individual charts.
	 */
	public abstract String getName();

	public String getSubfolder() {
		return topFolder + "\\" + getName();
	}

	public abstract Chart buildChart(int dayOfData);

	public int getFirstDayForAnimationBackend() {
		return stats.getFirstDayOfData();
	}

	public final int getFirstDayForAnimation() {
		return Math.max(Charts.getFirstDayForCharts(stats), getFirstDayForAnimationBackend());
	}

	public String getPngName(int dayOfData) {
		return getSubfolder() + "\\" + CalendarUtils.dayToFullDate(dayOfData, '-') + ".png";
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
		for (int dayOfData = getFirstDayForAnimation(); dayOfData <= stats.getLastDay(); dayOfData++) {
			Chart c = buildChart(dayOfData);
			Charts.setDelay(stats, dayOfData, gif);
			gif.addFrame(c.getImage());
		}
		gif.finish();
	}
}
