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
 * LAPIS API and turns them into the same kind of Voc a hand-exported
 * cov-spectrum comparison CSV gives: variants named by "X*" query, prevalence
 * inclusive of descendants, 7-day smoothed. That is what lets Voc build() and
 * VocSewage treat the two paths alike. docs/reference/lineages.txt owns the
 * constants below and the inclusive-count invariant.
 * <p>
 * This is the open (GenBank) dataset. It is thinner than the GISAID-backed
 * pages the manual exports came from, which is why that path is kept.
 */
public class Lapis {

	public static final String BASE_URL = "https://lapis.cov-spectrum.org/open/v2/sample/aggregated";

	/*
	 * The first emitted day is this many days before today. With SMOOTH it
	 * also sets the request's dateFrom, which the cache does not notice; see
	 * CACHE_FILE.
	 */
	public static final int WINDOW_DAYS = 365;

	/*
	 * The last emitted day is this many days before today, to match the manual
	 * exports (LSet.TODAY(-10)). It is not the sequencing delay, which is
	 * longer: Voc build() trims the trailing days no lineage has prevalence
	 * on, and that trim, not this, is where the LAPIS charts end.
	 */
	public static final int LAG_DAYS = 10;

	/* Half-width of the centered smoothing window; 3 gives cov-spectrum's 7 days. */
	public static final int SMOOTH = 3;

	public static final int TTL_HOURS = 24;

	/*
	 * A lineage with fewer sequences than this over the fetched days is folded
	 * into its parent before the Voc is built. The count is its own, not
	 * inclusive, plus whatever was folded into it first, so a sparse chain
	 * folds upward until the leftovers reach this. VocSewage's floor keeps
	 * anything with ten smoothed days, which two sequences three days apart
	 * already give; that was fine for a hand-picked list of candidates but not
	 * for every designated lineage.
	 */
	public static final int MIN_SEQUENCES = 20;

	/*
	 * No prevalence is emitted for a day whose smoothing window holds fewer
	 * sequences than this. With a handful of sequences one lineage can be
	 * 100% of the day, which is noise, and the logit axis cannot draw it.
	 */
	public static final int MIN_WINDOW_SEQUENCES = 20;

	/*
	 * Reused for TTL_HOURS whatever URL create() builds, so a change to what it
	 * asks for is not fetched until the file expires; see
	 * docs/active/findings/2026-09-09-lapis-cache-keyed-by-filename-not-url.md.
	 */
	public static final String CACHE_FILE = System.getProperty("java.io.tmpdir") + "\\" + Nwss.FOLDER + "\\"
			+ "lapis-usa-aggregated.json";

	/**
	 * Zero or one Voc, from WINDOW_DAYS before today to LAG_DAYS before it,
	 * less the trailing days Voc build() trims. Empty, with a line printed,
	 * when the download fails or the file cannot be read; an unreadable file
	 * is deleted so the next run fetches it again. A sequence whose lineage is
	 * missing or does not resolve still counts in its day's total, so it ends
	 * up in "others".
	 */
	public static LinkedList<Voc> create() {
		LinkedList<Voc> vocs = new LinkedList<>();

		int today = CalendarUtils.today();
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
				/*
				 * A root has nowhere to fold, so its own sequences are left to
				 * the denominator and show up in "others". Few of its own does
				 * not make the family rare, and a root whose descendants survive
				 * is still built below as their ancestor: on 2026-09-10's data
				 * XDV was dropped with 2 of its own under a family of 2,050.
				 */
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
		 * Integer sums up to the division, and the same denominator for every
		 * lineage on a day, keep the later child-minus-parent subtraction's
		 * residual far under VocSewage.MINIMUM. It is not exact -- the division
		 * is per lineage, so the subtraction is a/den - b/den -- but the
		 * negatives it does produce are small enough that
		 * Variant.subtractPrevalence drops them without warning.
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
	 * Every lineage of an LEnum list whose start date is on or after the first
	 * emitted day, for Voc.pinned, which VocSewage never merges away on size
	 * or cost. The start-date test keeps a list picked for a past wave from
	 * pinning its lineages, which would clutter every chart; a list for a wave
	 * in progress pins until its start date ages out of the window.
	 * <p>
	 * The MIN_SEQUENCES fold in create() runs first and does not consult this
	 * set, so a pinned lineage under that count is folded into its parent
	 * before VocSewage can keep it; see
	 * docs/active/findings/2026-09-10-a-pinned-lineage-is-folded-before-its-pin-applies.md.
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
