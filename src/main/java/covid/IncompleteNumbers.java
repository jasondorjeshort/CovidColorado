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
 * @author jdorje@gmail.com
 */
public class IncompleteNumbers extends Numbers {

	/*
	 * we want daily numbers, but the sheet only gives cumulative for some
	 */
	private boolean isCumulative;

	private int firstDayOfType = Integer.MAX_VALUE;
	private int firstDayOfData = Integer.MAX_VALUE;
	private int lastDayOfData = Integer.MIN_VALUE;
	// The last day of type is implicitly the same as the last day of data;
	// sometimes we have to fill in 0s to get there

	public boolean hasData() {
		return lastDayOfData >= firstDayOfData || lastDayOfData >= firstDayOfType;
	}

	protected class DayOfData {
		protected final HashMap<Integer, Double> numbers = new HashMap<>();
		protected final HashMap<Integer, Double> R = new HashMap<>();
	}

	private final HashMap<Integer, DayOfData> allNumbers = new HashMap<>();

	private final NumbersTiming timing;

	public IncompleteNumbers(NumbersType type, NumbersTiming timing) {
		super(type);
		this.timing = timing;
	}

	public NumbersTiming getTiming() {
		return timing;
	}

	public synchronized double getNumbers(int dayOfData, int dayOfType) {
		DayOfData daily = allNumbers.get(dayOfData);

		if (daily == null) {
			return 0.0;
		}

		Double number = daily.numbers.get(dayOfType);

		if (number == null) {
			return 0.0;
		}

		return number;
	}

	public synchronized int getFirstDayOfType() {
		return firstDayOfType;
	}

	public synchronized int getFirstDayOfData() {
		return firstDayOfData;
	}

	public synchronized int getLastDay() {
		return lastDayOfData;
	}

	public synchronized double getNumbers(int dayOfData, int dayOfType, Smoothing smoothing) {
		int lastDayOfCalc;

		switch (smoothing.getTiming()) {
		case SYMMETRIC:
			lastDayOfCalc = dayOfType + smoothing.getDays() / 2;
			break;
		case TRAILING:
			lastDayOfCalc = dayOfType;
			break;
		default:
			throw new RuntimeException("...");
		}

		switch (smoothing.getType()) {
		case AVERAGE:
		case TOTAL:
			double sum = 0;
			for (int d = lastDayOfCalc - smoothing.getDays() + 1; d <= lastDayOfCalc; d++) {
				double v = getNumbers(dayOfData, d);
				sum += v;
			}

			if (smoothing.getType() == Smoothing.Type.AVERAGE) {
				sum /= smoothing.getDays();
			}
			return sum;
		case GEOMETRIC_AVERAGE:
			double product = 1.0;
			for (int d = lastDayOfCalc - smoothing.getDays() + 1; d <= lastDayOfCalc; d++) {
				double v = getNumbers(dayOfData, d);
				// unsolvable issue with negative tests here. Analytics...
				product *= Math.max(v, 0.0);
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

	public synchronized Double getBigR(int dayOfData, int dayOfType) {
		if (dayOfType < firstDayOfType || dayOfData < firstDayOfData) {
			return null;
		}
		if (dayOfType > lastDayOfData || dayOfData > lastDayOfData) {
			return null;
		}

		DayOfData daily = allNumbers.get(dayOfData);

		int count = 0;
		double r = 1.0;
		int RANGE = 5;
		for (int d = dayOfType - RANGE; d <= dayOfType + RANGE; d++) {
			Double dailyR = daily.R.get(d);
			if (dailyR == null) {
				return null;
			}
			r *= dailyR;
			count++;
		}

		return Math.pow(r, 1.0 / count);
	}

	/*
	 * Returns the new numbers for this day-of-type that appeared on the given
	 * day-of-data
	 */
	public synchronized double getNewNumbers(int dayOfData, int dayOfType) {
		return getNumbers(dayOfData, dayOfType) - getNumbers(dayOfData - 1, dayOfType);
	}

	public synchronized boolean build() {

		/**
		 * Fill in empty values
		 */
		for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
			if (allNumbers.get(dayOfData) == null) {
				/*
				 * new Exception("Null day of data for " + getType() + "-" +
				 * getTiming() + " on " +
				 * CalendarUtils.dayToDate(dayOfData)).printStackTrace();
				 */
				DayOfData daily = new DayOfData();
				allNumbers.put(dayOfData, daily);
			}

			DayOfData daily = allNumbers.get(dayOfData);
			boolean started = false;
			for (int dayOfType = lastDayOfData; dayOfType >= firstDayOfType; dayOfType--) {
				if (daily.numbers.get(dayOfType) == null) {
					if (started) {
						daily.numbers.put(dayOfType, 0.0);
					}
				} else {
					started = true;
				}
			}
		}

		if (isCumulative) {
			// first smooth values so we don't end up with negative daily
			// values. We can only smooth within an individual day of data;
			// negatives can still happen between days of data.
			for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
				double min = 0;
				DayOfData daily = allNumbers.get(dayOfData);
				for (int dayOfType = firstDayOfType; dayOfType <= dayOfData; dayOfType++) {
					Double numbers = daily.numbers.get(dayOfType);
					if (numbers == null || numbers < min) {
						numbers = min;
						daily.numbers.put(dayOfType, numbers);
					}
					min = numbers;
				}
			}

			// TODO: should avoid negatives first
			for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
				double last = 0;
				DayOfData daily = allNumbers.get(dayOfData);
				for (int dayOfType = firstDayOfType; dayOfType <= dayOfData; dayOfType++) {
					double newLast = getNumbers(dayOfData, dayOfType);

					daily.numbers.put(dayOfType, newLast - last);
					last = newLast;
				}
			}

			isCumulative = false;
		}

		int SERIAL_INTERVAL = 5;
		// kinda have to do weekly or we hit day-of-week issues
		int R_SMOOTHING_INTERVAL = 7;

		Smoothing smoothing = new Smoothing(R_SMOOTHING_INTERVAL, Smoothing.Type.AVERAGE, Smoothing.Timing.SYMMETRIC);
		for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
			DayOfData daily = allNumbers.get(dayOfData);
			if (daily == null) {
				continue;
			}

			for (int dayOfType = firstDayOfType; dayOfType <= dayOfData - SERIAL_INTERVAL; dayOfType++) {
				Double end = getNumbers(dayOfData, dayOfType, smoothing);
				Double start = getNumbers(dayOfData, dayOfType - SERIAL_INTERVAL, smoothing);
				if (end != null && start != null && end != 0 && start != 0) {
					daily.R.put(dayOfType, end / start);
				}
			}
		}

