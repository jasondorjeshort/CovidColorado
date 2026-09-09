package nwss;

import java.util.HashMap;

/**
 * The CDC "Wastewater Data for SARS-CoV-2" dataset identifies jurisdictions
 * by two-letter code, but regions.csv and the state charts use full names.
 */
public class StateNames {

	private static final HashMap<String, String> names = new HashMap<>();

	static {
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

	/** Full name for a code, or the code itself if it is unknown. */
	public static String get(String code) {
		String name = names.get(code.trim().toLowerCase());
		return name == null ? code : name;
	}

}
