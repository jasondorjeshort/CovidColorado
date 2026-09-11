**Which day index a date string gets depends on the time of day the run
happens, and in one evening hour it is not even consistent across the year.**
`covid/CalendarUtils.java`, `dateToCalendar`, unchanged since `c02a27f`, as
exposed by the truncating `timeToDay` from `a6952dd`.

`dateToCalendar` takes `Calendar.getInstance()`, the current moment in the
machine's zone, and `set`s only year, month and day, so the hour, minute,
second and millisecond of the run are carried into every parsed date.
`dateToDay` then truncates the UTC milliseconds to a day. On this machine
(Windows zone "Mountain Standard Time", which keeps DST), local time plus the
UTC offset of the parsed date passes midnight UTC in the evening, and the date
lands one day late. Probed with `jshell` in `America/Denver`, same code:

- 09:00: 2025-03-08, 03-09, 03-10 give 20155, 20156, 20157 (correct).
- 18:30: every date is one day late (03-10 gives 20158, which `dayToDate`
  prints as 3/11/2025).
- 17:30: dates under MST shift and dates under MDT do not, so 03-08 and 03-09
  both give 20156, and 2025-11-01 to 11-03 give 20393, 20395, 20396 with
  11-02's day missing.

Every live date goes through this: `nwss/Nwss.java` on `sample_collect_date`,
where two dates on one index are averaged together as "several samples on one
day", and `variants/Lapis.java`, `variants/Voc.java`, `sewage/All.java` and
`sewage/Abstract.java`. A run that crosses 17:00 or 18:00 local time while it
parses gets the two regimes mixed within one run. The run that wrote
`open-charts.txt` at 17:22 on 2026-09-10 ran in the inconsistent hour.

What it would take: in `dateToCalendar`, use a UTC calendar and `clear()` it
before `set`, so a date always means UTC midnight and `timeToDay` is exact.
`dateToTime`'s only caller outside `CalendarUtils` is the dead
`colorado/Event.java`, so nothing live depends on the time of day being kept.
Then a daytime run and an evening run should draw the same charts, which is
the validation. Not fixed in the pass that found it because that pass was a
read-only archaeology review of `colorado/Event.java`, and this fix changes
what every chart computes, so it needs a run.
