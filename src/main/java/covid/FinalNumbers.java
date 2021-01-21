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

	private final HashMap<Integer, Integer> cumulative = new HashMap<>();
	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;

	public FinalNumbers(NumbersType type) {
		super(type);
	}

	public synchronized int getNumbersInInterval(int day, int interval) {
		return getCumulativeNumbers(day) - getCumulativeNumbers(day - interval);
	}

	public synchronized int getDailyNumbers(int day) {
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

	public synchronized int getCumulativeNumbers(int day) {
		if (day < firstDay) {
			return 0;
		}
		if (day > lastDay) {
			day = lastDay;
		}
		Integer numbersForDay = cumulative.get(day);
		if (numbersForDay == null) {
			new Exception("Uh oh!").printStackTrace();
			return getCumulativeNumbers(day - 1);
		}
		return numbersForDay;
	}

	public synchronized void setCumulativeNumbers(int day, int numbersForDay) {
		firstDay = Math.min(firstDay, day);
		lastDay = Math.max(firstDay, day);
		cumulative.put(day, numbersForDay);
	}

	public synchronized boolean build() {
		int max = Integer.MAX_VALUE;
		for (int day = lastDay; day >= firstDay; day--) {
			Integer number = cumulative.get(day);
			if (number == null || number > max) {
				cumulative.put(day, max);
			} else {
				max = number;
			}
		}
		return true;
	}
}
