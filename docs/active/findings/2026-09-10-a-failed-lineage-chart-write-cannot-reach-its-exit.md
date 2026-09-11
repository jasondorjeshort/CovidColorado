**`buildAbsolute`'s exit on a failed save cannot fire for a failed write.**
`charts/ChartSewage.java` buildAbsolute, the `try` around
`Charts.saveBufferedImageAsPNG`, from `5118ea1`; the catch that stops it is in
`charts/Charts.java` saveBufferedImageAsPNG, from `9952746`.

`5118ea1` ("Make variant graphs") first wrote one chart per lineage, named for
the lineage, into the variants folder. It stripped the `*` from that name and
wrapped the save in `catch (Exception e) { e.printStackTrace(); System.exit(1); }`,
the only one of the live save calls with a wrapper. But saveBufferedImageAsPNG
had already caught `IOException` itself since `9952746`: it prints a stack
trace and "Fail on file" and returns normally. On Windows an unwritable name
fails in the `FileOutputStream` constructor with a `FileNotFoundException`,
which was checked by opening `a*b.png`, `a?b.png`, `a"b.png`, `a<b.png` and
`a|b.png` from Java 25 on this machine. So a failed write never reaches the
exit. Only an unchecked exception thrown inside the save can reach it, such as
`ImageIO.write`'s `IllegalArgumentException` for a null image, and a
`createBufferedImage` result is never null.

So the run behaves as if the exit were not there. A lineage chart that cannot
be written leaves a stack trace in the log, and the run goes on and ends
normally. Every save call in `charts/ChartSewage.java` then rebuilds the path
from its own uncleaned name and may queue it with `library/OpenImage.java`
openImage, whether or not anything was written.

Which behaviour is meant is the maintainer's call. One option makes a failed
write fatal: saveBufferedImageAsPNG throws, or returns whether it wrote, and
the callers act on that. The other accepts that a failed write is not fatal
and deletes the `try` in buildAbsolute. Not fixed in the review pass on
`charts/Charts.java` that found it. Either fix edits `charts/ChartSewage.java`,
which was outside that pass, and the first changes what a run does when a
write fails, which a normal run cannot show.
