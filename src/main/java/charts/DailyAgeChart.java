package charts;

import java.awt.BasicStroke;
import java.util.Set;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
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
public class DailyAgeChart extends AbstractChart {

	/*
	 * Preliminary here.
	 * 
	 * This charts the distribution of case reporting over time.
	 */

	private final Set<NumbersType> types;
	private final NumbersTiming timing;

	public DailyAgeChart(ColoradoStats stats, Set<NumbersType> types, NumbersTiming timing) {
		super(stats, Charts.FULL_FOLDER + "\\" + "daily-age");
		this.types = types;
		this.timing = timing;
	}

	@Override
	public boolean publish(int dayOfData) {
		if (dayOfData != stats.getLastDay()) {
			return false;
		}
		if (timing == NumbersTiming.ONSET && types.size() == 3) {
			return true;
		}
		return false;
	}

	public static final int AGE_INTERVAL = 7;

	@Override
	public JFreeChart buildChart(int dayOfData) {

		TimeSeriesCollection collection = new TimeSeriesCollection();
		DeviationRenderer renderer = new DeviationRenderer(true, false);
		int seriesCount = 0;

		for (NumbersType type : types) {
			IncompleteNumbers numbers = stats.getNumbers(type, timing);

			if (!numbers.dayHasData(dayOfData) || !numbers.dayHasData(dayOfData - AGE_INTERVAL)) {
				continue;
			}

			double total = 0;
			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= dayOfData; dayOfType++) {
				total += numbers.getNewNumbers(dayOfData, dayOfType, 7);
			}
			if (total <= 0) {
				continue;
			}
			TimeSeries series = new TimeSeries(String.format("%s (%,d)", type.capName, Math.round(total)));
			for (int dayOfType = numbers.getFirstDayOfType(); dayOfType <= dayOfData; dayOfType++) {
				double number = numbers.getNewNumbers(dayOfData, dayOfType, AGE_INTERVAL);
				series.add(CalendarUtils.dayToDay(dayOfType), 100.0 * number / total);
			}

			collection.addSeries(series);
			renderer.setSeriesStroke(seriesCount, new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			renderer.setSeriesPaint(seriesCount, type.color);
			renderer.setSeriesFillPaint(seriesCount, type.color.darker());
			seriesCount++;
		}

		StringBuilder title = new StringBuilder();
		title.append("Date of " + timing.lowerName + " of newly released ");
		if (types.size() > 1) {
			title.append("numbers");
		} else {
			for (NumbersType type : types) {
				title.append(type.lowerName);
			}
		}
		title.append(" (%) \n");
		title.append(String.format("from %s to %s", CalendarUtils.dayToDate(dayOfData - AGE_INTERVAL),
				CalendarUtils.dayToDate(dayOfData)));

		JFreeChart chart = ChartFactory.createTimeSeriesChart(title.toString(), "Date", "Count", collection);

		XYPlot plot = chart.getXYPlot();
		plot.setRenderer(renderer);

		DateAxis xAxis = new DateAxis("Date");
		int last = stats.getLastDay();
		int first = last - 180;
		xAxis.setMinimumDate(CalendarUtils.dayToJavaDate(first));
		xAxis.setMaximumDate(CalendarUtils.dayToJavaDate(last));
		plot.setDomainAxis(xAxis);

		return chart;
	}

	@Override
	public String getName() {
		return "daily-age-" + NumbersType.name(types, "-") + "-" + timing.lowerName;
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

	@Override
	public boolean dayHasData(int dayOfData) {
		for (NumbersType type : types) {
			IncompleteNumbers n = stats.getNumbers(type, timing);
			if (n.dayHasData(dayOfData)) {
				return true;
			}
		}
		return false;
	}
}
