package covid;

import java.util.ArrayList;

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
public class IncompleteNumbers {

	public class Incomplete {
		int samples = 0;
		double ratio = 1;
	}

	private boolean isCumulative; // we want daily numbers, but the sheet only
									// gives cumulative

	private int firstDayOfType = Integer.MAX_VALUE;

	protected class Daily {
		protected final ArrayList<Double> numbers = new ArrayList<>();
		protected final ArrayList<Double> projected = new ArrayList<>();
		protected final ArrayList<Incomplete> ratios = new ArrayList<>();

		protected double getNumbers(int day, boolean isProjected) {
			if (isProjected) {
				return projected.get(day);
			}
			return numbers.get(day);
		}
	}

	private final ArrayList<Daily> allNumbers = new ArrayList<>();

	public final NumbersType type;
	public final NumbersTiming timing;

	public IncompleteNumbers(NumbersType type, NumbersTiming timing) {
		this.type = type;
		this.timing = timing;
	}

	public double getNumbers(int dayOfData, int dayOfType, boolean projected) {
		if (projected) {
			return getProjectedNumbers(dayOfData, dayOfType);
		}
		return getNumbers(dayOfData, dayOfType);
	}

	public double getNumbers(int dayOfData, int dayOfType, boolean projected, Smoothing smoothing) {
		double numbers;
		switch (smoothing) {
		case ALGEBRAIC_SYMMETRIC_WEEKLY:
			numbers = 0;
			for (int d = -3; d <= 3; d++) {
				numbers += getNumbers(dayOfData, dayOfType + d, projected);
			}
			return numbers / 7.0;
		case GEOMETRIC_SYMMETRIC_WEEKLY:
			numbers = 1;
			for (int d = -3; d <= 3; d++) {
				numbers *= getNumbers(dayOfData, dayOfType + d, projected);
			}
			return Math.pow(numbers, 1.0 / 7.0);
		case GEOMETRIC_SYMMETRIC_13DAY:
			numbers = 1;
			for (int d = -6; d <= 6; d++) {
				numbers *= getNumbers(dayOfData, dayOfType + d, projected);
			}
			return Math.pow(numbers, 1.0 / 13.0);
		case GEOMETRIC_SYMMETRIC_21DAY:
			numbers = 1;
			for (int d = -10; d <= 10; d++) {
				numbers *= getNumbers(dayOfData, dayOfType + d, projected);
			}
			return Math.pow(numbers, 1.0 / 21.0);
		case NONE:
			return getNumbers(dayOfData, dayOfType, projected);
		case TOTAL_14_DAY:
			numbers = 0;
			for (int d = -13; d <= 0; d++) {
				numbers += getNumbers(dayOfData, dayOfType + d, projected);
			}
			return numbers;
		default:
		}
		throw new RuntimeException("...");
	}

	public double getNumbers(int dayOfData, int dayOfType, boolean projected, int interval) {
		double numbers = 0;
		for (int d = 0; d < interval; d++) {
			numbers += getNumbers(dayOfData, dayOfType - d, projected);
		}
		return numbers;
	}

