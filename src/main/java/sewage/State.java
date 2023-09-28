package sewage;

public class State extends Multi {

	private final String state;

	public State(String state) {
		this.state = state;
	}

	public synchronized String getState() {
		return state;
	}

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
