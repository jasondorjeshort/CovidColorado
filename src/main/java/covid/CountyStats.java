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
public class CountyStats {
	// cumulative stats for ONE county
	private final String name, displayName;
	private final FinalNumbers cases = new FinalNumbers(NumbersType.CASES);
	private final FinalNumbers deaths = new FinalNumbers(NumbersType.DEATHS);

	CountyStats(String name) {
		this.name = name;
		this.displayName = name.replaceAll(" ", "_");
		if (name.contains("county") || name.contains("County")) {
			new Exception("County name shouldn't include 'county'").printStackTrace();
		}
	}

	public FinalNumbers getCases() {
		return cases;
	}

	public FinalNumbers getDeaths() {
		return deaths;
	}

	public String getName() {
		return name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean build() {
		cases.build(name + "/cases");
		deaths.build(name + "/deaths");
		return true;
	}
}
