**A smoothed sewage line counts every day without a reading as zero, so a
series sampled less than daily is drawn low and falls off the chart at every
gap.** `sewage/Abstract.java`, the branch of makeTimeSeries for more than one
day averaged, from `2b193d5` (365 days only) and `112320a` (any window).

The branch sums the entries in the trailing window and divides by
daysAveraged, however many of the window's days have an entry, and it adds a
point for every day from the first to today. So:

- a window with readings on k of its days reads k/daysAveraged of the level:
  a series sampled twice a week draws at about 2/7 of it on the 7-day chart;
- a window with no reading reads 0, drawn as 1E-6, which drops off the bottom
  of the chart;
- after the last day the line decays over one window and then sits at 1E-6
  until today, and the first windows reach back before the first day, so the
  line ramps up from nothing.

Most plants sample less than daily (131 of 2,525 sample daily, per
`docs/active/findings/2026-09-10-every-gap-between-samples-restarts-a-plants-fade-in.md`),
and an aggregate of a few such plants, a county say, has days with no entry
too. The national series has an entry on nearly every day and is affected
only at its ends. Every plant, county, state and region, and the nation, gets
7-, 14-, 28- and 365-day charts from `charts/ChartSewage.java` createSewage.

Seen in the charts of the run of 2026-09-10. Adams county, Colorado, runs at
20 to 40 through the summer of 2022 on its daily chart and at about 10 on its
7-day chart, which a mean of the same days would not give. The national 7-day
chart falls off the bottom at its right edge, after its last day of
2026-09-01.

What it would take: divide by the number of days in the window with an entry,
and add no point for a window with none, which also ends the line at the last
day. The first windows would then average only the days since the first; the
Javadoc on buildBackend in `sewage/Multi.java` notes that an aggregate's
trimmed first days still feed them, which the fix should keep in view. The
same branch also skipped the normalizer, which is fixed: the window sum is
now multiplied by it before the divide, and what is left wrong is what this
entry describes. Every smoothed chart changes; a run, compared with the daily
charts of the same series, shows it.

Not fixed in the review of `sewage/Abstract.java` that found it: it changes
what every smoothed chart draws, and only a run can show it.
