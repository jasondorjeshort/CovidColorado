**The LEnum lineage lists were last edited in June 2024, so the cov-spectrum
links they print would leave most of today's sequences in "others".**
`variants/LEnum.java`, both constants, unchanged since `01aa6d5`; the list
content is from `3242dec`, June 2024.

The manual export path exists for the GISAID data the open LAPIS endpoint does
not carry, and these lists are its only input: `nwss/Nwss.java` read() prints a
cov-spectrum comparison link per list, each name queried inclusively, and
whatever an export from that link does not name, Voc build() puts in "others".
Counted against the LAPIS cache of 2026-09-10 -- 959 US sequences dated
2026-05-21 to 2026-08-19, each expanded through pango-designation's
alias_key.json and tested for descent from a listed name -- 284 fall under a
SEP_TO_NOV_2023 name and 165 under an ALL_TIME_VARIANTS name. XFG alone is 517
of the 959 and is under neither, being a recombinant root that neither list
names; so are XFJ (87) and XFZ (32). ALL_TIME_VARIANTS also misses XDV (154),
the root NB.1.8.1 and PQ descend from. That is the open data rather than the
GISAID data an export would carry, but the lineage mix is the same.
SEP_TO_NOV_2023's link also runs from 2023-09-15 to ten days ago, three years
for a list picked for one wave.

What is drawn today is not affected: `variants/Lapis.java` pins only lists that
start inside its emitted window, and neither does.

Fixing it means choosing the lineages and the start date again, which is the
maintainer's judgement, and a start date inside the LAPIS window would also pin
those lineages and change what the LAPIS charts draw, which only a run can
check. Not fixed in the review pass that found it for both reasons. Whoever
takes it should consider deriving the list instead: the lineages the merge
loop in `variants/VocSewage.java` keeps on the national chart are a current
candidate list every run.
