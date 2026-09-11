**A plant with a blank counties_served becomes a county with no name.**
`nwss/Nwss.java` read(), where each plant's counties_served is split into
counties, from `6abdab2`; the blank value arrived with the j9g8-acpt intake in
`109170f`.

read() splits the plant's counties_served on commas and makes a
`sewage/County.java` for every piece, with no check on what the piece is. The
empty string splits to one empty piece, so a blank column makes a County named
"" in the plant's state. It gets a full chart set, files named `-recent.png`,
`-all.png` and so on, directly in that state's directory under the counties
folder, titled " county, Colorado (53,356 line pop)". It is also a display child
of its State, though at that population it is not among the five the state
chart draws. `sewage/Plant.java` getTitleLine formats the same blank into the
plant's own title, which reads "Plant 197 -  county, Colorado".

How much, in the CDC download of 2026-09-10: replaying Nwss's most-recent-row
metadata choice over all 2,534 plants, exactly one yields a blank piece,
STATE_TERRITORY_co_197_wwtp_raw wastewater. Its counties_served is blank on all
390 of its rows, 2020-08-03 to 2024-07-29, while its county_fips is 08014,
which is Broomfield; no other plant names Broomfield, so the county has no
chart under its real name. The archived metric dataset has no row with FIPS
08014, so the plant is new with the migration. No name in the download has
leading or trailing whitespace, and none contains a character a Windows file
name refuses, so the blank is the only malformed piece. The run of 2026-09-10
wrote the six files and the title quoted above.

The fix is in read(): either skip empty pieces, which drops the plant from
every county and must also stop counting them in the 1 / countyNames.length
population split, or fall back to the county_fips column, which needs a FIPS
table the live path does not load (`sewage/Fips.java` is one, and unused). The
class Javadoc on `sewage/County.java` says a blank column makes an empty-named
county, so it changes with the fix.

Not fixed in the review of County that found it: the fix lives in Nwss, not
County, it changes which charts are written, and whether the plant should be
dropped from the county level or named from its FIPS is the maintainer's call.
