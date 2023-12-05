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
			"eg.5.1",
			"eg.5.1.1",
			"eg.5.1.3",
			"eg.5.1.4",
			"eg.5.1.6",
			"eg.5.1.8",
			"eg.6.1",
			"fl.1.5.1",
			"fl.1.5.2",
			"fl.15.1.1",
			"fu.2",
			"ge.1",
			"gj.1.2",
			"gj.1.2.2",
			"gk.1.1",
			"gk.1.8",
			"gk.3.1",
			"gk.2",
			"gs.4.1",
			"hf.1",
			"hf.1.1",
			"hk.3",
			"hk.3.1",
			"hk.3.4",
			"hk.6",
			"hk.8",
			"hk.13",
			"hn.1",
			"hn.2",
			"hn.5",
			"hv.1",
			"hv.1.1",
			"jd.1.1",
			"jd.1.1.1",
			"je.1.1",
			"jf.1",
			"jg.3",
			"jg.3.1",
			"jn.1",
			"jn.2",
			"jn.3",
			"xbb.1.16",
			"xbb.1.16.11",
			"xbb.1.16.15",
			"xbb.1.16.6",
			"xbb.1.41",
			"xbb.1.5",
			"xbb.1.9",
			"xbb.2.3",
			"xch.1",
			"xcr",
			"xda",
			"xbb.1.16.1",
			"xbb.1.16.9",
			"xbb.1.5.72",
			"xbb.1.42.2",
			"hz.1",
			"gk.1.1.1",
			"xch",
			"xbb.1.22",
			"xbb.1.5.10",
			"hn.4",
			"xbb.1.16.23",
			"ge.1.4",
			"fu.1",
			"hy.1",
			"fu.2.1",
			"eg.5.2",
			"eg.10.1",
			"hh.1",
			"xbb.1.41.2",
			"fl.15",
			"eg.5.1.9",
			"dv.7.1.4",
			"jm.2",
			"hn.6",
			"jc.5",
			"fy.5",
			"hf.1.2",
			"gk.2.1",
			"xbb.2.3.15",
			"eg.5.2.1",
			"xbb.2.3.3",
			"fy.8",
			"hv.1.4",
			"gk.1",
			"hn.3.1",
			"gn.1",
			"fl.20",
			"xbb.1.16.14",
			"gs.1",
			"hk.7",
			"gj.5",
			"jc.1",
			"hk.11",
			"xbb.1.5.28",
			"dv.7.1.5",
			"xbb.1.16.2",
			"eg.2",
			"hv.1.2",
			"fl.20.2",
			"fd.1.1",
			"ft.3.1.1",
			"xbb.1.16.8",
			"fl.10.1",
			"xbb.2.3.8",
			"hv.1.3",
			"fu.1.1.1",
			"jj.1",
			"gv.1"

	// watch list:
	// XDD (not numbered)
	//
	// JG.3.2 (lacks assignment)
	// HK.20.1 (23, +23%), lacks assignment
	// HK.2 (27, +15%)
	// EG.6.1.1 (23, +10%)
	//
	// Next removal:
	// HN.2
	),

	BA_286("2023-09-10", null, "BA.2.86", "BA.2.86.1", "JN.1", "JN.2", "JN.3"),

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
