package nwss;

import java.util.Collection;
import java.util.HashMap;

import org.jfree.data.time.TimeSeries;

import charts.ChartSewage;
import covid.CalendarUtils;

public class Sewage {

	private final HashMap<Integer, DaySewage> entries = new HashMap<>();

	private int firstDay = Integer.MAX_VALUE, lastDay = Integer.MIN_VALUE;

	public enum Type {
		PLANT,
		COUNTY,
		STATE,
		COUNTRY;
	}

	public final Type type;
	public final String id;
	private double normalizer = 1, oldNormalizer = 1;
	private String smoothing;
	private String state, county;

	/*
	 * If population is included it applies to the entire day set. If null then
	 * each day set should have its own.
	 */
	private Integer population;
	public int plantId;

	public Sewage(Type type, String plantName) {
		this.type = type;
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
			if (pop != null && popo != null && pop < popo * 0.75 && day > getLastDay() - 21) {
				// break;
			}

			double number = entry.getSewage();
			if (!isLogarithmic || number > 0) {
				series.add(CalendarUtils.dayToDay(day), number * normalizer);
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

	public synchronized int getNextZero(int startDay) {
		for (int day = startDay; day <= lastDay; day++) {
			DaySewage ds = entries.get(day);
			if (ds == null || ds.getSewage() <= 0) {
				return day;
			}
		}
		return lastDay + 1;
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
		int sFirstDay = sewage.getFirstDay(), sLastDay = sewage.getLastDay();
		int lastZero = sFirstDay - 1, nextZero = sewage.getNextZero(sFirstDay);
		double norm = sewage.normalizer;
		for (int day = sFirstDay; day <= sLastDay; day++) {
			DaySewage ds1, ds2;
			synchronized (sewage) {
				ds1 = sewage.entries.get(day);
			}
			if (ds1 == null) {
				lastZero = day;
				continue;
			}

			if (ds1.getSewage() == 0.0) {
				// lastZero = day;
			}
			synchronized (this) {
				ds2 = entries.get(day);
				if (ds2 == null) {
					ds2 = new DaySewage();
					entries.put(day, ds2);
				}
				includeDay(day);
			}

			if (day > nextZero) {
				nextZero = sewage.getNextZero(day);
			}
			double startMultiplier = Math.min(Math.pow((day - lastZero) / 182.0, 2.0), 1.0);
			double endMultiplier = Math.min(Math.pow((nextZero - day) / 14.0, 2.0), 1.0);
			ds2.addDay(ds1, norm, pop * popMultiplier, startMultiplier * endMultiplier);

			int dayPop = (int) Math.round(ds2.getPop());
			synchronized (this) {
				population = Math.max(population, dayPop);
			}
		}
	}

	public void buildNormalizer(Sewage baseline) {
		oldNormalizer = normalizer;

		double ours = 0, base = 0;
		for (int day = firstDay; day < lastDay; day++) {
			DaySewage ds1, ds2;

			synchronized (this) {
				ds1 = entries.get(day);
			}
			synchronized (baseline) {
				ds2 = baseline.entries.get(day);
			}
			if (ds1 == null || ds2 == null) {
				continue;
			}
			ours += ds1.getSewage();
			base += ds2.getSewage();
		}

		if (base == 0 || ours == 0) {
			return;
		}
		normalizer = base / ours;
		if (normalizer < 0 || base < 0 || ours < 0) {
			new Exception("Uh oh big fail.").printStackTrace();
		}
	}

	@SuppressWarnings("unused")
	private void normalizeFromCDC(Collection<Sewage> plants) {
		synchronized (this) {
			entries.clear();
		}
		plants.forEach(p -> {
			if (p.id.startsWith("CDC")) {
				System.out.println("Fixing baseline from " + p.id);
				includeSewage(p, 1.0);
			}
		});
		plants.forEach(p -> {
			if (!p.id.startsWith("CDC")) {
				p.buildNormalizer(this);
			}
		});
	}

	public synchronized double getTotalSewage(int first, int last) {
		double sewage = 0.0;
		for (int day = first; day <= last; day++) {
			DaySewage ds = entries.get(day);
			if (ds != null) {
				sewage += ds.getSewage();
			}
		}
		return sewage;
	}

	/*
	 * CDC numbers claim to be normalized, but the scales differ by up to 100.
	 * This makes averaging nigh on impossible, big problem. So I just normalize
	 * it here with some crazy area-preserving algorithm. It assumes (pretty
	 * close BUT probably not accurate for urban vs rural) that over long enough
	 * everywhere will have around the same amount of covid.
	 */
	@SuppressWarnings("unused")
	private void normalizeFull(Collection<Sewage> plants) {

		synchronized (this) {
			entries.clear();
		}
		plants.forEach(p -> includeSewage(p, 1.0));
		double area = getTotalSewage(firstDay, lastDay);

		synchronized (this) {
			entries.clear();
		}
		for (int i = 0; i < 2000; i++) {
			synchronized (this) {
				entries.clear();
			}
			plants.forEach(p -> includeSewage(p, 1.0));
			plants.forEach(p -> p.buildNormalizer(this));

			double renorm = getTotalSewage(firstDay, lastDay) / area;
			plants.forEach(p -> p.normalizer /= renorm);

			double normDiff = -1;
			Sewage normPlant = this;

			for (Sewage p : plants) {
				double d = Math.abs(Math.log(p.normalizer / p.oldNormalizer));
				if (normDiff > d) {
					normDiff = d;
					normPlant = p;
				}
				normDiff = Math.max(normDiff, d);
			}

			if (normDiff < 1E-6) {
				break;
			}
		}

		double cdcNorm = 0.0, cdcs = 0;
		for (Sewage p : plants) {
			if (p.id.startsWith("CDC")) {
				cdcNorm += Math.log(p.normalizer) * p.population;
				cdcs += p.population;
			}
		}
		double renorm = Math.exp(cdcNorm / cdcs);
		plants.forEach(p -> p.normalizer /= renorm);
	}

	public void buildCountry(Collection<Sewage> plants) {
		normalizeFull(plants);

		synchronized (this) {
			entries.clear();
		}
		plants.forEach(p -> includeSewage(p, 1.0));

		while (entries.get(firstDay).getSewage() > entries.get(firstDay + 1).getSewage()) {
			firstDay++;
		}
	}

}
