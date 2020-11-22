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

	private final IncompleteNumbers[] incompleteNumbers = new IncompleteNumbers[NumbersType.values().length
			* NumbersTiming.values().length];
	private final FinalNumbers[] finalNumbers = new FinalNumbers[NumbersType.values().length];

	/*
	 * Colorado has two death numbers.
	 * 
	 * "Deaths with COVID"
	 * 
	 * "Deaths due to COVID"
	 * 
	 * Once someone dies with a COVID diagnosis they immediately join the first
	 * number. Later, they'll either be removed from that number (in theory this
	 * should happen 1/2000 or so of the time at random, but it seems to be far
	 * less) OR be added to the second number (most common). Most sources use
	 * the first number, presumably because it updates much faster. Since I just
	 * want "cases" "hospitalizations" "deaths" enumerated, I'm counting the
	 * first number as "deaths" and the second number is in the
	 * "confirmed deaths" structure below.
	 * 
	 * As an addendum, these numbers were only split off partway through the
	 * pandemic, so there's some special case handling to fill both values with
	 * the same number in early data.
	 */
	public final FinalNumbers confirmedDeaths = new FinalNumbers();

	/*
	 * Colorado has two testing numbers.
	 * 
	 * "People testing" is the number of people tested.
	 * 
	 * "Test encounters" is the number of times a person has been tested.
	 * 
	 * Since people are often tested multiple times, the second value is the one
	 * used for positivity. The first value doesn't really seem to have much
	 * use. It is worth noting, though, that there's probably no single correct
	 * definition of tests done or positivity.
	 * 
	 * As an addendum, these numbers were only split off partway through the
	 * pandemic, so there's some special case handling to fill both values with
	 * the same number in early data.
	 */
	public final FinalNumbers peopleTested = new FinalNumbers();
	public final FinalNumbers testEncounters = new FinalNumbers();

	public double getPositivity(int day, int interval) {
		double c = getNumbers(NumbersType.CASES).getNumbersInInterval(day, interval);
		double t = testEncounters.getNumbersInInterval(day, interval);
		if (t == 0) {
			return 0;
		}

		// System.out.println(
		// "On " + Date.dayToDate(day) + " positivity is " + c + " / " + t + " =
		// " + (100 * c / t) + "%.");

		return c / t;
	}

	public IncompleteNumbers getNumbers(NumbersType type, NumbersTiming timing) {
		return incompleteNumbers[type.ordinal() * NumbersTiming.values().length + timing.ordinal()];
	}

	public FinalNumbers getNumbers(NumbersType type) {
		return finalNumbers[type.ordinal()];
	}

	public int getFirstDay() {
		return firstDay;
	}

	public int getLastDay() {
		return lastDay;
	}

	public double getCasesByType(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType) {
		return getNumbers(type, timing).getNumbers(dayOfData, dayOfType);
	}

	public double getExactProjectedCasesByType(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType) {
		return getNumbers(type, timing).getProjectedNumbers(dayOfData, dayOfType);
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

	public double getNewCasesByType(NumbersType type, NumbersTiming timing, int dayOfData, int dayOfType) {
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
		int DAYS = 14;

		for (int dayOfType = getLastDay() - 60; dayOfType <= dayOfData; dayOfType++) {
			// IncompleteNumbers numbers = getNumbers(type, timing);
			double c = getExactProjectedCasesByType(type, timing, dayOfData, dayOfType);
			double week = getProjectedCasesInInterval(type, timing, dayOfData, dayOfType, DAYS);
			double lastWeek = getProjectedCasesInInterval(type, timing, dayOfData, dayOfType - DAYS, DAYS);
			double growth = 100 * Math.pow(week / lastWeek, 1.0 / DAYS) - 100;
			System.out.println(Date.dayToDate(dayOfType) + " => " + dayOfType + " => " + c + " => " + week + " => "
					+ growth + "%");
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
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d",
				getNumbers(NumbersType.DEATHS).getDailyNumbers(t), getNumbers(NumbersType.DEATHS).getDailyNumbers(y),
				lastWeek, getNumbers(NumbersType.DEATHS).getDailyNumbers(w)));

		FinalNumbers cases = getNumbers(NumbersType.CASES);
		System.out.println("Newly released cases");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", cases.getDailyNumbers(t),
				cases.getDailyNumbers(y), lastWeek, cases.getDailyNumbers(w)));

		System.out.println("New test encounters");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", testEncounters.getDailyNumbers(t),
				testEncounters.getDailyNumbers(y), lastWeek, testEncounters.getDailyNumbers(w)));

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
					getNumbers(NumbersType.CASES).setCumulativeNumbers(dayOfData, number);
				} else if (split[2].equals("Hospitalizations")) {
					getNumbers(NumbersType.HOSPITALIZATIONS).setCumulativeNumbers(dayOfData, number);
				} else if (split[2].equals("Deaths")) {
					// this was split up into deaths among cases (PUI) and
					// deaths due to covid (confirmed). Before it was just
					// deaths for both.
					getNumbers(NumbersType.DEATHS).setCumulativeNumbers(dayOfData, number);
					confirmedDeaths.setCumulativeNumbers(dayOfData, number);
				} else if (split[2].equals("Deaths Among Cases")) {
					getNumbers(NumbersType.DEATHS).setCumulativeNumbers(dayOfData, number);
				} else if (split[2].equals("Deaths Due to COVID-19")) {
					confirmedDeaths.setCumulativeNumbers(dayOfData, number);
				} else if (split[2].equals("Test Encounters")) {
					if (dayOfData < TEST_ENCOUNTERS_STARTED) {
						new Exception("SHOULD NOT BE HERE???").printStackTrace();
					}
					testEncounters.setCumulativeNumbers(dayOfData, number);
				} else if (split[2].equals("People Tested")) {
					peopleTested.setCumulativeNumbers(dayOfData, number);
					if (dayOfData < TEST_ENCOUNTERS_STARTED) {
						testEncounters.setCumulativeNumbers(dayOfData, number);
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

					getNumbers(NumbersType.CASES, NumbersTiming.ONSET).setNumbers(dayOfData, dayOfOnset, c);
					getNumbers(NumbersType.CASES, NumbersTiming.INFECTION).setNumbers(dayOfData, dayOfInfection, c);
				}
			} else if (split[0]
					.equals("Cumulative Number of Hospitalized Cases of COVID-19 in Colorado by Date of Illness Onset")
					|| split[0].equals("Cumulative Number of Hospitalizations by Onset Date")) {
				int dayOfOnset = Date.dateToDay(split[1]);
				int dayOfInfection = dayOfOnset - 5;
				int c = Integer.valueOf(split[3]);

				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.ONSET).setCumulative();
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).setCumulative();
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.ONSET).setNumbers(dayOfData, dayOfOnset, c);
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).setNumbers(dayOfData, dayOfInfection,
						c);
			} else if (split[0].equals("Cumulative Number of Deaths by Onset Date")
					|| split[0].equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Illness")) {
				int dayOfOnset = Date.dateToDay(split[1]);
				int dayOfInfection = dayOfOnset - 5;
				int c = Integer.valueOf(split[3]);

				getNumbers(NumbersType.DEATHS, NumbersTiming.ONSET).setCumulative();
				getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).setCumulative();
				getNumbers(NumbersType.DEATHS, NumbersTiming.ONSET).setNumbers(dayOfData, dayOfOnset, c);
				getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).setNumbers(dayOfData, dayOfInfection, c);
			} else if (split[0].equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Death")) {
				// TODO
			} else if (split[0].equals("Cases of COVID-19 in Colorado by Date Reported to the State")
					|| split[0].equals("Case Counts by Reported Date")) {
				if (split[2].equals("Cases")) {
					int dayOfReporting = Date.dateToDay(split[1]);
					int c = Integer.valueOf(split[3]);
					getNumbers(NumbersType.CASES, NumbersTiming.REPORTED).setNumbers(dayOfData, dayOfReporting, c);
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
					getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.REPORTED).setNumbers(dayOfData,
							dayOfReporting, c);
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
					getNumbers(NumbersType.DEATHS, NumbersTiming.REPORTED).setNumbers(dayOfData, dayOfReporting, c);
				} else {
					writeSplit(null, split);
				}
			} else if (split[0].equals("Colorado Case Counts by County") || split[0].equals("Case Counts by County")) {
				if (!split[1].equals("Note") && split[2].equals("Cases") && !split[1].contains("nknown")) {
					Integer c = Integer.valueOf(split[3]);
					CountyStats county = readCounty(split[1]);
					county.getCases().setCumulativeNumbers(dayOfData, c);
				}
			} else if (split[0].equals("Deaths") || split[0].equals("Number of Deaths by County")) {
				Integer c = Integer.valueOf(split[3]);
				CountyStats county = readCounty(split[1]);
				county.getDeaths().setCumulativeNumbers(dayOfData, c);
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
		for (int i = 0; i < incompleteNumbers.length; i++) {
			incompleteNumbers[i] = new IncompleteNumbers();
		}
		for (int i = 0; i < finalNumbers.length; i++) {
			finalNumbers[i] = new FinalNumbers();
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
			int number = testEncounters.getCumulativeNumbers(day);
			System.out.println("Day " + Date.dayToDate(day) + " test encounters = " + number);
		}

		/*
		 * So the challenge is that a negative test isn't associated with a
		 * reported/onset/infection day. It is therefore "impossible" to have a
		 * positivity rate for a given infection date. Sad!
		 * 
		 * This code splits up new tests (on this day of data) evenly among the
		 * cases from this day of data. So if we have 1000 new tests today and
		 * 100 new cases (10% positivity) evenly distributed over 10 days of
		 * onset, we'll assign 100 tests (10 cases / 10% positivity) to each of
		 * those 10 onset days.
		 * 
		 * Imperfect, but it's clearly the "correct" way to approximate it.
		 */
		for (int dayOfData = getFirstDay(); dayOfData <= getLastDay(); dayOfData++) {
			double casesOnDay = getNumbers(NumbersType.CASES).getDailyNumbers(dayOfData);
			double testsOnDay = testEncounters.getDailyNumbers(dayOfData);
			double positivity = casesOnDay / testsOnDay;
			System.out.println(String.format("%s : %.0f/%.0f = %.2f", Date.dayToDate(dayOfData), casesOnDay, testsOnDay,
					positivity * 100));
			for (NumbersTiming timing : NumbersTiming.values()) {
				IncompleteNumbers cases = getNumbers(NumbersType.CASES, timing);
				IncompleteNumbers tests = getNumbers(NumbersType.TESTS, timing);

				for (int dayOfTiming = 0; dayOfTiming <= dayOfData; dayOfTiming++) {
					double newCasesOnDay = cases.getNumbers(dayOfData, dayOfTiming)
							- cases.getNumbers(dayOfData - 1, dayOfTiming);
					double newTestsOnDay = newCasesOnDay / positivity;

					tests.addNumbers(dayOfData, dayOfTiming, tests.getNumbers(dayOfData - 1, dayOfTiming));
					tests.addNumbers(dayOfData, dayOfTiming, newTestsOnDay);
				}
			}
		}

		for (IncompleteNumbers incompletes : incompleteNumbers) {
			incompletes.build(this);
		}

		if (false) {
			outputProjections(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION);
			outputDailyStats();
		}

		// System.exit(0);
	}

}
