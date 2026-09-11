package sewage;

import nwss.DaySewage;

/**
 * One treatment plant's series, in the plant's own units, and the scalar
 * normalizer that puts it on the national baseline's scale.
 * {@code nwss/Nwss.java} readSewage makes one per {@link #id} and sets its
 * readings and metadata; {@link All} fits the normalizer; the aggregates and
 * the charts read the readings through it. docs/reference/wastewater.txt, under
 * THE NATIONAL BASELINE, has the design.
 * <p>
 * {@link #id} is the plant's identity: source, state, site, sample location and
 * sample matrix joined by underscores. The site id alone, which
 * {@link #getName} and the chart titles show, is not unique: one site reporting
 * through two sources or two sample matrices is two plants, and 416 site ids
 * belonged to more than one plant in the download of 2026-09-10.
 * <p>
 * Several members have no reader. For {@link Source}, see
 * docs/active/findings/2026-09-09-plant-source-enum-is-dead-code.md; the FIPS
 * ids and coordinates belong to the unfinished geographic aggregate,
 * docs/ideas/2026-09-10-the-abandoned-geo-aggregate-could-be-removed-or-finished.md.
 */
public class Plant extends Abstract {

	public final String id;
	private final Source source;
	/* The site id, or 0 when readSewage could not parse it as an integer. */
	private int plantId;

	/*
	 * The CDC column the series was read from (Normalization in
	 * nwss/Nwss.java), not a smoothing. Written once and read by nothing.
	 */
	private String smoothing;
	private String state, counties, fipsIds;
	/* setLatLon has no caller, so both stay 0.0; getLat and getLon have none. */
	private double lat, lon;

	public enum Source {
		STATE_TERRITORY,
		WASTEWATERSCAN,
		CDC_VERILY,
		CDC_BIOBOT;

		public static Source get(String plantId) {
			for (Source source : Source.values()) {
				if (plantId.startsWith(source.name())) {
					return source;
				}
			}
			return null;
		}
	}

	public Plant(String id) {
		this.id = id;
		/*
		 * The only caller, getPlantSewage in nwss/Nwss.java, builds the id by
		 * concatenation, so it is never null. If it were, the trace would print
		 * and Source.get would then throw NullPointerException: that throw, not
		 * this check, is what stops a null-id plant being made.
		 */
		if (id == null) {
			new Exception("Plant with null id is noooo go.").printStackTrace();
		}
		this.source = Source.get(id);
	}

	@Override
	public String getTSName() {
		return String.format("Plant %d (%,d pop, %.2f normalizer, %,d days)", getPlantId(), getPopulation(),
				getNormalizer(), numDays());
	}

	@Override
	public String getName() {
		return String.format("Plant %d", getPlantId());
	}

	public Source getSource() {
		return source;
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

	/**
	 * Sets the normalizer to (baseline total / this plant's total) over the days
	 * both have a reading, so that the plant's normalized readings sum to the
	 * baseline's over those days. {@link All} calls it for every plant in each
	 * round of its baseline loop, on the main thread. It writes the normalizer
	 * without the lock the accessors take, which is safe only because nothing
	 * else touches a plant while that loop runs.
	 * <p>
	 * Two things about today's behaviour, both recorded in
	 * docs/active/findings/2026-09-10-the-baseline-loop-never-converges.md. The
	 * loop stops short of the plant's last day, so its last reading never
	 * counts, and nothing records why. And when either sum is 0 the normalizer
	 * is left as it was, which after the first round is 1 divided by every
	 * renormalization so far; a plant with a one-day range always takes that
	 * branch.
	 */
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
		/*
		 * Cannot fire on today's intake: parseValue in nwss/Nwss.java clamps
		 * every reading at 0, so neither sum can be negative. Nor does it catch
		 * NaN, which is what a NaN baseline day would make of the normalizer,
		 * since every comparison with NaN is false.
		 */
		if (normalizer < 0 || base < 0 || ours < 0) {
			new Exception("Uh oh big fail.").printStackTrace();
		}
	}

	private double normalizer = 1;

	@Override
	public synchronized double getNormalizer() {
		return normalizer;
	}

	/**
	 * Divides the normalizer by {@code factor}. {@link All}'s baseline loop calls
	 * it on every plant after each round's fit, with the baseline's highest day
	 * since 2020-09-01 divided by 100, which scales that day to 100.
	 */
	public synchronized void renorm(double factor) {
		normalizer /= factor;
	}

	/**
	 * The id goes into the path as it is, and that is safe only because the
	 * dataset's values are: in the download of 2026-09-10 no id held a character
	 * Windows refuses in a file name, and {@code Charts.saveBufferedImageAsPNG}
	 * strips only ':' and '|'. Nor did two ids differ only in case, which on
	 * Windows would write one plant's charts over another's.
	 */
	@Override
	public String getChartFilename() {
		return charts.ChartSewage.PLANTS + "\\" + id;
	}

	@Override
	public synchronized String getTitleLine() {
		/*
		 * plantId 0 as "no metadata" dates from when metadata came from a
		 * separate table a plant could be missing from. Since the move to
		 * j9g8-acpt every plant's metadata comes from its own rows, and plantId
		 * stays 0 only for a site id that is not an integer, which none was in
		 * the download of 2026-09-10. counties is never null past this test:
		 * readSewage sets it alongside plantId, from the same row.
		 */
		if (getPlantId() == 0) {
			return "(no metadata for this plant)";
		}
		return String.format("Plant %d - %s %s, %s (%,d line pop)", getPlantId(), getCounties(),
				counties.contains(",") ? "counties" : "county", getState(), getPopulation());
	}
}
