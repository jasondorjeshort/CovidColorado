**A plant's smoothed lines and its peak labels are in the plant's own units,
not a percentage of the pandemic peak.** `sewage/Abstract.java`, the branch of
makeTimeSeries for more than one day averaged, from `2b193d5`, and the peak
label in getMarkers, from `11ee26d`.

makeTimeSeries multiplies each reading by getNormalizer() when daysAveraged is
1 and not when it is more. getMarkers labels a peak with the entry's
getSewage(), never normalized. An aggregate's normalizer is 1, so its own lines
and labels are right. A plant's is its fitted normalizer, which differs
between plants by orders of magnitude (the comment on
normalize() in `sewage/All.java` puts the flow-population column alone at
100x between its 5th and 95th percentiles). So:

- each plant's four smoothed charts (7, 14, 28 and 365 days, from
  `charts/ChartSewage.java` createSewage) draw its line in its own units,
  beside its red fit line, which makeFitSeries does normalize, on an axis
  labelled a percentage of the pandemic peak;
- each county's four smoothed charts draw its children, which are its plants
  (`nwss/Nwss.java` read()), in their own units against the county's
  normalized line; states, regions and the nation have aggregates as children
  and are not affected;
- every plant's six charts label each peak with its raw value.

Seen in the charts of the run of 2026-09-10. Plant 13,
CDC_BIOBOT_ak_13_wwtp_raw wastewater, has "0.01 normalizer" in its legend: its
daily chart runs from about 2 to 60, its 7-day chart from about 40 to 1,000,
with the fit line well below the smoothed line, and its one peak is labelled
"3/20/2023 956.9" on both, where the daily point is near 13. On Adams county,
Colorado, plant 2364 (normalizer 0.11) and plant 200 (0.38) run with the county
on the daily chart and well above it on the 7-day chart, while plant 195
(1.06) stays with it.

What it would take: multiply the window sum by getNormalizer(), and label the
peak with getNormalized(inflection.day). The same branch has a second defect,
`docs/active/findings/2026-09-10-a-smoothed-line-counts-a-day-without-a-reading-as-zero.md`,
and the two want fixing together. A run, looking at a plant whose normalizer
is far from 1 and at a county with several plants, shows both.

Not fixed in the review of `sewage/Abstract.java` that found it: it changes
what every plant chart and every county's smoothed charts draw, and only a
run can show it.
