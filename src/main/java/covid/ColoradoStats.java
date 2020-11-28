package covid;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 * 
 * @author jdorje@gmail.com
 */
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

	public double getPositivity(int day, int interval) {
		double c = getNumbers(NumbersType.CASES).getNumbersInInterval(day, interval);
		double t = getNumbers(NumbersType.TESTS).getNumbersInInterval(day, interval);
		if (t == 0) {
			return 0;
		}

		// System.out.println(
		// "On " + Date.dayToDate(day) + " positivity is " + c + " / " + t + " =
		// " + (100 * c / t) + "%.");

		return c / t;
	}

	private void setNumbers(NumbersType type, NumbersTiming timing, IncompleteNumbers numbers) {
		incompleteNumbers[type.ordinal() * NumbersTiming.values().length + timing.ordinal()] = numbers;
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

	public int getLastDayOfType(NumbersType type, NumbersTiming timing, int dayOfData) {
		return getNumbers(type, timing).getLastDay(dayOfData);
	}

	private static void write(String lead, CSVRecord line) {
		if (lead != null) {
			System.out.print(lead + " : ");
		}
		System.out.print(line.get(0));
		for (int i = 1; i < line.size(); i++) {
			System.out.print(" | ");
			System.out.print(line.get(i));
		}
		System.out.println("");
	}

	public void outputProjections(NumbersType type, NumbersTiming timing) {
		int dayOfData = getLastDay();
		int DAYS = 7;
		for (int dayOfType = getLastDay() - 60; dayOfType <= getLastDay(); dayOfType++) {
			/*
			 * double c = getCasesByType(type, timing, dayOfType, dayOfType);
			 * double week = getCasesInInterval(type, timing, dayOfType,
			 * dayOfType, 7); System.out.println(type.capName + "," +
			 * timing.capName + "," + Date.dayToDate(dayOfType) + " => " +
			 * dayOfType + " => " + c + " => " + week);
			 */

			// IncompleteNumbers numbers = getNumbers(type, timing);

			double c = getExactProjectedCasesByType(type, timing, dayOfData, dayOfType);
			double week = getProjectedCasesInInterval(type, timing, dayOfData, dayOfType, DAYS);
			double lastWeek = getProjectedCasesInInterval(type, timing, dayOfData, dayOfType - DAYS, DAYS);
			double growth = 100 * Math.pow(week / lastWeek, 1.0 / DAYS) - 100;
			System.out.println(type.capName + "," + timing.capName + "," + Date.dayToDate(dayOfType) + " => "
					+ dayOfType + " => " + c + " => " + week + " => " + growth + "%");

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
		System.out.println("Newly released deaths:");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d",
				getNumbers(NumbersType.DEATHS).getDailyNumbers(t), getNumbers(NumbersType.DEATHS).getDailyNumbers(y),
				lastWeek, getNumbers(NumbersType.DEATHS).getDailyNumbers(w)));

		FinalNumbers cases = getNumbers(NumbersType.CASES);
		System.out.println("Newly released cases:");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d", cases.getDailyNumbers(t),
				cases.getDailyNumbers(y), lastWeek, cases.getDailyNumbers(w)));

		System.out.println("New test encounters:");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d",
				getNumbers(NumbersType.TESTS).getDailyNumbers(t), getNumbers(NumbersType.TESTS).getDailyNumbers(y),
				lastWeek, getNumbers(NumbersType.TESTS).getDailyNumbers(w)));

		System.out.println("New hospitalizations:");
		System.out.println(String.format("\tT: %,d | Y : %,d | %s : %,d",
				getNumbers(NumbersType.HOSPITALIZATIONS).getDailyNumbers(t),
				getNumbers(NumbersType.HOSPITALIZATIONS).getDailyNumbers(y), lastWeek,
				getNumbers(NumbersType.HOSPITALIZATIONS).getDailyNumbers(w)));

		System.out.println("Daily positivity:");
		System.out.println(String.format("\tT: %.2f%% | Y : %.2f%% | %s : %.2f%%", 100 * getPositivity(t, 1),
				100 * getPositivity(y, 1), lastWeek, 100 * getPositivity(w, 1)));

		System.out.println("Data age (14-day totals):");
		System.out.println(String.format("\tCases: %.1f | Y : %.1f | %s : %.1f",
				getNumbers(NumbersType.CASES, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(t),
				getNumbers(NumbersType.CASES, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(y), lastWeek,
				getNumbers(NumbersType.CASES, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(w)));
		System.out.println(String.format("\tHospitalizations: %.1f | Y : %.1f | %s : %.1f",
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(t),
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(y),
				lastWeek,
				getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(w)));
		System.out.println(String.format("\tDeaths: %.1f | Y : %.1f | %s : %.1f",
				getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(t),
				getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(y), lastWeek,
				getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).getAverageAgeOfNewNumbers(w)));

		System.out.println("");
	}

	private HashSet<String> keySet = new HashSet<>();

	/*
	 * The "test encounters" metric only began on this day. Before that it's
	 * just "people tested".
	 */
	private static final int TEST_ENCOUNTERS_STARTED = 205;

	private final Charset charset = Charset.forName("US-ASCII");

	public boolean readCSV(int dayOfData) {
		File f = new File(csvFileName(dayOfData));
		try (CSVParser csv = CSVParser.parse(f, charset, CSVFormat.DEFAULT)) {
			synchronized (this) {
				lastDay = Math.max(dayOfData, lastDay);
			}

			for (CSVRecord line : csv) {
				int number;
				try {
					number = Integer.valueOf(line.get(3));
				} catch (Exception e) {
					number = 0;
				}

				if (line.get(0).equals("description") || line.get(1).equals("Note")) {
					// ignore notes!
				} else if (line.get(0).equals("State Data") && line.get(1).equals("Statewide")) {
					if (line.get(2).equals("Cases")) {
						getNumbers(NumbersType.CASES).setCumulativeNumbers(dayOfData, number);
					} else if (line.get(2).equals("Hospitalizations")) {
						getNumbers(NumbersType.HOSPITALIZATIONS).setCumulativeNumbers(dayOfData, number);
					} else if (line.get(2).equals("Deaths")) {
						// this was split up into deaths among cases (PUI) and
						// deaths due to covid (confirmed). Before it was just
						// deaths for both.
						getNumbers(NumbersType.DEATHS).setCumulativeNumbers(dayOfData, number);
						confirmedDeaths.setCumulativeNumbers(dayOfData, number);
					} else if (line.get(2).equals("Deaths Among Cases")) {
						getNumbers(NumbersType.DEATHS).setCumulativeNumbers(dayOfData, number);
					} else if (line.get(2).equals("Deaths Due to COVID-19")) {
						confirmedDeaths.setCumulativeNumbers(dayOfData, number);
					} else if (line.get(2).equals("Test Encounters")) {
						if (dayOfData < TEST_ENCOUNTERS_STARTED) {
							new Exception("SHOULD NOT BE HERE???").printStackTrace();
						}
						getNumbers(NumbersType.TESTS).setCumulativeNumbers(dayOfData, number);
					} else if (line.get(2).equals("People Tested")) {
						peopleTested.setCumulativeNumbers(dayOfData, number);
						if (dayOfData < TEST_ENCOUNTERS_STARTED) {
							getNumbers(NumbersType.TESTS).setCumulativeNumbers(dayOfData, number);
						}
					} else if (line.get(2).equals("Counties")) {
					} else if (line.get(2).equals("Rate Per 100000") || line.get(2).equals("Rate per 100,000")) {
						// uh, simple bug that the CSV reader ignores " escaping
						// and so treats 100,000 as a separator
					} else if (line.get(2).equals("Outbreaks")) {
					} else {
						write(Date.dayToDate(dayOfData) + "???", line);
					}
				} else if (line.get(0).equals("Case Counts by Onset Date")
						|| line.get(0).equals("Cases of COVID-19 in Colorado by Date of Illness Onset")) {
					if (line.get(2).equals("Cases")) {
						int dayOfOnset = Date.dateToDay(line.get(1));
						int dayOfInfection = dayOfOnset - 5;
						int c = Integer.valueOf(line.get(3));

						getNumbers(NumbersType.CASES, NumbersTiming.ONSET).setNumbers(dayOfData, dayOfOnset, c);
						getNumbers(NumbersType.CASES, NumbersTiming.INFECTION).setNumbers(dayOfData, dayOfInfection, c);
					}
				} else if (line.get(0).equals(
						"Cumulative Number of Hospitalized Cases of COVID-19 in Colorado by Date of Illness Onset")
						|| line.get(0).equals("Cumulative Number of Hospitalizations by Onset Date")) {
					int dayOfOnset = Date.dateToDay(line.get(1));
					int dayOfInfection = dayOfOnset - 5;
					int c = Integer.valueOf(line.get(3));

					getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.ONSET).setCumulative();
					getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).setCumulative();
					getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.ONSET).setNumbers(dayOfData, dayOfOnset, c);
					getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION).setNumbers(dayOfData,
							dayOfInfection, c);
				} else if (line.get(0).equals("Cumulative Number of Deaths by Onset Date") || line.get(0)
						.equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Illness")) {
					int dayOfOnset = Date.dateToDay(line.get(1));
					int dayOfInfection = dayOfOnset - 5;
					int c = Integer.valueOf(line.get(3));

					getNumbers(NumbersType.DEATHS, NumbersTiming.ONSET).setCumulative();
					getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).setCumulative();
					getNumbers(NumbersType.DEATHS, NumbersTiming.ONSET).setNumbers(dayOfData, dayOfOnset, c);
					getNumbers(NumbersType.DEATHS, NumbersTiming.INFECTION).setNumbers(dayOfData, dayOfInfection, c);
				} else if (line.get(0)
						.equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date of Death")) {
					// TODO
				} else if (line.get(0).equals("Cases of COVID-19 in Colorado by Date Reported to the State")
						|| line.get(0).equals("Case Counts by Reported Date")) {
					if (line.get(2).equals("Cases")) {
						int dayOfReporting = Date.dateToDay(line.get(1));
						int c = Integer.valueOf(line.get(3));
						getNumbers(NumbersType.CASES, NumbersTiming.REPORTED).setNumbers(dayOfData, dayOfReporting, c);
					} else if (line.get(2).equals("Three-Day Moving Average Of Cases")) {
						// redundant
					} else {
						write(null, line);
					}
				} else if (line.get(0).equals(
						"Cumulative Number of Hospitalized Cases of COVID-19 in Colorado by Date Reported to the State")
						|| line.get(0).equals("Cumulative Number of Hospitalizations by Reported Date")) {
					if (line.get(2).equals("Cases")) {
						int dayOfReporting = Date.dateToDay(line.get(1));
						int c = Integer.valueOf(line.get(3));
						getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.REPORTED).setCumulative();
						getNumbers(NumbersType.HOSPITALIZATIONS, NumbersTiming.REPORTED).setNumbers(dayOfData,
								dayOfReporting, c);
					} else {
						write(null, line);
					}
				} else if (line.get(0)
						.equals("Cumulative Number of Deaths From COVID-19 in Colorado by Date Reported to the State")
						|| line.get(0).equals("Cumulative Number of Deaths by Reported Date")) {
					if (line.get(2).equals("Cases")) {
						int dayOfReporting = Date.dateToDay(line.get(1));
						int c = Integer.valueOf(line.get(3));
						getNumbers(NumbersType.DEATHS, NumbersTiming.REPORTED).setCumulative();
						getNumbers(NumbersType.DEATHS, NumbersTiming.REPORTED).setNumbers(dayOfData, dayOfReporting, c);
					} else {
						write(null, line);
					}
				} else if (line.get(0).equals("Colorado Case Counts by County")
						|| line.get(0).equals("Case Counts by County")) {
					if (!line.get(1).equals("Note") && line.get(2).equals("Cases") && !line.get(1).contains("nknown")) {
						Integer c = Integer.valueOf(line.get(3));
						CountyStats county = readCounty(line.get(1));
						county.getCases().setCumulativeNumbers(dayOfData, c);
					}
				} else if (line.get(0).equals("Deaths") || line.get(0).equals("Number of Deaths by County")) {
					Integer c = Integer.valueOf(line.get(3));
					CountyStats county = readCounty(line.get(1));
					county.getDeaths().setCumulativeNumbers(dayOfData, c);
				} else if (line.get(0).equals("Daily Serology Data From Clinical Laboratories")) {
					// ignored?
				} else if (line.get(0).equals("Positivity Data from Clinical Laboratories")) {
					// ignored?
				} else if (line.get(0).equals("Case Status for Cases & Deaths")) {
					// ignored?
				} else if (line.get(0).equals("COVID-19 in Colorado by Sex")) {
					// TODO maybe?
				} else if (line.get(0).equals("COVID-19 in Colorado by Race & Ethnicity")) {
					// TODO maybe?
				} else if (line.get(0).equals("COVID-19 in Colorado by Age Group")) {
					// TODO maybe?
				} else if (line.get(0).equals("Case Counts by Age Group, Hospitalizations, and Deaths")) {
					// ignored?
				} else if (line.get(0).equals("Case Counts by Age Group, Hospitalizations")) {
					// ignored?
				} else if (line.get(0).equals("Transmission Type")) {
					// ignored?
				} else if (line.get(0).equals("Case Counts by Sex")) {
					// ignored?
				} else if (line.get(0).equals("Fatal cases by sex")) {
					// ignored?
				} else if (line.get(0).equals("Cumulative Number of Cases by Onset Date")) {
					// redundant
				} else if (line.get(0).equals("Cumulative Number of Cases by Reported Date")) {
					// redundant
				} else if (line.get(0)
						.equals("Cumulative Number of Cases of COVID-19 in Colorado by Date of Illness Onset")) {
					// redundant
				} else if (line.get(0)
						.equals("Cumulative Number of Cases of COVID-19 in Colorado by Date Reported to the State")) {
					// redundant
				} else if (line.get(0).equals("Total COVID-19 Tests Performed in Colorado by County")) {
					// TODO
				} else if (line.get(0).equals("Number of Deaths From COVID-19 in Colorado by Date of Death - By Day")) {
					// redundant
				} else if (line.get(0).equals("Cases of COVID-19 Reported in Colorado by Age Group")
						|| line.get(0).equals("Case Counts by Age Group")
						|| line.get(0).equals("Case Rates Per 100,000 People in Colorado by County")
						|| line.get(0).equals("Total Testing Rate Per 100,000 People in Colorado by County")
						|| line.get(0).equals(
								"Cases of COVID-19 Reported in Colorado by Age Group, Hospitalization, and Outcome")) {
					// ignored?
				} else {
					if (!keySet.contains(line.get(0))) {
						keySet.add(line.get(0));
						write(Date.dayToDate(dayOfData), line);
					}
				}
			}
		} catch (IOException e1) {
			return false;
		}

		return true;
	}

	public ColoradoStats() {
		for (NumbersTiming timing : NumbersTiming.values()) {
			for (NumbersType type : NumbersType.values()) {
				setNumbers(type, timing, new IncompleteNumbers(type, timing));
			}
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
			double testsOnDay = getNumbers(NumbersType.TESTS).getDailyNumbers(dayOfData);
			double positivity = casesOnDay / testsOnDay;
			// System.out.println(String.format("%s : %.0f/%.0f = %.2f",
			// Date.dayToDate(dayOfData), casesOnDay, testsOnDay,
			// positivity * 100));
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

		if (true) {
			outputProjections(NumbersType.HOSPITALIZATIONS, NumbersTiming.INFECTION);
			outputDailyStats();
		}

		// System.exit(0);
	}

}
