package variants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import covid.CalendarUtils;
import nwss.Nwss;

/**
 * Pulls exact per-day sequence counts for every US lineage from cov-spectrum's
 * LAPIS API and turns them into the same kind of Voc the hand-exported
 * cov-spectrum CSVs used to provide: one variant per lineage, prevalence
 * inclusive of descendants, 7-day smoothed. Everything downstream (child
 * subtraction in Voc.build, parent merging in VocSewage.build) is unchanged.
 *
 * This is the open (GenBank) dataset. It is thinner than the GISAID-backed
 * pages the manual exports came from; the printed cov-spectrum links still
 * work for that fallback.
 */
public class Lapis {

	public static final String BASE_URL = "https://lapis.cov-spectrum.org/open/v2/sample/aggregated";

	public static final int WINDOW_DAYS = 365;

	/*
	 * Sequences take a couple of weeks to show up, and the manual exports also
	 * stopped 10 days short of today.
	 */
	public static final int LAG_DAYS = 10;

	/* Half-width of the centered smoothing window; 3 gives cov-spectrum's 7 days. */
	public static final int SMOOTH = 3;

	public static final int TTL_HOURS = 24;

	/*
	 * A lineage with fewer sequences than this in the window is folded into
	 * its parent before the Voc is built. VocSewage keeps anything with ten
	 * smoothed days, which two sequences already give; that was fine for a
	 * hand-picked list of candidates but not for every designated lineage.
	 */
	public static final int MIN_SEQUENCES = 20;

	/*
	 * No prevalence is emitted for a day whose smoothing window holds fewer
	 * sequences than this. With a handful of sequences one lineage can be
	 * 100% of the day, which is noise, and the logit axis cannot draw it.
	 */
	public static final int MIN_WINDOW_SEQUENCES = 20;

	public static final String CACHE_FILE = System.getProperty("java.io.tmpdir") + "\\" + Nwss.FOLDER + "\\"
			+ "lapis-usa-aggregated.json";

