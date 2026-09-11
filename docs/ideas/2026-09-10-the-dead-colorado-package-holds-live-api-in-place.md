**Deleting the dead `colorado/` package, and the live members only it
uses, would leave the live files describing only what the live program does.**
Everything in `colorado/` except the entry point in
`colorado/CovidColorado.java`, plus the members of live files that only that
package calls.

The review-oldest-java passes of 2026-09-10 traced every file in `colorado/`
back to `CovidColorado.old()`, which nothing calls, and found the dead code
pinning live API as it goes:

- `library/ASync.java`: `submit` is called only by `colorado/AbstractChart.java`,
  and `getExecutions` only by `colorado/ChartMaker.java`.
- `charts/Charts.java`: `setDelay`, `useMedian`, `valueDesc`, `value` and
  `getTodayMarker` have no callers outside `colorado/`; `getIncompleteMarker`
  has none live.
- `charts/Chart.java`: only `colorado/` constructs one. `nwss/Nwss.java` and
  `charts/ChartSewage.java` name it only as the type argument of
  `ASync<Chart>`, and would take `ASync<Void>` just as well.

Each of those is documented, reviewed and kept compiling for code that never
runs. What it would take: move `main` out of `colorado/CovidColorado.java`
into a live package and update `build.gradle`'s `mainClass`, delete the rest
of `colorado/` along with the members above and `charts/Chart.java`, and
compile. The root `README.md`, which describes the first life, would want a
line saying the code for it now lives only in history.

Not a defect: the dead code runs nothing and computes nothing wrong.
`CLAUDE.md` keeps the package deliberately, and deleting it is the
maintainer's decision to make once, not something a maintenance pass arrives
at.
