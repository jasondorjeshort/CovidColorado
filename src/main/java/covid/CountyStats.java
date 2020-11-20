package covid;

public class CountyStats {
	// cumulative stats for ONE county
	private final String name, displayName;
	private final FinalNumbers cases = new FinalNumbers();
	private final FinalNumbers deaths = new FinalNumbers();

	CountyStats(String name) {
		this.name = name;
		this.displayName = name.replaceAll(" ", "_");
		if (name.contains("county") || name.contains("County")) {
			new Exception("County name shouldn't include 'county'").printStackTrace();
		}
	}

	public FinalNumbers getCases() {
		return cases;
	}

	public FinalNumbers getDeaths() {
		return deaths;
	}

	public String getName() {
		return name;
	}

	public String getDisplayName() {
		return displayName;
	}
}
