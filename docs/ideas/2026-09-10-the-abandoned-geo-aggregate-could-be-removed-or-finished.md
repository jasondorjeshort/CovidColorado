**The abandoned geographic aggregate could be taken out, or finished.**
`sewage/Geo.java`, wired into `nwss/Nwss.java` `read()` and `build()` by
`24e2bb1`.

Every run builds one `Geo` at fixed coordinates and passes every plant into it
through the inherited `sewage/Multi.java` `includeSewage`, which never looks at
the coordinates. It is then never drawn. The chart call in `build()` has been
commented out since `eaf182f`, and would not compile if restored, since
`charts/ChartSewage.java` `createSewage` has since grown a second argument.

The rest of the feature is just as idle:

- `sewage/Fips.java` is never constructed.
- `Plant.getFipsIds()` and `Plant.setLatLon()` have no callers.
- `Geo.readCsv` has none either.
- The `instanceof sewage.Geo` test in `ChartSewage` can never be true.
- `ChartSewage.mkdirs()` still makes an `LL` folder under the chart output,
  which stays empty.

Taking it out means deleting those pieces together. Finishing it needs
coordinates for plants, which the reader takes from nothing in the CDC
dataset; `Fips` looks like the intended route, by county.

Not a defect. The aggregate costs one inclusion pass on top of the two per
round of the baseline loop, and reaches no chart, log line or other
aggregate, so the output is the same either way. Whether the feature is
worth keeping is the maintainer's call.
