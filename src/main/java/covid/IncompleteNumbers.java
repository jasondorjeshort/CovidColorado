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
 * @author jdorje@gmail.com
 */
public class IncompleteNumbers extends Numbers {

	public class Incomplete {
		int samples = 0;
		double ratio = 1; // day-day ratio
		double multiplier = 1; // multiplication of all ratios
	}

	private boolean isCumulative; // we want daily numbers, but the sheet only
									// gives cumulative

	private int firstDayOfType = Integer.MAX_VALUE;
	private int firstDayOfData = Integer.MAX_VALUE;
	private int lastDayOfData = Integer.MIN_VALUE;
	// The last day of type is implicitly the same as the last day of data;
	// sometimes we have to fill in 0s to get there

	public boolean hasData() {
		return lastDayOfData >= firstDayOfData || lastDayOfData >= firstDayOfType;
	}

	protected class Daily {
		protected final HashMap<Integer, Double> numbers = new HashMap<>();
		protected final HashMap<Integer, Double> cumulativeNumbers = new HashMap<>();
		protected final HashMap<Integer, Double> projected = new HashMap<>();
		protected final HashMap<Integer, Double> cumulativeProjected = new HashMap<>();
		protected final HashMap<Integer, Incomplete> ratios = new HashMap<>();

		protected double getNumbers(int day, boolean isProjected) {
			if (isProjected) {
				return projected.get(day);
			}
			return numbers.get(day);
		}
	}

	private final HashMap<Integer, Daily> allNumbers = new HashMap<>();

	private final NumbersTiming timing;

	public IncompleteNumbers(NumbersType type, NumbersTiming timing) {
		super(type);
		this.timing = timing;
	}

	public NumbersTiming getTiming() {
		return timing;
	}

	private static final boolean USE_AVERAGES_FOR_PROJECTIONS = false;

