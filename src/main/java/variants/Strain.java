package variants;

import java.util.HashMap;

public enum Strain {

	BA_1("BA.1", "ba.1", "ba.3"),
	BA_2("BA.2", "ba.2"),
	BA_5("BA.5", "ba.5", "ba.4"),
	BA_2_75("BA.2.75", "ba.2.75"),
	BA_2_3_20("BA.2.3.20", "ba.2.3.20"),
	BQ_1("BQ.1", "bq.1"),
	CH_1_1("CH.1.1", "ch.1.1"),
	XBB("XBB", "xbb", "xbl", "xcf", "xch", "xcl", "xcr", "xda", "xdc", "xcv"),
	BA_2_86("BA.2.86", "ba.2.86", "xdd", "xdk", "xdp", "xdn", "xdr", "xdq", "xds", "xdv"),
	OTHERS("OTHERS");

	private final String name;
	private final String[] variants;

	Strain(String name, String... variants) {
		this.name = name;
		for (int i = 0; i < variants.length; i++) {
			variants[i] = Aliases.expand(variants[i]);
		}
		if (variants.length == 0 && !name.equalsIgnoreCase("OTHERS")) {
			new Exception("No variants on " + name() + ".").printStackTrace();
		}
		this.variants = variants;
	}

	public String getName() {
		return name;
	}

	private static final HashMap<String, Strain> backwardsMap = new HashMap<>();

	public static Strain findStrain(String variant) {
		String variantFull = Aliases.expand(variant);
		if (variantFull == null) {
			new Exception("Mismatched variant " + variant).printStackTrace();
		}

		synchronized (backwardsMap) {
			String variantLineage = null;
			Strain variantStrain = backwardsMap.get(variantFull);
			if (variantStrain != null) {
				return variantStrain;
			}

			for (Strain strain : values()) {
				for (String ancestor : strain.variants) {
					if (Aliases.isAncestorInclusive(ancestor, variantFull)) {
						if (variantStrain == null) {
							variantLineage = ancestor;
							variantStrain = strain;
						} else {
							if (Aliases.isAncestorExclusive(variantLineage, ancestor)) {
								variantLineage = ancestor;
								variantStrain = strain;
							}
						}
					}
				}
			}
			if (variantStrain == null) {
				new Exception("Unknown strain on " + variant).printStackTrace();
				variantStrain = OTHERS;
			}

			// System.out.println("Variant " + variant + " strained as " +
			// variantStrain.getName());

			backwardsMap.put(variantFull, variantStrain);
			return variantStrain;
		}
	}

	public static Strain findStrain(Variant variant) {
		if (variant.lineage != null) {
			return findStrain(variant.lineage);
		}

		return null;
	}

	public static Strain findStrain(Lineage lineage) {
		if (lineage == null) {
			return null;
		}
		return findStrain(lineage.getFull());
	}
}
