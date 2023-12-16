package variants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A variant is a lineage PLUS some data on the lineage prevalence. Direct daily
 * prevalence data is included in the Voc for now though.
 */
public class Variant {
	/** Index name. */
	public final String name;

	public final String displayName;

	/** Lineage for this variant data, or null for odd queries. */
	public final Lineage lineage;

	/** Cumulative prevalence (ASUs) over the time period */
	public double cumulativePrevalence;

	/** Weighted average of which day this variant was on. */
	public double averageDay;

	public Variant(String name) {
		this.name = name;
		this.displayName = Voc.display(name);

		Pattern p = Pattern.compile("nextcladePangoLineage:([A-Za-z]+[.0-9]*)\\*");
		Matcher m = p.matcher(name);
		if (m.matches()) {
			// may still be null
			lineage = Lineage.get(m.group(1));
		} else {
			lineage = null;
		}
		// System.out.println("Lineage for " + name + " is "
		// + (lineage == null ? "N/A" : lineage.getFull() + " / " +
		// lineage.getAlias()));
	}

	public boolean isAncestor(Variant descendant) {
		return lineage.isAncestor(descendant.lineage);
	}

}