package covid;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 * 
 * @author jdorje@gmail.com
 */
public enum Rate {

	/**
	 * A rate is a ratio between two numbers, expressed as a percentage.
	 * 
	 * That's basically it. We mostly talk about positivity and CFR, but any
	 * pair of numbers (NumbersType) could be divided to get a proportion.
	 */
	POSITIVITY(NumbersType.CASES, NumbersType.TESTS, Color.BLUE, 20, 7, false),
	CFR(NumbersType.DEATHS, NumbersType.CASES, Color.BLACK, 3, 35, true),
	CHR(NumbersType.HOSPITALIZATIONS, NumbersType.CASES, Color.RED, 31, 35, true),
	HFR(NumbersType.DEATHS, NumbersType.HOSPITALIZATIONS, Color.ORANGE, 50, 35, true),;

	public final NumbersType numerator, denominator;
	public final String lowerName = name().toLowerCase();
	public final int highestValue;
	public final Color color;
	public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
	public final String description, allCapsName;
	public final Smoothing smoothing;

	Rate(NumbersType numerator, NumbersType denominator, Color color, int highestValue, int smoothingDays,
			boolean allCaps) {
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
		smoothing = new Smoothing(smoothingDays, Smoothing.Type.AVERAGE, Smoothing.Timing.SYMMETRIC);
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