	public static LinkedList<Voc> create() {
		LinkedList<Voc> vocs = new LinkedList<>();

		int today = CalendarUtils.timeToDay(System.currentTimeMillis());
		/* Fetch a little extra history so the first emitted day has a full window. */
		int fetchFirst = today - WINDOW_DAYS - SMOOTH;
		int emitFirst = today - WINDOW_DAYS;
		int emitLast = today - LAG_DAYS;
		int numDays = today - fetchFirst + 1;

		String url = BASE_URL + "?country=USA&dateFrom=" + CalendarUtils.dayToFullDate(fetchFirst)
				+ "&fields=date,nextcladePangoLineage&orderBy=date";

		File f = Nwss.ensureFileUpdated(CACHE_FILE, url, TTL_HOURS);
		if (!f.exists()) {
			System.out.println("LAPIS download failed; no automatic variants this run.");
			return vocs;
		}

		int[] total = new int[numDays];
		HashMap<Lineage, int[]> exact = new HashMap<>();
		HashMap<String, Lineage> resolved = new HashMap<>();
		HashSet<String> unknown = new HashSet<>();
		long sequences = 0, unknownSequences = 0;

		try (FileReader fr = new FileReader(f); BufferedReader br = new BufferedReader(fr)) {
			JsonObject root = JsonParser.parseReader(br).getAsJsonObject();

			JsonObject info = root.getAsJsonObject("info");
			if (info != null && info.has("dataVersion")) {
				System.out.println("LAPIS data version " + info.get("dataVersion").getAsString());
			}

			for (JsonElement element : root.getAsJsonArray("data")) {
				JsonObject row = element.getAsJsonObject();

				JsonElement date = row.get("date");
				if (date == null || date.isJsonNull()) {
					continue;
				}
				int idx = CalendarUtils.dateToDay(date.getAsString()) - fetchFirst;
				if (idx < 0 || idx >= numDays) {
					continue;
				}

				int count = row.get("count").getAsInt();
				sequences += count;
				total[idx] += count;

				JsonElement name = row.get("nextcladePangoLineage");
				Lineage lineage = null;
				if (name != null && !name.isJsonNull()) {
					String s = name.getAsString();
					/* Lineage.get prints a stack trace on failure; ask once per string. */
					if (resolved.containsKey(s)) {
						lineage = resolved.get(s);
					} else {
						lineage = Lineage.get(s);
						resolved.put(s, lineage);
					}
					if (lineage == null) {
						unknown.add(s);
					}
				}
				if (lineage == null) {
					/* Still part of the denominator, just not attributable. */
					unknownSequences += count;
					continue;
				}

				exact.computeIfAbsent(lineage, l -> new int[numDays])[idx] += count;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Could not parse " + f + "; deleting it so the next run refetches.");
			f.delete();
			return vocs;
		}

		int folded = 0, dropped = 0;
		for (Lineage lineage : deepestFirst(exact.keySet())) {
			int[] counts = exact.get(lineage);
			long sum = 0;
			if (counts != null) {
				for (int c : counts) {
					sum += c;
				}
			}
			if (sum == 0 || sum >= MIN_SEQUENCES) {
				continue;
			}
			exact.remove(lineage);
			Lineage parent = lineage.getParent();
			if (parent == null) {
				/* A rare recombinant; it stays in the denominator and shows up in "others". */
				dropped++;
				continue;
			}
			int[] pc = exact.computeIfAbsent(parent, l -> new int[numDays]);
			for (int i = 0; i < numDays; i++) {
				pc[i] += counts[i];
			}
			folded++;
		}

		/*
		 * LAPIS counts are for the exact lineage. The rest of the pipeline was
		 * built around cov-spectrum "X*" queries, where a parent includes all
		 * of its descendants and Voc.build subtracts the children back out. So
		 * add every lineage into each of its ancestors, creating the ancestors
		 * as needed; the ones with no sequences of their own end up with a
		 * near-zero residual and get folded away by VocSewage.
		 */
		HashMap<Lineage, int[]> inclusive = new HashMap<>();
		exact.forEach((lineage, counts) -> {
			for (Lineage ancestor = lineage; ancestor != null; ancestor = ancestor.getParent()) {
				int[] inc = inclusive.computeIfAbsent(ancestor, l -> new int[numDays]);
				for (int i = 0; i < numDays; i++) {
					inc[i] += counts[i];
				}
			}
		});

		/*
		 * Integer sums up to the single division keep the later child-minus-
		 * parent subtraction exact instead of producing rounding-noise
		 * negatives.
		 */
		LinkedList<Variant> variants = new LinkedList<>();
		inclusive.forEach((lineage, inc) -> {
			Variant variant = new Variant(lineage.getQuery());
			for (int day = emitFirst; day <= emitLast; day++) {
				long num = 0, den = 0;
				for (int d = day - SMOOTH; d <= day + SMOOTH; d++) {
					int i = d - fetchFirst;
					if (i < 0 || i >= numDays) {
						continue;
					}
					num += inc[i];
					den += total[i];
				}
				if (num > 0 && den >= MIN_WINDOW_SEQUENCES) {
					variant.setPrevalence(day, (double) num / den);
				}
			}
			variants.add(variant);
		});

		HashSet<Lineage> pinned = pins(emitFirst);

		System.out.println(String.format(
				"LAPIS: %,d sequences, %,d lineages (+%,d ancestors) after folding %d rare ones into parents and dropping %d rare roots, %,d sequences in %d unknown lineages %s, %s to %s, %d pinned.",
				sequences, exact.size(), inclusive.size() - exact.size(), folded, dropped, unknownSequences,
				unknown.size(), unknown, CalendarUtils.dayToFullDate(emitFirst),
				CalendarUtils.dayToFullDate(emitLast), pinned.size()));

		vocs.add(new Voc(variants, emitFirst, emitLast, pinned));
		return vocs;
	}

	/**
	 * The given lineages plus every ancestor, deepest first, so that folding a
	 * child into its parent happens before the parent itself is judged.
	 */
	private static ArrayList<Lineage> deepestFirst(Iterable<Lineage> lineages) {
		HashSet<Lineage> all = new HashSet<>();
		for (Lineage lineage : lineages) {
			for (Lineage a = lineage; a != null; a = a.getParent()) {
				all.add(a);
			}
		}
		ArrayList<Lineage> order = new ArrayList<>(all);
		order.sort((a, b) -> Integer.compare(b.getFull().length(), a.getFull().length()));
		return order;
	}

	/**
	 * Lineages from LEnum lists that start inside the fetch window are never
	 * merged into their parent on count alone. The older lists stay as
	 * documentation and as link generators for the manual export, but pinning
	 * a couple hundred 2023 lineages would only clutter the charts.
	 */
	private static HashSet<Lineage> pins(int emitFirst) {
		HashSet<Lineage> pinned = new HashSet<>();
		for (LEnum list : LEnum.values()) {
			if (CalendarUtils.dateToDay(list.startDate) < emitFirst) {
				continue;
			}
			for (String name : list.lineages) {
				Lineage lineage = Lineage.get(name);
				if (lineage != null) {
					pinned.add(lineage);
				}
			}
		}
		return pinned;
	}

}
