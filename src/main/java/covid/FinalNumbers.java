package covid;

import java.util.HashMap;

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
 * -----
 * 
 * Final and cumulative numbers.
 * 
 * Important to note each number here is a cumulative total, so you have to
 * subtract to get daily (or interval) numbers.
 * 
 * @author jdorje@gmail.com
 *
 */
public class FinalNumbers extends Numbers {

	private final HashMap<Integer, Double> cumulative = new HashMap<>();
	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;

	public int getFirstDay() {
		return firstDay;
	}

	public int getLastDay() {
		return lastDay;
	}

	public FinalNumbers(NumbersType type) {
		super(type);
	}

	public synchronized double getNumbersInInterval(int day, int interval) {
		return getCumulativeNumbers(day) - getCumulativeNumbers(day - interval);
	}

	public synchronized double getDailyNumbers(int day) {
		return getNumbersInInterval(day, 1);
	}

	public synchronized double getNumbers(int day, Smoothing smoothing) {

		int lastDayOfCalc;

		switch (smoothing.getTiming()) {
		case SYMMETRIC:
			lastDayOfCalc = day + smoothing.getDays() / 2;
			break;
		case TRAILING:
			lastDayOfCalc = day;
			break;
		default:
			throw new RuntimeException("...");
		}

		switch (smoothing.getType()) {
		case AVERAGE:
			return getNumbersInInterval(lastDay, smoothing.getDays()) / smoothing.getDays();
		case TOTAL:
			return getNumbersInInterval(lastDay, smoothing.getDays());
		case GEOMETRIC_AVERAGE:
			double product = 1.0;
			for (int d = lastDayOfCalc - smoothing.getDays() + 1; d <= lastDayOfCalc; d++) {
				product *= Math.max(getDailyNumbers(d), 1.0);
			}
			product = Math.pow(product, 1.0 / smoothing.getDays());
			if (!Double.isFinite(product)) {
				throw new RuntimeException("Uh oh: " + product);
			}
			return product;
		default:
			break;
		}
		throw new RuntimeException("FAIL");
	}

	public synchronized double getCumulativeNumbers(int day) {
		if (day < firstDay) {
			return 0;
		}
		if (day > lastDay) {
			day = lastDay;
		}
		Double numbersForDay = cumulative.get(day);
		if (numbersForDay == null) {
			throw new RuntimeException("Uh oh: null day! On " + day + " + versus " + firstDay + "-" + lastDay);
		}
		return numbersForDay;
	}

	public synchronized void setCumulativeNumbers(int day, double numbersForDay) {
		firstDay = Math.min(firstDay, day);
		lastDay = Math.max(lastDay, day);
		cumulative.put(day, numbersForDay);
	}

	public synchronized boolean build(String tag) {
		Double max = Double.MAX_VALUE;
		for (int day = lastDay; day >= firstDay; day--) {
			Double number = cumulative.get(day);
			if (number == null) {
				// This happens!??

				// new Exception("Missing day in " + tag + "? " +
				// CalendarUtils.dayToDate(day)).printStackTrace();
			}
			if (number == null || number > max) {
				cumulative.put(day, max);
				// System.out.println("Dropping cumulative to " + max);
			} else {
				max = number;
			}
		}
		return true;
	}
}
