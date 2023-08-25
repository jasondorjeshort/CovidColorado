package nwss;

import java.util.HashMap;

import org.jfree.data.time.TimeSeries;

import covid.CalendarUtils;

public class Sewage {

	private final HashMap<Integer, DaySewage> entries = new HashMap<>();

	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;

	public final String id;
	private String smoothing;
	private String state, county;

	/*
	 * If population is included it applies to the entire day set. If null then
	 * each day set should have its own.
	 */
	private Integer population;
	private int plantId;

	public Sewage(String plantName) {
		this.id = plantName;
	}

	public synchronized void setSmoothing(String smoothing) {
		this.smoothing = smoothing;
	}

	public synchronized String getSmoothing() {
		return smoothing;
	}

	public synchronized int getFirstDay() {
		return firstDay;
	}

	public synchronized int getLastDay() {
		return lastDay;
	}

	private synchronized void includeDay(int day) {
		firstDay = Math.min(day, firstDay);
		lastDay = Math.max(day, lastDay);
	}

	public synchronized void addEntry(int day, double value) {
		DaySewage entry = new DaySewage(value);
		entries.put(day, entry);
		includeDay(day);
	}

	public synchronized void makeTimeSeries(TimeSeries series, boolean isLogarithmic) {
		Integer popo = getPopulation();
		for (int day = getFirstDay(); day <= getLastDay(); day++) {
			DaySewage entry;
			synchronized (this) {
				entry = entries.get(day);
			}
			if (entry == null) {
				continue;
			}

			Double pop = entry.getPop();
			if (pop != null && popo != null && pop < popo / 2.0) {
				continue;
			}

			double number = entry.getSewage();
			if (!isLogarithmic || number > 0) {
				series.add(CalendarUtils.dayToDay(day), number);
			}
		}
	}

	public synchronized String getState() {
		return state;
	}

	public synchronized void setState(String state) {
		this.state = state;
	}

	public synchronized String getCounty() {
		return county;
	}

	public synchronized void setCounty(String county) {
		this.county = county;
	}

	public synchronized Integer getPopulation() {
		return population;
	}

	public synchronized void setPopulation(int population) {
		this.population = population;
	}

	public synchronized int getPlantId() {
		return plantId;
	}

	public synchronized void setPlantId(int plantId) {
		this.plantId = plantId;
	}

	public void includeSewage(Sewage sewage, double popMultiplier) {
		Integer pop = sewage.getPopulation();
		if (pop == null) {
			new Exception("Uhhh no pop on " + sewage.id).printStackTrace();
			return;
		}
		synchronized (this) {
			if (population == null) {
				population = 0;
			}
		}
		for (int day = sewage.getFirstDay(); day <= sewage.getLastDay(); day++) {
			DaySewage ds1, ds2;
			synchronized (sewage) {
				ds1 = sewage.entries.get(day);
			}
			if (ds1 == null) {
				continue;
			}
			synchronized (this) {
				ds2 = entries.get(day);
				if (ds2 == null) {
					ds2 = new DaySewage();
					entries.put(day, ds2);
				}
				includeDay(day);
			}

			ds2.addDay(ds1, pop * popMultiplier + 0.5);

			int dayPop = (int) Math.round(ds2.getPop());
			synchronized (this) {
				population = Math.max(population, dayPop);
			}
		}
	}

}
