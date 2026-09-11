**LogitAxis rounds an upper bound under a tenth of peak to the wrong ceiling.** `myjfreechart/LogitAxis.java`, computeLogitCeil's last branch, from `c2d26a1`.

For an upper bound at or below peak/10 the method takes ceil(log10(upper)) and
passes it to unlogit, where LogarithmicAxis's computeLogCeil, which this was
copied from, has Math.pow(10, ...). unlogit turns a log10 exponent into a
log-odds position, so with the relative charts' peak of 100 anything in (1, 10]
gets an upper bound of 90.9 and anything in (0.1, 1] gets 50, where 10 and 1
were meant; 10.01 gets 50, so the ceiling is not even monotonic.
computeLogitFloor mirrors bounds above peak/2 through this method, so a lower
bound of 90 comes out as 9.09, 95 as 9.09, 99 as 50. Measured by compiling a
copy of the class in a throwaway probe and calling both methods through
reflection.

The fix is `Math.pow(10, upper)` in place of `unlogit(upper)`. It needs one more
thing: a chart whose data is a single value that is exactly a power of ten, or
peak less one (10, 90, 99 with a peak of 100), would then get a floor equal to
its ceiling, and JFreeChart 1.5.6's ValueAxis.setRange throws on a zero-length
range. Today the wrong ceiling happens to keep those two apart.

Not fixed in the review that found it because it changes what an axis draws,
and today's charts should not reach it: charts/ChartSewage.java buildRelative is
the one caller, each chart plots about ten lineages sharing every day's 100%, so
its largest point is over 10%, and the caller overwrites the lower bound with 1
or 0.1 after the auto-range. A run that confirms no relative chart has its
maximum at or under 10% would settle whether the fix changes any output at all.
