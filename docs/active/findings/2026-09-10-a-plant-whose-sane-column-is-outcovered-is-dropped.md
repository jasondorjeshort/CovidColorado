**A plant whose only believable column is out-covered by unbelievable ones is
dropped as having no usable values.** `nwss/Nwss.java`, PlantRows choose(),
from `109170f`.

choose() sets its 90% coverage bar from the best-covered of the three columns,
and only then asks each column whether it passes isSane. A column that fails
isSane still sets the bar. So when the flow-population and raw columns report
on more days and both span more than 10,000x, a sane microbial column under 90%
of their coverage is refused as well: choose() returns null, and the plant is
counted among those "with no usable values" and appears in no chart and no
aggregate. `109170f` says the insane columns are rejected, and
`docs/reference/wastewater.txt`, under CHOOSING ONE COLUMN PER PLANT, says a
plant is skipped when it has no usable column at all. A rejected column taking
a usable one down with it is neither, so the doc is left saying what was meant.

How much, in the CDC download of 2026-09-10: a throwaway probe replayed
readSewage's plant key, value parsing and per-day averaging, choose() and
isSane over the cached CSV, with days keyed by the date string. 9 of 2,534
plants were skipped, 7 of them with a column that passes isSane. The five it
printed all had flow-population and raw on the same number of days, both
failing isSane, and a sane microbial column on fewer, for example
`STATE_TERRITORY_ny_1284_wwtp_raw wastewater` at 500/362/500 days and
`STATE_TERRITORY_mt_1149_wwtp_raw wastewater` at 229/5/229. Taking the bar
from the sane columns only changed no other plant's choice.

What it would take: compute `most` over the columns that pass isSane. The 7
plants would then take their sane column, join the baseline and their
aggregates, and get charts. Some are thin: mt_1149's column is five days, which
isSane passes because it is under the 10-value floor, so whether a column that
short should count is part of the same decision. Not fixed in the review of
`nwss/Nwss.java` that found it: it adds plants to the baseline, which moves
every normalizer a little, and only a run shows it.
