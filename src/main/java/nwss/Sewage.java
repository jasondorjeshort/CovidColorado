package nwss;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jfree.data.time.TimeSeries;

import Variants.Voc;
import covid.CalendarUtils;

public class Sewage {

	private int numPlants = 0;
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

	public synchronized void makeTimeSeries(TimeSeries series) {
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
			if (pop != null && popo != null && pop < popo / 4.0 && day > getLastDay() - 21) {
				break;
			}

			double number = entry.getSewage();
			number *= normalizer;
			if (number <= 0) {
				number = 1E-6;
			}
			series.add(CalendarUtils.dayToDay(day), number);
		}
	}

	public synchronized void makeTimeSeries(TimeSeries series, Voc voc, String variant) {
		for (int day = Math.max(getFirstDay(), voc.getFirstDay()); day <= getLastDay()
				&& day <= voc.getLastDay(); day++) {
			DaySewage entry;
			synchronized (this) {
				entry = entries.get(day);
			}
			if (entry == null) {
				continue;
			}

			Double pop = entry.getPop();

			double number = entry.getSewage();
			number *= normalizer;
			if (number <= 0) {
				number = 1E-6;
			}

			number *= voc.getPrevalence(day, variant);
			if (number <= 0) {
				number = 1E-12;
			}

			series.add(CalendarUtils.dayToDay(day), number);
		}
	}

	public synchronized TimeSeries makeRegressionTS(Voc voc, String variant) {
		final SimpleRegression fit = new SimpleRegression();
		final int fitLastDay = Math.min(getLastDay(), voc.getLastDay());
		final int fitFirstDay = Math.max(getFirstDay(), voc.getFirstDay());
		for (int day = fitFirstDay; day <= fitLastDay; day++) {
			DaySewage entry;
			synchronized (this) {
				entry = entries.get(day);
			}
			if (entry == null) {
				continue;
			}

			double number = entry.getSewage();
			number *= normalizer;

			number *= voc.getPrevalence(day, variant);
			if (number <= 0) {
				continue;
			}

			fit.addData(day, Math.log(number));
		}

		String name = variant.replaceAll("nextcladePangoLineage:", "");
		TimeSeries series = new TimeSeries(
				String.format("%s (%+.0f%%/week)", name, 100.0 * (Math.exp(7.0 * fit.getSlope()) - 1)));
		int first = Math.max(getFirstDay(), voc.getFirstDay());
		series.add(CalendarUtils.dayToDay(first), Math.exp(fit.predict(first)));
		int last = getLastDay() + 28;
		series.add(CalendarUtils.dayToDay(last), Math.exp(fit.predict(last)));
		return series;
	}

	public int getFirstDay(Voc voc) {
		return Math.max(getFirstDay(), voc.getFirstDay());
	}

	public int getLastDay(Voc voc) {
		return Math.min(getLastDay(), voc.getLastDay());
	}

	public synchronized HashMap<String, Double> getCumulativePrevalence(Voc voc, ArrayList<String> variants) {
		HashMap<String, Double> prevalence = new HashMap<>();

		int vFirstDay = getFirstDay(voc), vLastDay = getLastDay(voc);
		for (String variant : variants) {
			double number = 0;
			for (int day = vFirstDay; day <= vLastDay; day++) {
				Double prev = getSewageNormalized(day);
				if (prev == null) {
					continue;
				}
				number += prev * voc.getPrevalence(day, variant);
			}
			prevalence.put(variant, number);
		}

		variants.sort((v1, v2) -> -Double.compare(prevalence.get(v1), prevalence.get(v2)));

		for (String last : variants) {
			System.out.println("CP: " + last + " : " + prevalence.get(last));
		}

		return prevalence;
	}

	public Double getSewageNormalized(int day) {
		DaySewage entry;
		double n;
		synchronized (this) {
			entry = entries.get(day);
			n = normalizer;
		}
		if (entry == null) {
			return null;
		}
		return entry.getSewage() * n;
	}

	public synchronized TimeSeries makeRegressionTS(Voc voc, ArrayList<String> variants) {
		final int fitFirstDay = getFirstDay(voc);
		final int fitLastDay = getLastDay(voc);
		HashMap<String, SimpleRegression> fits = new HashMap<>();
		HashMap<String, Double> finals = new HashMap<>();
		int tsLastDay = getLastDay() + 28;
		for (String variant : variants) {
			final SimpleRegression fit = new SimpleRegression();
			for (int day = fitFirstDay; day <= fitLastDay; day++) {
				Double number = getSewageNormalized(day);
				if (number == null) {
					continue;
				}
				number *= voc.getPrevalence(day, variant);
				if (number <= 0) {
					continue;
				}

				fit.addData(day, Math.log(number));
			}

			fits.put(variant, fit);
			finals.put(variant, Math.exp(fits.get(variant).predict(tsLastDay)));
		}

		variants.sort((v1, v2) -> -Double.compare(finals.get(v1), finals.get(v2)));

		TimeSeries series = new TimeSeries(String.format("Collective fit"));
		for (int day = fitFirstDay; day <= tsLastDay; day++) {
			double number = 0.0;
			for (String variant : variants) {
				if (fits.get(variant) == null) {
					System.out.println("Impossible variant : " + variant);
					continue;
				}
				number += Math.exp(fits.get(variant).predict(day));
			}
			series.add(CalendarUtils.dayToDay(day), number);
		}

		return series;
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

	public synchronized double getNormalizer() {
		return normalizer;
	}

	public synchronized void setPopulation(int population) {
		this.population = population;
	}

	public synchronized int getPlantId() {
		return plantId;
	}

	public synchronized void setPlantId(int plantId) {
		this.plantId = plantId;
		numPlants = 1;
	}

	private synchronized void clear() {
		numPlants = 0;
		entries.clear();
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
			// new Exception("Uhhh no pop on " + sewage.id).printStackTrace();
			return;
		}
		synchronized (this) {
			if (population == null) {
				population = 0;
			}
			numPlants++;
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
		clear();
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

	public synchronized int getNumPlants() {
		return numPlants;
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
		clear();
		plants.forEach(p -> includeSewage(p, 1.0));
		double area = getTotalSewage(firstDay, lastDay);

		for (int i = 0; i < 2000; i++) {

			clear();
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

		clear();
		plants.forEach(p -> includeSewage(p, 1.0));

		while (entries.get(firstDay).getSewage() > entries.get(firstDay + 1).getSewage()) {
			firstDay++;
		}
	}

}
