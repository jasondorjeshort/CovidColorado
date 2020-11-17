package CovidColorado;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import library.MyExecutor;

public class CovidStats {

	private static final int firstDay = 48; // 2/17/2020, first day of data
	private static final int firstCSV = 77; // 77, 314

	private static String csvFileName(int day) {
		return String.format("H:\\Downloads\\CovidColoradoCSV\\covid19_case_summary_%s.csv",
				Date.dayToFullDate(day, '-'));
	}

	public class County {
		// cumulative stats for ONE county
		final String name;
		final ArrayList<Integer> cases = new ArrayList<>();
		final ArrayList<Integer> deaths = new ArrayList<>();

		County(String name) {
			this.name = name;
		}

		public int getCases(int day) {
			if (day < 0 || day >= cases.size()) {
				return 0;
			}
			Integer caseCount = cases.get(day);
			if (caseCount == null) {
				return 0;
			}
			return caseCount;
		}

	}

	private final HashMap<String, County> counties = new HashMap<>();

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

	private final ArrayList<IncompleteCases> onsetCases = new ArrayList<>();
	private final ArrayList<IncompleteCases> infectionCases = new ArrayList<>();
	private final ArrayList<IncompleteCases> reportedCases = new ArrayList<>();

	private ArrayList<IncompleteCases> getCases(CaseType type) {
		switch (type) {
		case ONSET_TESTS:
			return onsetCases;
		case INFECTION_TESTS:
			return infectionCases;
		case REPORTED_TESTS:
			return reportedCases;
		default:
			return null;
		}
	}

	public int getFirstDay() {
		return firstDay;
	}

	public int getLastDay() {
		return lastDay;
	}

	public int getCasesByType(CaseType type, int dayOfData, int dayOfType) {
		ArrayList<IncompleteCases> numbers = getCases(type);
		if (dayOfData >= numbers.size()) {
			return 0;
		}
		IncompleteCases daily = numbers.get(dayOfData);
		if (dayOfType >= daily.cases.size()) {
			return 0;
		}
		Integer i = daily.cases.get(dayOfType);
		if (i == null) {
			return 0;
		}
		return i;
	}

