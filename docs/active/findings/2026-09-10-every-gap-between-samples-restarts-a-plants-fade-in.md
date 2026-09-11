**Every gap between samples restarts a plant's fade-in, so the aggregates are
weighted to the few plants that sample daily.** `sewage/Multi.java`
includeSewage, its fade-in and fade-out multipliers, from `a38c69e` (then in
`nwss/Sewage.java`; moved in `6abdab2`). [no-check: nwss/Sewage.java is the
pre-split file]

includeSewage sets lastZero on every day of a plant's range with no entry, and
getNextZero, in `sewage/Abstract.java`, stops at the first such day. The
reader makes entries only on sample days, and most plants sample once or a few
times a week. For a sample day between two days without samples the weight is
(1/182)^2 * (1/14)^2, about 1.5E-7, where a plant sampled every day for half a
year, and for another fortnight after, gets 1. `docs/reference/wastewater.txt`, under AGGREGATION AND THE
HIERARCHY, says the fade exists so a plant that has just started or resumed
reporting enters over half a year; a plant between two routine samples has
done neither, so the doc is left saying what was meant.

How verified: a throwaway probe replayed `nwss/Nwss.java` readSewage (plant
key, column choice, spike cap) and includeSewage's multipliers on the cached
j9g8-acpt CSV of 2026-09-10, with exact calendar dates. 2,525 plants, 2,486 of
them included; of 604,814 included plant-days, the 10th to 75th percentile
weight is exactly 1.54E-7, the 99th 0.31, and 3,574 are at full weight. 12
plants ever reach full weight and 35 ever exceed 0.001. By median gap between
samples, 131 plants sample daily and most of the rest every 2 to 7 days. On the
national aggregate over the last 365 days, the median day has 304 plants
reporting and a Kish effective count (the sum of the weights, squared, over
the sum of their squares) of 2.35 plants; population weighting alone would give 56.
Over all days it is 185 plants, 2.68 against 30.6. So the national baseline
that every normalizer is fitted to is, on a typical day, a handful of plants.
It is not a migration artifact: in the predecessor concentration dataset, as
last cached on 2026-09-09, only 12,352 of 211,035 sample days had samples on
both neighbouring days.

What it would take: decide what counts as a stop in reporting, for instance a
gap longer than the plant's own sampling interval or than a fixed number of
days, and have both lastZero and getNextZero use it. That meets
`docs/active/findings/2026-09-10-a-zero-reading-is-weighted-out-of-every-aggregate.md`
at getNextZero, and the two fixes want to be designed together. Every
aggregate and the baseline move, so every normalizer and chart does; a run
compared against the charts from before is the validation, and the printed
round count of the baseline loop is worth watching too.

Not fixed in the review of `sewage/Multi.java` that found it: it changes every
chart, needs a choice of what a stop is, and needs a run.
