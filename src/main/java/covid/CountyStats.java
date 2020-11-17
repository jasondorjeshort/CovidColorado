package covid;

public class CountyStats {
	// cumulative stats for ONE county
	private final String name, displayName;
	private final FinalCases cases = new FinalCases();

	CountyStats(String name) {
		this.name = name;
		this.displayName = name.replaceAll(" ", "_");
		if (name.contains("county") || name.contains("County")) {
			new Exception("County name shouldn't include 'county'").printStackTrace();
		}
	}

	public int getCases(int day) {
		return cases.getCases(day);
	}

	public void setCases(int day, int numCases) {
		cases.setCases(day, numCases);
	}

	public String getName() {
		return name;
	}
	
	public String getDisplayName() {
		return displayName;
	}
}
