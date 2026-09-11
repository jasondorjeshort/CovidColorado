**A download from a server that stops sending hangs the run for good.**
`nwss/Nwss.java` download, which opens the stream with no timeouts, from
`5dc0ebc`. The CDC CSV and the LAPIS JSON both go through it.

`url.openStream()` takes the connection's defaults, a connect timeout and a
read timeout of 0, which means none. So a server that accepts the connection
and then sends nothing, or stops partway through the 269 MB CDC file, blocks
the pool thread in `download` indefinitely. read() waits on that task through
`ASync.complete()`, which has no limit either, so the run neither fails nor
finishes. This is the hang `a099b4f` closed for the pango-designation pull in
`library/GitUpdater.java`, on the HTTP path that commit did not touch.

Verified with a throwaway probe on 2026-09-10: `openConnection()` reported both
timeouts as 0, and `Nwss.download` against a local socket that accepted the
connection and never answered was still blocked after 45 s, with no file
written.

What it would take: open the connection with `url.openConnection()`, set a
connect and a read timeout on it, and read from `getInputStream()`. A read
timeout bounds a silence between reads, not the whole download, so a slow but
live transfer is unaffected. Timed that day, both endpoints sent their first
bytes in under a second, so 60 s, the stall bound GitUpdater now uses, leaves
wide room. A timeout then takes download's existing failure path: the file is
deleted, the LAPIS reader prints that there are no automatic variants this
run, and the CDC reader ends the run as described in
`docs/active/findings/2026-09-10-an-unreadable-cdc-download-ends-the-run-as-a-success.md`,
so the two want deciding together. Not fixed in the review of
`nwss/Nwss.java` that found it: it changes what every fetch does, a normal run
cannot show it working, and the value is a guess about somebody else's server.
