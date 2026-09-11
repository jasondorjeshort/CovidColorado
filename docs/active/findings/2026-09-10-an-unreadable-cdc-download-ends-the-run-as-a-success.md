**A CDC download the reader cannot read ends the whole run with exit status
0.** `nwss/Nwss.java` readSewage, the catch around the parse, from `fc1a863`
(the delete in it from `2669088`). The same exit is in build(), in the catch
around each lineage's charts, from `112320a`.

Any exception in readSewage's parse prints a stack trace, deletes the CSV and
calls `System.exit(0)`. readSewage is one of read()'s pool tasks, so the exit
takes the other readers with it before build() has drawn anything, and
`./gradlew run` reports success. Probed against commons-csv 1.14.1 with
readSewage's format: a renamed column fails `CSVRecord.get` with
`IllegalArgumentException: Mapping for source not found, expected one of [...]`,
and a missing file fails `CSVParser.parse` with `NoSuchFileException`. Both
land in the catch.

A missing file is what a failed fetch leaves. `ensureFileUpdated` deletes a
stale file before it fetches, and `download` writes to a sibling `.part` file
it deletes when it fails, so nothing arrives under the final name at all. So a
run made offline more than four hours after the last fetch throws
away a usable file, then exits with status 0 and no charts, and the next run
has nothing to fall back on either. A renamed column, which CLAUDE.md expects
sooner or later, costs a 269 MB fetch on every run until the reader is fixed,
since each run deletes the file it could not read. (From reading the code; the
probe covered only the parser's two exceptions.)

build()'s exit has the same shape. Any exception while building one lineage's
VocSewage or charts ends the JVM with status 0 while the pool is still drawing
other charts, skips the remaining lineages, and never reaches
`library/OpenImage.java`.

What it would take is the maintainer's choice, as for the siblings
`docs/active/findings/2026-09-10-an-unreadable-cov-spectrum-export-ends-the-run-as-a-success.md`
and
`docs/active/findings/2026-09-10-an-unresolvable-name-in-lineages-csv-ends-the-run-as-a-success.md`:
exit with a non-zero status, or, in build(), skip the lineage and carry on.
Keeping the stale CSV until a new one has arrived would let an offline run
draw from the last good file. The rename-into-place fix that half of it waited
on has landed in `nwss/Nwss.java` `download()`, so the final name now only ever
holds a complete file; all that is left is for `ensureFileUpdated`'s delete
before the fetch to move after the download. Not fixed in the review of
`nwss/Nwss.java` that found it, because each choice changes what a failed run
does, which no normal run shows, and which failures should stop the run is the
maintainer's call.
