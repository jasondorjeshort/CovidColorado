package sewage;

public class County extends Multi {

	private final String county;
	private final String state;

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

	@Override
	public String getChartFilename() {
		return charts.ChartSewage.COUNTIES + "\\" + state + "\\" + county;
	}

	@Override
	public String getTitleLine() {
		return String.format("%s county, %s (%,d line pop)", getCounty(), getState(), getPopulation());
	}

	@Override
	public String getName() {
		return getCounty();
	}
}
