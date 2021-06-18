package covid;

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
public class Smoothing {

	public static final Smoothing NONE = new Smoothing(1, Type.TOTAL, Timing.SYMMETRIC);
	public static final Smoothing GEOMETRIC_SYMMETRIC_WEEKLY = new Smoothing(7, Type.GEOMETRIC_AVERAGE,
			Timing.SYMMETRIC);
	public static final Smoothing GEOMETRIC_SYMMETRIC_13DAY = new Smoothing(13, Type.GEOMETRIC_AVERAGE,
			Timing.SYMMETRIC);
	public static final Smoothing GEOMETRIC_SYMMETRIC_21DAY = new Smoothing(21, Type.GEOMETRIC_AVERAGE,
			Timing.SYMMETRIC);
	public static final Smoothing TOTAL_7_DAY = new Smoothing(7, Type.TOTAL, Timing.TRAILING);
	public static final Smoothing AVERAGE_7_DAY = new Smoothing(7, Type.AVERAGE, Timing.TRAILING);
	public static final Smoothing TOTAL_14_DAY = new Smoothing(14, Type.TOTAL, Timing.TRAILING);
	public static final Smoothing AVERAGE_14_DAY = new Smoothing(14, Type.AVERAGE, Timing.TRAILING);
	public static final Smoothing ALGEBRAIC_SYMMETRIC_WEEKLY = new Smoothing(7, Type.AVERAGE, Timing.SYMMETRIC);
	public static final Smoothing ALGEBRAIC_SYMMETRIC_13DAY = new Smoothing(13, Type.AVERAGE, Timing.SYMMETRIC);
	public static final Smoothing ALGEBRAIC_SYMMETRIC_21DAY = new Smoothing(21, Type.AVERAGE, Timing.SYMMETRIC);

	public enum Type {
		TOTAL,
		AVERAGE,
		GEOMETRIC_AVERAGE;

		public final String lowerName = name().toLowerCase().replaceAll("_", " ");
		public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
	}

	public enum Timing {
		SYMMETRIC,
		TRAILING;

		public final String lowerName = name().toLowerCase().replaceAll("_", " ");
		public final String capName = name().substring(0, 1) + name().substring(1).toLowerCase().replaceAll("_", " ");
	}

	private final int days;
	private final Type type;
	private final Timing timing;
	private final String description;

	public Smoothing(int days, Type type, Timing timing) {
		this.days = days;
		this.type = type;
		this.timing = timing;

		if (days == 1) {
			description = "exact";
		} else {
			description = String.format("%d-day %s %s", days, timing.lowerName, type.lowerName);
		}
	}

	public Type getType() {
		return type;
	}

	public Timing getTiming() {
		return timing;
	}

	public int getDays() {
		return days;
	}

	public String getDescription() {
		return description;
	}

}
