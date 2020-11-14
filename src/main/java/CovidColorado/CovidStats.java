package CovidColorado;

import java.util.ArrayList;
import java.util.List;

import library.MyExecutor;

public class CovidStats {

	private static final int firstDay = 48; // 2/17/2020, first day of data
	private static final int firstCSV = 77; // 77, 314

	private static String csvFileName(int day) {
		return String.format("H:\\Downloads\\CovidColoradoCSV\\covid19_case_summary_%s.csv",
				Date.dayToFullDate(day, '-'));
	}

	/*
	 * Cases of COVID-19 in Colorado by Date Reported to the State | 2020-05-10
	 * | Three-Day Moving Average Of Cases | 370 |
	 * 
	 * New format : <type> | <date> | <useless text> | <number>
	 * 
	 * 
	 * 
	 * 
	 */

	int lastDay;

	// onsetString | date | "Cases" | #
	private static final String oldOnset = "Case Counts by Onset Date";
	private static final String newOnset = "Cases of COVID-19 in Colorado by Date of Illness Onset";

	public class Incomplete {
		int samples = 0;
		double ratio = 1;
	}

	public class IncompleteCases {
		public final ArrayList<Integer> cases = new ArrayList<>();
		public final ArrayList<Double> projected = new ArrayList<>();
		public final ArrayList<Incomplete> ratios = new ArrayList<>();
	}

	public class NumbersByDay {
		public final IncompleteCases onset = new IncompleteCases();
		public final IncompleteCases reported = new IncompleteCases();
	}

	private final ArrayList<NumbersByDay> numbersByDay = new ArrayList<>();

	public int getFirstDay() {
		return firstDay;
	}

	public int getLastDay() {
		return lastDay;
	}

