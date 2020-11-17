package covid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import library.MyExecutor;

public class ColoradoStats {

	private static final int firstDay = 48; // 2/17/2020, first day of data
	private static final int firstCSV = 77; // 77, 314

	private static String csvFileName(int day) {
		return String.format("H:\\Downloads\\CovidColoradoCSV\\covid19_case_summary_%s.csv",
				Date.dayToFullDate(day, '-'));
	}

	private final HashMap<String, CountyStats> counties = new HashMap<>();

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

	private final IncompleteCases[] cases = new IncompleteCases[CaseType.values().length];

	private IncompleteCases getCases(CaseType type) {
		return cases[type.ordinal()];
	}

	public int getFirstDay() {
		return firstDay;
	}

	public int getLastDay() {
		return lastDay;
	}

	public int getCasesByType(CaseType type, int dayOfData, int dayOfType) {
		return getCases(type).getCases(dayOfData, dayOfType);
	}

	public double getExactProjectedCasesByType(CaseType type, int dayOfData, int dayOfType) {
		return getCases(type).getExactProjectedCases(dayOfData, dayOfType);
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
		return getCases(type).getLastDay(dayOfData);
	}

	private static void writeSplit(String[] split) {
		System.out.print(split[0]);
		for (int i = 1; i < split.length; i++) {
			System.out.print(" | ");
			System.out.print(split[i]);
		}
		System.out.println("");
	}

	public void outputProjections(CaseType type) {
		int dayOfData = getLastDay();

		for (int dayOfType = getFirstDay(); dayOfType <= dayOfData; dayOfType++) {
			double cases = getExactProjectedCasesByType(type, dayOfData, dayOfType);
			double week = getProjectedCasesInLastWeek(type, dayOfData, dayOfType);
			System.out.println(Date.dayToDate(dayOfType) + " => " + dayOfType + " => " + cases + " => " + week);
		}
	}

	public CountyStats getCountyStats(String countyName) {
		CountyStats county = counties.get(countyName);
		if (county == null) {
			county = new CountyStats(countyName);
			counties.put(countyName, county);
		}
		return county;
	}

	public Map<String, CountyStats> getCounties() {
		return counties;
	}

	public ColoradoStats() {
		for (CaseType type : CaseType.values()) {
			cases[type.ordinal()] = new IncompleteCases();
		}

		/*
		 * Monolithic code to read all the CSVs into one big spaghetti
		 * structure.
		 */
		for (int dayOfData = firstCSV;; dayOfData++) {

			List<String[]> csv = CSVReader.read(csvFileName(dayOfData));

			if (csv == null) {
				lastDay = dayOfData - 1;
				System.out.println("Last day: " + Date.dayToDate(lastDay));
				break;
			}

			for (String[] split : csv) {

				if (split.length < 4) {
					System.out.print("Length isn't 4: ");
					writeSplit(split);
				}

				if (split[0].equalsIgnoreCase(oldOnset) || split[0].equalsIgnoreCase(newOnset)) {
					if (split[2].equalsIgnoreCase("Cases")) {

						int dayOfOnset = Date.dateToDay(split[1]);
						int dayOfInfection = dayOfOnset - 5;

						int cases = Integer.valueOf(split[3]);
						getCases(CaseType.ONSET_TESTS).setCases(dayOfData, dayOfOnset, cases);
						getCases(CaseType.INFECTION_TESTS).setCases(dayOfData, dayOfInfection, cases);
					}
				}

				if (split[0].equalsIgnoreCase("Cases of COVID-19 in Colorado by Date Reported to the State")) {
					if (split[2].equalsIgnoreCase("Cases")) {
						int dayOfReporting = Date.dateToDay(split[1]);
						int cases = Integer.valueOf(split[3]);
						getCases(CaseType.REPORTED_TESTS).setCases(dayOfData, dayOfReporting, cases);
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

						CountyStats county = getCountyStats(countyName);

						if (countyName.equalsIgnoreCase("Saguache")) {
							System.out.println(countyName + " => " + Date.dayToDate(dayOfData) + " => " + cases);
						}

						county.setCases(dayOfData, cases);
					}
				} else if (split[0].equalsIgnoreCase("Colorado Case Counts by County")) {

				} else if (split[0].contains("Saguache") || split[1].contains("Saguache")
						|| split[2].contains("Saguache") || split[3].contains("Saguache")) {
					// writeSplit(split);
				}
			}

		}

		for (IncompleteCases incompletes : cases) {
			incompletes.buildIncompletes(this);
		}

		MyExecutor.executeCode(() -> outputProjections(CaseType.INFECTION_TESTS));
	}

}
