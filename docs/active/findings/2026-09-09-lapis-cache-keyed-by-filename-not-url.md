**The LAPIS JSON cache is keyed by a constant filename while the URL it caches
embeds a date and a field list.** `variants/Lapis.java`, `CACHE_FILE` and the
`url` built in `create()`, in the tree as of the LAPIS work.

`CACHE_FILE` is a constant, but the URL handed to `nwss/Nwss.java`
`ensureFileUpdated` contains `dayToFullDate(fetchFirst)` and the requested
fields, and `ensureFileUpdated` decides whether to refetch by comparing the
file's mtime against the TTL alone. It never sees the URL unless it is about to
download. So the file on disk and the URL that would produce it can disagree
for up to `TTL_HOURS`.

What that costs is narrower than it looks. Day to day, nothing: a run the next
morning computes a `fetchFirst` one day later than the cached response starts,
so the response still covers the new window with a day to spare, and the day
the window gains at the far end is today, which never has sequences given the
sequencing lag. The trap is tuning. Raising `WINDOW_DAYS` or `SMOOTH` moves
`dateFrom` earlier than the cached response goes, so for up to a day the extra
days are silently empty and the first emitted days are smoothed over fewer
sequences than they claim; changing the requested fields reuses a response that
does not have them. Lowering either constant, or changing `LAG_DAYS`, takes
effect at once, because those only move where the code reads the response and
not what it asked for. That split is what makes it confusing: some edits to the
same three constants work immediately and some silently do not.

Not fixed now because the fix is a choice the maintainer should make rather
than one a review should pick: hash the URL into the cache filename, so a
different query is a different file and the old one ages out on its own; or
have `ensureFileUpdated` record the URL beside the file and ignore the TTL when
it differs. The first is smaller and leaves stale files behind; the second
changes a signature used by the CDC download too.
