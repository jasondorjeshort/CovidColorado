package covid;

public enum Smoothing {
	NONE("daily numbers"),
	ALGEBRAIC_SYMMETRIC_WEEKLY("7-day symmetric average"),
	GEOMETRIC_SYMMETRIC_WEEKLY("7-day symmetric geometric average");

	Smoothing(String description) {
		this.description = description;
	}

	public final String description;
}
