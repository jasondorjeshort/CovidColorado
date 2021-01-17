package covid;

import java.util.Calendar;

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
public class CalendarUtils {

	public static Calendar dateToCalendar(String date) {
		String[] split = date.split("[/-]");

		if (split.length != 3) {
			throw new RuntimeException("Fail date: " + date);
		}

		int year, month, dayOfMonth;

		if (split[0].length() == 4) {
			// YYYY-MM-DD
			year = Integer.valueOf(split[0]);
			month = Integer.valueOf(split[1]);
			dayOfMonth = Integer.valueOf(split[2]);
		} else if (split[2].length() == 4) {
			// MM-DD-YYYY
			year = Integer.valueOf(split[2]);
			month = Integer.valueOf(split[0]);
			dayOfMonth = Integer.valueOf(split[1]);
		} else {
			return null;
		}

		Calendar cal = Calendar.getInstance();
		cal.set(year, month - 1, dayOfMonth);
		return cal;
	}

	private static final long MILLIS_PER_DAY = 86400l * 1000l;

	public static int dateToDay(String date) {
		return timeToDay(dateToTime(date));
	}

	public static long dateToTime(String date) {
		return dateToCalendar(date).getTimeInMillis();
	}

	public static int timeToDay(long time) {
		return (int) ((time + MILLIS_PER_DAY / 2) / MILLIS_PER_DAY);
	}

	public static long dayToTime(int day) {
		return MILLIS_PER_DAY * day + MILLIS_PER_DAY / 2;
	}

	public static String dayToDate(int day) {
		long time = dayToTime(day);
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(time);
		return calendarToDate(cal);
	}

	public static String calendarToDate(Calendar cal) {
		return String.format("%d/%d/%4d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
				cal.get(Calendar.YEAR));
	}

	public static Calendar dayToCalendar(int day) {
		long time = dayToTime(day);
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(time);
		return cal;
	}

	public static java.util.Date dayToJavaDate(int day) {
		return dayToCalendar(day).getTime();
	}

	public static String dayToFullDate(int day, char sep) {
		Calendar cal = dayToCalendar(day);

		return String.format("%d%c%02d%c%02d", cal.get(Calendar.YEAR), sep, cal.get(Calendar.MONTH) + 1, sep,
				cal.get(Calendar.DAY_OF_MONTH));
	}

	public static String dayToFullDate(int day) {
		return dayToFullDate(day, '-');
	}

	public static Day dayToDay(int day) {
		Calendar cal = dayToCalendar(day);
		return new Day(cal.getTime());
	}

}
