package variants;

/**
 * Hand-picked lineage lists, each with the date window it was picked for: the
 * lineages the maintainer wanted drawn as lines of their own. A list may name
 * both an ancestor and its descendants, because each name is queried
 * inclusively ("X*") and Voc build() subtracts the listed descendants back out,
 * so an ancestor's line is what is left of it.
 * <p>
 * Two things read these, and a new constant reaches both with no registration.
 * nwss/Nwss.java read() turns every constant into a variants/LSet and prints
 * its cov-spectrum links, from which the maintainer exports the CSVs Voc
 * create() reads; that is console output only. And variants/Lapis.java pins()
 * pins every lineage of a list whose start date is inside the emitted LAPIS
 * window, which VocSewage then never merges away on size or cost, and that
 * does change the charts. Neither list below starts inside the window, so
 * today nothing drawn depends on this file. A list added for a wave in
 * progress would pin its lineages until its start date aged out of the
 * window, which happens by itself.
 * <p>
 * Names are not checked against lineages.csv. One whose letters are neither an
 * alias nor a root is refused with a stack trace and skipped; a
 * well-formed name nobody designated becomes a lineage with no sequences,
 * silently. Both lists were last edited in June 2024 and do not cover the
 * lineages circulating in 2026; see
 * docs/active/findings/2026-09-10-the-lineage-lists-miss-most-of-todays-sequences.md.
 */
public enum LEnum {

	/**
	 * The running list for the wave that began in autumn 2023: named in
	 * November 2023, extended through JN.1 and its descendants, and last
	 * edited in June 2024.
	 */
	SEP_TO_NOV_2023(
			"2023-09-15",
			null,
			/*
			 * xbb and ba.2.75 were added when 101 of their descendants were cut
			 * from this list in June 2024, so those sequences still land on a
			 * named line rather than in "others".
			 */
			"xbb",
			"ba.2.75",
			"ba.2.86",
			"ba.2.86.1",

			"jn.1",
			"jn.1.1",
			"jn.1.1.1",
			"jn.1.1.2",
			"jn.1.1.3",
			"jn.1.1.4",
			"jn.1.1.5",
			"jn.1.1.6",
			"jn.1.1.7",
			"jn.1.1.8",
			"jn.1.10",
			"jn.1.11",
			"jn.1.11.1",
			"jn.1.12",
			"jn.1.13",
			"jn.1.13.1",
			"jn.1.14",
			"jn.1.15",
			"jn.1.16",
			"jn.1.16.1",
			"jn.1.16.2",
			"jn.1.17",
			"jn.1.18",
			"jn.1.18.1",
			"jn.1.18.2",
			"jn.1.19",
			"jn.1.2",
			"jn.1.20",
			"jn.1.21",
			"jn.1.22",
			"jn.1.23",
			"jn.1.24",
			"jn.1.24.1",
			"jn.1.25",
			"jn.1.25.1",
			"jn.1.26",
			"jn.1.27",
			"jn.1.28",
			"jn.1.28.1",
			"jn.1.29",
			"jn.1.3",
			"jn.1.30",
			"jn.1.30.1",
			"jn.1.31",
			"jn.1.32",
			"jn.1.32.1",
			"jn.1.37",
			"jn.1.39.2",
			"jn.1.4",
			"jn.1.4.1",
			"jn.1.4.2",
			"jn.1.4.3",
			"jn.1.4.4",
			"jn.1.4.5",
			"jn.1.4.6",
			"jn.1.40",
			"jn.1.48",
			"jn.1.48.1",
			"jn.1.48.2",
			"jn.1.49.2",
			"jn.1.5",
			"jn.1.6",
			"jn.1.6.1",
			"jn.1.7",
			"jn.1.7.1",
			"jn.1.7.2",
			"jn.1.7.3",
			"jn.1.8",
			"jn.1.8.1",
			"jn.1.8.2",
			"jn.1.9",
			"jn.1.9.1",
			"jn.1.9.2",
			"jn.2",
			"jn.2.5",
			"jn.3",
			"jn.6",
			"jr.1.1",
			"jr.1.1.1",
			"kb.1",
			"kc.1",
			"kk.1",
			"kl.1",
			"kn.1",
			"kn.1.1",
			"kp.1",
			"kp.1.1",
			"kp.1.1.1",
			"kp.1.2",
			"kp.2",
			"kp.2.1",
			"kp.2.10",
			"kp.2.2",
			"kp.2.3",
			"kp.2.5",
			"kp.2.7",
			"kp.2.9",
			"kp.3",
			"kp.3.1",
			"kp.3.2",
			"kp.3.3",
			"kp.4",
			"kp.4.1",
			"kp.4.2",
			"kp.5",
			"kq.1",
			"kr.1",
			"ks.1",
			"ku.2",
			"kw.1",
			"kw.1.1",
			"la.1",
			"lb.1",
			"le.1.1",
			"le.1.2",
			"lf.1",
			"lf.1.1",
			"xbb.1.16",
			"xbb.1.16.1",
			"xbb.1.16.11",
			"xbb.1.16.14",
			"xbb.1.16.15",
			"xbb.1.16.2",
			"xbb.1.16.23",
			"xbb.1.16.6",
			"xbb.1.16.8",
			"xbb.1.16.9",
			"xbb.1.22",
			"xbb.1.41",
			"xbb.1.41.2",
			"xbb.1.42.2",
			"xbb.1.5",
			"xbb.1.5.10",
			"xbb.1.5.28",
			"xbb.1.5.72",
			"xbb.1.9",
			"xbb.2.3",
			"xbb.2.3.15",
			"xbb.2.3.3",
			"xbb.2.3.8",
			"xch",
			"xch.1",
			"xcr",
			"xda",
			"xda.1",
			"xdd",
			"xdd.1",
			"xdd.1.1",
			"xdk",
			"xdk.1",
			"xdn",
			"xdp",
			"xdp.1",
			"xdq",
			"xdq.1",
			"xdr",
			"xds",
			"xdv",
			"xdv.1"),

	/** One line per family, over the whole pandemic. */
	ALL_TIME_VARIANTS(
			"2020-01-06",
			null,
			"b.1", // includes early VOCs
			"b.1.617.2",
			"ba.1",
			"ba.2", // w' ba.2.12.1, ba.2.10, etc
			"ba.2.75", // ch.1.1
			"xbb", // huge diversity
			"ba", // B.1.1.529: BA.4 and BA.5 once the rest are out
			"bq.1",
			"ba.2.86"),

	;

	/** Pango names in alias form, any case; order means nothing. */
	public final String[] lineages;

	/*
	 * Both dates are YYYY-MM-DD: LSet pastes them into the cov-spectrum URL as
	 * they are, and Lapis parses startDate for the pin test. A null endDate is
	 * ten days before today, which LSet substitutes.
	 */
	public final String startDate;
	public final String endDate;

	LEnum(String startDate, String endDate, String... lineages) {
		this.startDate = startDate;
		this.endDate = endDate;
		this.lineages = lineages;
	}
}
