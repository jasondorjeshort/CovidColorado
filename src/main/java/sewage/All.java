package sewage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import covid.CalendarUtils;

public class All extends Multi {

	public final String desc;

	public All(String desc) {
		this.desc = desc;
	}

	final ArrayList<Plant> plants = new ArrayList<>();

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

	private final int peakStart = CalendarUtils.dateToDay("9-1-2020");

	public static final double SCALE_PEAK_RENORMALIZER = 100.0;
	public static final String SCALE_NAME = "Percentage of Jan 2022 peak";

	private void include() {
		clear();
		plants.forEach(p -> includeSewage(p, 1.0));
	}

	/*
	 * CDC numbers claim to be normalized, but the scales differ by up to 100.
	 * This makes averaging nigh on impossible, big problem. So I just normalize
	 * it here with some crazy area-preserving algorithm. It assumes (pretty
	 * close BUT probably not accurate for urban vs rural) that over long enough
	 * everywhere will have around the same amount of covid.
	 */
	private void normalize() {
		HashMap<Plant, Double> oldNormalizers = new HashMap<>();
		for (int i = 0; i < 2000; i++) {
			oldNormalizers.clear();
			plants.forEach(p -> oldNormalizers.put(p, p.getNormalizer()));

			include();
			plants.forEach(p -> p.buildNormalizer(this));

			include();
			double renorm = getHighestSewage(peakStart, getLastDay()) / SCALE_PEAK_RENORMALIZER;
			plants.forEach(p -> p.renorm(renorm));

			double normDiff = 0;
			Plant normPlant = null;

			for (Plant p : plants) {
				double d = Math.abs(Math.log(p.getNormalizer() / oldNormalizers.get(p)));
				if (d > normDiff) {
					normDiff = d;
					normPlant = p;
				}
			}

			if (normDiff < 1E-9 || normPlant == null) {
				break;
			}
		}
	}

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
