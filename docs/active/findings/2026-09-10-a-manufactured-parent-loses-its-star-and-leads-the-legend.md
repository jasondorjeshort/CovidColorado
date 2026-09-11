**A parent variant that the merge loop manufactures is labelled without its
star, and it leads the non-fit relative chart's legend along with "others".**
`variants/Variant.java`, the `Variant(Lineage)` constructor and the
`averageDay` field, from `1b97260`; the sort that reads `averageDay` is in
`charts/ChartSewage.java` `buildRelative`.

`variants/VocSewage.java` `build()` folds a lineage into its direct parent and,
when that parent was already folded away, manufactures it with
`new Variant(p)`. That constructor names the variant by the expanded lineage and
labels it with the bare alias, where every variant Lapis supplies is named
`nextcladePangoLineage:<alias>*` and labelled `<alias>*`. On a chart both mean
the lineage plus whatever descendants are not drawn separately, so the
missing star is an inconsistency rather than a wrong number, but it is on the
legend. The "Merged variant ... into ..." log lines show the same split, with
expanded names beside query names.

`averageDay` is set once, in `variants/Voc.java` `build()`, and nothing keeps
it: the Others bucket (added after the loop that sets it) and every
manufactured parent keep 0.0, and `Variant.add` leaves a merge target's value
where it was before it absorbed its children. `buildRelative` sorts its non-fit
chart ascending by it, so those zeros come first and take the first colours.

Verified by reading the code and against the 2026-09-10 run's
`United States-1-relative-old-variant-legend-all.png` (no hand-exported CSVs
were present, so Voc 1 was the LAPIS one): the legend reads "xfg.6, rv.1,
others, xfj.3.1*, xfg*, nb.1.8.1*, ..." -- the two starless labels, which only
`Variant(Lineage)` can produce, and "others", ahead of everything else.

Building the manufactured variant from `p.getQuery()` instead would make its
name, label and `duplicate()` behave like the rest; `averageDay` wants either
recomputing after `VocSewage` finishes merging or dropping as a sort key. Not
fixed in the review of `Variant.java` because both change what is drawn, which
a review pass cannot validate without a run.
