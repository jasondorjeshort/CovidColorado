package sewage;

/**
 * One county: the plants whose counties_served lists it, each included
 * directly by {@code nwss/Nwss.java} {@code read()} with its population split
 * evenly among the counties it lists (docs/reference/wastewater.txt, under
 * AGGREGATION AND THE HIERARCHY). Those plants are also its display children,
 * all of them drawn on its charts, and the county is in turn a display child
 * of its {@link State}.
 *
 * Nwss keeps one instance per state and county name, made in
 * {@code getCountySewage}. The name is one entry of the plant's most recent
 * counties_served, split on commas, and nothing checks it: a plant whose
 * counties_served is blank makes a county named the empty string, whose charts
 * are files named like {@code -recent.png} in the state's directory.
 */
public class County extends Multi {

	private final String county;
	private final String state;

	/**
	 * @param county
	 *            the name as the plant lists it, never null, possibly empty
	 * @param state
	 *            the full state name, never null: the same string as the
	 *            {@link State} key, which is how Nwss finds this county's
	 *            parent and the directory its charts go in
	 */
	public County(String county, String state) {
		this.county = county;
		this.state = state;
	}

	public synchronized String getCounty() {
		return county;
	}

	public synchronized String getState() {
		return state;
	}

	/**
	 * {@code counties\<state>\<county>}, relative to the sewage chart folder,
	 * and {@code charts/ChartSewage.java} appends which chart it is. The save
	 * does not create the state directory: {@code ChartSewage.reportState}
	 * makes one for every state before any chart is drawn. Distinct names give
	 * distinct files unless they differ only in case, which Windows folds, or
	 * in the ':' and '|' that {@code charts/Charts.java} rewrites.
	 */
	@Override
	public String getChartFilename() {
		return charts.ChartSewage.COUNTIES + "\\" + state + "\\" + county;
	}

	@Override
	public String getTitleLine() {
		return String.format("%s county, %s (%,d line pop)", getCounty(), getState(), getPopulation());
	}

	/**
	 * The bare county name, which labels this county's line on its own charts
	 * and its state's. It is not unique across states and can equal a state's
	 * name (Colorado County, Texas); the "Colorado" test that picks charts to
	 * open in {@code charts/ChartSewage.java} would match such a county too.
	 */
	@Override
	public String getName() {
		return getCounty();
	}
}
