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

public class Aliases {

	// private static final String ALIAS_FILE =
	// System.getProperty("java.io.tmpdir") + "\\" + Nwss.FOLDER + "\\"
	// + "aliases.json";
	// private static final String ALIAS_URL =
	// "https://raw.githubusercontent.com/cov-lineages/pango-designation/master/pango_designation/alias_key.json";

	private static final String ALIAS_FILE = "I:\\pango-designation\\pango_designation\\alias_key.json";

	public static String simplify(String s) {
		return s.trim().toLowerCase();
	}

	private static final TreeSet<String> roots = new TreeSet<>();

	/* Maps from alias to full lineage */
	private static final TreeMap<String, String> forward = new TreeMap<>();

	/* Maps from full lineage back to alias */
	private static final TreeMap<String, String> backward = new TreeMap<>();

	private static boolean built = false;

	public static synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		// File f = Nwss.ensureFileUpdated(ALIAS_FILE, ALIAS_URL, 168);
		File f = new File(ALIAS_FILE);

		try (FileReader fr = new FileReader(f); BufferedReader br = new BufferedReader(fr)) {
			JsonElement jsonTree = JsonParser.parseReader(br);

			Set<Map.Entry<String, JsonElement>> tree = ((JsonObject) jsonTree).entrySet();

			for (Map.Entry<String, JsonElement> ele : tree) {
				String alias = simplify(ele.getKey());
				JsonElement value = ele.getValue();

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
					new Exception("uh oh.").printStackTrace();
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

	private static final Pattern shortenPattern = Pattern
			.compile("(?<pre>[A-Za-z]+(\\.\\d+\\.\\d+\\.\\d+)*)(?<post>(\\.\\d+)(\\.\\d+)?(\\.\\d+)?)");

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

	public static String getParent(String longLineage) {
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
	 * Exclusive. Lineage names must be expanded.
	 */
	public static boolean isAncestorExclusive(String ancestorLong, String childLong) {
		return childLong.startsWith(ancestorLong + ".");
	}

	public static boolean isAncestorInclusive(String ancestorLong, String childLong) {
		return childLong.equals(ancestorLong) || childLong.startsWith(ancestorLong + ".");
	}

}
