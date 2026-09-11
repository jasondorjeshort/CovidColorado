**A day inside a Voc's range that no variant has any prevalence on is drawn as
100% "others", not as a day with no data.** `variants/Voc.java` `build()`, the
block that adds the "others" variant, from `cdfdd0f`; the LAPIS side of it
from `a850f73`.

`build()` gives "others" 1.0 less the sum of the other variants on every day
from the first to the last, and trims only trailing days on which nothing has
prevalence. A day in the interior where nothing does keeps its place in the
range and comes out as all "others". `variants/Lapis.java` produces such a day
whenever a smoothing window holds fewer than MIN_WINDOW_SEQUENCES (20)
sequences: it gives every lineage nothing there, because a thin day is noise,
and docs/reference/lineages.txt says such a day "gets no prevalence at all".
An interior day on which every row of a comparison export reads "null", which
the CSV reader skips, would do the same. VocSewage addRelative drops a 100%
point, so the relative chart shows a gap there; the absolute chart plots
"others" at the full sewage level for that day and the cumulative chart sums
it, while every lineage is absent.

Latent today. Replaying the 2026-09-10 LAPIS cache over that run's emitted
window (2025-09-10 to 2026-08-31): 16 days had a window under 20 sequences, all
of them the trailing run from 2026-08-16 that the trim removes. Away from the
last few weeks the thinnest window held 49, in early June 2026, so a quiet
week at today's 300-600 open sequences a month would be enough.

What it would take: give "others" no value on a day where no other variant has
any prevalence -- the test the trailing trim already makes -- so the day reads
as missing for "others" as it already does for every lineage. That changes
what the absolute and cumulative charts draw on such a day, so it needs a run
with one present to check. Not fixed in the review of `variants/Voc.java` that
found it, which was limited to changes that do not alter what is drawn.
