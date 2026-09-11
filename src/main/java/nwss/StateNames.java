package nwss;

import java.util.HashMap;

/**
 * The CDC "Wastewater Data for SARS-CoV-2" dataset identifies jurisdictions
 * by two-letter code, but regions.csv and the state charts use full names.
 * The name given here is what the rest of the program knows a state by, and
 * two other places spell it independently: regions.csv, looked up by
 * {@link Regions} with no folding, and the literal "Colorado" in
 * {@code nwss/Nwss.java} and {@code charts/ChartSewage.java}. A name changed
 * here must change there too.
 *
 * The map is written only by the static initializer, which class
 * initialization publishes to every thread; the one caller,
 * {@code nwss/Nwss.java} {@code readSewage()}, runs on a pool thread.
 */
public class StateNames {

	private static final HashMap<String, String> names = new HashMap<>();

	static {
		/*
		 * as, mp, pr and nyc have no rows in the 2026-09-10 download, which
		 * carries the other 53 codes, all lowercase.
		 */
		String[][] pairs = { { "al", "Alabama" }, { "ak", "Alaska" }, { "az", "Arizona" }, { "ar", "Arkansas" },
				{ "ca", "California" }, { "co", "Colorado" }, { "ct", "Connecticut" }, { "de", "Delaware" },
				{ "dc", "District of Columbia" }, { "fl", "Florida" }, { "ga", "Georgia" }, { "hi", "Hawaii" },
				{ "id", "Idaho" }, { "il", "Illinois" }, { "in", "Indiana" }, { "ia", "Iowa" }, { "ks", "Kansas" },
				{ "ky", "Kentucky" }, { "la", "Louisiana" }, { "me", "Maine" }, { "md", "Maryland" },
				{ "ma", "Massachusetts" }, { "mi", "Michigan" }, { "mn", "Minnesota" }, { "ms", "Mississippi" },
				{ "mo", "Missouri" }, { "mt", "Montana" }, { "ne", "Nebraska" }, { "nv", "Nevada" },
				{ "nh", "New Hampshire" }, { "nj", "New Jersey" }, { "nm", "New Mexico" }, { "ny", "New York" },
				{ "nc", "North Carolina" }, { "nd", "North Dakota" }, { "oh", "Ohio" }, { "ok", "Oklahoma" },
				{ "or", "Oregon" }, { "pa", "Pennsylvania" }, { "ri", "Rhode Island" }, { "sc", "South Carolina" },
				{ "sd", "South Dakota" }, { "tn", "Tennessee" }, { "tx", "Texas" }, { "ut", "Utah" },
				{ "vt", "Vermont" }, { "va", "Virginia" }, { "wa", "Washington" }, { "wv", "West Virginia" },
				{ "wi", "Wisconsin" }, { "wy", "Wyoming" }, { "as", "American Samoa" }, { "gu", "Guam" },
				{ "mp", "Northern Mariana Islands" }, { "pr", "Puerto Rico" }, { "vi", "U.S. Virgin Islands" },
				{ "nyc", "New York City" } };
		for (String[] pair : pairs) {
			names.put(pair[0], pair[1]);
		}
	}

	/**
	 * Full name for a code, matched after trimming and lowercasing. An unknown
	 * code comes back exactly as passed rather than null, so its plants become
	 * a state named by the code, fall to the "Other" region and are still
	 * charted; a null state would leave them out of every state, county and
	 * region aggregate in {@code nwss/Nwss.java} {@code read()}. Null throws
	 * NullPointerException.
	 */
	public static String get(String code) {
		String name = names.get(code.trim().toLowerCase());
		return name == null ? code : name;
	}

}
