package variants;

import java.util.HashMap;

public class Lineage {

	private static HashMap<String, Lineage> lineages = new HashMap<>();

	private final String full;
	private final String alias;
	// private final boolean isRoot;
	private Lineage parent;

	private Lineage(String full) {
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
		return Aliases.isAncestor(full, descendant.full);
	}

	public static Lineage get(String name) {
		name = Aliases.expand(name);
		if (name == null) {
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