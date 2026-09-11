package variants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import nwss.Nwss;

/**
 * Pango lineage names, translated between the short alias form ("JN.1") and
 * the fully expanded form ("b.1.1.529.2.86.1.1"), from pango-designation's
 * alias_key.json. Expanded names are what make ancestry a string-prefix test,
 * which is how Lineage finds parents and Voc build() subtracts children.
 * <p>
 * Every name this class stores is trimmed and lowercased by {@link #simplify}.
 * Only {@link #expand} does that to its argument, so the other methods expect
 * names that came out of it.
 * <p>
 * A recombinant is a root here, exactly like A and B: its parents are not
 * recorded, so ancestry never crosses a recombination, and
 * {@link #getParent} returns null for "xbb" as it does for "b".
 * <p>
 * The tables are filled once by {@link #build} and never written again, so
 * after it returns they are safe to read from any thread.
 */
public class Aliases {

	/*
	 * In the pango-designation checkout Nwss.read() git-pulls, beside the
	 * lineages.csv Lineages reads; see build() for why the pull comes first.
	 */
	private static final String ALIAS_FILE = Nwss.GIT_LOCATION + "\\pango_designation\\alias_key.json";

	/** The case and whitespace every name is stored and compared in. */
	public static String simplify(String s) {
		return s.trim().toLowerCase();
	}

	/*
	 * A and B, whose value in the file is an empty string, and every
	 * recombinant, whose value is the array of its parent lineages.
	 */
	private static final TreeSet<String> roots = new TreeSet<>();

	/* Maps from alias to full lineage */
	private static final TreeMap<String, String> forward = new TreeMap<>();

	/* Maps from full lineage back to alias */
	private static final TreeMap<String, String> backward = new TreeMap<>();

	private static boolean built = false;

	/**
	 * Loads alias_key.json, once; later calls return immediately.
	 * <p>
	 * Whichever call comes first decides which revision of the file is loaded,
	 * so it must come after the checkout's git pull has finished: Lineages
	 * reads lineages.csv from the pulled checkout, and an alias new to that
	 * pull would otherwise be unknown. nwss/Nwss.java read() pulls
	 * synchronously on the main thread and then calls this, both before it
	 * starts its pool; the lazy calls in {@link #expand}, {@link #shorten} and
	 * {@link #getParent} are no-ops after that. The method is synchronized, so
	 * a second caller waits for the load rather than seeing half-filled tables.
	 * <p>
	 * A missing, unreadable or malformed file, or one that is not a JSON object
	 * or holds a null or object value, prints a stack trace and exits the JVM
	 * with status 1: nothing downstream can name a lineage without it.
	 */
	public static synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		File f = new File(ALIAS_FILE);

