package covid;

import java.util.HashMap;

import org.jfree.data.time.TimeSeries;

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

	public FinalNumbers(NumbersType type) {
		super(type);
	}

	public int getFirstDay() {
		return firstDay;
	}

	public int getLastDay() {
		return lastDay;
	}

	public boolean hasData() {
		return lastDay >= firstDay;
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
		/*
		 * case CUMULATIVE: return cumulative.get(day);
		 */
		case AVERAGE:
			return getNumbersInInterval(lastDayOfCalc, smoothing.getDays()) / smoothing.getDays();
		case TOTAL:
			return getNumbersInInterval(lastDayOfCalc, smoothing.getDays());
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

	public synchronized void makeTimeSeries(TimeSeries series, Smoothing smoothing, boolean isLogarithmic) {
		for (int day = getFirstDay(); day <= getLastDay(); day++) {
			double numbers = getNumbers(day, smoothing);

			if (!Double.isFinite(numbers)) {
				throw new RuntimeException("Uh oh.");
			}
			if (!isLogarithmic || numbers > 0) {
				series.add(CalendarUtils.dayToDay(day), numbers);
			}
		}
	}

	/**
	 * Sometimes (only with counties?), there are just empty values in the
	 * middle.
	 * 
	 * We fill those in with the values from the previous day.
	 */
	public synchronized void smoothNulls() {
		if (!hasData()) {
			return;
		}
		double min = cumulative.get(firstDay);

		for (int day = firstDay + 1; day <= lastDay; day++) {
			Double number = cumulative.get(day);

			if (number == null) {
				cumulative.put(day, min);
			} else {
				min = number;
			}
		}
	}

	/**
	 * Sometimes (often), cumulative numbers will rise and then drop again. This
	 * is just reporting error, and while small inaccuracies are unavoidable,
	 * negatives tend to break things.
	 * 
	 * To avoid this, we count backwards and if there are increases (temporally
	 * in reverse), flatten them.
	 * 
	 * End result is that a [1, 1, 3, 2, 4] will be changed to [1, 1, 2, 2, 4].
	 * 
	 * Nulls must be smoothed first.
	 */
	public synchronized void smoothDrops() {
		if (!hasData()) {
			return;
		}
		double max = cumulative.get(lastDay);

		for (int day = lastDay - 1; day >= firstDay; day--) {
			double number = cumulative.get(day);
			if (number > max) {
				cumulative.put(day, max);
				// System.out.println("Dropping cumulative to " + max);
			} else {
				max = number;
			}
		}
	}

	/**
	 * This is another hack to "fix" issues in the data. Some days with (rarely)
	 * test counts just aren't included, then 3 days later you get all 3 days
	 * worth of tests added at once. This fucks with the positivity, and really
	 * has no other effect. To balance it out we iterate over those days and
	 * distribute the tests backwards evenly with the tests.
	 * 
	 * E.g., say we had 1 case and 2 tests every day, but the test data was
	 * incomplete so it was [2, 2, 2, 8, 10] while cases were [1, 2, 3, 4, 5].
	 * Then we just take those 6 new tests from that new day and distribute them
	 * linearly (in this example) as [2, 4, 6, 8, 10].
	 * 
	 * This could be used with other values, but...I doubt it would ever be
	 * useful.
	 */
	public synchronized void smoothFlatDays(FinalNumbers source) {
		if (!hasData()) {
			return;
		}

		for (int day = firstDay; day < lastDay; day++) {
			double number = cumulative.get(day);

			int jumpDay = day + 1;
			if (cumulative.get(jumpDay) == number) {
				for (; jumpDay <= lastDay; jumpDay++) {
					if (cumulative.get(jumpDay) != number) {
						break;
					}
				}

				System.out.println("Jump on " + getType().lowerName + " from " + CalendarUtils.dayToDate(day) + " to "
						+ CalendarUtils.dayToDate(jumpDay));

				if (jumpDay <= lastDay) {
					int interval = jumpDay - day;
					double baseline = source.cumulative.get(day);
					double margin = (cumulative.get(jumpDay) - number);
					double ratioDiff = source.cumulative.get(jumpDay) - baseline;
					double ratio = margin / ratioDiff;

					for (int d = 1; d < interval; d++) {
						double matcher = source.cumulative.get(day + d) - baseline;
						double newValue = number + matcher * ratio;
						System.out.println("Changing " + getType().lowerName + " on " + CalendarUtils.dayToDate(day + d)
								+ " from " + cumulative.get(day + d) + " to " + newValue);
						cumulative.put(day + d, newValue);
					}
				}

				day = jumpDay;
			}
		}
	}

	/**
	 * Combined function to smooth drops and nulls and ...
	 */
	public synchronized void smooth() {
		smoothNulls();
		smoothDrops();
	}
}
