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
public enum Smoothing {
	NONE("daily numbers"),
	ALGEBRAIC_SYMMETRIC_WEEKLY("7-day symmetric average"),
	GEOMETRIC_SYMMETRIC_WEEKLY("7-day symmetric geometric average"),
	TOTAL_14_DAY("14-day total"),
	TOTAL_7_DAY("7-day total");

	Smoothing(String description) {
		this.description = description;
	}

	public final String description;
}
