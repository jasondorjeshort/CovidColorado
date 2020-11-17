package CovidColorado;

import java.util.ArrayList;

public class IncompleteCases {

	public class Incomplete {
		int samples = 0;
		double ratio = 1;
	}

	public class Daily {
		public final ArrayList<Integer> cases = new ArrayList<>();
		public final ArrayList<Double> projected = new ArrayList<>();
		public final ArrayList<Incomplete> ratios = new ArrayList<>();
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

	public void buildIncompletes(CovidStats stats) {
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
		for (int dayOfData = stats.getFirstDay(); dayOfData <= stats.getLastDay(); dayOfData++) {
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
}
