**The national baseline loop never converges; it always runs all 100 rounds.**
`sewage/All.java` normalize(), with `sewage/Plant.java` buildNormalizer, both
from `765e7c0` (then in `nwss/Sewage.java`). [no-check: nwss/Sewage.java is
the pre-split file]

buildNormalizer returns without touching the normalizer when the plant's sums
over the days it shares with the baseline are zero, and normalize() then
divides every plant's normalizer by the round's renormalization anyway. Once
the fitted plants settle, that renormalization settles too, but not at 1:
0.99124 in the download of 2026-09-10. So each unfitted plant's normalizer
moves by |log 0.99124| = 0.0088 every round, the largest log-change never
drops below 0.0088, and the 1E-6 test never passes.

How verified: a throwaway probe replayed `nwss/Nwss.java` readSewage from the
cached j9g8-acpt CSV of 2026-09-10 and ran All's loop through reflection,
separating the plants buildNormalizer cannot fit. 2,525 plants kept, 40
unfitted: 39 with a one-day range, since buildNormalizer sums
`day < lastDay` and a one-day plant has no such day, and one eight-day plant,
STATE_TERRITORY_ny_2618_upstream_raw wastewater, whose only positive reading
is on its last day. The fitted plants' largest change fell under 1E-6 at
round index 51, 52 rounds in, and was 7.8E-12 by the hundredth, so the
normalizers the charts use are converged; the other 48 rounds buy nothing, at
about half a second each here, some 20 s of every run. The loop's own
per-round line shows it: from about round 30 it reads 0.0088 and stays there.

What it costs besides the time: the unfitted plants' normalizers end as 1
divided by all 100 renormalizations (0.497 today), a number that means
nothing and changes with the round count, and `nwss/Nwss.java` draws every
plant's own chart on it. No aggregate moves visibly: Multi's includeSewage
skips the 39 one-day plants, and the eight-day plant's readings are 0, 0 and
3E-11 in its own units, its zeros weighted out and its last day entering at
negligible weight.

The fix is to measure the change over fitted plants only, which needs
buildNormalizer to say whether it fitted (a boolean return, say), and to
decide what normalizer an unfitted plant should carry: 1, left out of the
renormalization, or no chart at all. Separately, `day < lastDay` drops every
plant's last day from its sums with no comment saying why; if that is an
off-by-one rather than a choice, `<=` fits the one-day plants as well, and
moves every normalizer slightly -- not every one slightly: a plant with two
readings is fitted on its first alone, and 35 of the plants kept on 2026-09-10
have exactly two. The same change must correct the count
normalize() prints at the end, which after a break is the index of the round
that passed, one less than the rounds run, and the comment on normalize() that
says the cap always ends the loop. Expected effect: about 52 rounds; fitted
normalizers move by a few millionths in log (the per-round change was
contracting by about 0.78 a round), which no chart shows; the unfitted
plants' own charts change scale. A run shows both, in the printed round count
and in one of those plants' charts.

`docs/reference/wastewater.txt`, under THE NATIONAL BASELINE, describes the
loop as meant, ending when the largest log-change falls under 1E-6, and is
left saying so.

Not fixed in the review of `sewage/All.java` that found it: the fix reaches
`sewage/Plant.java`, changes what the unfitted plants are drawn with, and
what they should carry is a choice. That review did remove the loop's other
exit, `normPlant == null`, which could not fire alone: normPlant was assigned
only together with a positive normDiff, so it was null only when normDiff was
0, which the 1E-6 test already caught.