	public synchronized double getNumbers(int dayOfData, int dayOfType, boolean projected) {
		if (projected) {
			if (USE_AVERAGES_FOR_PROJECTIONS) {
				/*
				 * This is something I played around with to counter the
				 * day-of-week issues with the rolling average system. But while
				 * it does negate that, it also decreases accuracy by averaging
				 * in older data.
				 */
				int days = 0;
				double total = 0;
				for (int _dayOfData = dayOfData - 6; _dayOfData <= dayOfData; _dayOfData++) {
					double daily = getProjectedNumbers(_dayOfData, dayOfType);
					if (daily != 0) {
						total += daily;
						days++;
					}
				}
				if (days == 0) {
					return 0;
				}
				return total / days;
			}
			return getProjectedNumbers(dayOfData, dayOfType);
		}
		return getNumbers(dayOfData, dayOfType);
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

	public synchronized double getCumulativeNumbers(int dayOfData, int dayOfType, boolean projected) {
		if (dayOfType < firstDayOfType) {
			return 0;
		}
		if (dayOfType >= dayOfData) {
			dayOfType = dayOfData;
		}
		Daily daily = allNumbers.get(dayOfData);
		String dateOfData = CalendarUtils.dayToDate(dayOfData);
		String dateOfType = CalendarUtils.dayToDate(dayOfType);
		Double value;
		if (daily == null) {
			return 0;
		}
		if (projected) {
			return daily.cumulativeProjected.get(dayOfType);
		}
		return daily.cumulativeNumbers.get(dayOfType);
	}

	public synchronized double getNumbers(int dayOfData, int dayOfType, boolean projected, Smoothing smoothing) {

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
		case CUMULATIVE:
			return getCumulativeNumbers(dayOfData, dayOfType, projected);
		case AVERAGE:
		case TOTAL:
			double sum = getCumulativeNumbers(dayOfData, lastDayOfCalc, projected)
					- getCumulativeNumbers(dayOfData, lastDayOfCalc - smoothing.getDays(), projected);
			if (smoothing.getType() == Smoothing.Type.AVERAGE) {
				sum /= smoothing.getDays();
			}
			return sum;
		case GEOMETRIC_AVERAGE:
			double product = 1.0;
			for (int d = lastDayOfCalc - smoothing.getDays() + 1; d <= lastDayOfCalc; d++) {
				// unsolvable issue with negative tests here. Analytics...
				product *= Math.max(getNumbers(dayOfData, d, projected), 0.0);
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

	public synchronized double getNumbers(int dayOfData, int dayOfType, boolean projected, int interval) {
		double numbers = 0;
		for (int d = 0; d < interval; d++) {
			numbers += getNumbers(dayOfData, dayOfType - d, projected);
		}
		return numbers;
	}

	/*
	 * Returns the new numbers for this day-of-type that appeared on the given
	 * day-of-data
	 */
	public synchronized double getNewNumbers(int dayOfData, int dayOfType) {
		return getNumbers(dayOfData, dayOfType) - getNumbers(dayOfData - 1, dayOfType);
	}

	public synchronized double getNumbers(int dayOfData, int dayOfType) {
		Daily daily = allNumbers.get(dayOfData);
		if (daily == null) {
			return 0;
		}
		Double i = daily.numbers.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public synchronized double getProjectedNumbers(int dayOfData, int dayOfType) {
		Daily daily = allNumbers.get(dayOfData);
		if (daily == null) {
			return 0;
		}
		Double i = daily.projected.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	private Incomplete getIncompletion(int dayOfType, int delay) {
		if (dayOfType < firstDayOfType || dayOfType > lastDayOfData) {
			throw new RuntimeException("Uh oh: " + CalendarUtils.dayToDate(dayOfType) + " is not between "
					+ CalendarUtils.dayToDate(firstDayOfType) + " and " + CalendarUtils.dayToDate(lastDayOfData));
		}
		Daily daily = allNumbers.get(dayOfType);
		Incomplete incompletion = daily.ratios.get(delay);
		if (incompletion == null) {
			incompletion = new Incomplete();
			daily.ratios.put(delay, incompletion);
		}
		return incompletion;
	}

	private static final double SAMPLE_DAYS = 14;

	public synchronized boolean build() {
		if (getType() != NumbersType.HOSPITALIZATIONS || timing != NumbersTiming.INFECTION) {
			// return false;
		}
		int logDayOfType = -100; // CalendarUtils.dateToDay("12-28-2020");
		if (logDayOfType > 0) {
			System.out.println("Doing build for " + getType() + "/" + timing + " from "
					+ CalendarUtils.dayToDate(firstDayOfData) + " / " + CalendarUtils.dayToDate(firstDayOfType) + " to "
					+ CalendarUtils.dayToDate(lastDayOfData) + " / " + CalendarUtils.dayToDate(lastDayOfData));
		}

		if (isCumulative) {
			// first smooth values so we don't end up with negative daily
			// values. We can only smooth within an individual day of data;
			// negatives can still happen between days of data.
			for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
				double min = 0;
				Daily daily = allNumbers.get(dayOfData);
				for (int dayOfType = firstDayOfType; dayOfType <= dayOfData; dayOfType++) {
					Double numbers = daily.numbers.get(dayOfType);
					if (numbers == null || numbers < min) {
						numbers = min;
						daily.numbers.put(dayOfType, numbers);
					}
					min = numbers;
				}
			}

			// ???
			/*
			 * for (int dayOfData = logDayOfType; dayOfData <= lastDayOfData;
			 * dayOfData++) { double numbers = getNumbers(dayOfData,
			 * logDayOfType); }
			 */

			// TODO: should avoid negatives first
			for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
				double last = 0;
				Daily daily = allNumbers.get(dayOfData);
				for (int dayOfType = firstDayOfType; dayOfType <= dayOfData; dayOfType++) {
					double newLast = getNumbers(dayOfData, dayOfType);

					if (dayOfType == logDayOfType) {
						System.out.println("De-cumulating " + CalendarUtils.dayToDate(logDayOfType) + " on "
								+ CalendarUtils.dayToDate(dayOfData) + " to " + (newLast - last));
					}
					daily.numbers.put(dayOfType, newLast - last);
					last = newLast;
				}
			}

			isCumulative = false;
		}

		/*
		 * Delay 10 means the difference from day 10 to day 11. This will be in
		 * the array under incomplete[10].
		 */
		int continuing = 0, nc = 0;

		for (int delay = 0; delay < lastDayOfData - firstDayOfType; delay++) {
			for (int typeDay = firstDayOfType; typeDay < lastDayOfData - delay; typeDay++) {
				int dayOfData1 = typeDay + delay;
				int dayOfData2 = typeDay + delay + 1;

				double numbers1 = getNumbers(dayOfData1, typeDay);
				double numbers2 = getNumbers(dayOfData2, typeDay);

				if (typeDay == logDayOfType) {
					System.out.println("Numbers about " + CalendarUtils.dayToDate(logDayOfType) + " jumped from "
							+ numbers1 + " to " + numbers2 + " on " + CalendarUtils.dayToDate(dayOfData1) + " to "
							+ CalendarUtils.dayToDate(dayOfData2));
				}

				if (numbers1 <= 0 || numbers2 <= 0 || !Double.isFinite(numbers1) || !Double.isFinite(numbers2)) {
					if (typeDay == logDayOfType) {
						System.out.println("Continuing..." + continuing);
						continuing++;
					}
					continue;
				}
				double newRatio = numbers2 / numbers1;
				if (typeDay == logDayOfType) {
					System.out.println("Not continuing..." + nc + " ...with new ratio " + newRatio + " as " + numbers1
							+ " -> " + numbers2);
					nc++;
				}

				Incomplete incomplete1 = getIncompletion(dayOfData1, delay);
				Incomplete incomplete2 = getIncompletion(dayOfData2, delay);

				if (incomplete1 == null) {
					new Exception("Incomplete1 null " + dayOfData1).printStackTrace();
					continue;
				}
				if (incomplete2 == null) {
					new Exception("Incomplete2 null " + dayOfData2).printStackTrace();
					continue;
				}

				incomplete2.samples = incomplete1.samples + 1;
				double sampleDays = SAMPLE_DAYS + delay;
				if (incomplete1.samples < sampleDays) {
					double samplePortion = (double) incomplete1.samples / incomplete2.samples;
					incomplete2.ratio = Math.pow(incomplete1.ratio, samplePortion)
							* Math.pow(newRatio, 1 - samplePortion);
				} else {
					incomplete2.ratio = Math.pow(incomplete1.ratio, (sampleDays - 1) / sampleDays)
							* Math.pow(newRatio, 1.0 / sampleDays);
				}
				if (typeDay == logDayOfType) {
					System.out.println("New ratio for delay=" + delay + " is " + incomplete2.ratio + " with "
							+ incomplete2.samples + " samples.");
				}
			}
		}

		// projections. Could do this in quadratic instead of cubic time if we
		// assempled multipliers along the way, but reordering the logic seems
		// tedious.
		for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
			Daily daily = allNumbers.get(dayOfData);
			if (daily == null) {
				if (logDayOfType > 0) {
					new Exception("Problem with " + getType() + "/" + timing + " on day " + dayOfData + " aka "
							+ CalendarUtils.dayToDate(dayOfData)).printStackTrace();
				}
				continue;
			}
			for (int dayOfType = firstDayOfType; dayOfType <= dayOfData; dayOfType++) {
				Double p = daily.numbers.get(dayOfType);
				if (p == null) {
					continue;
				}
				double projected = p;
				Incomplete multiplier = daily.ratios.get(dayOfData - dayOfType);
				for (int delay = dayOfData - dayOfType;; delay++) {
					Incomplete ratio = daily.ratios.get(delay);
					if (dayOfData == logDayOfType + 10 && dayOfType == logDayOfType) {
						System.out.print("On " + CalendarUtils.dayToDate(dayOfData) + " for "
								+ CalendarUtils.dayToDate(dayOfType) + "; delay " + delay + ": ");
					}
					if (ratio == null) {
						if (dayOfData == logDayOfType + 10 && dayOfType == logDayOfType) {
							System.out.println("Breaking out with null ratio");
						}
						break;
					}
					if (ratio.ratio == 0.0) {
						if (dayOfData == logDayOfType + 10 && dayOfType == logDayOfType) {
							System.out.println("Continuing with 0 ratio");
						}
						continue;
					}
					if (delay > 30 && ratio.samples < 60) {
						if (dayOfData == logDayOfType + 10 && dayOfType == logDayOfType) {
							System.out.println("Breaking out with not enough values ");
						}
						// two months of samples before we consider adjusting
						break;
					}
					if (dayOfData == logDayOfType + 10 && dayOfType == logDayOfType) {
						System.out
								.println("Adjusting previous projection of " + projected + " by factor " + ratio.ratio);
					}
					projected *= ratio.ratio;
					multiplier.multiplier *= ratio.ratio;
				}
				if (dayOfData == logDayOfType + 10 && dayOfType == logDayOfType) {
					System.out.println("On " + CalendarUtils.dayToDate(dayOfData) + ", projection for " + getType()
							+ "/" + timing + " on " + CalendarUtils.dayToDate(dayOfType) + " is " + projected + " vs "
							+ getNumbers(dayOfData, dayOfType) + ".");
				}
				daily.projected.put(dayOfType, projected);
			}
		}

		/*
		 * Build cumulative numbers
		 */
		for (int dayOfData = firstDayOfData; dayOfData <= lastDayOfData; dayOfData++) {
			double cumulative = 0, cumulativeProjected = 0;
			Daily daily = allNumbers.get(dayOfData);
			if (daily == null) {
				continue;
			}
			for (int dayOfType = firstDayOfType; dayOfType <= dayOfData; dayOfType++) {
				cumulative += getNumbers(dayOfData, dayOfType);
				daily.cumulativeNumbers.put(dayOfType, cumulative);

				cumulativeProjected += getProjectedNumbers(dayOfData, dayOfType);
				daily.cumulativeProjected.put(dayOfType, cumulativeProjected);
			}
		}

		return true;
	}

	// not used
	public synchronized TimeSeries createTimeSeries(int dayOfData, String name, boolean isProjected) {
		Daily daily = allNumbers.get(dayOfData);

		int firstDay = 0;
		while (daily.getNumbers(firstDay, isProjected) == 0) {
			firstDay++;
		}

		if (name == null) {
			name = isProjected ? "Projected" : "Cases";
		}

		TimeSeries series = new TimeSeries(name);
		for (int day = firstDay; day < allNumbers.size(); day++) {
			series.add(CalendarUtils.dayToDay(day), daily.getNumbers(day, isProjected));
		}

		return series;
	}

	/**
	 * Sets numbers for the given days.
	 */
	public synchronized void setNumbers(int dayOfData, int dayOfType, double numbers) {
		Daily daily = allNumbers.get(dayOfData);
		if (daily == null) {
			if (numbers == 0.0) {
				return; // avoid unnecessary first/last days
			}
			daily = new Daily();
			allNumbers.put(dayOfData, daily);
		}
		if (daily.numbers.get(dayOfType) == null && numbers == 0.0) {
			return; // avoid unnecessary first/last days
		}
		if (daily.numbers.get(dayOfType) != null && numbers == daily.numbers.get(dayOfType)) {
			return; // avoid unnecessary first/last days
		}
		daily.numbers.put(dayOfType, numbers);

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
		Daily daily = allNumbers.get(dayOfData);
		if (daily == null) {
			daily = new Daily();
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
			for (int dayOfType = firstDayOfType; dayOfType < dayOfData; dayOfType++) {
				double newNumbers = getNumbers(dayOfData, dayOfType) - getNumbers(dayOfData - 1, dayOfType);
				numbersSum += newNumbers;
				daySum += newNumbers * (dayOfData - dayOfType);
			}
		}

		return daySum / numbersSum;
	}
}
