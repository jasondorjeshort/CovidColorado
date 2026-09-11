**A zero reading is weighted out of every aggregate instead of averaged in.**
`sewage/Abstract.java` getNextZero, as used by the fade-out multiplier in
`sewage/Multi.java` includeSewage, from `a38c69e` (then in `nwss/Sewage.java`;
moved in `6abdab2`). [no-check: nwss/Sewage.java is the pre-split file]

getNextZero returns the first day with no entry *or* a reading of zero or
less. includeSewage's end multiplier, `min(((nextZero - day) / 14)^2, 1)`, is
therefore 0 on every zero reading and ramps the plant down over the 14 days
before each one. A zero is not counted as a low value in the
population-weighted mean `nwss/DaySewage.java` accumulates; it is dropped, and
the plant's readings before it are discounted too. The readings lost are the
lowest the plant has, so any aggregate holding such a plant reads high on
exactly the days it should read lowest. On an aggregate day where every
contributor read zero, DaySewage's getSewage returns its placeholder 1
instead.

How much, in the CDC download of 2026-09-10 (dataset j9g8-acpt): only
`pcr_target_mic_lin` carries zeros, 12,884 of 362,858 values; the
flow-population and raw-concentration columns have none, and no value was
negative, so
`nwss/Nwss.java` parseValue's clamp to 0 changed nothing. Replaying Nwss's
column choice, 340 of 2,534 plants use the microbial column, 246 of them have
at least one zero day, and 6,388 of their 121,501 plant-days are zero (5.3%).

This is not what the code was written to do. `a38c69e`, which added the
weighting, says of zero readings in the reader: "If using a geometric system
we'd need to skip these ... If using algebraic then we do want to include the
zeroes." The aggregate is algebraic, a weighted arithmetic mean. The same
commit also restarted the 182-day fade-in at every zero (`lastZero = day`);
that line is now commented out in includeSewage, and the fade-out half was left
in. `docs/reference/wastewater.txt`, under AGGREGATION AND THE HIERARCHY,
defines nextZero as the next day with no entry and says the fade exists so a
plant joining or leaving the pool does not put a step in the aggregate. A
non-detect is neither, so the doc is left saying what was meant.

The fix is to make getNextZero stop only at a missing day, and it has one
consequence left to handle in the same change: an aggregate day whose
contributors all read zero would then be exactly 0 with nonzero weight, which
DaySewage's placeholder does not cover. `sewage/Abstract.java` makeFitSeries
no longer takes the log of every day -- it skips a reading that is not
positive -- so a 0 reaching a fit is no longer one of these consequences.
(`variants/VocSewage.java` divides by getSewage too, but a zero reading makes
its numerator zero and the MINIMUM test skips the day first.) And the baseline
in `sewage/All.java` would move, so every normalizer and chart moves with it.
The comment on that placeholder in DaySewage's getSewage names this file, so it
changes with the fix.

Not fixed in the review of DaySewage that found it: the fix lives in two other
files, and it changes every aggregate curve. Only a full run, compared against
the charts from before, can show it.
