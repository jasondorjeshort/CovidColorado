package covid;

import java.util.ArrayList;

import org.jfree.data.time.TimeSeries;

public class IncompleteCases {

	public class Incomplete {
		int samples = 0;
		double ratio = 1;
	}

	private boolean isCumulative; // we want daily numbers, but the sheet only
									// gives cumulative

	private class Daily {
		private final ArrayList<Integer> cases = new ArrayList<>();
		private final ArrayList<Double> projected = new ArrayList<>();
		private final ArrayList<Incomplete> ratios = new ArrayList<>();

		protected double getCases(int day, boolean isProjected) {
			if (isProjected) {
				return projected.get(day);
			}
			return cases.get(day);
		}
	}

	private final ArrayList<Daily> numbers = new ArrayList<>();

	public int getCases(int dayOfData, int dayOfType) {
		if (dayOfData >= numbers.size()) {
			return 0;
		}
		Daily daily = numbers.get(dayOfData);
		if (dayOfType >= daily.cases.size()) {
			return 0;
		}
		Integer i = daily.cases.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public double getExactProjectedCases(int dayOfData, int dayOfType) {
		Daily daily = numbers.get(dayOfData);
		ArrayList<Double> cases = daily.projected;
		if (dayOfType >= cases.size()) {
			return 0;
		}
		Double i = cases.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public int getLastDay(int dayOfData) {
		return numbers.get(dayOfData).cases.size() - 1;
	}

	private Incomplete getIncompletion(int dayOfType, int delay) {
		Daily daily = numbers.get(dayOfType);
		while (daily.ratios.size() <= delay) {
			daily.ratios.add(new Incomplete());
		}
		return daily.ratios.get(delay);
	}

	public double SAMPLE_DAYS = 14;

	public void build(ColoradoStats stats) {
		if (isCumulative) {

			isCumulative = false;
		}

		/*
		 * Delay 10 means the difference from day 10 to day 11. This will be in
		 * the array under incomplete[10].
		 */
		for (int delay = 0; delay < 90; delay++) {
			for (int typeDay = stats.getFirstDay(); typeDay < stats.getLastDay() - delay; typeDay++) {
				int dayOfData1 = typeDay + delay;
				int dayOfData2 = typeDay + delay + 1;

				int cases1 = getCases(dayOfData1, typeDay);
				int cases2 = getCases(dayOfData2, typeDay);
				double newRatio = (double) cases2 / cases1;

				if (cases1 <= 0 || cases2 <= 0) {
					continue;
				}

				Incomplete incomplete1 = getIncompletion(dayOfData1, delay);
				Incomplete incomplete2 = getIncompletion(dayOfData2, delay);

				incomplete2.samples = incomplete1.samples + 1;
				if (incomplete1.samples < SAMPLE_DAYS) {
					double samplePortion = (double) incomplete1.samples / incomplete2.samples;
					incomplete2.ratio = Math.pow(incomplete1.ratio, samplePortion)
							* Math.pow(newRatio, 1 - samplePortion);
				} else {
					incomplete2.ratio = Math.pow(incomplete1.ratio, (SAMPLE_DAYS - 1) / SAMPLE_DAYS)
							* Math.pow(newRatio, 1.0 / SAMPLE_DAYS);
				}
			}
		}

		// projections
		for (int dayOfData = 0; dayOfData < numbers.size(); dayOfData++) {
			Daily daily = numbers.get(dayOfData);
			for (int typeDay = stats.getFirstDay(); typeDay < dayOfData && typeDay < daily.cases.size(); typeDay++) {
				Integer p = daily.cases.get(typeDay);
				if (p == null) {
					continue;
				}
				double projected = p;
				for (int delay = dayOfData - typeDay; delay < daily.ratios.size(); delay++) {
					projected *= daily.ratios.get(delay).ratio;
				}
				while (daily.projected.size() <= typeDay) {
					daily.projected.add(0.0);
				}
				daily.projected.set(typeDay, projected);
			}
		}
	}

	TimeSeries createTimeSeries(int dayOfData, String name, boolean isProjected) {
		Daily daily = numbers.get(dayOfData);

		int firstDay = 0;
		while (daily.getCases(firstDay, isProjected) == 0) {
			firstDay++;
		}

		if (name == null) {
			name = isProjected ? "Projected" : "Cases";
		}

		TimeSeries series = new TimeSeries(name);
		for (int day = firstDay; day < numbers.size(); day++) {
			series.add(Date.dayToDay(day), daily.getCases(day, isProjected));
		}

		return series;
	}

	public void setCases(int dayOfData, int dayOfType, int cases) {
		while (numbers.size() <= dayOfData) {
			numbers.add(new Daily());
		}
		Daily daily = numbers.get(dayOfData);
		while (daily.cases.size() <= dayOfType) {
			daily.cases.add(0);
		}
		daily.cases.set(dayOfType, cases);
	}

	public void setCumulative() {
		isCumulative = true;
	}
}
