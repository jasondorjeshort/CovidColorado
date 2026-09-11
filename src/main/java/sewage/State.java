package sewage;

/**
 * One state or territory: every plant in it, added directly by
 * {@code nwss/Nwss.java} {@code read()} rather than summed from its counties
 * (docs/reference/wastewater.txt, under AGGREGATION AND THE HIERARCHY). Its
 * display children are its counties, and its sewage charts show the five most
 * populous, the limit Nwss {@code build()} passes.
 *
 * The name is the full one {@code nwss/StateNames.java} gives the CDC's code,
 * and other code finds the state by it. Nwss's map of states and the directory
 * its counties' charts go under are made from this same string; regions.csv
 * and the literal "Colorado" are spelled independently. A name regions.csv
 * lacks puts the state in the "Other" region, and only the state named
 * "Colorado" gets lineage charts, from Nwss {@code build()}, and has its
 * sewage charts opened, by {@code charts/ChartSewage.java}.
 */
public class State extends Multi {

	private final String state;

	public State(String state) {
		this.state = state;
	}

	public synchronized String getState() {
		return state;
	}

	/**
	 * The stem of every chart file drawn from this state, the name under the
	 * states folder. The name is a path component as it stands: nothing cleans
	 * it but the ':' that {@code charts/Charts.java}
	 * {@code saveBufferedImageAsPNG} drops and the '|' it spells out, and none
	 * of StateNames' names needs more.
	 */
	@Override
	public String getChartFilename() {
		return charts.ChartSewage.STATES + "\\" + state;
	}

	@Override
	public String getTitleLine() {
		return String.format("%s (%,d line pop)", getState(), getPopulation());
	}

	@Override
	public String getName() {
		return getState();
	}

}
