package charts;

import java.io.File;
import java.util.Set;

import covid.ColoradoStats;
import covid.IncompleteNumbers;
import covid.NumbersTiming;
import covid.NumbersType;
import covid.Rate;
import library.ASync;

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
public class ChartMaker {

	int built = 0;

	private final ColoradoStats stats;

	public ChartMaker(ColoradoStats stats) {
		this.stats = stats;
	}

	public void buildCharts() {
		// folders must be at very top
		new File(Charts.TOP_FOLDER).mkdir();
		new File(Charts.FULL_FOLDER).mkdir();
		ChartCounty county = new ChartCounty(stats);
		Age age = new Age(stats);
		Finals finals = new Finals(stats);
		Distribution distribution = new Distribution(stats);
		Set<NumbersType> fullTypes = NumbersType.getSet();
		Set<NumbersType> noTests = NumbersType.getSet(NumbersType.CASES, NumbersType.DEATHS,
				NumbersType.HOSPITALIZATIONS);

		long buildStarted = System.currentTimeMillis();

		ASync<Void> build = new ASync<>();

		if (false) {
			new ChartIncompletes(stats, noTests, NumbersTiming.INFECTION, true).buildChartsOnly(build);
			build.complete();
			return;
		}

		build.execute(() -> finals.createCumulativeStats());

		/* These are just ordered from slowest to fastest */

		build.execute(() -> new ChartRates(stats, Rate.getSet(Rate.values())).buildAllCharts());
		for (Rate rate : Rate.values()) {
			build.execute(() -> new ChartRates(stats, Rate.getSet(rate)).buildAllCharts());
		}

		build.execute(() -> new Reproductive(stats, noTests).buildAllCharts());
		for (NumbersType type : NumbersType.values()) {
			build.execute(() -> new Reproductive(stats, NumbersType.getSet(type)).buildAllCharts());
		}

		for (NumbersTiming timing : NumbersTiming.values()) {
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, true).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, false).buildAllCharts());
			build.execute(() -> age.buildChart(noTests, timing));

			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, true).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, false).buildAllCharts());
			// No point to testing age; it's identical to cases

			for (NumbersType type : NumbersType.values()) {
				Set<NumbersType> types = NumbersType.getSet(type);
				IncompleteNumbers numbers = stats.getNumbers(type, timing);
				build.execute(() -> new ChartIncompletes(stats, types, timing, true).buildAllCharts());
				build.execute(() -> new ChartIncompletes(stats, types, timing, false).buildAllCharts());
				build.execute(() -> distribution.buildDistributions(numbers));
				build.execute(() -> age.buildChart(types, timing));
			}
		}

		stats.getCounties().forEach((key, value) -> build.execute(() -> county.createCountyStats(value)));
		build.complete();
		System.out.println("Built charts in " + (System.currentTimeMillis() - buildStarted) + " ms with "
				+ build.getExecutions() + " executions.");
	}
}
