package variants;

import java.util.HashMap;

/**
 * A Pango lineage, interned: {@link #get} hands out one instance per expanded
 * name for the life of the JVM and the constructor is private, so identity is
 * name equality. That is why equals and hashCode are Object's, and callers
 * depend on it: they key maps and sets by instance, and Variant.duplicate()
 * compares a re-parsed lineage with {@code !=}.
 * <p>
 * Ancestry comes from the expanded name (see {@link Aliases}). A recombinant is
 * a root like A and B, so no parent chain crosses a recombination.
 * <p>
 * Thread-safe: several of Nwss.read()'s pool tasks call get() at once, and it
 * builds a lineage and its whole parent chain under one lock.
 */
public class Lineage {

	/* Keyed by expanded name; also the lock get() builds under. */
	private static final HashMap<String, Lineage> lineages = new HashMap<>();

	private final String full; // potentially very long numbering
	private final String alias; // shortest alias
	private final String query; // cov-spectrum query text
	private final Lineage parent;
	private int ordering;

	private Lineage(String full) {
		/*
		 * Aliases.expand() spells an unknown alias as "null" plus its numbers
		 * ("null.1"). get() catches only the bare "null"; this catches the rest,
		 * and get() turns the throw into a stack trace and a null.
		 */
		if (full == null || full.equalsIgnoreCase("null") || full.contains("null")) {
			throw new RuntimeException("Null lineage??? " + full);
		}
		this.full = full;
		alias = Aliases.shorten(full);
		String parentLineage = Aliases.getParent(full);
		if (parentLineage == null) {
			parent = null;
		} else {
			/*
			 * A parent that fails to build comes back null and leaves this
			 * lineage a root, with only get()'s stack trace to say so. None
			 * did, for any lineage in lineages.csv or its ancestry, on
			 * 2026-09-10.
			 */
			parent = get(parentLineage);
		}
		query = "nextcladePangoLineage:" + alias + "*";
	}

	/**
	 * Expanded and lowercased, "b.1.1.529.2.86.1.1" for JN.1: the name
	 * ancestry compares, and the cache key.
	 */
	public String getFull() {
		return full;
	}

	/** Shortest alias, lowercased ("jn.1"); a root is its own alias. */
	public String getAlias() {
		return alias;
	}

	/**
	 * The cov-spectrum query for this lineage and all its descendants,
	 * {@code nextcladePangoLineage:<alias>*}. It is the one form
	 * Variant(String) resolves to a lineage, and it resolves back to this
	 * instance.
	 */
	public String getQuery() {
		return query;
	}

	/**
	 * The lineage whose expanded name is this one's less its last number:
	 * JN.1's is BA.2.86.1. Not always a designated lineage (B.1.1.529 is not
	 * in lineages.csv). Null for a root, which is A, B and every recombinant.
	 */
	public Lineage getParent() {
		return parent;
	}

	/**
	 * Whether this is a strict ancestor of the descendant, which must not be
	 * null: never of itself, and never across a recombination (BA.2 is not an
	 * ancestor of XBB.1.5; XBB is of EG.5.1). The same answer as walking the
	 * descendant's {@link #getParent} chain.
	 */
	public boolean isAncestor(Lineage descendant) {
		return Aliases.isAncestorExclusive(full, descendant.full);
	}

	public synchronized void setOrdering(int number) {
		this.ordering = number;
	}

	/**
	 * Position of this lineage's first appearance in pango-designation's
	 * lineages.csv, counting from 1, as Lineages.build() sets it; that file
	 * named 6,006 lineages on 2026-09-10. Meant as a rough order of emergence;
	 * Lineages.build() says why it is only rough. Zero for an ancestor the file
	 * does not name, and until that build reaches the lineage. Nothing calls
	 * this.
	 */
	public synchronized int getOrdering() {
		return ordering;
	}

	/**
	 * The one instance for a name in any case, alias or expanded: "JN.1",
	 * "jn.1" and "B.1.1.529.2.86.1.1" are the same lineage. Built on first
	 * request, parent chain included.
	 * <p>
	 * Never throws. Anything that does not resolve prints a stack trace and
	 * returns null: a name that is not letters and dotted numbers ("XBB.1.5*"),
	 * one whose letters are no alias ("Unassigned", "QQQ.1"), and null itself.
	 * A failure is not cached, so asking again prints again.
	 */
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
