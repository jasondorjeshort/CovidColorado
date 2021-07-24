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
	}

	public void buildCharts() {
		// folders must be at very top
		new File(Charts.TOP_FOLDER).mkdir();
		new File(Charts.FULL_FOLDER).mkdir();
		ChartCounty county = new ChartCounty(stats);
		AverageAge age = new AverageAge(stats);
		Finals finals = new Finals(stats);
		Set<NumbersType> fullTypes = NumbersType.getSet();
		Set<NumbersType> noTests = NumbersType.getSet(NumbersType.CASES, NumbersType.DEATHS,
				NumbersType.HOSPITALIZATIONS);
		Set<NumbersType> casesHosps = NumbersType.getSet(NumbersType.CASES, NumbersType.HOSPITALIZATIONS);

		long buildStarted = System.currentTimeMillis();

		ASync<Void> build = new ASync<>();

		// maybe a chart later
		build.execute(() -> stats.calculateReinfections());

		if (false) {
			build.execute(() -> new ChartIncompletes(stats, noTests, NumbersTiming.INFECTION, true, true)
					.buildChartsOnly(build));
			build.execute(() -> new ChartRates(stats, Rate.getSet(Rate.POSITIVITY), NumbersTiming.INFECTION)
					.buildChartsOnly(build));
			build.execute(
					() -> new ChartRates(stats, Rate.getSet(Rate.CFR), NumbersTiming.INFECTION).buildChartsOnly(build));
			build.execute(
					() -> new ChartRates(stats, Rate.getSet(Rate.CHR), NumbersTiming.INFECTION).buildChartsOnly(build));
			build.execute(
					() -> new ChartRates(stats, Rate.getSet(Rate.HFR), NumbersTiming.INFECTION).buildChartsOnly(build));
			build.complete();
			return;
		}

		build.execute(() -> finals.createCumulativeStats());

		for (NumbersTiming timing : NumbersTiming.values()) {
			build.execute(() -> new ChartRates(stats, Rate.getSet(Rate.values()), timing).buildAllCharts());
			build.execute(() -> new Reproductive(stats, noTests, timing).buildAllCharts());
			build.execute(() -> new Reproductive(stats, casesHosps, timing).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, true, true).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, true, false).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, noTests, timing, false, true).buildAllCharts());
			build.execute(() -> new FullDelayChart(stats, noTests, timing, true).buildAllCharts());
			build.execute(() -> new FullDelayChart(stats, noTests, timing, false).buildAllCharts());
			build.execute(() -> new DailyAgeChart(stats, noTests, timing).buildAllCharts());
			build.execute(() -> new DailyDelayChart(stats, noTests, timing).buildAllCharts());
			build.execute(() -> age.buildChart(noTests, timing));

			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, true, true).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, true, false).buildAllCharts());
			build.execute(() -> new ChartIncompletes(stats, fullTypes, timing, false, true).buildAllCharts());
			// No point to testing age; it's identical to cases

			for (Rate rate : Rate.values()) {
				build.execute(() -> new ChartRates(stats, Rate.getSet(rate), timing).buildAllCharts());
			}
			for (NumbersType type : NumbersType.values()) {
				build.execute(() -> new Reproductive(stats, NumbersType.getSet(type), timing).buildAllCharts());
				build.execute(() -> new ChartIncompletes(stats, type.set, timing, true, true).buildAllCharts());
				build.execute(() -> new ChartIncompletes(stats, type.set, timing, true, false).buildAllCharts());
				build.execute(() -> new ChartIncompletes(stats, type.set, timing, false, true).buildAllCharts());
				build.execute(() -> new FullDelayChart(stats, type.set, timing, true).buildAllCharts());
				build.execute(() -> new FullDelayChart(stats, type.set, timing, false).buildAllCharts());
				build.execute(() -> new DailyAgeChart(stats, type.set, timing).buildAllCharts());
				build.execute(() -> new DailyDelayChart(stats, type.set, timing).buildAllCharts());
				build.execute(() -> age.buildChart(type.set, timing));
			}
		}

		stats.getCounties().forEach((key, value) -> build.execute(() -> county.createCountyStats(value)));
		build.complete();
		System.out.println("Built charts in " + (System.currentTimeMillis() - buildStarted) + " ms with "
				+ build.getExecutions() + " executions.");
	}
}
