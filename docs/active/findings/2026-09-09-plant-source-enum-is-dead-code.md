**`sewage/Plant.java`'s `Source` enum is dead code that reads as a live
dependency.** `sewage/Plant.java`, the `Source` enum, the `source` field, and
`getSource()`, rewritten by the migration to dataset j9g8-acpt (`109170f`).

The migration updated the four constants -- STATE_TERRITORY, WASTEWATERSCAN,
CDC_VERILY, CDC_BIOBOT -- to match the new dataset's `source` values, and they
do match: `nwss/Nwss.java` uppercases that column into the front of the plant
id, and `Source.get` recovers it by prefix. The work was correct. It is also
unobservable: nothing outside `Plant.java` calls `getSource()` or reads the
field, so no chart, filter or log would change if `Source.get` returned null for
every plant, and nothing would have caught it if the migration had got the
constants wrong.

The cost is what a reader concludes. `docs/reference/wastewater.txt` said until
now that the id is uppercased "so that Plant.java's Source enum still matches it
by prefix" -- an inference anyone reading `readSewage` would draw, and it is not
true, because no reader of the enum depends on that shape. The doc sentence has
been corrected; the code has not.

Not fixed now because deleting it and keeping it are both defensible -- the enum
is the only place the dataset's provider vocabulary is written down, and a
per-source chart or a WASTEWATERSCAN-only filter is a plausible next use of it
-- and `CLAUDE.md` says not to delete dead code on your own initiative. The
call is the maintainer's: delete the three members, or give the enum a reader.
Deleting it also removes what actually stops a null id: the constructor's null
check only prints, and it is `Source.get`'s NullPointerException that aborts,
as the comment at that check now says; a deletion should make the check throw.
