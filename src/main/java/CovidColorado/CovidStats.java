package CovidColorado;

import java.util.ArrayList;
import java.util.List;

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

	public class IncompleteCases {
		public final ArrayList<Integer> cases = new ArrayList<>();
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
	}

}