		return true;
	}

	/**
	 * Sets numbers for the given days.
	 */
	public synchronized void setNumbers(int dayOfData, int dayOfType, double numbers) {
		DayOfData daily = allNumbers.get(dayOfData);
		if (daily == null) {
			if (numbers == 0.0) {
				return; // avoid unnecessary first/last days
			}
			daily = new DayOfData();
			allNumbers.put(dayOfData, daily);
		}
		if (daily.numbers.get(dayOfType) == null && numbers == 0.0) {
			return; // avoid unnecessary first/last days
		}
		if (daily.numbers.get(dayOfType) != null && numbers == daily.numbers.get(dayOfType)) {
			return; // avoid unnecessary first/last days
		}
		if (false && getTiming() == NumbersTiming.INFECTION) {
			daily.numbers.put(dayOfType, numbers / 2);
			daily.numbers.put(dayOfType + 1, numbers / 4);
			daily.numbers.put(dayOfType - 1, numbers / 4);
		} else {
			daily.numbers.put(dayOfType, numbers);
		}

		firstDayOfData = Math.min(firstDayOfData, dayOfData);
		lastDayOfData = Math.max(lastDayOfData, dayOfData);
		firstDayOfType = Math.min(firstDayOfType, dayOfType);
	}

	/**
	 * Adds more numbers for the given days.
	 */
	public synchronized void addNumbers(int dayOfData, int dayOfType, double numbers) {
		if (numbers == 0.0) {
			return; // avoid unnecessary first/last days
		}
		DayOfData daily = allNumbers.get(dayOfData);
		if (daily == null) {
			daily = new DayOfData();
			allNumbers.put(dayOfData, daily);
		}
		Double original = daily.numbers.get(dayOfType);
		if (original != null) {
			numbers += original;
		}
		daily.numbers.put(dayOfType, numbers);

		firstDayOfData = Math.min(firstDayOfData, dayOfData);
		lastDayOfData = Math.max(lastDayOfData, dayOfData);
		firstDayOfType = Math.min(firstDayOfType, dayOfType);
	}

	/**
	 * Marks this numbers as a cumulative type. This later will mean some extra
	 * handling in finalize to separate it into daily numbers. Some of the
	 * fields in the CSV files are just cumulative.
	 */
	public synchronized void setCumulative() {
		isCumulative = true;
	}

	public synchronized double getAverageAgeOfNewNumbers(int baseDayOfData, int dayRange) {
		double daySum = 0, numbersSum = 0;
		int dayMinimum = baseDayOfData - dayRange + 1, dayMaximum = baseDayOfData;

		// TODO: this isn't TOO slow, but it's quadratic time and potentially
		// run multiple times. Caching could help.

		for (int dayOfData = dayMinimum; dayOfData <= dayMaximum; dayOfData++) {
			for (int dayOfType = firstDayOfType; dayOfType <= dayOfData; dayOfType++) {
				double n2 = getNumbers(dayOfData, dayOfType);
				double n1 = getNumbers(dayOfData - 1, dayOfType);
				double newNumbers = n2 - n1;
				numbersSum += newNumbers;
				daySum += newNumbers * (dayOfData - dayOfType);
			}
		}

		return daySum / numbersSum;
	}
}
