package variants;

public enum VariantEnum {

	/*
	 * APRIL_TO_SEPTEMBER_VARIANTS( "2023-04-01", "2023-09-01", "xbb.1.5",
	 * "xbb.1.16", "xbb.1.16.6", "eg.5.1", "eg.5.1.1", "xbb.1.16.1", "xbb.1.22",
	 * "fl.1.5.1", "gj.1.2", "xbb.2.3", "xbb.1.5.10", "xbb.1.5.72", "fu.1",
	 * "xbb.1.16.11", "eg.6.1", "xbb.1.9.1", "eg.5.1.3", "ge.1", "xbb.2.3.2",
	 * "eg.5.1.4", "xbb.1.5.49", "xbb.1.42.2", "xbb.1.9.2", "eg.1", "hf.1",
	 * "hv.1", "fd.1.1", "fu.2.1", "HH.1", "fu.2", "fe.1.2", "he.1", "eg.5.2",
	 * "eg.5.1.6", "xbb.1.16.2", "fl.15", "xbb.1.5.77", "hz.1", "xbb.2.3.8",
	 * "bq.1", "xbb.1", "fk.1.1", "ch.1.1"),
	 */

	SEP_TO_NOV_2023(
			"2023-09-10",
			null,
			"ba.2.86",
			"ba.2.86.1",
			"dv.7.1",
			"dv.7.1.4",
			"dv.7.1.5",
			"eg.10.1",
			"eg.2",
			"eg.5.1",
			"eg.5.1.1",
			"eg.5.1.3",
			"eg.5.1.4",
			"eg.5.1.6",
			"eg.5.1.8",
			"eg.5.1.9",
			"eg.5.1.13",
			"eg.5.1.16",
			"eg.5.2",
			"eg.5.2.1",
			"eg.6.1",
			"eg.6.1.1",
			"fd.1.1",
			"fl.1.5.1",
			"fl.1.5.2",
			"fl.10.1",
			"fl.15",
			"fl.15.1.1",
			"fl.20",
			"fl.20.2",
			"ft.3.1.1",
			"fu.1",
			"fu.1.1.1",
			"fu.2",
			"fu.2.1",
			"fy.5",
			"ge.1",
			"ge.1.4",
			"gj.1.2",
			"gj.1.2.2",
			"gj.5",
			"gk.1",
			"gk.1.1",
			"gk.1.1.1",
			"gk.1.8",
			"gk.2",
			"gk.2.1",
			"gk.3.1",
			"gn.1",
			"gs.1",
			"gs.4.1",
			"gv.1",
			"hf.1",
			"hf.1.1",
			"hf.1.2",
			"hh.1",
			"hk.11",
			"hk.13",
			"hk.20.1",
			"hk.3",
			"hk.3.1",
			"hk.3.4",
			"hk.6",
			"hk.7",
			"hk.8",
			"hk.31",
			"hn.1",
			"hn.2",
			"hn.3.1",
			"hn.4",
			"hn.5",
			"hn.6",
			"hv.1",
			"hv.1.1",
			"hv.1.2",
			"hv.1.3",
			"hv.1.4",
			"hy.1",
			"hz.1",
			"jc.1",
			"jc.5",
			"jd.1.1",
			"jd.1.1.1",
			"je.1.1",
			"jf.1",
			"jg.3",
			"jg.3.1",
			"jg.3.2",
			"jj.1",
			"jm.2",
			"jn.1",
			"jn.1.1",
			"jn.2",
			"jn.3",
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
			"xdd"

	// watch list:
	//
	// HK.2 (16, +15%)
	//
	// Next removal:
	// HN.2
	),

	BA_286("2023-09-10", null, "BA.2.86", "BA.2.86.1", "JN.1", "JN.1.1", "JN.1.2", "JN.2", "JN.3", "XDD"),

	ALL_TIME_VARIANTS(
			"2020-01-06",
			null,
			"b.1", // includes early VOCs
			"b.1.617.2",
			"ba.1",
			"ba.2", // w' ba.2.12.1, ba.2.10, etc
			"ba.2.75", // ch.1.1
			"xbb", // huge diversity
			"ba", // 4 and 5
			"bq.1",
			"ba.2.86"),

	;

	public final String[] variants;
	public final String startDate;
	public final String endDate; // or null;

	VariantEnum(String startDate, String endDate, String... variants) {
		this.startDate = startDate;
		this.endDate = endDate;
		this.variants = variants;
	}
}
