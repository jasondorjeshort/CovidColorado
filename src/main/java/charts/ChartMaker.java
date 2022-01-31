package charts;

import java.io.File;
import java.util.Set;

import covid.ColoradoStats;
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
		county = new ChartCounty(stats);
		finals = new Finals(stats);
		age = new AverageAge(stats);
	}

	ASync<Chart> build = new ASync<>();
	Set<NumbersType> noTests = NumbersType.getSet(NumbersType.CASES, NumbersType.DEATHS, NumbersType.HOSPITALIZATIONS);
	Finals finals;
	Set<NumbersType> fullTypes = NumbersType.getSet();
	Set<NumbersType> casesHosps = NumbersType.getSet(NumbersType.CASES, NumbersType.HOSPITALIZATIONS);
	ChartCounty county;
	AverageAge age;

	private void fastBuild() {
		if (true) {
			build.execute(() -> finals.createCumulativeStats());
		}
		if (false) {
			new Reproductive(stats, noTests, NumbersTiming.ONSET).buildChartsOnly(build);
		}
		if (false) {
			new ChartRates(stats, Rate.getSet(Rate.CFR), NumbersTiming.ONSET).buildChartsOnly(build);
			new ChartRates(stats, Rate.getSet(Rate.CHR), NumbersTiming.ONSET).buildChartsOnly(build);
			new ChartRates(stats, Rate.getSet(Rate.HFR), NumbersTiming.ONSET).buildChartsOnly(build);
			new ChartRates(stats, Rate.getSet(Rate.POSITIVITY), NumbersTiming.ONSET).buildChartsOnly(build);
		}
	}

	private void fullBuild() {
		build.execute(() -> finals.createCumulativeStats());

		for (NumbersTiming timing : NumbersTiming.values()) {
			build.execute(() -> new ChartRates(stats, Rate.getSet(Rate.values()), timing).buildAllCharts());
			build.execute(() -> new Reproductive(stats, noTests, timing).buildAllCharts());
			build.execute(() -> new Reproductive(stats, casesHosps, timing).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, 365, Flag.LOGARITHMIC, Flag.SMOOTHED,
					Flag.OLD_YEARS).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, 90, Flag.LOGARITHMIC).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, 365, Flag.SMOOTHED).buildAllCharts());
			build.execute(() -> new FullDelayChart(stats, noTests, timing, true).buildAllCharts());
			build.execute(() -> new FullDelayChart(stats, noTests, timing, false).buildAllCharts());
			build.execute(() -> new DailyAgeChart(stats, noTests, timing).buildAllCharts());
			build.execute(() -> new DailyDelayChart(stats, noTests, timing).buildAllCharts());
			build.execute(() -> age.buildChart(noTests, timing));

			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, 365, Flag.LOGARITHMIC, Flag.SMOOTHED)
					.buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, 365, Flag.SMOOTHED).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, 365, Flag.LOGARITHMIC).buildAllCharts());
			// No point to testing age; it's identical to cases

			for (Rate rate : Rate.values()) {
				build.execute(() -> new ChartRates(stats, Rate.getSet(rate), timing).buildAllCharts());
			}
			for (NumbersType type : NumbersType.values()) {
				build.execute(() -> new Reproductive(stats, NumbersType.getSet(type), timing).buildAllCharts());
				build.execute(() -> new ChartIncompletes(stats, type.set, timing, 365, Flag.LOGARITHMIC, Flag.SMOOTHED,
						Flag.OLD_YEARS).buildAllCharts());
				build.execute(() -> new ChartIncompletes(stats, type.set, timing, 365, Flag.SMOOTHED).buildAllCharts());
				build.execute(
						() -> new ChartIncompletes(stats, type.set, timing, 365, Flag.LOGARITHMIC).buildAllCharts());
				build.execute(() -> new FullDelayChart(stats, type.set, timing, true).buildAllCharts());
				build.execute(() -> new FullDelayChart(stats, type.set, timing, false).buildAllCharts());
				build.execute(() -> new DailyAgeChart(stats, type.set, timing).buildAllCharts());
				build.execute(() -> new DailyDelayChart(stats, type.set, timing).buildAllCharts());
				build.execute(() -> age.buildChart(type.set, timing));
			}
		}

		stats.getCounties().forEach((key, value) -> build.execute(() -> county.createCountyStats(value)));
	}

	public void buildCharts() {
		// folders must be at very top
		new File(Charts.TOP_FOLDER).mkdir();
		new File(Charts.FULL_FOLDER).mkdir();

		long buildStarted = System.currentTimeMillis();

		// maybe a chart later
		build.execute(() -> stats.calculateReinfections());

		if (false) {
			fastBuild();
		} else {
			fullBuild();
		}
		build.complete();
		System.out.println("Built charts in " + (System.currentTimeMillis() - buildStarted) + " ms with "
				+ build.getExecutions() + " executions.");
		library.OpenImage.open();
	}
}