	public double getExactProjectedCasesByType(CaseType type, int dayOfData, int dayOfType) {
		ArrayList<IncompleteCases> numbers = getCases(type);
		IncompleteCases daily = numbers.get(dayOfData);
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

	public double getSmoothedProjectedCasesByType(CaseType type, int dayOfData, int dayOfType) {
		return getExactProjectedCasesByType(type, dayOfData, dayOfType) * 0.4
				+ getExactProjectedCasesByType(type, dayOfData, dayOfType + 1) * 0.2
				+ getExactProjectedCasesByType(type, dayOfData, dayOfType - 1) * 0.2
				+ getExactProjectedCasesByType(type, dayOfData, dayOfType + 2) * 0.1
				+ getExactProjectedCasesByType(type, dayOfData, dayOfType - 2) * 0.1;
	}

	public double getProjectedCasesInLastWeek(CaseType type, int dayOfData, int dayOfType) {
		double sum = 0;

		for (int i = 0; i < 7; i++) {
			sum += getExactProjectedCasesByType(type, dayOfData, dayOfType - i);
		}

		return sum;
	}

	public int getNewCasesByType(CaseType type, int dayOfData, int dayOfType) {
		return getCasesByType(type, dayOfData, dayOfType) - getCasesByType(type, dayOfData - 1, dayOfType);
	}

	public double getAverageAgeOfNewCases(CaseType type, int dayOfType) {
		double daySum = 0, casesSum = 0;
		for (int dayOfOnset = firstDay; dayOfOnset < dayOfType; dayOfOnset++) {
			double newCases = getCasesByType(type, dayOfType, dayOfOnset)
					- getCasesByType(type, dayOfType - 1, dayOfOnset);
			casesSum += newCases;
			daySum += newCases * dayOfOnset;
		}

		return dayOfType - daySum / casesSum;
	}

	public int getLastDayOfType(CaseType type, int dayOfData) {
		return getCases(type).get(dayOfData).cases.size() - 1;
	}

	private static void writeSplit(String[] split) {
		System.out.print(split[0]);
		for (int i = 1; i < split.length; i++) {
			System.out.print(" | ");
			System.out.print(split[i]);
		}
		System.out.println("");
	}

	private Incomplete getIncompletion(CaseType type, int dayOfType, int delay) {
		ArrayList<IncompleteCases> fullNumbers = getCases(type);
		IncompleteCases numbers = fullNumbers.get(dayOfType);
		while (numbers.ratios.size() <= delay) {
			numbers.ratios.add(new Incomplete());
		}
		return numbers.ratios.get(delay);
	}

	public double SAMPLE_DAYS = 14;

	private void buildIncompletes(CaseType type) {
		ArrayList<IncompleteCases> incompletes = getCases(type);
		/*
		 * Delay 10 means the difference from day 10 to day 11. This will be in
		 * the array under incomplete[10].
		 */
		for (int delay = 0; delay < 90; delay++) {
			for (int typeDay = getFirstDay(); typeDay < getLastDay() - delay; typeDay++) {
				int dayOfData1 = typeDay + delay;
				int dayOfData2 = typeDay + delay + 1;

				int cases1 = getCasesByType(type, dayOfData1, typeDay);
				int cases2 = getCasesByType(type, dayOfData2, typeDay);
				double newRatio = (double) cases2 / cases1;

				if (cases1 <= 0 || cases2 <= 0) {
					continue;
				}

				Incomplete incomplete1 = getIncompletion(type, dayOfData1, delay);
				Incomplete incomplete2 = getIncompletion(type, dayOfData2, delay);

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
		for (int dayOfData = getFirstDay(); dayOfData <= getLastDay(); dayOfData++) {
			IncompleteCases numbers = incompletes.get(dayOfData);
			for (int typeDay = getFirstDay(); typeDay < dayOfData && typeDay < numbers.cases.size(); typeDay++) {
				Integer p = numbers.cases.get(typeDay);
				if (p == null) {
					continue;
				}
				double projected = p;
				for (int delay = dayOfData - typeDay; delay < numbers.ratios.size(); delay++) {
					projected *= numbers.ratios.get(delay).ratio;
				}
				while (numbers.projected.size() <= typeDay) {
					numbers.projected.add(0.0);
				}
				numbers.projected.set(typeDay, projected);
			}
		}
	}

	public void outputProjections(CaseType type) {
		int dayOfData = getLastDay();

		for (int dayOfType = getFirstDay(); dayOfType <= dayOfData; dayOfType++) {
			double cases = getExactProjectedCasesByType(type, dayOfData, dayOfType);
			double week = getProjectedCasesInLastWeek(type, dayOfData, dayOfType);
			System.out.println(Date.dayToDate(dayOfType) + " => " + dayOfType + " => " + cases + " => " + week);
		}
	}

	public County getCountyStats(String countyName) {
		County county = counties.get(countyName);
		if (county == null) {
			county = new County(countyName);
			counties.put(countyName, county);
		}
		return county;
	}

	public Map<String, County> getCounties() {
		return counties;
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

			while (onsetCases.size() <= day) {
				onsetCases.add(new IncompleteCases());
			}
			while (infectionCases.size() <= day) {
				infectionCases.add(new IncompleteCases());
			}
			while (reportedCases.size() <= day) {
				reportedCases.add(new IncompleteCases());
			}
			IncompleteCases onset = onsetCases.get(day), reported = reportedCases.get(day),
					infection = infectionCases.get(day);

			for (String[] split : csv) {

				if (split.length < 4) {
					System.out.print("Length isn't 4: ");
					writeSplit(split);
				}

				if (split[0].equalsIgnoreCase(oldOnset) || split[0].equalsIgnoreCase(newOnset)) {
					if (split[2].equalsIgnoreCase("Cases")) {

						int onsetDay = Date.dateToDay(split[1]);
						int infectionDay = onsetDay - 5;
						while (onset.cases.size() <= onsetDay) {
							onset.cases.add(0);
						}
						while (infection.cases.size() <= infectionDay) {
							infection.cases.add(0);
						}

						int cases = Integer.valueOf(split[3]);
						onset.cases.set(onsetDay, cases);
						infection.cases.set(infectionDay, cases);
					}
				}

				if (split[0].equalsIgnoreCase("Cases of COVID-19 in Colorado by Date Reported to the State")) {
					if (split[2].equalsIgnoreCase("Cases")) {
						int theDay = Date.dateToDay(split[1]);
						while (reported.cases.size() <= theDay) {
							reported.cases.add(null);
						}
						int cases = Integer.valueOf(split[3]);
						reported.cases.set(theDay, cases);
					}
				}

				if (split[0].equalsIgnoreCase("Colorado Case Counts by County")
						|| split[0].equalsIgnoreCase("Case Counts by County")) {
					if (!split[1].equalsIgnoreCase("Note") && split[2].equalsIgnoreCase("Cases")
							&& !split[1].contains("nknown")) {
						String countyName = split[1];
						Integer cases = Integer.valueOf(split[3]);

						if (countyName.endsWith(" County")) {
							countyName = countyName.substring(0, countyName.length() - 7);
						}
						countyName = countyName.trim();

						County county = getCountyStats(countyName);
						while (county.cases.size() <= day) {
							county.cases.add(0);
						}

						if (countyName.equalsIgnoreCase("Saguache")) {
							System.out.println(countyName + " => " + Date.dayToDate(day) + " => " + cases);
						}

						county.cases.set(day, cases);
					}
				} else if (split[0].equalsIgnoreCase("Colorado Case Counts by County")) {

				} else if (split[0].contains("Saguache") || split[1].contains("Saguache")
						|| split[2].contains("Saguache") || split[3].contains("Saguache")) {
					// writeSplit(split);
				}
			}

		}

		buildIncompletes(CaseType.ONSET_TESTS);
		buildIncompletes(CaseType.INFECTION_TESTS);
		buildIncompletes(CaseType.REPORTED_TESTS);

		MyExecutor.executeCode(() -> outputProjections(CaseType.INFECTION_TESTS));
	}

}
