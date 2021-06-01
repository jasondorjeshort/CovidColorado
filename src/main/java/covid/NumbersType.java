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
public enum NumbersType {

	TESTS(Smoothing.ALGEBRAIC_SYMMETRIC_WEEKLY, 100000, Color.YELLOW),
	CASES(Smoothing.ALGEBRAIC_SYMMETRIC_WEEKLY, 10000, Color.BLUE),
	HOSPITALIZATIONS(Smoothing.ALGEBRAIC_SYMMETRIC_WEEKLY, 500, Color.RED),
	DEATHS(Smoothing.ALGEBRAIC_SYMMETRIC_WEEKLY, 100, Color.BLACK);

	NumbersType(Smoothing smoothing, int highestValue, Color color) {
		this.smoothing = smoothing;
		this.highestValue = highestValue;
		this.color = color;
		this.set.add(this);
	}

	public static String name(NumbersType type) {
		if (type == null) {
			return "full";
		}
		return type.lowerName;
	}

	public static Set<NumbersType> getSet(NumbersType... type) {
		Set<NumbersType> types = new HashSet<>();
		if (type.length == 0) {
			for (NumbersType t : NumbersType.values()) {
				types.add(t);
			}
		} else {
			for (NumbersType t : type) {
				types.add(t);
			}
		}
		return types;
	}

	public static String name(Set<NumbersType> types, String sep) {
		String name = null;
		for (NumbersType type : NumbersType.values()) {
			if (!types.contains(type)) {
				continue;
			}
			if (name == null) {
				name = type.lowerName;
			} else {
				name = name + sep + type.lowerName;
			}
		}
		return name;
	}

	public static int getHighest(Set<NumbersType> types) {
		int highest = 1;
		for (NumbersType type : types) {
			highest = Math.max(highest, type.highestValue);
		}
		return highest;
	}

	public final String lowerName = name().toLowerCase();
	public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
	public final Smoothing smoothing;
	public final int highestValue;
	public final Color color;
	public final HashSet<NumbersType> set = new HashSet<>();
}
