package sewage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import covid.CalendarUtils;

/**
 * The nationwide aggregate, and the owner of the national baseline.
 * {@link #build} sets every plant's normalizer, and
 * {@link Multi#includeSewage} reads a plant's normalizer when it adds the
 * plant, so build must run before any other aggregate includes one.
 * {@code nwss/Nwss.java} read() calls it once, on the main thread, after the
 * parallel reads complete. docs/reference/wastewater.txt, under THE NATIONAL
 * BASELINE, has the design.
 */
public class All extends Multi {

	public final String desc;

	public All(String desc) {
		this.desc = desc;
	}

	final ArrayList<Plant> plants = new ArrayList<>();

	/*
	 * Never called since it was written with the first normalization code
	 * (765e7c0): a baseline of only the plants whose id starts with CDC, taken
	 * as already comparable, with every other plant fitted to it. As it stands
	 * it leaves those plants' normalizers alone and skips the scaling to the
	 * peak.
	 */
	@SuppressWarnings("unused")
	private void normalizeFromCDC() {
		clear();
		plants.forEach(p -> {
			if (p.id.startsWith("CDC")) {
				System.out.println("Fixing baseline from " + p.id);
				includeSewage(p, 1.0);
			}
		});
		plants.forEach(p -> {
			if (!p.id.startsWith("CDC")) {
				p.buildNormalizer(this);
			}
		});
	}

	/*
	 * The peak is looked for from here on because the start of the pandemic is
	 * "unknown values", in the words of the commit that widened the search from
	 * the winter of 2021-22 alone. The days before it do not count toward the
	 * peak and can read above 100: 112 in the download of 2026-09-10.
	 */
	private final int peakStart = CalendarUtils.dateToDay("9-1-2020");

	/**
	 * What the baseline's highest day since 2020-09-01 is scaled to, so that
	 * every normalized value is a percentage of the pandemic peak.
	 */
	public static final double SCALE_PEAK_RENORMALIZER = 100.0;
	/** The axis label for that scale. */
	public static final String SCALE_NAME = "Percentage of pandemic peak";

	private void include() {
		clear();
		plants.forEach(p -> includeSewage(p, 1.0));
	}

	/*
	 * The plants' numbers cannot be averaged as they come. Even the
	 * flow-population column, the one meant to be comparable across plants,
	 * needs normalizers about 100x apart between its 5th- and 95th-percentile
	 * plants (download of 2026-09-10), and the other two columns are in units of
	 * their own. So each plant is scaled until its readings sum to the
	 * baseline's over the days they share, the baseline being the weighted mean
	 * of the scaled plants. That is circular, and this iterates it to a fixed
	 * point. It assumes (pretty close, but probably not accurate for urban
	 * against rural) that over long enough everywhere has about the same amount
	 * of covid.
	 *
	 * It stops once no normalizer moves by more than 1E-6 in log, a millionth
	 * and far below anything a chart shows, or after 100 rounds. Hitting the cap
	 * is not treated as a failure: the count printed at the end is 100, and the
	 * normalizers are the last round's. Neither number has a recorded
	 * derivation.
	 *
	 * Today the cap always ends it. A plant buildNormalizer cannot fit keeps its
	 * normalizer and is still renormalized, and the renormalization settles near
	 * 0.991 rather than 1, so that plant's normalizer moves about 0.9% every
	 * round and the test never passes; see
	 * docs/active/findings/2026-09-10-the-baseline-loop-never-converges.md.
	 */
	private void normalize() {
		HashMap<Plant, Double> oldNormalizers = new HashMap<>();
		long time = System.currentTimeMillis();
		int i;
		for (i = 0; i < 100; i++) {
			oldNormalizers.clear();
			plants.forEach(p -> oldNormalizers.put(p, p.getNormalizer()));

			include();
			plants.forEach(p -> p.buildNormalizer(this));

			include();
			double renorm = getHighestSewage(peakStart, getLastDay()) / SCALE_PEAK_RENORMALIZER;
			plants.forEach(p -> p.renorm(renorm));

			double normDiff = 0;

			for (Plant p : plants) {
				double d = Math.abs(Math.log(p.getNormalizer() / oldNormalizers.get(p)));
				if (d > normDiff) {
					normDiff = d;
				}
			}

			System.out.println("Normalize " + i + " => " + normDiff);

			if (normDiff < 1E-6) {
				break;
			}
		}

		time = System.currentTimeMillis() - time;

		System.out.println("Looped normalization " + i + " times in " + time + " ms.");
	}

	/**
	 * Keeps every plant with a positive total, sets each one's normalizer
	 * against the national baseline, and leaves this object holding that
	 * baseline, whose highest day since 2020-09-01 is
	 * {@link #SCALE_PEAK_RENORMALIZER}.
	 * <p>
	 * A kept plant that {@link Multi#includeSewage} skips, for a range under two
	 * days or no population, does not add to the baseline but is still fitted
	 * to it. A plant {@link Plant#buildNormalizer} cannot fit, which includes
	 * every one-day plant because its sums stop short of the plant's last day,
	 * is left with 1 divided by every round's renormalization.
	 * <p>
	 * Throws NullPointerException when the baseline has no day on or after
	 * 2020-09-01, an empty download for one: there is no peak to scale to.
	 */
	public void build(Collection<Plant> thePlants) {
		plants.clear();
		for (Plant p : thePlants) {
			if (p.hasDays() && p.getTotalSewage() > 0) {
				plants.add(p);
			}
		}
		plants.sort((p1, p2) -> Integer.compare(p1.getPlantId(), p2.getPlantId()));

		normalize();
		include();
	}

	@Override
	public String getChartFilename() {
		return desc;
	}

	@Override
	public String getTitleLine() {
		return String.format("%s (%,d line pop)", desc, getPopulation());
	}

	@Override
	public String getName() {
		return "Nationwide";
	}
}
