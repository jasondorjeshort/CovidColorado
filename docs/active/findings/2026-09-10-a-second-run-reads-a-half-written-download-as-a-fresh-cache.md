**Two runs at once corrupt each other's input without a word: a download in
progress looks like a fresh, complete cache.** `nwss/Nwss.java`,
`ensureFileUpdated` and `download`, which both the CDC CSV and the LAPIS JSON go
through.

`download` opens a `FileOutputStream` on the final path and writes to it in
1 KB chunks, so the file exists, and its mtime is current, from the first write
to the last. `ensureFileUpdated` decides whether to download by mtime alone. A
second run that starts while the first is still fetching finds a file younger
than the TTL, skips the download, and parses however much has arrived. The CDC
file is 269 MB and takes about three minutes, so the window is wide.

Seen on 2026-09-10: another run of the program downloaded both files at
13:50-13:53 while a validation run started at about 13:51 and read the CSV
mid-write. It logged no download of its own, parsed 2,458 plants instead of
2,534, dropped 98 spike days instead of 383, and drew a national series at 47M
line population instead of 112M -- a curve different enough to put every
variant fit's start at the left edge of the window and let 200 lineages survive
the merge instead of 105. Every figure looked plausible on its own. Nothing
failed: the CSV parser only throws on a truncated last line that is short of the
fields the reader asks for, the highest of which is `pcr_target_mic_lin`, the
thirtieth, so a line cut anywhere after it parses. (Had it thrown, `readSewage`
deletes the file and exits, which would at least have been loud.)

What it would take: download to a sibling `.part` file and rename it over the
target when the stream closes, so the final path only ever holds a complete
file; `Files.move` with `ATOMIC_MOVE` does it on one volume. That alone closes
the window. A lock file would also stop two runs fetching the same thing twice,
but the rename is the part that matters. Not fixed in the pass that found it
because that pass was committing unrelated chart guards, and the fix belongs to
a helper both downloads share.
