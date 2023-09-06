package colorado;

import java.util.Set;

public abstract class TypesTimingChart extends AbstractChart {

	protected final Set<NumbersType> types;
	protected final NumbersTiming timing;

	public TypesTimingChart(ColoradoStats stats, String topFolder, Set<NumbersType> types, NumbersTiming timing,
			Flag... flags) {
		super(stats, topFolder, flags);
		this.types = types;
		this.timing = timing;
	}

	public Set<NumbersType> getTypes() {
		return types;
	}

	public NumbersTiming getTiming() {
		return timing;
	}

	/**
	 * Some combinations here have no data and this is the easiest way to find
	 * that out.
	 */
	@Override
	public final boolean hasData() {
		for (NumbersType type : types) {
			IncompleteNumbers n = stats.getNumbers(type, timing);
			if (n.hasData()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public final boolean dayHasData(int dayOfData) {
		for (NumbersType type : types) {
			IncompleteNumbers n = stats.getNumbers(type, timing);
			if (n.dayHasData(dayOfData)) {
				return true;
			}
		}
		return false;
	}
}
