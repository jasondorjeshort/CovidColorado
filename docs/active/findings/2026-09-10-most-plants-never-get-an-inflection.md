**Nine plants in ten never get an inflection, because turns are looked for
only between readings on consecutive days.** `sewage/Abstract.java`
buildInflections: the return after "Uh oh skipping inflections", from
`112320a`, and the scan's comparison of each day with the next, from `beade3a`
(then in getMarkers; moved in `172d4d5`).

buildInflections sets its starting direction from the first reading on or
after 2020-09-01 and the reading on the next calendar day, and returns with no
inflections when there is none on that day. Before `112320a` that case threw
NullPointerException out of the unboxing. Past it, the scan compares each day
only with the next and skips a pair with either missing, so a turn can be
found only where a series has readings on two consecutive days. Most plants
sample every few days (131 of 2,525 sample daily, per
`docs/active/findings/2026-09-10-every-gap-between-samples-restarts-a-plants-fade-in.md`).

How verified: a throwaway Python probe replayed `nwss/Nwss.java` readSewage on
the cached j9g8-acpt CSV of 2026-09-10 (plant key, parseValue, column choice,
spike cap; exact calendar dates) and then buildInflections, on raw values,
since a positive normalizer changes no comparison. Of the 2,525 plants
`sewage/All.java` keeps, 39 have a one-day range and get no inflections by
design; 2,188 take the early return; 90 pass it and find no turn; 208 have at
least one inflection, three for the median one. So 2,317 of 2,525 plants have
none. `docs/active/findings/2026-09-09-inflection-building-floods-the-log.md`
counts about 3,200 early returns a run, which suggests many aggregates take it
too; the aggregates were not replayed.

What a series without an inflection loses: markers on its charts;
makeFitSeries's bound at a week after the last inflection, so its fit is
limited only by the confidence-interval walk and can reach back across earlier
waves; and a recent chart that reaches back to the last inflection when that
is more than 180 days ago (`charts/ChartSewage.java`
buildSewageTimeseriesChart). The national and Colorado series, which seed the
lineage fits in `variants/VocSewage.java`, both show inflection markers on the
run's charts. `docs/reference/wastewater.txt`, under INFLECTIONS AND FIT
LINES, says the method records where a series turns, and is left saying what
was meant.

That sibling finding calls skipping these series the correct handling. It is
the correct handling of the log line; what is skipped is every inflection the
series would have had.

What it would take: compare each reading with the previous reading that
exists, rather than with the previous calendar day, and take the starting
direction from the first two readings. The 28-day confirmation window and the
14-day tail are in days and can stay. Both "Uh oh" messages go with it, which
resolves the sibling finding in the same change. Markers, fit windows and
recent-chart ranges move on most plant charts and on the aggregates that take
the early return. The national and Colorado series move only if they have
days without an entry, which was not checked; if they do, the lineage fits
seeded from them move too. A run is the validation.

Not fixed in the review of `sewage/Abstract.java` that found it: it changes
what most sewage charts draw and may move the lineage fits, and only a run
can show it.
