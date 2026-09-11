package variants;

import java.util.HashMap;

/**
 * The named strains of the per-strain charts, each a label and the lineages it
 * covers, written as aliases and expanded when the enum loads. A lineage belongs
 * to the strain that lists its most specific ancestor, by {@link Aliases}'
 * string-prefix ancestry over expanded names, and one under no strain is
 * {@link #OTHERS}. That ancestry gives a recombinant root no parent, so a
 * recombinant belongs to a strain only if its root is listed by name, which is
 * what the X* entries are.
 * <p>
 * The charts draw and label strains in declaration order. Were two strains to
 * list the same ancestor, the first declared would win it.
 */
public enum Strain {

	// Hand-maintained. Where they fall short of current data:
	// docs/active/findings/2026-09-09-strain-buckets-end-at-ba-2-86.md
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

	/* Expanded lineage name to strain, filled on first ask and kept for the run. */
	private static final HashMap<String, Strain> backwardsMap = new HashMap<>();

	/*
	 * Takes an expanded name, which is all findStrain(Lineage) passes, and
	 * expanding one again returns it unchanged: every alias in
	 * pango-designation's alias_key.json expands to a name that begins at a
	 * root. So the null branch cannot fire from here; were it reached, the
	 * ancestry test below would throw on the null.
	 */
	private static Strain findStrain(String variant) {
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

	/**
	 * The strain of the variant's lineage, or null for a variant with no
	 * lineage, such as the residual "others" variant a Voc adds. Callers skip
	 * null, so that variant is on no strain line and is not part of
	 * {@link #OTHERS}.
	 */
	public static Strain findStrain(Variant variant) {
		if (variant.lineage != null) {
			return findStrain(variant.lineage);
		}

		return null;
	}

	/**
	 * The strain listing the most specific ancestor of the lineage, the lineage
	 * itself included; {@link #OTHERS} when none does, after printing an
	 * "Unknown strain" stack trace; null for a null lineage. Answers are cached
	 * for the run, so the trace prints once per lineage. Safe to call from the
	 * chart-building threads.
	 */
	public static Strain findStrain(Lineage lineage) {
		if (lineage == null) {
			return null;
		}
		return findStrain(lineage.getFull());
	}
}