		try (FileReader fr = new FileReader(f); BufferedReader br = new BufferedReader(fr)) {
			JsonElement jsonTree = JsonParser.parseReader(br);

			Set<Map.Entry<String, JsonElement>> tree = ((JsonObject) jsonTree).entrySet();

			for (Map.Entry<String, JsonElement> ele : tree) {
				String alias = simplify(ele.getKey());
				JsonElement value = ele.getValue();

				/* A recombinant: the array names its parents, which are dropped. */
				if (value.isJsonArray()) {
					roots.add(alias);
					continue;
				}

				JsonPrimitive prim = (JsonPrimitive) value;
				String lineage = simplify(prim.getAsString());

				if (lineage.equals("")) {
					roots.add(alias);
					continue;
				}

				if (alias.contains(".")) {
					new Exception("Alias with a dot, which expand() can never look up: " + alias).printStackTrace();
				}
				if (forward.put(alias, lineage) != null) {
					new Exception("Double alias (forward) for " + alias).printStackTrace();
				}
				if (backward.put(lineage, alias) != null) {
					new Exception("Double alias (backward) for " + lineage).printStackTrace();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		if (false) {
			roots.forEach(root -> System.out.println("Alias> Root: " + root));
			forward.forEach((k, v) -> System.out.println("Alias> " + k + " => " + v));
		}
	}

	private static final Pattern expandPattern = Pattern.compile("(?<pre>[A-Za-z]+)(?<post>(\\.\\d+)*)");

	/**
	 * Fully expanded form of a lineage name in any case, e.g. "JN.1" to
	 * "b.1.1.529.2.86.1.1". A name under a root comes back simplified but
	 * otherwise unchanged.
	 * <p>
	 * Returns null for a name that is not letters followed by dotted numbers
	 * ("", "XBB.1.5*"). A name whose letters are neither a root nor an alias
	 * comes back as the string "null" followed by its numbers ("null", "null.1")
	 * with no message. Lineage.get() refuses both forms with a stack trace; a
	 * caller that checks only for null will not notice them.
	 */
	public static String expand(String shortLineage) {
		build();

		shortLineage = simplify(shortLineage);

		Matcher m = expandPattern.matcher(shortLineage);
		if (!m.matches()) {
			// System.out.println("Missing: " + shortLineage);
			return null;
		}

		String pre = m.group("pre");
		if (roots.contains(pre)) {
			// System.out.println("Root: " + shortLineage);
			return shortLineage;
		}

		String pre2 = forward.get(pre);
		String post = m.group("post");

		String variant2 = pre2 + post;
		// System.out.println("Expand: " + pre + " -> " + pre2 + " ====> " +
		// shortLineage + " -> " + variant2);
		return variant2;
	}

	/*
	 * The prefix takes whole triples of numbers, as many as leave one to three
	 * for the suffix. That is Pango's rule: a name never carries more than three
	 * numbers after its letters, so every alias stands for a multiple of three
	 * levels below its root. All 417 aliases in alias_key.json did on
	 * 2026-09-10; one that did not could never be found by this split.
	 */
	private static final Pattern shortenPattern = Pattern
			.compile("(?<pre>[A-Za-z]+(\\.\\d+\\.\\d+\\.\\d+)*)(?<post>(\\.\\d+)(\\.\\d+)?(\\.\\d+)?)");

	/**
	 * Shortest alias form of an expanded name, lowercased; a root comes back as
	 * itself. It inverted {@link #expand} for every lineage in lineages.csv, and
	 * every ancestor of one, on 2026-09-10. The argument must already be
	 * expanded and simplified; it is not lowercased here, so "JN.1" fails.
	 * <p>
	 * A name that does not split prints a stack trace and returns null; a
	 * prefix with no alias prints one and returns "null" plus the suffix.
	 */
	public static String shorten(String longLineage) {
		build();

		if (roots.contains(longLineage)) {
			// System.out.println("Alias -> " + longLineage + " : root");
			return longLineage;
		}

		Matcher m = shortenPattern.matcher(longLineage);
		if (!m.matches()) {
			new Exception("Fail match: " + longLineage).printStackTrace();
			return null;
		}

		String pre = m.group("pre");
		if (!roots.contains(pre)) {
			pre = backward.get(pre);
			if (pre == null) {
				new Exception("Missing pre: " + m.group("pre") + " on " + longLineage).printStackTrace();
			}
		}

		String post = m.group("post");

		return pre + post;
	}

	/**
	 * Expanded parent of an expanded name: the name minus its last number.
	 * Null for a root, which includes every recombinant. Throws
	 * RuntimeException for a name that is neither a root nor has a number to
	 * drop.
	 */
	public static String getParent(String longLineage) {
		build();

		if (roots.contains(longLineage)) {
			return null;
		}
		Pattern parentPattern = Pattern.compile("(?<parent>[A-Za-z]+(\\.\\d+)*)(\\.\\d+)");
		Matcher m = parentPattern.matcher(longLineage);
		if (!m.matches()) {
			throw new RuntimeException("No parent match on " + longLineage);
		}
		return m.group("parent");
	}

	/**
	 * Whether the first name is a strict ancestor of the second, by string
	 * prefix. Both must be expanded; an alias form gives a wrong answer rather
	 * than an error.
	 */
	public static boolean isAncestorExclusive(String ancestorLong, String childLong) {
		return childLong.startsWith(ancestorLong + ".");
	}

	/** {@link #isAncestorExclusive}, but a name is also its own ancestor. */
	public static boolean isAncestorInclusive(String ancestorLong, String childLong) {
		return childLong.equals(ancestorLong) || childLong.startsWith(ancestorLong + ".");
	}

}
