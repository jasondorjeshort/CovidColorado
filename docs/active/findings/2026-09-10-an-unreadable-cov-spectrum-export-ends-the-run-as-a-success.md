**A hand-exported cov-spectrum CSV the reader cannot parse ends the whole run
with exit status 0, and keeps ending it for eight hours.** `variants/Voc.java`,
the `Voc(List<File>, boolean)` constructor's catch, present in some form since
the first Voc in `8a190e5`.

Any exception while reading an export -- a row shorter than the columns read,
an empty proportion, a date `CalendarUtils.dateToDay` rejects -- prints a stack
trace and calls `System.exit(0)`. `Voc.create()` runs as one of
`nwss/Nwss.java` read()'s pool tasks, so the exit takes the LAPIS read, the
sewage read and every chart down with it, and `./gradlew run` reports success.
The file is not deleted, so every run until it is eight hours old dies the same
way. Probed against commons-csv 1.14.1 with `CSVFormat.DEFAULT`: a four-field
row gives `ArrayIndexOutOfBoundsException` on `get(4)`, the column a
comparison export's variant is read from, and an empty field comes back as ""
and fails `Double.valueOf` with `NumberFormatException`.

The columns are read by position -- date 0, proportion 1, variant 4 -- and
never checked against the header, which is skipped unread. The exports are
somebody else's file: a column dropped from their layout lands here, and one
that moved is read from the wrong place without complaint.

What it would take is the maintainer's choice: skip the unreadable file with a
printed line and carry on, which is what a fallback nobody is required to
supply suggests, or fail the run with a non-zero status. Reading the columns by
header name would make a moved column an error message instead of a wrong read
or a crash, but needs a real export to learn the names from; none was present
in the downloads folder on 2026-09-10. Not fixed in the review of
`variants/Voc.java` that found it, because either choice changes what a run
does and the choice is not a reviewer's.
