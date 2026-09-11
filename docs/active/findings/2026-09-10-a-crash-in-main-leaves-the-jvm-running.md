**A run whose `main` throws after work has been queued never exits.**
`library/MyExecutor.java`, the `codePool` field, from `eca855f`; the exit it
relies on is the end of `main` in `colorado/CovidColorado.java`.

`eca855f` replaced the code pool's `Executors.newWorkStealingPool()` with
`Executors.newFixedThreadPool(threads)`, to leave half the machine free. The
old pool was a `ForkJoinPool`, whose workers are daemon threads. The new one
uses the JDK's default thread factory, which makes non-daemon threads, and a
fixed pool's core threads never time out. So once `ASync` has run a single
task, the JVM stays up until someone shuts the pool down, and the only code
that does is the last two lines of `main`: `MyExecutor.awaitTermination`, then
`shutdown`.

Task failures do not reach that path, because `catchWrapper` prints and
swallows every `Exception` inside the pool. What does reach it is an unchecked
exception thrown on the main thread after `Nwss.read()` has queued its first
task. The likeliest source is the combining code at the end of `read()`
(`all.build`, then the region, state and county wiring), which runs on data
the pool has just parsed. Then `main` dies with a stack trace, the idle pool
threads keep the process alive, and `./gradlew run` hangs instead of failing. An exception before the first
task (the `pango-designation` pull, `Aliases.build()`) still exits, because no
pool thread exists yet.

There are two fixes. Wrap the body of `main` in `try`/`finally` around
`awaitTermination`. Or build the pools with a thread factory that makes daemon
threads, which is safe because `main` waits on them explicitly. Both are
small. Not fixed in the review pass that found it, because a normal run cannot
show the change working: only a run made to throw would. The choice between
the two is also a design decision (daemon threads also change what happens if
a future caller forgets to wait) rather than a maintenance edit.
