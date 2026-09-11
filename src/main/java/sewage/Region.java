package sewage;

/**
 * The wastewater curve for one region of this program's own grouping, which
 * {@code nwss/Regions.java} reads from regions.csv. {@code nwss/Nwss.java}
 * {@code read()} includes each plant whose jurisdiction maps here directly,
 * with its whole population, rather than summing the states; the states are
 * attached afterwards as children, for display only. Every region is in turn
 * a child of the national {@link All}, so each is also a line on the national
 * chart. One instance per distinct name, made in
 * {@code Nwss.getRegionSewage}.
 *
 * A jurisdiction with no row in regions.csv gets the region "Other", and that
 * is an ordinary Region: its own charts, its own line on the national chart,
 * and nothing outside {@code nwss/Regions.java} treats the name specially.
 */
public class Region extends Multi {

	private final String region;

	/**
	 * @param region
	 *            the name as {@code nwss/Regions.java} {@code getRegion}
	 *            returns it, never null. It goes verbatim into the title, the
	 *            legend label and the chart file name, so it must be usable
	 *            in a file name.
	 */
	public Region(String region) {
		this.region = region;
	}

	public synchronized String getRegion() {
		return region;
	}

	/**
	 * {@code regions\<name>}, relative to the sewage chart folder, and
	 * {@code charts/ChartSewage.java} appends which chart it is, such as
	 * {@code -recent} or {@code -all-avg7}. The save does not create the
	 * regions folder, so {@code ChartSewage.mkdirs()} has to have made it.
	 */
	@Override
	public String getChartFilename() {
		return charts.ChartSewage.REGIONS + "\\" + region;
	}

	/**
	 * The name and the "line pop": the largest single-day sum of the included
	 * plants' populations, which {@link Multi#includeSewage} keeps and which
	 * overstates the people served where one site reports two sample matrices.
	 */
	@Override
	public String getTitleLine() {
		return String.format("%s (%,d line pop)", getRegion(), getPopulation());
	}

	@Override
	public String getName() {
		return getRegion();
	}

}