	public double getNumbers(int dayOfData, int dayOfType) {
		if (dayOfData >= allNumbers.size() || dayOfData <= 0) {
			return 0;
		}
		Daily daily = allNumbers.get(dayOfData);
		if (dayOfType >= daily.numbers.size() || dayOfType < 0) {
			return 0;
		}
		Double i = daily.numbers.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public double getProjectedNumbers(int dayOfData, int dayOfType) {
		if (dayOfData >= allNumbers.size()) {
			return 0;
		}
		Daily daily = allNumbers.get(dayOfData);
		ArrayList<Double> projected = daily.projected;
		if (dayOfType >= projected.size() || dayOfType < 0) {
			return 0;
		}
		Double i = projected.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public int getLastDay(int dayOfData) {
		return allNumbers.get(dayOfData).numbers.size() - 1;
	}

	private Incomplete getIncompletion(int dayOfType, int delay) {
		Daily daily = allNumbers.get(dayOfType);
		while (daily.ratios.size() <= delay) {
			daily.ratios.add(new Incomplete());
		}
		return daily.ratios.get(delay);
	}

	public double SAMPLE_DAYS = 14;

	public boolean build() {
		if (isCumulative) {
			// TODO: should avoid negatives first
			for (int dayOfData = 0; dayOfData < allNumbers.size(); dayOfData++) {
				double last = 0;
				Daily daily = allNumbers.get(dayOfData);
				for (int dayOfType = 0; dayOfType < dayOfData && dayOfType < daily.numbers.size(); dayOfType++) {
					double newLast = daily.numbers.get(dayOfType);
					daily.numbers.set(dayOfType, newLast - last);
					last = newLast;
				}
			}

			isCumulative = false;
		}

		/*
		 * Delay 10 means the difference from day 10 to day 11. This will be in
		 * the array under incomplete[10].
		 */
		for (int delay = 0; delay < allNumbers.size(); delay++) {
			for (int typeDay = 0; typeDay < allNumbers.size() - delay; typeDay++) {
				int dayOfData1 = typeDay + delay;
				int dayOfData2 = typeDay + delay + 1;

				double numbers1 = getNumbers(dayOfData1, typeDay);
				double numbers2 = getNumbers(dayOfData2, typeDay);
				double newRatio = numbers2 / numbers1;

				if (numbers1 <= 0 || numbers2 <= 0 || !Double.isFinite(numbers1) || !Double.isFinite(numbers2)) {
					continue;
				}

				Incomplete incomplete1 = getIncompletion(dayOfData1, delay);
				Incomplete incomplete2 = getIncompletion(dayOfData2, delay);

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
			}
		}

		// projections
		for (int dayOfData = 0; dayOfData < allNumbers.size(); dayOfData++) {
			Daily daily = allNumbers.get(dayOfData);
			for (int dayOfType = firstDayOfType; dayOfType < dayOfData && dayOfType < daily.numbers.size(); dayOfType++) {
				Double p = daily.numbers.get(dayOfType);
				if (p == null) {
					continue;
				}
				double projected = p;
				for (int delay = dayOfData - dayOfType; delay < daily.ratios.size(); delay++) {
					Incomplete ratio = daily.ratios.get(delay);
					if (delay > 30 && ratio.samples < 60) {
						// two months of samples before we consider adjusting
						break;
					}
					projected *= ratio.ratio;
				}
				while (daily.projected.size() <= dayOfType) {
					daily.projected.add(0.0);
				}
				daily.projected.set(dayOfType, projected);
			}
		}

		return true;
	}

	TimeSeries createTimeSeries(int dayOfData, String name, boolean isProjected) {
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
	public void setNumbers(int dayOfData, int dayOfType, double numbers) {
		firstDayOfType = Math.min(firstDayOfType, dayOfType);
		if (dayOfType < 0) {
			new Exception("Improbable day-of-type " + CalendarUtils.dayToDate(dayOfType)).printStackTrace();
			dayOfType = 0;
		}

		while (allNumbers.size() <= dayOfData) {
			allNumbers.add(new Daily());
		}
		Daily daily = allNumbers.get(dayOfData);
		while (daily.numbers.size() <= dayOfType) {
			daily.numbers.add(0.0);
		}
		daily.numbers.set(dayOfType, numbers);
	}

	/**
	 * Adds more numbers for the given days.
	 */
	public void addNumbers(int dayOfData, int dayOfType, double numbers) {
		while (allNumbers.size() <= dayOfData) {
			allNumbers.add(new Daily());
		}
		Daily daily = allNumbers.get(dayOfData);
		while (daily.numbers.size() <= dayOfType) {
			daily.numbers.add(0.0);
		}
		numbers += daily.numbers.get(dayOfType);
		daily.numbers.set(dayOfType, numbers);
	}

	/**
	 * Marks this numbers as a cumulative type. This later will mean some extra
	 * handling in finalize to separate it into daily numbers. Some of the
	 * fields in the CSV files are just cumulative.
	 */
	public void setCumulative() {
		isCumulative = true;
	}

	public double getAverageAgeOfNewNumbers(int baseDayOfData, Smoothing smoothing) {
		double daySum = 0, numbersSum = 0;
		int dayMinimum = baseDayOfData, dayMaximum = baseDayOfData;

		switch (smoothing) {
		case ALGEBRAIC_SYMMETRIC_WEEKLY:
			dayMinimum = baseDayOfData - 3;
			dayMaximum = baseDayOfData + 3;
			break;
		case NONE:
			break;
		case TOTAL_14_DAY:
			dayMinimum = baseDayOfData - 13;
			break;
		case TOTAL_7_DAY:
			dayMinimum = baseDayOfData - 6;
			break;
		default:
			throw new RuntimeException("Not implemented.");
		}

		for (int dayOfData = dayMinimum; dayOfData <= dayMaximum; dayOfData++) {
			for (int dayOfType = 0; dayOfType < dayOfData; dayOfType++) {
				double newNumbers = getNumbers(dayOfData, dayOfType) - getNumbers(dayOfData - 1, dayOfType);
				numbersSum += newNumbers;
				daySum += newNumbers * (dayOfData - dayOfType);
			}
		}

		return daySum / numbersSum;
	}

}