	public int getCasesByOnsetDay(int dayOfData, int dayOfOnset) {
		if (dayOfData >= numbersByDay.size()) {
			dayOfData = numbersByDay.size() - 1;
		}
		NumbersByDay numbers = numbersByDay.get(dayOfData);
		ArrayList<Integer> onsetCases = numbers.onset.cases;
		if (onsetCases == null) {
			System.out.println("No onset cases for " + dayOfData + " with " + dayOfOnset);
			return 0;
		}
		if (dayOfOnset >= onsetCases.size()) {
			return 0;
		}
		Integer i = onsetCases.get(dayOfOnset);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public double getExactProjectedCasesByOnsetDay(int dayOfData, int dayOfOnset) {
		if (dayOfData >= numbersByDay.size()) {
			dayOfData = numbersByDay.size() - 1;
		}
		NumbersByDay numbers = numbersByDay.get(dayOfData);
		ArrayList<Double> onsetCases = numbers.onset.projected;
		if (onsetCases == null) {
			System.out.println("No onset cases for " + dayOfData + " with " + dayOfOnset);
			return 0;
		}
		if (dayOfOnset >= onsetCases.size()) {
			return 0;
		}
		Double i = onsetCases.get(dayOfOnset);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public double getExactProjectedCasesByReportedDay(int dayOfData, int dayOfReporting) {
		if (dayOfData >= numbersByDay.size()) {
			dayOfData = numbersByDay.size() - 1;
		}
		NumbersByDay numbers = numbersByDay.get(dayOfData);
		ArrayList<Double> reportedCases = numbers.reported.projected;
		if (reportedCases == null) {
			System.out.println("No onset cases for " + dayOfData + " with " + dayOfReporting);
			return 0;
		}
		if (dayOfReporting >= reportedCases.size()) {
			return 0;
		}
		Double i = reportedCases.get(dayOfReporting);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public double getProjectedCasesByOnsetDay(int dayOfData, int dayOfOnset) {
		return getExactProjectedCasesByOnsetDay(dayOfData, dayOfOnset) * 0.4
				+ getExactProjectedCasesByOnsetDay(dayOfData, dayOfOnset + 1) * 0.2
				+ getExactProjectedCasesByOnsetDay(dayOfData, dayOfOnset - 1) * 0.2
				+ getExactProjectedCasesByOnsetDay(dayOfData, dayOfOnset + 2) * 0.1
				+ getExactProjectedCasesByOnsetDay(dayOfData, dayOfOnset - 2) * 0.1;
	}

	public double getProjectedCasesInLastWeek(int dayOfData, int dayOfOnset) {
		double sum = 0;

		for (int i = 0; i < 7; i++) {
			sum += getExactProjectedCasesByInfectionDay(dayOfData, dayOfOnset - i);
		}

		return sum;
	}

	public double getProjectedCasesByReportedDay(int dayOfData, int dayOfReporting) {
		return getExactProjectedCasesByReportedDay(dayOfData, dayOfReporting) * 0.4
				+ getExactProjectedCasesByReportedDay(dayOfData, dayOfReporting + 1) * 0.2
				+ getExactProjectedCasesByReportedDay(dayOfData, dayOfReporting - 1) * 0.2
				+ getExactProjectedCasesByReportedDay(dayOfData, dayOfReporting + 2) * 0.1
				+ getExactProjectedCasesByReportedDay(dayOfData, dayOfReporting - 2) * 0.1;
	}

	public int getCasesByReportedDay(int dayOfData, int dayOfReporting) {
		if (dayOfData >= numbersByDay.size()) {
			dayOfData = numbersByDay.size() - 1;
		}
		NumbersByDay numbers = numbersByDay.get(dayOfData);
		ArrayList<Integer> reportedCases = numbers.reported.cases;
		if (reportedCases == null) {
			// System.out.println("No onset cases for " + dayOfData + " with " +
			// dayOfReporting);
			return 0;
		}
		if (dayOfReporting >= reportedCases.size()) {
			return 0;
		}
		Integer i = reportedCases.get(dayOfReporting);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public int getNewCasesByOnsetDay(int dayOfData, int dayOfOnset) {
		return getCasesByOnsetDay(dayOfData, dayOfOnset) - getCasesByOnsetDay(dayOfData - 1, dayOfOnset);
	}

	public double getNewCasesByInfectionDay(int dayOfData, int dayOfInfection) {
		return getCasesByInfectionDay(dayOfData, dayOfInfection)
				- getCasesByInfectionDay(dayOfData - 1, dayOfInfection);
	}

	public double getAverageAgeOfNewCases(int dayOfData) {
		double daySum = 0, casesSum = 0;
		for (int dayOfOnset = firstDay; dayOfOnset < dayOfData; dayOfOnset++) {
			double newCases = getCasesByInfectionDay(dayOfData, dayOfOnset)
					- getCasesByInfectionDay(dayOfData - 1, dayOfOnset);
			casesSum += newCases;
			daySum += newCases * dayOfOnset;
		}

		return dayOfData - daySum / casesSum;
	}

	public double getCasesByInfectionDay(int dayOfData, int dayOfInfection) {

		return getCasesByOnsetDay(dayOfData, dayOfInfection + 5);

		/*
		 * return getCasesByOnsetDay(dayOfData, dayOfInfection + 5) * 0.5 +
		 * getCasesByOnsetDay(dayOfData, dayOfInfection + 4) * 0.25 +
		 * getCasesByOnsetDay(dayOfData, dayOfInfection + 6) * 0.25;
		 */
	}

	public double getProjectedCasesByInfectionDay(int dayOfData, int dayOfInfection) {
		return getProjectedCasesByOnsetDay(dayOfData, dayOfInfection + 5);
	}

	public double getExactProjectedCasesByInfectionDay(int dayOfData, int dayOfInfection) {
		return getExactProjectedCasesByOnsetDay(dayOfData, dayOfInfection + 5);
	}

	public int getLastOnsetDay(int dayOfData) {
		return numbersByDay.get(dayOfData).onset.cases.size() - 1;
	}

	private static void writeSplit(String[] split) {
		System.out.print(split[0]);
		for (int i = 1; i < split.length; i++) {
			System.out.print(" | ");
			System.out.print(split[i]);
		}
		System.out.println("");
	}

	public Incomplete getOnsetIncompletion(int dayOfData, int delay) {
		NumbersByDay numbers = numbersByDay.get(dayOfData);
		while (numbers.onset.ratios.size() <= delay) {
			numbers.onset.ratios.add(new Incomplete());
		}
		return numbers.onset.ratios.get(delay);
	}

	public void buildIncompletesOnset() {
		/*
		 * Delay 10 means the difference from day 10 to day 11. This will be in
		 * the array under incomplete[10].
		 */
		for (int delay = 0; delay < 30; delay++) {
			for (int onsetDay = getFirstDay(); onsetDay < getLastDay() - delay; onsetDay++) {
				int dayOfData1 = onsetDay + delay;
				int dayOfData2 = onsetDay + delay + 1;

				int cases1 = getCasesByOnsetDay(dayOfData1, onsetDay);
				int cases2 = getCasesByOnsetDay(dayOfData2, onsetDay);
				double newRatio = (double) cases2 / cases1;

				if (cases1 <= 0 || cases2 <= 0) {
					continue;
				}

				Incomplete incomplete1 = getOnsetIncompletion(dayOfData1, delay);
				Incomplete incomplete2 = getOnsetIncompletion(dayOfData2, delay);

				incomplete2.samples = incomplete1.samples + 1;
				if (incomplete1.samples < 7) {
					double samplePortion = (double) incomplete1.samples / incomplete2.samples;
					incomplete2.ratio = Math.pow(incomplete1.ratio, samplePortion)
							* Math.pow(newRatio, 1 - samplePortion);
				} else {
					incomplete2.ratio = Math.pow(incomplete1.ratio, 6.0 / 7.0) * Math.pow(newRatio, 1.0 / 7.0);
				}
			}
		}

		// projections
		for (int dayOfData = getFirstDay(); dayOfData <= getLastDay(); dayOfData++) {
			NumbersByDay numbers = numbersByDay.get(dayOfData);
			for (int onsetDay = getFirstDay(); onsetDay < dayOfData
					&& onsetDay < numbers.onset.cases.size(); onsetDay++) {
				Integer p = numbers.onset.cases.get(onsetDay);
				if (p == null) {
					continue;
				}
				double projected = p;
				for (int delay = dayOfData - onsetDay; delay < numbers.onset.ratios.size(); delay++) {
					projected *= numbers.onset.ratios.get(delay).ratio;
				}
				while (numbers.onset.projected.size() <= onsetDay) {
					numbers.onset.projected.add(0.0);
				}
				numbers.onset.projected.set(onsetDay, projected);
			}
		}
	}

	public Incomplete getReportedIncompletion(int dayOfData, int delay) {
		NumbersByDay numbers = numbersByDay.get(dayOfData);
		while (numbers.reported.ratios.size() <= delay) {
			numbers.reported.ratios.add(new Incomplete());
		}
		return numbers.reported.ratios.get(delay);
	}

	public void buildIncompletesReported() {
		/*
		 * Delay 10 means the difference from day 10 to day 11. This will be in
		 * the array under incomplete[10].
		 */
		for (int delay = 0; delay < 30; delay++) {
			for (int reportedDay = getFirstDay(); reportedDay < getLastDay() - delay; reportedDay++) {
				int dayOfData1 = reportedDay + delay;
				int dayOfData2 = reportedDay + delay + 1;

				int cases1 = getCasesByReportedDay(dayOfData1, reportedDay);
				int cases2 = getCasesByReportedDay(dayOfData2, reportedDay);
				double newRatio = (double) cases2 / cases1;

				if (cases1 <= 0 || cases2 <= 0) {
					continue;
				}

				Incomplete incomplete1 = getReportedIncompletion(dayOfData1, delay);
				Incomplete incomplete2 = getReportedIncompletion(dayOfData2, delay);

				incomplete2.samples = incomplete1.samples + 1;
				if (incomplete1.samples < 7) {
					double samplePortion = (double) incomplete1.samples / incomplete2.samples;
					incomplete2.ratio = Math.pow(incomplete1.ratio, samplePortion)
							* Math.pow(newRatio, 1 - samplePortion);
				} else {
					incomplete2.ratio = Math.pow(incomplete1.ratio, 6.0 / 7.0) * Math.pow(newRatio, 1.0 / 7.0);
				}
			}
		}

		// projections
		for (int dayOfData = getFirstDay(); dayOfData <= getLastDay(); dayOfData++) {
			NumbersByDay numbers = numbersByDay.get(dayOfData);
			for (int reportedDay = getFirstDay(); reportedDay < dayOfData
					&& reportedDay < numbers.reported.cases.size(); reportedDay++) {
				Integer p = numbers.reported.cases.get(reportedDay);
				if (p == null) {
					continue;
				}
				double projected = p;
				for (int delay = dayOfData - reportedDay; delay < numbers.reported.ratios.size(); delay++) {
					projected *= numbers.reported.ratios.get(delay).ratio;
				}
				while (numbers.reported.projected.size() <= reportedDay) {
					numbers.reported.projected.add(0.0);
				}
				numbers.reported.projected.set(reportedDay, projected);
			}
		}
	}

	public void outputInfectionProjections() {
		int dayOfData = getLastDay();

		for (int dayOfInfection = getFirstDay(); dayOfInfection <= dayOfData; dayOfInfection++) {
			double cases = getExactProjectedCasesByInfectionDay(dayOfData, dayOfInfection);

			double week = getProjectedCasesInLastWeek(dayOfData, dayOfInfection);
			System.out
					.println(Date.dayToDate(dayOfInfection) + " => " + dayOfInfection + " => " + cases + " => " + week);
		}
	}

	public CovidStats() {
		/*
		 * Monolithic code to read all the CSVs into one big spaghetti
		 * structure.
		 */
		for (int day = firstCSV;; day++) {

			List<String[]> csv = CSVReader.read(csvFileName(day));

			if (csv == null) {
				lastDay = day - 1;
				System.out.println("Last day: " + Date.dayToDate(lastDay));
				break;
			}

			while (numbersByDay.size() <= day) {
				numbersByDay.add(new NumbersByDay());
				// System.out.println("Size increased to " +
				// numbersByDay.size());
			}

			for (String[] split : csv) {
				NumbersByDay numbers = numbersByDay.get(day);

				if (split.length < 4) {
					System.out.print("Length isn't 4: ");
					writeSplit(split);
				}

				if (split[0].equalsIgnoreCase(oldOnset) || split[0].equalsIgnoreCase(newOnset)) {
					if (split[2].equalsIgnoreCase("Cases")) {

						int theDay = Date.dateToDay(split[1]);
						while (numbers.onset.cases.size() <= theDay) {
							numbers.onset.cases.add(null);
						}

						int cases = Integer.valueOf(split[3]);
						numbers.onset.cases.set(theDay, cases);
					}
				}

				if (split[0].equalsIgnoreCase("Cases of COVID-19 in Colorado by Date Reported to the State")) {

					if (split[2].equalsIgnoreCase("Cases")) {
						int theDay = Date.dateToDay(split[1]);
						while (numbers.reported.cases.size() <= theDay) {
							numbers.reported.cases.add(null);
						}
						int cases = Integer.valueOf(split[3]);
						numbers.reported.cases.set(theDay, cases);
					}
				}

				if (split[0].contains("reported") || split[0].contains("Reported")) {
					// writeSplit(split);
				}
			}

		}

		buildIncompletesOnset();
		buildIncompletesReported();

		MyExecutor.executeCode(() -> outputInfectionProjections());
	}

}
