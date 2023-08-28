package nwss;

import java.util.Objects;

public class DaySewage {
	/*
	 * If there's no pop the sewageTot is just a value. But if pop IS included
	 * then it's the weighted sewage total, aka sewage * pop
	 */
	private double sewageTot;
	private Double effPop;
	private Double realPop;

	public DaySewage(double sewage) {
		this.sewageTot = sewage;
		this.effPop = this.realPop = null;
	}

	public DaySewage() {
		this.sewageTot = 0.0;
		this.effPop = this.realPop = 0.0;
	}

	public Double getPop() {
		return realPop;
	}

	public void addDay(DaySewage day, double normalizer, double dayPop, double weighting) {
		double daySewage;
		synchronized (day) {
			if (day.effPop != null) {
				new Exception("Uh oh.").printStackTrace();
			}
			daySewage = day.sewageTot;
		}
		daySewage *= normalizer;

		double ePop = dayPop * weighting;
		synchronized (this) {
			sewageTot += ePop * daySewage;
			effPop += ePop;
			realPop += dayPop;
		}
	}

	public double getSewage() {
		synchronized (this) {
			if (Objects.equals(effPop, 0.0)) {
				return 1;
			}
			return effPop == null ? sewageTot : sewageTot / effPop;
		}
	}
}
