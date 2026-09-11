package variants;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import covid.CalendarUtils;
import covid.DailyTracker;
import nwss.Nwss;

/**
 * One lineage-prevalence dataset: a set of {@link Variant}s over a range of
 * days, from either the LAPIS pull ({@link Lapis#create()}) or cov-spectrum
 * CSVs exported by hand ({@link #create()}). docs/reference/lineages.txt owns
 * the two pipelines.
 * <p>
 * Prevalence arrives inclusive of descendants, and each constructor finishes
 * by running {@code build()}, which trims trailing days no variant has any
 * prevalence on, subtracts every child out of its ancestors so that each
 * variant is exclusive, sets each variant's cumulativePrevalence and
 * averageDay, and, when there is more than one variant, adds an "others"
 * variant holding the rest of each day. Nothing changes a Voc after that:
 * VocSewage merges duplicates of its variants, not the variants themselves.
 */
public class Voc extends DailyTracker {

	/*
	 * The two cov-spectrum exports: a comparison plot, one row per variant per
	 * day, and a single-variant plot. A second export of the same plot is read
	 * as <name>(1).csv, then (2), with no space, up to the first number
	 * missing.
	 */
	private static final String CSV_NAME1 = "C:\\Users\\jdorj\\Downloads\\" + "VariantComparisonTimeDistributionPlot";
	private static final String CSV_NAME2 = "C:\\Users\\jdorj\\Downloads\\" + "VariantTimeDistributionPlot";
	private static final Charset CHARSET = Charset.forName("US-ASCII");

	/*
	 * Always false: the merger Voc that set it went in cdfdd0f. VocSewage
	 * copies it, so every test of it in ChartSewage takes the non-merger side.
	 */
	public final boolean isMerger;

	/*
	 * Set by build() once any child is subtracted from an ancestor. Its only
	 * readers are the commented-out "-exc" filename tags in ChartSewage.
	 */
	public boolean exclusions = false;

	/* Built from the LAPIS pull rather than exported CSVs. */
	public final boolean lapis;

	/* Lineages VocSewage must never fold into their parent on count alone. */
	public final Set<Lineage> pinned;

	private static File csv1(int i) {
		return new File(CSV_NAME1 + (i == 0 ? "" : "(" + i + ")") + ".csv");
	}

	private static File csv2(int i) {
		return new File(CSV_NAME2 + (i == 0 ? "" : "(" + i + ")") + ".csv");
	}

	/**
	 * A Voc for each export name present in the downloads folder; an empty
	 * list is the normal case. Each name's unnumbered file and its numbered
	 * copies make one Voc, and the unnumbered file's age decides for all of
	 * them: past eight hours they are deleted instead of read. A numbered copy
	 * with no unnumbered file beside it is neither read nor deleted.
	 */
	public static LinkedList<Voc> create() {
		LinkedList<Voc> vocs = new LinkedList<>();
		File f;
		LinkedList<File> files = new LinkedList<>();

		int i = 0;
		f = csv1(i);
		if (f.exists()) {
			files.add(f);
			while (true) {
				i++;
				File f2 = csv1(i);
				if (!f2.exists()) {
					break;
				}
				files.add(f2);
			}
			if (System.currentTimeMillis() - f.lastModified() > 8 * Nwss.HOUR) {
				System.out.println("Deleting " + f.getPath() + ", age "
						+ (System.currentTimeMillis() - f.lastModified()) / Nwss.HOUR + "h.");
				files.forEach(file -> file.delete());
			} else {
				vocs.add(new Voc(files, true));
			}
		}

		files.clear();
		i = 0;
		f = csv2(i);
		if (f.exists()) {
			files.add(f);
			while (true) {
				i++;
				File f2 = csv2(i);
				if (!f2.exists()) {
					break;
				}
				files.add(f2);
			}
			if (System.currentTimeMillis() - f.lastModified() > 8 * Nwss.HOUR) {
				System.out.println("Deleting " + f.getPath() + ", age "
						+ (System.currentTimeMillis() - f.lastModified()) / Nwss.HOUR + "h.");
				files.forEach(file -> file.delete());
			} else {
				vocs.add(new Voc(files, false));
			}
		}

		return vocs;
	}

