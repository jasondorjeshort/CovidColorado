**A zero reading inside a plant's fit window turns its fit line into NaN.**
`sewage/Abstract.java` makeFitSeries, the `Math.log(number)` in its backward
walk. The log fit came in with `5f1be64` (then in `nwss/Sewage.java`), and the
walk has had its current shape since `8c8b98d`. [no-check: nwss/Sewage.java is
the pre-split file]

makeFitSeries adds `log(reading * normalizer)` for every day it walks, with no
check for a reading of zero. A plant's own entries can be zero, because
`nwss/Nwss.java` parseValue keeps a zero and clamps a negative to zero. So a
zero day in the window adds y = -Infinity. Read in the commons-math 3.6.1
source, SimpleRegression.addData then keeps ybar at -Infinity, and the next
point's `y - ybar` is +Infinity, so sumYY and sumXY go infinite or NaN and the
slope, the confidence interval and predict() come back NaN. The series title
then prints NaN, and `Math.max(1E-6, NaN)` is NaN, so the two points of the
drawn line are NaN too. What JFreeChart renders from that has not been looked
at.

Every plant has charts with a fit. `charts/ChartSewage.java` createSewage
builds each plant's series at 1, 7, 14, 28 and 365 days averaged, and
buildSewageTimeseriesChart adds makeFitSeries(28) to each one averaged over
fewer than 30 days, which is four of the five. The plants exposed are the 246
on the microbial column that have a zero day (see the sibling finding
`docs/active/findings/2026-09-10-a-zero-reading-is-weighted-out-of-every-aggregate.md`).
Each is hit only when a zero falls between its fit start and its last day.
How many do today has not been counted. Aggregates are not exposed today,
because `nwss/DaySewage.java` never lets an aggregate day read zero.

The fix is small: skip readings that are not positive in the fit, the way
makeTimeSeries already floors them for drawing. Not fixed in the DaySewage
review that found it. It is in another file, it changes what plant charts
draw, and only a run with a plant known to have a zero in its window can show
the change.
