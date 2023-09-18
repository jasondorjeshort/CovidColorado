package sewage;

import nwss.DaySewage;

public class Plant extends Abstract {

	public final String id;
	private int plantId;

	private String smoothing;
	private String state, counties, fipsIds;
	private double lat, lon;

	public Plant(String id) {
		this.id = id;
		if (id == null) {
			new Exception("Plant with null id is noooo go.").printStackTrace();
		}
	}

	@Override
	public String getTSName() {
		return String.format("Plant %d (%,d pop, %.2f normalizer, %,d days)", getPlantId(), getPopulation(),
				getNormalizer(), numDays());
	}

	public synchronized void setSmoothing(String smoothing) {
		this.smoothing = smoothing;
	}

	public synchronized String getSmoothing() {
		return smoothing;
	}

	public synchronized String getState() {
		return state;
	}

	public synchronized void setState(String state) {
		this.state = state;
	}

	public synchronized String getCounties() {
		return counties;
	}

	public synchronized void setCounties(String counties) {
		this.counties = counties;
	}

	public synchronized String getFipsIds() {
		return fipsIds;
	}

	public synchronized void setFipsIds(String fipsIds) {
		this.fipsIds = fipsIds;
	}

	public synchronized int getPlantId() {
		return plantId;
	}

	public synchronized void setPlantId(int plantId) {
		this.plantId = plantId;
	}

	public synchronized void setLatLon(double newLat, double newLon) {
		this.lat = newLat;
		this.lon = newLon;
	}

	public synchronized double getLat() {
		return lat;
	}

	public synchronized double getLon() {
		return lon;
	}

	public void buildNormalizer(All baseline) {
		double ours = 0, base = 0;
		int firstDay = getFirstDay(), lastDay = getLastDay();
		for (int day = firstDay; day < lastDay; day++) {
			DaySewage ds1 = getEntry(day), ds2 = baseline.getEntry(day);
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

	private double normalizer = 1;

	@Override
	public synchronized double getNormalizer() {
		return normalizer;
	}

	public synchronized void renorm(double factor) {
		normalizer /= factor;
	}

	@Override
	public String getChartFilename() {
		return charts.ChartSewage.PLANTS + "\\" + id;
	}

	@Override
	public synchronized String getTitleLine() {
		if (getPlantId() == 0) {
			return "(no metadata for this plant)";
		}
		return String.format("Plant %d - %s %s, %s (%,d line pop)", getPlantId(), getCounties(),
				counties.contains(",") ? "counties" : "county", getState(), getPopulation());
	}
}
