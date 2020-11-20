package covid;

import java.util.HashMap;
import java.util.HashSet;
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

	private final IncompleteNumbers[] cases = new IncompleteNumbers[NumbersType.values().length
			* NumbersTiming.values().length];

	public final FinalNumbers totalCases = new FinalNumbers();
	public final FinalNumbers totalHospitalizations = new FinalNumbers();
	public final FinalNumbers totalDeaths = new FinalNumbers();
	public final FinalNumbers totalDeathsPUI = new FinalNumbers();
	public final FinalNumbers peopleTested = new FinalNumbers();
	public final FinalNumbers testEncounters = new FinalNumbers();

	public double getPositivity(int day, int interval) {
		double c = totalCases.getCasesInInterval(day, interval);
		double t = testEncounters.getCasesInInterval(day, interval);
		if (t == 0) {
			return 0;
		}

		// System.out.println(
		// "On " + Date.dayToDate(day) + " positivity is " + c + " / " + t + " =
		// " + (100 * c / t) + "%.");

		return c / t;
	}

	private IncompleteNumbers getNumbers(NumbersType type, NumbersTiming timing) {
		return cases[type.ordinal() * NumbersTiming.values().length + timing.ordinal()];
	}

	public int getFirstDay() {
		return firstDay;
	}

	public int getLastDay() {
		return lastDay;
	}

	public int getCasesByType(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType) {
		return getNumbers(type, timing).getCases(dayOfData, dayOfType);
	}

	public double getExactProjectedCasesByType(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType) {
		return getNumbers(type, timing).getExactProjectedCases(dayOfData, dayOfType);
	}

	public double getSmoothedProjectedCasesByType(NumbersType type, NumbersTiming timing, int dayOfData,
			int dayOfType) {
		double sum = 0;
		for (int i = -3; i <= 3; i++) {
			sum += getExactProjectedCasesByType(type, timing, dayOfData, dayOfType + i);
		}
		return sum / 7.0;
		/*
		 * return getExactProjectedCasesByType(type, timing, dayOfData,
		 * dayOfType) * 0.4 + getExactProjectedCasesByType(type, timing,
		 * dayOfData, dayOfType + 1) * 0.2 + getExactProjectedCasesByType(type,
		 * timing, dayOfData, dayOfType - 1) * 0.2 +
		 * getExactProjectedCasesByType(type, timing, dayOfData, dayOfType + 2)
		 * * 0.1 + getExactProjectedCasesByType(type, timing, dayOfData,
		 * dayOfType - 2) * 0.1;
		 */
	}

	public double getProjectedCasesInInterval(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType,
			int interval) {
		double sum = 0;

		for (int i = 0; i < interval; i++) {
			sum += getExactProjectedCasesByType(type, timing, dayOfData, dayOfType - i);
		}

		return sum;
	}

	public double getCasesInInterval(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType,
			int interval) {
		double sum = 0;

		for (int i = 0; i < interval; i++) {
			sum += getCasesByType(type, timing, dayOfData, dayOfType - i);
		}

		return sum;
	}

	public int getNewCasesByType(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType) {
		return getCasesByType(type, timing, dayOfData, dayOfType)
				- getCasesByType(type, timing, dayOfData - 1, dayOfType);
	}

	public double getAverageAgeOfNewCases(NumbersType type, NumbersTiming timing, int dayOfType) {
		double daySum = 0, casesSum = 0;
		for (int dayOfOnset = firstDay; dayOfOnset < dayOfType; dayOfOnset++) {
			double newCases = getCasesByType(type, timing, dayOfType, dayOfOnset)
					- getCasesByType(type, timing, dayOfType - 1, dayOfOnset);
			casesSum += newCases;
			daySum += newCases * dayOfOnset;
		}

		return dayOfType - daySum / casesSum;
	}

	public int getLastDayOfType(NumbersType type, NumbersTiming timing, int dayOfData) {
		return getNumbers(type, timing).getLastDay(dayOfData);
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

	public void outputProjections(NumbersType type, NumbersTiming timing) {
		int dayOfData = getLastDay();

		for (int dayOfType = getFirstDay(); dayOfType <= dayOfData; dayOfType++) {
			double c = getExactProjectedCasesByType(type, timing, dayOfData, dayOfType);
			double week = getProjectedCasesInInterval(type, timing, dayOfData, dayOfType, 7);
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
		System.out.println("Newly released deaths");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", totalDeathsPUI.getDailyCases(t),
				totalDeathsPUI.getDailyCases(y), lastWeek, totalDeathsPUI.getDailyCases(w)));

		System.out.println("Newly released cases");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", totalCases.getDailyCases(t),
				totalCases.getDailyCases(y), lastWeek, totalCases.getDailyCases(w)));

		System.out.println("New test encounters");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", testEncounters.getDailyCases(t),
				testEncounters.getDailyCases(y), lastWeek, testEncounters.getDailyCases(w)));

		System.out.println("Current hospitalizations");

		System.out.println("Hospitalizations PUI");

		System.out.println("Positivity rate");

		System.out.println("");
	}

	private HashSet<String> keySet = new HashSet<>();

	/*
	 * The "test encounters" metric only began on this day. Before that it's
	 * just "people tested".
	 */
	private static final int TEST_ENCOUNTERS_STARTED = 205;

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

			if (split[0].equals("description") || split[1].equals("Note")) {
				// ignore notes!
			} else if (split[0].equals("State Data") && split[1].equals("Statewide")) {
				if (split[2].equals("Cases")) {
					totalCases.setCases(dayOfData, number);
				} else if (split[2].equals("Hospitalizations")) {
					totalHospitalizations.setCases(dayOfData, number);
				} else if (split[2].equals("Deaths")) {
					// this was split up into deaths among cases (PUI) and
					// deaths due to covid (confirmed). Before it was just
					// deaths for both.
					totalDeathsPUI.setCases(dayOfData, number);
					totalDeaths.setCases(dayOfData, number);
				} else if (split[2].equals("Deaths Among Cases")) {
					totalDeathsPUI.setCases(dayOfData, number);
				} else if (split[2].equals("Deaths Due to COVID-19")) {
					totalDeaths.setCases(dayOfData, number);
				} else if (split[2].equals("Test Encounters")) {
					if (dayOfData < TEST_ENCOUNTERS_STARTED) {
						new Exception("SHOULD NOT BE HERE???").printStackTrace();
					}
					testEncounters.setCases(dayOfData, number);
				} else if (split[2].equals("People Tested")) {
					peopleTested.setCases(dayOfData, number);
					if (dayOfData < TEST_ENCOUNTERS_STARTED) {
						testEncounters.setCases(dayOfData, number);
					}
				} else if (split[2].equals("Counties")) {
				} else if (split[2].equals("Rate Per 100000") || split[2].equals("\"Rate per 100")) {
					// uh, simple bug that the CSV reader ignores " escaping
					// and so treats 100,000 as a separator
				} else if (split[2].equals("Outbreaks")) {
				} else {
					writeSplit(Date.dayToDate(dayOfData) + "???", split);
				}
			} else if (split[0].equals("Case Counts by Onset Date")
					|| split[0].equals("Cases of COVID-19 in Colorado by Date of Illness Onset")) {
				if (split[2].equals("Cases")) {
					int dayOfOnset = Date.dateToDay(split[1]);
					int dayOfInfection = dayOfOnset - 5;
					int c = Integer.valueOf(split[3]);

					getNumbers(NumbersType.CASES, NumbersTiming.ONSET).setCases(dayOfData, dayOfOnset, c);
					getNumbers(NumbersType.CASES, NumbersTiming.INFECTION).setCases(dayOfData, dayOfInfection, c);
				}
			} else if (split[0]
					.equals("Cumulative Number of Hospitalized Cases of COVID-19 in Colorado by Date of Illness Onset")
					|| split[0].equals("Cumulative Number of Hospitalizations by Onset Date")) {
				int dayOfOnset = Date.dateToDay(split[1]);
				int dayOfInfection = dayOfOnset - 5;
				int c = Integer.valueOf(split[3]);

				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.ONSET).setCumulative();
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).setCumulative();
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.ONSET).setCases(dayOfData, dayOfOnset, c);
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).setCases(dayOfData, dayOfInfection,
						c);
			} else if (split[0].equals("Cumulative Number of Deaths by Onset Date")
					|| split[0].equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Illness")) {
				int dayOfOnset = Date.dateToDay(split[1]);
				int dayOfInfection = dayOfOnset - 5;
				int c = Integer.valueOf(split[3]);

				getNumbers(NumbersType.DEATHS, NumbersTiming.ONSET).setCumulative();
				getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).setCumulative();
				getNumbers(NumbersType.DEATHS, NumbersTiming.ONSET).setCases(dayOfData, dayOfOnset, c);
				getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).setCases(dayOfData, dayOfInfection, c);
			} else if (split[0].equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Death")) {
				// TODO
			} else if (split[0].equals("Cases of COVID-19 in Colorado by Date Reported to the State")
					|| split[0].equals("Case Counts by Reported Date")) {
				if (split[2].equals("Cases")) {
					int dayOfReporting = Date.dateToDay(split[1]);
					int c = Integer.valueOf(split[3]);
					getNumbers(NumbersType.CASES, NumbersTiming.REPORTED).setCases(dayOfData, dayOfReporting, c);
				} else if (split[2].equals("Three-Day Moving Average Of Cases")) {
					// redundant
				} else {
					writeSplit(null, split);
				}
			} else if (split[0].equals(
					"Cumulative Number of Hospitalized Cases of COVID-19 in Colorado by Date Reported to the State")
					|| split[0].equals("Cumulative Number of Hospitalizations by Reported Date")) {
				if (split[2].equals("Cases")) {
					int dayOfReporting = Date.dateToDay(split[1]);
					int c = Integer.valueOf(split[3]);
					getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.REPORTED).setCumulative();
					getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.REPORTED).setCases(dayOfData, dayOfReporting,
							c);
				} else {
					writeSplit(null, split);
				}
			} else if (split[0]
					.equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date Reported to the State")
					|| split[0].equals("Cumulative Number of Deaths by Reported Date")) {
				if (split[2].equals("Cases")) {
					int dayOfReporting = Date.dateToDay(split[1]);
					int c = Integer.valueOf(split[3]);
					getNumbers(NumbersType.DEATHS, NumbersTiming.REPORTED).setCumulative();
					getNumbers(NumbersType.DEATHS, NumbersTiming.REPORTED).setCases(dayOfData, dayOfReporting, c);
				} else {
					writeSplit(null, split);
				}
			} else if (split[0].equals("Colorado Case Counts by County") || split[0].equals("Case Counts by County")) {
				if (!split[1].equals("Note") && split[2].equals("Cases") && !split[1].contains("nknown")) {
					Integer c = Integer.valueOf(split[3]);
					CountyStats county = readCounty(split[1]);
					county.getCases().setCases(dayOfData, c);
				}
			} else if (split[0].equals("Deaths") || split[0].equals("Number of Deaths by County")) {
				Integer c = Integer.valueOf(split[3]);
				CountyStats county = readCounty(split[1]);
				county.getDeaths().setCases(dayOfData, c);
			} else if (split[0].equals("Daily Serology Data From Clinical Laboratories")) {
				// ignored?
			} else if (split[0].equals("Positivity Data from Clinical Laboratories")) {
				// ignored?
			} else if (split[0].equals("Case Status for Cases & Deaths")) {
				// ignored?
			} else if (split[0].equals("COVID-19 in Colorado by Sex")) {
				// TODO maybe?
			} else if (split[0].equals("COVID-19 in Colorado by Race & Ethnicity")) {
				// TODO maybe?
			} else if (split[0].equals("COVID-19 in Colorado by Age Group")) {
				// TODO maybe?
			} else if (split[0].equals("Case Counts by Age Group")) {
				// ignored?
			} else if (split[0].equals("Transmission Type")) {
				// ignored?
			} else if (split[0].equals("Case Counts by Sex")) {
				// ignored?
			} else if (split[0].equals("Fatal cases by sex")) {
				// ignored?
			} else if (split[0].equals("Cumulative Number of Cases by Onset Date")) {
				// redundant
			} else if (split[0].equals("Cumulative Number of Cases by Reported Date")) {
				// redundant
			} else if (split[0].equals("Cumulative Number of Cases of COVID-19 in Colorado by Date of Illness Onset")) {
				// redundant
			} else if (split[0]
					.equals("Cumulative Number of Cases of COVID-19 in Colorado by Date Reported to the State")) {
				// redundant
			} else if (split[0].equals("Total COVID-19 Tests Performed in Colorado by County")) {
				// TODO
			} else if (split[0].equals("Number of Deaths From COVID-19 in Colorado by Date of Death - By Day")) {
				// redundant
			} else if (split[0].equals("\"Cases of COVID-19 Reported in Colorado by Age Group")
					|| split[0].equals("\"Case Counts by Age Group") || split[0].equals("\"Case Rates Per 100")
					|| split[0].equals("\"Total Testing Rate Per 100")) {
				// ignored? Also buggy use of " ,
			} else {
				if (!keySet.contains(split[0])) {
					keySet.add(split[0]);
					writeSplit(Date.dayToDate(dayOfData), split);
				}
			}
		}

		return true;
	}

	public ColoradoStats() {
		for (int i = 0; i < cases.length; i++) {
			cases[i] = new IncompleteNumbers();
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

		System.out.println();
		for (int day = getFirstDay(); day < getLastDay(); day++) {
			int number = testEncounters.getCases(day);
			System.out.println("Day " + Date.dayToDate(day) + " test encounters = " + number);
		}

		for (IncompleteNumbers incompletes : cases) {
			incompletes.build(this);
		}

		outputDailyStats();
		// outputProjections(CaseType.INFECTION_TESTS);

		// System.exit(0);
	}

}
