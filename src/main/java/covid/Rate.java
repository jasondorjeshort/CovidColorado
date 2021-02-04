package covid;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

public enum Rate {

	POSITIVITY(NumbersType.CASES, NumbersType.TESTS, Color.BLUE, 25, false),
	CFR(NumbersType.DEATHS, NumbersType.CASES, Color.BLACK, 10, true),
	CHR(NumbersType.DEATHS, NumbersType.HOSPITALIZATIONS, Color.RED, 40, true),
	HFR(NumbersType.CASES, NumbersType.TESTS, Color.ORANGE, 100, true),

	;

	public final NumbersType numerator, denominator;
	public final String lowerName = name().toLowerCase();
	public final int highestValue;
	public final Color color;
	public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
	public final String description, allCapsName;

	Rate(NumbersType numerator, NumbersType denominator, Color color, int highestValue, boolean allCaps) {
		this.numerator = numerator;
		this.denominator = denominator;
		this.color = color;
		this.highestValue = highestValue;
		description = capName + " (" + numerator.lowerName + " / " + denominator.lowerName + ")";
		if (allCaps) {
			allCapsName = name();
		} else {
			allCapsName = capName;
		}
	}

	public static Set<Rate> getSet(Rate... rate) {
		Set<Rate> rates = new HashSet<>();
		if (rate.length == 0) {
			for (Rate r : Rate.values()) {
				rates.add(r);
			}
		} else {
			for (Rate r : rate) {
				rates.add(r);
			}
		}
		return rates;
	}

	public static String name(Set<Rate> rates, String sep) {
		String name = null;
		for (Rate rate : Rate.values()) {
			if (!rates.contains(rate)) {
				continue;
			}
			if (name == null) {
				name = rate.lowerName;
			} else {
				name = name + sep + rate.lowerName;
			}
		}
		return name;
	}

	public static String allCapsName(Set<Rate> rates, String sep) {
		String name = null;
		for (Rate rate : Rate.values()) {
			if (!rates.contains(rate)) {
				continue;
			}
			if (name == null) {
				name = rate.allCapsName;
			} else {
				name = name + sep + rate.allCapsName;
			}
		}
		return name;
	}

}
