package covid;

import java.util.ArrayList;

/**
 * Final and cumulative numbers.
 * 
 * Important to note each number here is a cumulative total, so you have to
 * subtract to get daily (or interval) numbers.
 * 
 * @author jdorje@gmail.com
 *
 */
public class FinalNumbers {

	private final ArrayList<Integer> numbers = new ArrayList<>();

	public int getNumbersInInterval(int day, int interval) {
		return getCumulativeNumbers(day) - getCumulativeNumbers(day - interval);
	}

	public int getDailyNumbers(int day) {
		return getNumbersInInterval(day, 1);
	}

	public double getNumbers(int day, Smoothing smoothing) {
		switch (smoothing) {
		case ALGEBRAIC_SYMMETRIC_WEEKLY:
			return getNumbersInInterval(day + 3, 7) / 7.0;
		case NONE:
			return getDailyNumbers(day);
		case TOTAL_14_DAY:
			return getNumbersInInterval(day, 14);
		default:
		}
		throw new RuntimeException("FAIL");
	}

	public int getCumulativeNumbers(int day) {
		if (day < 0 || day >= numbers.size()) {
			return 0;
		}
		Integer numbersForDay = numbers.get(day);
		if (numbersForDay == null) {
			return 0;
		}
		return numbersForDay;
	}

	public void setCumulativeNumbers(int day, int numbersForDay) {
		while (numbers.size() <= day) {
			numbers.add(0);
		}
		numbers.set(day, numbersForDay);
	}
}
