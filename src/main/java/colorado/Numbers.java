package colorado;

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
public class Numbers {

	/*
	 * At one point I thought this could be used with smoothing class to work
	 * for both, but I don't see how. So it's essentially empty.
	 */

	private final NumbersType type;

	public Numbers(NumbersType type) {
		this.type = type;
	}

	public final NumbersType getType() {
		return type;
	}
}
