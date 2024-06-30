package variants;

import java.util.HashMap;

public class Lineage {

	private static HashMap<String, Lineage> lineages = new HashMap<>();

	private final String full; // potentially very long numbering
	private final String alias; // shortest alias
	private final String query; // cov-spectrum query text
	private final Lineage parent;
	private int ordering;

	private Lineage(String full) {
		if (full == null || full.equalsIgnoreCase("null") || full.contains("null")) {
			throw new RuntimeException("Null lineage??? " + full);
		}
		this.full = full;
		alias = Aliases.shorten(full);
		String parentLineage = Aliases.getParent(full);
		if (parentLineage == null) {
			parent = null;
		} else {
			parent = get(parentLineage);
		}
		query = "nextcladePangoLineage:" + alias + "*";
	}

	public String getFull() {
		return full;
	}

	public String getAlias() {
		return alias;
	}

	public String getQuery() {
		return query;
	}

	public Lineage getParent() {
		return parent;
	}

	public boolean isAncestor(Lineage descendant) {
		return Aliases.isAncestorExclusive(full, descendant.full);
	}

	public synchronized void setOrdering(int number) {
		this.ordering = number;
	}

	/**
	 * Ordering number, 1 = first ~4000 = last lineage. Not perfect though and
	 * not sure how to make it perfect.
	 */
	public synchronized int getOrdering() {
		return ordering;
	}

	public static Lineage get(final String name0) {
		try {
			String name = Aliases.expand(name0);
			if (name == null || name.equalsIgnoreCase("null")) {
				new Exception("Somehow there's a null lineage on '" + name0 + "'.").printStackTrace();
				return null;
			}

			Lineage lineage;
			synchronized (lineages) {
				lineage = lineages.get(name);

				if (lineage == null) {
					lineage = new Lineage(name);
					lineages.put(name, lineage);
				}

				return lineage;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Unsure lineage '" + name0 + "'.");
		}
		return null;
	}

}
