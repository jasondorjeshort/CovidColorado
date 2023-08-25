package nwss;

import java.util.Objects;

public class DaySewage {
	/*
	 * If there's no pop the sewageTot is just a value. But if pop IS included
	 * then it's the weighted sewage total, aka sewage * pop
	 */
	private double sewageTot;
	private Integer pop;

	public DaySewage(double sewage) {
		this.sewageTot = sewage;
		this.pop = null;
	}

	public DaySewage() {
		this.sewageTot = 0;
		this.pop = 0;
	}

	public void addDay(DaySewage day, int dayPop) {
		double daySewage;
		synchronized (day) {
			if (day.pop != null) {
				new Exception("Uh oh.").printStackTrace();
			}
			daySewage = day.sewageTot;
		}

		synchronized (this) {
			sewageTot += daySewage * pop;
			pop += dayPop;
		}
	}

	public double getSewage() {
		synchronized (this) {
			if (Objects.equals(pop, 0)) {
				return 0;
			}
			return pop == null ? sewageTot : sewageTot / pop;
		}
	}
}
