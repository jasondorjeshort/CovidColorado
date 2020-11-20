package covid;

public enum NumbersTiming {
	INFECTION,
	ONSET,
	REPORTED;

	public final String lowerName = name().toLowerCase();
	public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
}
