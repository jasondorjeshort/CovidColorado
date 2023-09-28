package sewage;

public class Region extends Multi {

	private final String region;

	public Region(String region) {
		this.region = region;
	}

	public synchronized String getRegion() {
		return region;
	}

	@Override
	public String getChartFilename() {
		return charts.ChartSewage.REGIONS + "\\" + region;
	}

	@Override
	public String getTitleLine() {
		return String.format("%s (%,d line pop)", getRegion(), getPopulation());
	}

	@Override
	public String getName() {
		return getRegion();
	}

}
