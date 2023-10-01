package sewage;

import java.util.Collection;
import java.util.HashMap;

import covid.CalendarUtils;

public class All extends Multi {

	public final String desc;

	public All(String desc) {
		this.desc = desc;
	}

	@SuppressWarnings("unused")
	private void normalizeFromCDC(Collection<Plant> plants) {
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

	int peakStart = CalendarUtils.dateToDay("12-1-2021");
	int peakEnd = CalendarUtils.dateToDay("3-1-2022");

	public static final double SCALE_PEAK_RENORMALIZER = 100.0;
	public static final String SCALE_NAME = "Percentage of Jan 2022 peak";

	private void renorm(Collection<Plant> plants) {
		double renorm = getHighestSewage(peakStart, peakEnd) / SCALE_PEAK_RENORMALIZER;
		plants.forEach(p -> p.renorm(renorm));
	}

	/*
	 * CDC numbers claim to be normalized, but the scales differ by up to 100.
	 * This makes averaging nigh on impossible, big problem. So I just normalize
	 * it here with some crazy area-preserving algorithm. It assumes (pretty
	 * close BUT probably not accurate for urban vs rural) that over long enough
	 * everywhere will have around the same amount of covid.
	 */
	@SuppressWarnings("unused")
	private void normalizeFull(Collection<Plant> plants) {
		clear();
		plants.forEach(p -> includeSewage(p, 1.0));
		int firstDay = getFirstDay(), lastDay = getLastDay();

		HashMap<Plant, Double> oldNormalizers = new HashMap<>();
		for (int i = 0; i < 2000; i++) {
			oldNormalizers.clear();
			plants.forEach(p -> oldNormalizers.put(p, p.getNormalizer()));

			clear();
			plants.forEach(p -> includeSewage(p, 1.0));
			plants.forEach(p -> p.buildNormalizer(this));

			renorm(plants);

			double normDiff = -1;
			Abstract normPlant = this;

			for (Abstract p : plants) {
				double d = Math.abs(Math.log(p.getNormalizer() / oldNormalizers.get(p)));
				if (normDiff > d) {
					normDiff = d;
					normPlant = p;
				}
				normDiff = Math.max(normDiff, d);
			}

			if (normDiff < 1E-6) {
				break;
			}
		}

		renorm(plants);
	}

	public void build(Collection<Plant> plants) {
		normalizeFull(plants);

		clear();
		plants.forEach(p -> includeSewage(p, 1.0));
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
