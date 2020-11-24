package covid;

import org.jfree.data.time.Day;

/**
 * 
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
 * -----
 * 
 * Trivial reinvention of the wheel to turn a date in 2020 into the number of
 * days since the start of 2020, and back.
 * 
 * @author jdorje@gmail.com
 */
public class Date {

	static enum Month {
		JANUARY(31),
		FEBRUARY(29),
		MARHC(31),
		APRIL(30),
		MAY(31),
		JUNE(30),
		JULY(31),
		AUGUST(31),
		SEPTEMBER(30),
		OCTOBER(31),
		NOVEMBER(30),
		DECEMBER(31)

		;

		final int days;
		int totalDays = -1;

		Month(int days) {
			this.days = days;
		}
	}

	static final int YEAR = 2020; // 2021 doesn't matter, right?

	static {
		int tot = 0;
		for (Month m : Month.values()) {
			m.totalDays = tot;
			tot += m.days;
		}
	}

	// Number of days since start of 2020 (1 = Jan 1).
	public static int dateToDay(String date) {
		String[] split = date.split("[/-]");

		if (split.length != 3) {
			throw new RuntimeException("Fail date: " + date);
		}

		if (split[0].length() == 4) {
			// YYYY-MM-DD
			int year = Integer.valueOf(split[0]);
			if (year != YEAR && year != YEAR % 100) {
				throw new RuntimeException("Fail: " + date + ", " + split[2]);
			}

			int month = Integer.valueOf(split[1]);
			int day = Integer.valueOf(split[2]);

			Month m = Month.values()[month - 1];

			day += m.totalDays;

			// System.out.println("Read " + date + " as " + day);
			return day;
		}

		if (split[2].length() == 4) {
			// MM-DD-YYYY
			int year = Integer.valueOf(split[2]);
			if (year != YEAR && year != YEAR % 100) {
				throw new RuntimeException("Fail: " + date + ", " + split[2]);
			}

			int month = Integer.valueOf(split[0]);
			int day = Integer.valueOf(split[1]);

			Month m = Month.values()[month - 1];
			day += m.totalDays;
			return day;
		}

		return -1;
	}

	public static long dateToTime(String date) {
		int day = dateToDay(date);
		return dayToTime(day);
	}

	public static long dayToTime(int day) {
		return dayToJavaDate(day).getTime();
	}

	public static String dayToDate(int day) {
		for (Month m : Month.values()) {
			if (day > m.totalDays && day <= m.totalDays + m.days) {
				return String.format("%d/%d", m.ordinal() + 1, day - m.totalDays);
			}
		}

		return "???";
	}

	public static java.util.Date dayToJavaDate(int day) {
		for (Month m : Month.values()) {
			if (day > m.totalDays && day <= m.totalDays + m.days) {
				return new java.util.Date(YEAR - 1900, m.ordinal(), day - m.totalDays);
			}
		}
		return null;
	}

	public static String dayToFullDate(int day, char sep) {
		for (Month m : Month.values()) {
			if (day > m.totalDays && day <= m.totalDays + m.days) {
				return String.format("%d%c%02d%c%02d", YEAR, sep, m.ordinal() + 1, sep, day - m.totalDays);
			}
		}

		return "???";
	}

	public static String dayToFullDate(int day) {
		return dayToFullDate(day, '-');
	}

	public static Day dayToDay(int day) {
		for (Month m : Month.values()) {
			if (day > m.totalDays && day <= m.totalDays + m.days) {
				return new Day(day - m.totalDays, m.ordinal() + 1, YEAR);
			}
		}
		return null;
	}

}
