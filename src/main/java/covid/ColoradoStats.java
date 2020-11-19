package covid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColoradoStats {

	private final int firstDay = 48; // 2/17/2020, first day of data
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

	private final IncompleteCases[] cases = new IncompleteCases[CaseType.values().length];

	private final FinalCases totalCases = new FinalCases();
	private final FinalCases totalHospitalizations = new FinalCases();
	private final FinalCases totalDeaths = new FinalCases();
	private final FinalCases totalDeathsPUI = new FinalCases();
	private final FinalCases peopleTested = new FinalCases();
	private final FinalCases testEncounters = new FinalCases();

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

	public double getProjectedCasesInInterval(CaseType type, int dayOfData, int dayOfType, int interval) {
		double sum = 0;

		for (int i = 0; i < interval; i++) {
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

	private static void writeSplit(String lead, String[] split) {
		if (lead != null) {
			System.out.print(lead + " : ");
		}
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
			double c = getExactProjectedCasesByType(type, dayOfData, dayOfType);
			double week = getProjectedCasesInInterval(type, dayOfData, dayOfType, 7);
			System.out.println(Date.dayToDate(dayOfType) + " => " + dayOfType + " => " + c + " => " + week);
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

	private CountyStats readCounty(String countyName) {
		if (countyName.endsWith(" County")) {
			countyName = countyName.substring(0, countyName.length() - 7);
		}
		countyName = countyName.trim();

		return getCountyStats(countyName);
	}

	public void outputDailyStats() {
		int t = lastDay, y = lastDay - 1, w = lastDay - 7;
		String today = Date.dayToDate(lastDay);
		String lastWeek = Date.dayToDate(w);

		System.out.println("Update for " + today);
		System.out.println("Newly reported deaths");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", totalDeathsPUI.getDailyCases(t),
				totalDeathsPUI.getDailyCases(y), lastWeek, totalDeathsPUI.getDailyCases(w)));

		System.out.println("Newly reported cases");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", totalCases.getDailyCases(t),
				totalCases.getDailyCases(y), lastWeek, totalCases.getDailyCases(w)));

		System.out.println("New tests");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", testEncounters.getDailyCases(t),
				testEncounters.getDailyCases(y), lastWeek, testEncounters.getDailyCases(w)));

		System.out.println("Current hospitalizations");

		System.out.println("Hospitalizations PUI");

		System.out.println("Positivity rate");

		System.out.println("");
	}

	public boolean readCSV(int dayOfData) {

		List<String[]> csv = CSVReader.read(csvFileName(dayOfData));

		if (csv == null) {
			return false;
		}

		synchronized (this) {
			lastDay = Math.max(dayOfData, lastDay);
		}

		for (String[] split : csv) {

			if (split.length < 4) {
				System.out.print("Length isn't 4: ");
				writeSplit("Fail", split);
			}

			int number;
			try {
				number = Integer.valueOf(split[3]);
			} catch (Exception e) {
				number = 0;
			}

			if (split[1].equals("Note")) {
				// ignore notes!
			} else if (split[0].equalsIgnoreCase("State Data") && split[1].equalsIgnoreCase("Statewide")) {
				if (split[2].equalsIgnoreCase("Cases")) {
					totalCases.setCases(dayOfData, number);
				} else if (split[2].equalsIgnoreCase("Hospitalizations")) {
					totalHospitalizations.setCases(dayOfData, number);
				} else if (split[2].equalsIgnoreCase("Deaths")) {
					totalDeathsPUI.setCases(dayOfData, number);
					totalDeaths.setCases(dayOfData, number);
				} else if (split[2].equalsIgnoreCase("Deaths Among Cases")) {
					totalDeathsPUI.setCases(dayOfData, number);
				} else if (split[2].equalsIgnoreCase("Deaths Due to COVID-19")) {
					totalDeaths.setCases(dayOfData, number);
				} else if (split[2].equalsIgnoreCase("Test Encounters")) {
					testEncounters.setCases(dayOfData, number);
				} else if (split[2].equalsIgnoreCase("People Tested")) {
					peopleTested.setCases(dayOfData, number);
					if (testEncounters.getDailyCases(dayOfData) == 0) {
						testEncounters.setCases(dayOfData, number);
					}
				} else if (split[2].equalsIgnoreCase("Counties")) {
				} else if (split[2].equalsIgnoreCase("Rate per 100000")
						|| split[2].equalsIgnoreCase("\"Rate per 100")) {
					// uh, simple bug that the CSV reader ignores " escaping
					// and so treats 100,000 as a separator
				} else if (split[2].equalsIgnoreCase("Outbreaks")) {
				} else {
					writeSplit(Date.dayToDate(dayOfData), split);
				}
			} else if (split[0].equalsIgnoreCase("Case Counts by Onset Date")
					|| split[0].equalsIgnoreCase("Cases of COVID-19 in Colorado by Date of Illness Onset")) {
				if (split[2].equalsIgnoreCase("Cases")) {
					int dayOfOnset = Date.dateToDay(split[1]);
					int dayOfInfection = dayOfOnset - 5;
					int c = Integer.valueOf(split[3]);

					getCases(CaseType.ONSET_TESTS).setCases(dayOfData, dayOfOnset, c);
					getCases(CaseType.INFECTION_TESTS).setCases(dayOfData, dayOfInfection, c);
				}
			} else if (split[0].equalsIgnoreCase(
					"Cumulative Number of Hospitalized Cases of COVID-19 in Colorado by Date of Illness Onset")
					|| split[0].equalsIgnoreCase("Cumulative Number of Hospitalizations by Onset Date")) {
				int dayOfOnset = Date.dateToDay(split[1]);
				int dayOfInfection = dayOfOnset - 5;
				int c = Integer.valueOf(split[3]);

				getCases(CaseType.ONSET_HOSP).setCumulative();
				getCases(CaseType.INFECTION_HOSP).setCumulative();
				getCases(CaseType.ONSET_HOSP).setCases(dayOfData, dayOfOnset, c);
				getCases(CaseType.INFECTION_HOSP).setCases(dayOfData, dayOfInfection, c);
			} else if (split[0].equalsIgnoreCase("Cumulative Number of Deaths by Onset Date") || split[0]
					.equalsIgnoreCase("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Illness")) {
				int dayOfOnset = Date.dateToDay(split[1]);
				int dayOfInfection = dayOfOnset - 5;
				int c = Integer.valueOf(split[3]);

				getCases(CaseType.ONSET_DEATH).setCumulative();
				getCases(CaseType.INFECTION_DEATH).setCumulative();
				getCases(CaseType.ONSET_DEATH).setCases(dayOfData, dayOfOnset, c);
				getCases(CaseType.INFECTION_DEATH).setCases(dayOfData, dayOfInfection, c);
			} else if (split[0]
					.equalsIgnoreCase("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Death")) {
				// TODO
			} else if (split[0].equalsIgnoreCase("Cases of COVID-19 in Colorado by Date Reported to the State")) {
				if (split[2].equalsIgnoreCase("Cases")) {
					int dayOfReporting = Date.dateToDay(split[1]);
					int c = Integer.valueOf(split[3]);
					getCases(CaseType.REPORTED_TESTS).setCases(dayOfData, dayOfReporting, c);
				} else if (split[2].equalsIgnoreCase("Three-Day Moving Average Of Cases")) {
					// redundant
				} else {
					writeSplit(null, split);
				}
			} else if (split[0].equalsIgnoreCase(
					"Cumulative Number of Hospitalized Cases of COVID-19 in Colorado by Date Reported to the State")
					|| split[0].equalsIgnoreCase("Cumulative Number of Hospitalizations by Reported Date")) {
				if (split[2].equalsIgnoreCase("Cases")) {
					int dayOfReporting = Date.dateToDay(split[1]);
					int c = Integer.valueOf(split[3]);
					getCases(CaseType.REPORTED_HOSP).setCumulative();
					getCases(CaseType.REPORTED_HOSP).setCases(dayOfData, dayOfReporting, c);
				} else {
					writeSplit(null, split);
				}
			} else if (split[0].equalsIgnoreCase(
					"Cumulative Number of Deaths From COVID-19 in Colorado by Date Reported to the State")
					|| split[0].equalsIgnoreCase("Cumulative Number of Deaths by Reported Date")) {
				if (split[2].equalsIgnoreCase("Cases")) {
					int dayOfReporting = Date.dateToDay(split[1]);
					int c = Integer.valueOf(split[3]);
					getCases(CaseType.REPORTED_DEATH).setCumulative();
					getCases(CaseType.REPORTED_DEATH).setCases(dayOfData, dayOfReporting, c);
				} else {
					writeSplit(null, split);
				}
			} else if (split[0].equalsIgnoreCase("Colorado Case Counts by County")
					|| split[0].equalsIgnoreCase("Case Counts by County")) {
				if (!split[1].equalsIgnoreCase("Note") && split[2].equalsIgnoreCase("Cases")
						&& !split[1].contains("nknown")) {
					Integer c = Integer.valueOf(split[3]);
					CountyStats county = readCounty(split[1]);
					county.getCases().setCases(dayOfData, c);
				}
			} else if (split[0].equalsIgnoreCase("Deaths") || split[0].equalsIgnoreCase("Number of Deaths by County")) {
				Integer c = Integer.valueOf(split[3]);
				CountyStats county = readCounty(split[1]);
				county.getDeaths().setCases(dayOfData, c);
			} else if (split[0].equalsIgnoreCase("Daily Serology Data From Clinical Laboratories")) {
				// ignored?
			} else if (split[0].equalsIgnoreCase("Positivity Data from Clinical Laboratories")) {
				// ignored?
			} else if (split[0].equalsIgnoreCase("Case Status for Cases & Deaths")) {
				// ignored?
			} else if (split[0].equalsIgnoreCase("COVID-19 in Colorado by Sex")) {
				// TODO maybe?
			} else if (split[0].equalsIgnoreCase("COVID-19 in Colorado by Race & Ethnicity")) {
				// TODO maybe?
			} else if (split[0].equalsIgnoreCase("COVID-19 in Colorado by Age Group")) {
				// TODO maybe?
			} else if (split[0]
					.equalsIgnoreCase("Number of Deaths From COVID-19 in Colorado by Date of Death - By Day")) {
				// redundant
			} else if (split[0].equalsIgnoreCase("\"Cases of COVID-19 Reported in Colorado by Age Group")
					|| split[0].equalsIgnoreCase("\"Case Counts by Age Group")) {
				// ignored? Also buggy use of " ,
			} else if (split[0].contains("Death") || split[1].contains("Death") || split[2].contains("Death")
					|| split[3].contains("Death")) {
				writeSplit(Date.dayToDate(dayOfData), split);
			} else {
				if (dayOfData == Date.dateToDay("11-18-2020")) {
					// writeSplit(Date.dayToDate(dayOfData), split);
				}
			}
		}

		return true;
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
			if (!readCSV(dayOfData)) {
				break;
			}
		}

		for (IncompleteCases incompletes : cases) {
			incompletes.build(this);
		}

		outputDailyStats();
		// outputProjections(CaseType.INFECTION_TESTS);

		// System.exit(0);
	}

}
