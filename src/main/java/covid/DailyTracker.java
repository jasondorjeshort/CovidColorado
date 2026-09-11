package covid;

/**
 * The inclusive range of days a series covers, in {@link CalendarUtils} day
 * indices. A subclass widens it with {@link #includeDay} for each dated value
 * it takes in, and may then trim either end. The range only bounds the data:
 * a day inside it need not have any.
 * <p>
 * Before the first {@code includeDay} the range is empty and its bounds are
 * sentinels, {@code Integer.MAX_VALUE} first and {@code Integer.MIN_VALUE}
 * last, so a {@code first <= day <= last} loop runs zero times. Trimming the
 * ends past each other also leaves it empty, with ordinary bounds. Nothing
 * resets it.
 * <p>
 * Each method is atomic under a private lock, not the object's monitor. A
 * sequence of calls is not atomic, so a caller that reads a bound and trims on
 * the strength of it must keep other writers out itself; the trimming loops in
 * {@code sewage.Multi} and {@code variants.Voc} do so by running inside their
 * owner's synchronized {@code build()}.
 */
public class DailyTracker {

	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;
	private final Object lock = new Object();

	/** The first day of the range, or {@code Integer.MAX_VALUE} before any is included. */
	public int getFirstDay() {
		synchronized (lock) {
			return firstDay;
		}
	}

	/**
	 * Trims the first day off the range. Nothing stops the first day passing
	 * the last, which empties the range. Not for a tracker that has never
	 * included a day: the sentinel overflows to {@code Integer.MIN_VALUE} and
	 * the range then reads as one day long.
	 */
	public void bumpFirstDay() {
		synchronized (lock) {
			firstDay++;
		}
	}

	/** The last day of the range, or {@code Integer.MIN_VALUE} before any is included. */
	public int getLastDay() {
		synchronized (lock) {
			return lastDay;
		}
	}

	/**
	 * Trims the last day off the range. Nothing stops the last day passing the
	 * first, which empties the range. Not for a tracker that has never
	 * included a day: the sentinel overflows to {@code Integer.MAX_VALUE} and
	 * the range then reads as one day long.
	 */
	public void dropLastDay() {
		synchronized (lock) {
			lastDay--;
		}
	}

	/**
	 * Widens the range, if it has to, to cover {@code day}. No trim is
	 * remembered, so an include after one can undo it: trim after the last
	 * include.
	 */
	public void includeDay(int day) {
		synchronized (lock) {
			firstDay = Math.min(day, firstDay);
			lastDay = Math.max(day, lastDay);
		}
	}

	/** Whether the range holds at least one day. */
	public boolean hasDays() {
		synchronized (lock) {
			return lastDay >= firstDay;
		}
	}

	/** The number of days in the range, both ends included; 0 when it is empty. */
	public int numDays() {
		synchronized (lock) {
			/* Compare before subtracting: on the empty sentinels last - first + 1 overflows to 2. */
			return lastDay >= firstDay ? lastDay - firstDay + 1 : 0;
		}
	}
}
