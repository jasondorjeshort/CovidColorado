package covid;

public class CountyStats {
	// cumulative stats for ONE county
	private final String name, displayName;
	private final FinalCases cases = new FinalCases();
	private final FinalCases deaths = new FinalCases();

	CountyStats(String name) {
		this.name = name;
		this.displayName = name.replaceAll(" ", "_");
		if (name.contains("county") || name.contains("County")) {
			new Exception("County name shouldn't include 'county'").printStackTrace();
		}
	}

	public FinalCases getCases() {
		return cases;
	}

	public FinalCases getDeaths() {
		return deaths;
	}

	public String getName() {
		return name;
	}

	public String getDisplayName() {
		return displayName;
	}
}
