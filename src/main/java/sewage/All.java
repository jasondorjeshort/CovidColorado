package sewage;

import java.util.Collection;
import java.util.HashMap;

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
		double area = getTotalSewage(firstDay, lastDay);

		System.out.println("Sewage area: " + area);

		HashMap<Plant, Double> oldNormalizers = new HashMap<>();
		for (int i = 0; i < 2000; i++) {

			oldNormalizers.clear();
			plants.forEach(p -> oldNormalizers.put(p, p.getNormalizer()));

			clear();
			plants.forEach(p -> includeSewage(p, 1.0));
			plants.forEach(p -> p.buildNormalizer(this));

			double renorm = getTotalSewage(firstDay, lastDay) / area;
			plants.forEach(p -> p.renorm(renorm));

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

		double cdcNorm = 0.0, cdcs = 0;
		for (Plant p : plants) {
			if (p.id.startsWith("CDC")) {
				cdcNorm += Math.log(p.getNormalizer()) * p.getPopulation();
				cdcs += p.getPopulation();
			}
		}
		double renorm = Math.exp(cdcNorm / cdcs);
		plants.forEach(p -> p.renorm(renorm));
	}

	public void build(Collection<Plant> plants) {
		normalizeFull(plants);

		clear();
		plants.forEach(p -> includeSewage(p, 1.0));

		while (getEntry(getFirstDay()).getSewage() > getEntry(getFirstDay() + 1).getSewage()) {
			bumpFirstDay();
		}
	}

	@Override
	public String getChartFilename() {
		return desc;
	}

	@Override
	public String getTitleLine() {
		return String.format("%s (%,d line pop)", desc, getPopulation());
	}
}
