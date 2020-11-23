package covid;

public enum NumbersType {
	CASES(Smoothing.GEOMETRIC_SYMMETRIC_WEEKLY),
	HOSPITALIZATIONS(Smoothing.GEOMETRIC_SYMMETRIC_WEEKLY),
	DEATHS(Smoothing.ALGEBRAIC_SYMMETRIC_WEEKLY),
	TESTS(Smoothing.ALGEBRAIC_SYMMETRIC_WEEKLY);

	NumbersType(Smoothing smoothing) {
		this.smoothing = smoothing;
	}

	public final String lowerName = name().toLowerCase();
	public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
	public final Smoothing smoothing;
}
