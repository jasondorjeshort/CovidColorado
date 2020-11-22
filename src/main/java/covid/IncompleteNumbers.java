package covid;

import java.util.ArrayList;

import org.jfree.data.time.TimeSeries;

public class IncompleteNumbers {

	public class Incomplete {
		int samples = 0;
		double ratio = 1;
	}

	private boolean isCumulative; // we want daily numbers, but the sheet only
									// gives cumulative

	protected class Daily {
		protected final ArrayList<Integer> numbers = new ArrayList<>();
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

	public int getNumbers(int dayOfData, int dayOfType) {
		if (dayOfData >= allNumbers.size()) {
			return 0;
		}
		Daily daily = allNumbers.get(dayOfData);
		if (dayOfType >= daily.numbers.size() || dayOfType < 0) {
			return 0;
		}
		Integer i = daily.numbers.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public double getProjectedNumbers(int dayOfData, int dayOfType) {
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

	public void build(ColoradoStats stats) {
		if (isCumulative) {
			for (int dayOfData = 0; dayOfData < allNumbers.size(); dayOfData++) {
				int last = 0;
				Daily daily = allNumbers.get(dayOfData);
				for (int dayOfType = 0; dayOfType < dayOfData && dayOfType < daily.numbers.size(); dayOfType++) {
					int newLast = daily.numbers.get(dayOfType);
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
		for (int delay = 0; delay < 900; delay++) {
			for (int typeDay = 0; typeDay < stats.getLastDay() - delay; typeDay++) {
				int dayOfData1 = typeDay + delay;
				int dayOfData2 = typeDay + delay + 1;

				int numbers1 = getNumbers(dayOfData1, typeDay);
				int numbers2 = getNumbers(dayOfData2, typeDay);
				double newRatio = (double) numbers2 / numbers1;

				if (numbers1 <= 0 || numbers2 <= 0) {
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
			for (int dayOfType = 0; dayOfType < dayOfData && dayOfType < daily.numbers.size(); dayOfType++) {
				Integer p = daily.numbers.get(dayOfType);
				if (p == null) {
					continue;
				}
				double projected = p;
				for (int delay = dayOfData - dayOfType; delay < daily.ratios.size(); delay++) {
					projected *= daily.ratios.get(delay).ratio;
				}
				while (daily.projected.size() <= dayOfType) {
					daily.projected.add(0.0);
				}
				daily.projected.set(dayOfType, projected);
			}
		}
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
			series.add(Date.dayToDay(day), daily.getNumbers(day, isProjected));
		}

		return series;
	}

	public void setNumbers(int dayOfData, int dayOfType, int numbers) {
		while (allNumbers.size() <= dayOfData) {
			allNumbers.add(new Daily());
		}
		Daily daily = allNumbers.get(dayOfData);
		while (daily.numbers.size() <= dayOfType) {
			daily.numbers.add(0);
		}
		daily.numbers.set(dayOfType, numbers);
	}

	public void setCumulative() {
		isCumulative = true;
	}
}
