**The cumulative lineage chart does not say which sewage series it is of.**
`charts/ChartSewage.java` buildSewageCumulativeChart, the `createBarChart`
call, from `21d7e55`.

The chart's whole title is "Cumulative prevalence". Every other chart in the
file puts the series' `getTitleLine()` and a "Source:" line in its title; this
one has neither, and no date. That was harmless while the national series was
the only one with lineage charts. `nwss/Nwss.java` build() has charted
Colorado against a single-variant export since `1b2e50d` and against the LAPIS
Voc since `281ec93`, so each run now draws two
cumulative-variant and two cumulative-strain charts that differ only in their
file names, and queues all four for the end-of-run thumbnail window, where the
chart itself cannot say which it is.

Verified against the 2026-09-10 17:22 run: `United States-1-cumulative-variant.png`
under the nwss folder and `Colorado-1-cumulative-variant.png` under its
states folder both carry the same one-line title, and both paths are in that
run's open-charts.txt. [no-check: runtime chart files outside the checkout]

The fix is to build the title the way buildAbsolute and buildRelative do,
from `vocSewage.sewage.getTitleLine()` plus the source line. Not fixed in the
review of `charts/ChartSewage.java` that found it because it changes what is
drawn, which that pass could not validate with a run.