	private HashSet<Variant> variants = new HashSet<>();

	public final int id;

	private static int nextId = 1;
	private static final Object nextIdLock = new Object();

	/*
	 * False only for single-variant exports. Nwss.build() adds Colorado
	 * charts only for a single-variant export or the LAPIS Voc.
	 */
	public final boolean multiVariant;

	/**
	 * A Voc from variants somebody else already filled in (see Lapis). Same
	 * build as the CSV path: subtract children from ancestors, add "others".
	 * The variants are taken, not copied, and build() mutates them. The range
	 * is firstDay to lastDay less the trailing days build() trims.
	 */
	public Voc(Collection<Variant> prebuilt, int firstDay, int lastDay, Set<Lineage> pinned) {
		isMerger = false;
		synchronized (nextIdLock) {
			id = nextId++;
		}
		multiVariant = true;
		lapis = true;
		this.pinned = pinned;
		variants.addAll(prebuilt);
		includeDay(firstDay);
		includeDay(lastDay);
		build();
	}

	/**
	 * A Voc from one export name's files, read in order. The first row of each
	 * is a header and is skipped. Column 0 is the date, in either shape
	 * CalendarUtils.dateToDay parses; column 1 the proportion, a row reading
	 * "null" there being skipped without its day joining the range; and in a
	 * comparison export column 4 the variant's query, which a later file's row
	 * for the same query and day overwrites. A single-variant export's variant
	 * is named "Variant N" by the file's position, so it has no lineage and
	 * takes no part in the child subtraction.
	 * <p>
	 * Anything that fails in the read -- a short row, an empty proportion, a
	 * date that does not parse -- prints a stack trace and exits the program
	 * with status 0; see
	 * docs/active/findings/2026-09-10-an-unreadable-cov-spectrum-export-ends-the-run-as-a-success.md.
	 */
	public Voc(List<File> files, boolean multiVariant) {
		isMerger = false;
		lapis = false;
		pinned = Collections.emptySet();
		synchronized (nextIdLock) {
			id = nextId++;
		}
		int fNumber = 0;
		this.multiVariant = multiVariant;
		HashMap<String, Variant> variantMap = new HashMap<>();
		for (File f : files) {
			fNumber++;
			try (CSVParser csv = CSVParser.parse(f, CHARSET, CSVFormat.DEFAULT)) {
				int records = 0;
				for (CSVRecord line : csv) {
					if (records++ == 0) {
						continue;
					}

					String date = line.get(0);
					String proportion = line.get(1);
					if (proportion == null || proportion.equalsIgnoreCase("null")) {
						continue;
					}
					String variantName;
					if (multiVariant) {
						variantName = line.get(4);
					} else {
						variantName = "Variant " + fNumber;
					}
					int day = CalendarUtils.dateToDay(date);

					Variant variant = variantMap.get(variantName);
					if (variant == null) {
						variant = new Variant(variantName);
						variantMap.put(variantName, variant);
					}

					double prev = Double.valueOf(proportion);
					variant.setPrevalence(day, prev);
					includeDay(day);
					variants.add(variant);
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}

		build();
	}

	boolean built = false;

	private synchronized void build() {
		if (built) {
			return;
		}
		built = true;

		/*
		 * Trim trailing days on which no variant has any prevalence, so the
		 * charts show the data cutoff where the data stops. Written for
		 * single-variant exports, whose query can run past the last sequences;
		 * for LAPIS it drops the tail whose smoothing windows fell under
		 * Lapis.MIN_WINDOW_SEQUENCES, which LAG_DAYS does not reach. Only the
		 * tail: a day like that inside the range stays, and "others" below
		 * takes all of it.
		 */
		while (true) {
			int last = getLastDay();
			if (last < getFirstDay()) {
				break;
			}

			double prev = 0.0;
			for (Variant variant : variants) {
				prev += variant.getPrevalence(last);
			}
			if (prev > 0.0) {
				break;
			}
			dropLastDay();
		}

		/*
		 * Off since 0ad0a3d. What the TODO asks for is what VocSewage build()
		 * now does: its floor folds a lineage with under ten days of
		 * prevalence since its fit start into its parent, after the
		 * subtraction below.
		 */
		if (false) {
			/*
			 * Remove variants without enough prevalence.
			 * 
			 * TODO: this should go below the lineage subtraction, which then
			 * requires adding back on any removed lineages to the closest
			 * parent.
			 * 
			 * Current prevalence requirements are just 10 days of data. With
			 * weekly smoothing this requires at least 2 sequences separated by
			 * ~3 days.
			 */
			int num = variants.size();
			variants.removeIf(variant -> variant.getNumDays(getFirstDay(), getLastDay()) < 10);
			num -= variants.size();
			System.out.println("Removed " + num + " variants.");
		}

		/*
		 * Subtract each child out of every ancestor, making every variant with
		 * a lineage exclusive. Ancestry is a strict prefix of the expanded
		 * name, so walking from the longest name down reaches every descendant
		 * before its ancestors, and a variant is already net of its own
		 * descendants when it is taken out of its ancestors. Each ancestor
		 * therefore loses each descendant once: a grandchild comes out of its
		 * grandparent directly, and the child comes out already net of it.
		 */
		ArrayList<Variant> myList = new ArrayList<>(variants.size());
		for (Variant v : variants) {
			if (v.lineage != null) {
				myList.add(v);
			}
		}
		myList.sort((v1, v2) -> Integer.compare(v1.lineage.getFull().length(), v2.lineage.getFull().length()));
		for (int i = myList.size() - 1; i >= 0; i--) {
			Variant child = myList.get(i);

			for (int j = 0; j < i; j++) {
				Variant parent = myList.get(j);
				if (!parent.isAncestor(child)) {
					continue;
				}

				for (int day = getFirstDay(); day <= getLastDay(); day++) {
					parent.subtractPrevalence(day, child.getPrevalence(day));
				}
				exclusions = true;
			}
		}

		/*
		 * Build the variant data directly: cumulative prevalence and average
		 * time
		 */
		for (Variant variant : variants) {
			variant.cumulativePrevalence = 0;
			double totalDay = 0;
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				double prev = variant.getPrevalence(day);
				variant.cumulativePrevalence += prev;
				totalDay += day * prev;
			}
			variant.averageDay = totalDay / variant.cumulativePrevalence;
		}

		/*
		 * Output (display). The count is taken before the "others" bucket is
		 * added below, so it is the named variants only; sb is the cov-spectrum
		 * query for everything outside them, in the same !A*&!B* form as
		 * LSet.getCovSpectrumReverseLink(), and sb2 a quoted list of labels.
		 */
		System.out.println(String.format("%,d named variants", variants.size()));
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		for (Variant variant : variants) {
			if (sb.length() > 0) {
				sb.append("&");
			}
			/*
			 * The name, not displayName: a query needs the
			 * nextcladePangoLineage: prefix that displayName strips.
			 */
			sb.append("!(");
			sb.append(variant.name);
			sb.append(")");

			if (sb2.length() > 0) {
				sb2.append(",");
			}
			sb2.append("\"");
			sb2.append(variant.displayName);
			sb2.append("\"");

			// System.out.println(variant.name + " : " +
			// CalendarUtils.dayToDate(variant.averageDay));
		}
		System.out.println(sb.toString());
		System.out.println(sb2.toString());

		/*
		 * With the variants exclusive, 1 less their sum is the share of the day
		 * no variant names. Every day of the range gets one, so a day no
		 * variant covers is all "others"; see
		 * docs/active/findings/2026-09-10-a-day-no-variant-covers-is-all-others.md.
		 */
		if (variants.size() > 1) {
			Variant others = new Variant("others");
			for (int day = getFirstDay(); day <= getLastDay(); day++) {
				others.setPrevalence(day, 1.0);
				for (Variant variant : variants) {
					others.subtractPrevalence(day, variant.getPrevalence(day));
				}
			}
			variants.add(others);
		}
	}

	/**
	 * The live set, with "others" if build() added it. Duplicate a variant
	 * before changing it.
	 */
	public Collection<Variant> getVariants() {
		return variants;
	}

	public synchronized int numVariants() {
		return variants.size();
	}
}
