package covid;

public class DailyTracker {

	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;

	public synchronized int getFirstDay() {
		return firstDay;
	}

	public synchronized int getLastDay() {
		return lastDay;
	}

	public synchronized void includeDay(int day) {
		firstDay = Math.min(day, firstDay);
		lastDay = Math.max(day, lastDay);
	}
}
