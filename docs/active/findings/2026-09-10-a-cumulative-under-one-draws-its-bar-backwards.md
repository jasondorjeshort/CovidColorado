**On the cumulative lineage chart, a lineage whose cumulative is under 1
draws a bar pointing the other way, longer the smaller it is.**
`charts/ChartSewage.java` buildSewageCumulativeChart, the
`Math.log(prevalence) / Math.log(10)` in both branches, from `21d7e55`.

Each bar is the log10 of the lineage's cumulative -- sewage as a percentage of
the pandemic peak times its share, summed over the VocSewage's days -- drawn
from zero on an ordinary number axis. A cumulative above 1 draws rightward and
one below 1 draws leftward, so bar length stops meaning size at 1: a lineage at
0.63 gets a bar as long as one at 1.6, and one at exactly 1 gets none. The
order is still right, since the lineages are sorted by cumulative, and the axis
is labelled in powers of ten, but the lengths contradict it.

Verified against the 2026-09-10 17:22 run's Colorado-1-cumulative-variant.png
under the states folder: xfg.6.13* draws leftward to -0.2, visibly longer than
the bars for sv.2* (about 0.05) and sv.1* (about 0), both of which rank above
it. The national chart from the same run has every lineage above 1 and shows
nothing wrong. [no-check: runtime chart file outside the checkout]

The fix is to draw the raw cumulatives on a log axis -- JFreeChart's LogAxis
can be a CategoryPlot's range axis -- or to start the bars from a floor below
the smallest value rather than from zero. Not fixed in the review of
`charts/ChartSewage.java` that found it because either changes what is drawn,
which that pass could not validate with a run.
