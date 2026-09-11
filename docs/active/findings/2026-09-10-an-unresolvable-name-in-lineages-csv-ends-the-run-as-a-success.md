**An unresolvable name in lineages.csv ends the whole run with exit status 0.** `variants/Lineages.java`, build(), the null check after `Lineage.get`, from `112320a`.

When `Lineage.get` returns null for a name in pango-designation's
lineages.csv, build() prints a stack trace and calls `System.exit(0)`. It runs
as one of `Nwss.read()`'s pool tasks, so the exit takes down the sewage read,
the LAPIS read and everything after them: no charts are drawn and
`./gradlew run` reports success. `Lineage.get` returns null for a name that is
not letters and dotted numbers, or whose letters are no alias in
alias_key.json -- for instance a lineages.csv row that names an alias the same
pull's alias file does not carry.

What the exit protects is nothing the program uses. build()'s only products are
the printed lineage count, a warm `Lineage` cache, and each lineage's ordering,
and `Lineage.getOrdering()` has no caller. So `continue` in place of the exit
would lose nothing; failing the run with a non-zero status is the other
choice, if a bad lineages.csv should stop everything.

Latent today: a throwaway probe compiling the current `Aliases` and `Lineage`
against the checkout resolved all 6,006 distinct names in lineages.csv on
2026-09-10. Not fixed in the review of `variants/Lineage.java` that found it,
because it is in another file and the choice between skipping and failing is
the maintainer's.
