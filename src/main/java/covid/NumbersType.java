package covid;

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
	CASES(Smoothing.GEOMETRIC_SYMMETRIC_WEEKLY),
	HOSPITALIZATIONS(Smoothing.GEOMETRIC_SYMMETRIC_WEEKLY),
	DEATHS(Smoothing.ALGEBRAIC_SYMMETRIC_WEEKLY),
	TESTS(Smoothing.GEOMETRIC_SYMMETRIC_WEEKLY);

	NumbersType(Smoothing smoothing) {
		this.smoothing = smoothing;
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

	public final String lowerName = name().toLowerCase();
	public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
	public final Smoothing smoothing;
}
