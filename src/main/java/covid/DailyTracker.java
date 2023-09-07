package covid;

public class DailyTracker {

	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;
	private final Object lock = new Object();

	public int getFirstDay() {
		synchronized (lock) {
			return firstDay;
		}
	}

	public void bumpFirstDay() {
		synchronized (lock) {
			firstDay++;
		}
	}

	public int getLastDay() {
		synchronized (lock) {
			return lastDay;
		}
	}

	public void includeDay(int day) {
		synchronized (lock) {
			firstDay = Math.min(day, firstDay);
			lastDay = Math.max(day, lastDay);
		}
	}

	public boolean hasDays() {
		synchronized (lock) {
			return lastDay >= firstDay;
		}
	}

	public int numDays() {
		synchronized (lock) {
			return Math.max(lastDay - firstDay + 1, 0);
		}
	}
}
