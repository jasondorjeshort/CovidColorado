package variants;

import java.util.HashMap;

public class Lineage {

	private static HashMap<String, Lineage> lineages = new HashMap<>();

	private final String full;
	private final String alias;
	// private final boolean isRoot;
	private Lineage parent;

	private Lineage(String full) {
		if (full == null || full.equalsIgnoreCase("null")) {
			throw new RuntimeException("Null lineage???");
		}
		this.full = full;
		alias = Aliases.shorten(full);
	}

	public String getFull() {
		return full;
	}

	public String getAlias() {
		return alias;
	}

	public Lineage getParent() {
		synchronized (this) {
			if (parent != null) {
				return parent;
			}
		}

		return null;
	}

	public boolean isAncestor(Lineage descendant) {
		return Aliases.isAncestorExclusive(full, descendant.full);
	}

	public static Lineage get(String name) {
		name = Aliases.expand(name);
		if (name == null || name.equalsIgnoreCase("null")) {
			// no idea wtf the "null" is doing here.
			return null;
		}

		Lineage lineage;
		synchronized (lineages) {
			lineage = lineages.get(name);

			if (lineage == null) {
				lineage = new Lineage(name);
				lineages.put(name, lineage);
			}
		}
		return lineage;
	}

}
