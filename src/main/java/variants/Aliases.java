package variants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import nwss.Nwss;

public class Aliases {

	public static class Alias {
		String from, to;
	}

	private static final String ALIAS_FILE = System.getProperty("java.io.tmpdir") + "\\" + Nwss.FOLDER + "\\"
			+ "aliases.json";
	private static final String ALIAS_URL = "https://raw.githubusercontent.com/cov-lineages/pango-designation/bf7bf4a1bcfbc1642291507a766bdaa7341fab50/pango_designation/alias_key.json";

	public static String simplify(String s) {
		return s.trim().toLowerCase();
	}

	public static final TreeSet<String> roots = new TreeSet<>();
	public static final TreeMap<String, String> aliases = new TreeMap<>();

	public static void load() {
		File f = Nwss.ensureFileUpdated(ALIAS_FILE, ALIAS_URL, 168);

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

				aliases.put(alias, lineage);
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		for (String root : roots) {
			System.out.println("Alias> Root: " + root);
		}

		aliases.forEach((k, v) -> System.out.println("Alias> " + k + " => " + v));
	}
}
