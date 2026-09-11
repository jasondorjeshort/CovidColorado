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
 * Converts between date strings, epoch milliseconds and the program's day
 * index, the int that every per-day map and array is keyed by. A day index
 * counts days from 1970-01-01, the Java epoch, not from 2020: 2020-01-01 is day
 * 18262.
 *
 * The two directions are not symmetric. A day becomes a moment at noon UTC
 * ({@link #dayToTime}), and the methods that render a day read that moment in
 * the machine's zone, which gives the day's own date in any zone less than
 * twelve hours ahead of UTC. A moment becomes a day by truncating its UTC
 * milliseconds ({@link #timeToDay}), so what it gives is the UTC date. That,
 * with {@link #dateToCalendar} keeping the time of day of the call, is why a
 * date string parsed in the evening lands a day late; see
 * docs/active/findings/2026-09-10-a-date-parsed-in-the-evening-lands-on-the-wrong-day.md.
 *
 * Every method builds its own {@link Calendar}, so the class holds no state and
 * any thread may call it.
 *
 * @author jdorje@gmail.com
 */
public class CalendarUtils {

	/** The tropical year in days, for stepping a day index by a year. */
	public static final double YEAR = 365.24217;

	/**
	 * Parses YYYY-MM-DD or MM-DD-YYYY, with '-' or '/' in any mix and fields
	 * padded or not; which shape it is comes from whether the first or the third
	 * field is four characters long.
	 *
	 * The calendar is lenient, so an out-of-range field rolls over instead of
	 * failing: 2025-02-30 is 2025-03-02 and month 13 is the next January.
	 *
	 * As it stands, the result is in the machine's zone at the time of day of
	 * the call, not at midnight; see the evening finding named in the class
	 * Javadoc.
	 *
	 * @throws RuntimeException if there are not three fields, or neither the
	 *                          first nor the third is four characters long (a
	 *                          two-digit year, a leading space)
	 * @throws NumberFormatException if a field is not an integer, as when a time
	 *                               follows a YYYY-MM-DD date
	 */
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
			throw new RuntimeException("Fail date: " + date);
		}

		Calendar cal = Calendar.getInstance();
		cal.set(year, month - 1, dayOfMonth);
		return cal;
	}

	private static final long MILLIS_PER_DAY = 86400l * 1000l;

	/**
	 * Day index of a date string, parsed and failing as
	 * {@link #dateToCalendar}. As it stands, the index depends on the time of
	 * day of the call; see the evening finding named in the class Javadoc.
	 */
	public static int dateToDay(String date) {
		return timeToDay(dateToTime(date));
	}

	/** The moment {@link #dateToCalendar} gives, in epoch milliseconds. */
	public static long dateToTime(String date) {
		return dateToCalendar(date).getTimeInMillis();
	}

	/**
	 * Day index of the UTC date containing a moment, and the exact inverse of
	 * {@link #dayToTime} on whole days. Given the current moment it gives the UTC
	 * date, which on this machine is tomorrow's local date from 17:00 in winter
	 * and 18:00 in summer.
	 */
	public static int timeToDay(long time) {
		return (int) ((time) / MILLIS_PER_DAY);
	}

	/**
	 * Noon UTC of a day, in epoch milliseconds; a fractional day is offset from
	 * that noon by its fraction.
	 */
	public static long dayToTime(double day) {
		return Math.round(MILLIS_PER_DAY * day + MILLIS_PER_DAY / 2);
	}

	/** The day's date as M/D/YYYY, unpadded, read in the machine's zone. */
	public static String dayToDate(double day) {
		long time = dayToTime(day);
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(time);
		return calendarToDate(cal);
	}

	public static String calendarToDate(Calendar cal) {
		return String.format("%d/%d/%4d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
				cal.get(Calendar.YEAR));
	}

	public static Calendar dayToCalendar(double day) {
		return timeToCalendar(dayToTime(day));
	}

	public static Calendar timeToCalendar(long time) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(time);
		return cal;
	}

	public static java.util.Date dayToJavaDate(double day) {
		return dayToCalendar(day).getTime();
	}

	/**
	 * Noon UTC of the parsed date's day index, not the moment
	 * {@link #dateToCalendar} gives.
	 */
	public static java.util.Date dateToJavaDate(String date) {
		return dayToCalendar(dateToDay(date)).getTime();
	}

	/** The day's date as YYYY-MM-DD, zero-padded, with sep for the dashes. */
	public static String dayToFullDate(double day, char sep) {
		Calendar cal = dayToCalendar(day);

		return String.format("%d%c%02d%c%02d", cal.get(Calendar.YEAR), sep, cal.get(Calendar.MONTH) + 1, sep,
				cal.get(Calendar.DAY_OF_MONTH));
	}

	public static String dayToFullDate(double day) {
		return dayToFullDate(day, '-');
	}

	/**
	 * The JFreeChart {@link Day} of a day index. {@link Day#Day(java.util.Date)}
	 * reads the moment in the machine's zone, so this is the day's own date on
	 * the terms the class Javadoc gives.
	 */
	public static Day dayToDay(int day) {
		Calendar cal = dayToCalendar(day);
		return new Day(cal.getTime());
	}

}
