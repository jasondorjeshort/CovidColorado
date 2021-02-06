package charts;

import java.util.Set;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import covid.CalendarUtils;
import covid.ColoradoStats;
import covid.IncompleteNumbers;
import covid.NumbersTiming;
import covid.NumbersType;

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
public class NewNumbersChart extends AbstractChart {

	/*
	 * Preliminary here.
	 * 
	 * This charts the distribution of case reporting over time.
	 */

	private final Set<NumbersType> types;
	private final NumbersTiming timing;

	public NewNumbersChart(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing) {
		super(stats, Charts.FULL_FOLDER + "\\" + "new-numbers");
		this.types = types;
		this.timing = timing;
	}

	@Override
	public Chart buildChart(int dayOfData) {

		TimeSeriesCollection collection = new TimeSeriesCollection();

		for (NumbersType type : types) {
			IncompleteNumbers numbers = stats.getNumbers(type, timing);
			TimeSeries series = new TimeSeries(type.capName);

			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= dayOfData; dayOfType++) {
				Double number = numbers.getNewNumbers(dayOfData, dayOfType);

				if (number == null) {
					continue;
				}

				series.add(CalendarUtils.dayToDay(dayOfType), number);
			}

			collection.addSeries(series);
		}

		StringBuilder title = new StringBuilder();
		title.append("Date of " + timing.lowerName + " of newly released \n");
		if (types.size() > 1) {
			title.append("numbers");
		} else {
			for (NumbersType type : types) {
				title.append(type.lowerName);
			}
		}
		title.append(" for ");
		title.append(CalendarUtils.dayToDate(dayOfData));
		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", "Count", collection);

		Chart c = new Chart(chart.createBufferedImage(Charts.WIDTH, Charts.HEIGHT), getPngName(dayOfData));
		c.saveAsPNG();
		if (dayOfData == stats.getLastDay() && timing == NumbersTiming.INFECTION && types.size() >= 3) {
			c.open();
		}
		return c;
	}

	@Override
	public String getName() {
		return NumbersType.name(types, "-") + "-" + timing.lowerName;
	}

	@Override
	public boolean hasData() {
		for (NumbersType type : types) {
			IncompleteNumbers n = stats.getNumbers(type, timing);
			if (n.hasData()) {
				return true;
			}
		}
		return false;
	}
}
