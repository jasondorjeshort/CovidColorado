**Inflection building prints thousands of "Uh oh" lines a run and buries real
errors.** `sewage/Abstract.java` `buildInflections`, the two `System.out`
calls at the top of the method, in the tree as of the LAPIS work.

`buildInflections` runs for every plant, county, state and region -- several
thousand series -- and each one that starts on a day with no normalized value
prints "Uh oh bumping day because null normalization." once per day skipped,
then "Uh oh skipping inflections because null normalization." if the second day
is missing too. A run produces roughly 2,200 of the first and 3,200 of the
second. Neither is an error: a sparse plant legitimately has gaps at the start
of its series, and skipping it is the correct handling.

The cost is that the console is the only output this program has, and the real
diagnostics -- negative prevalences, manufactured parents, fit overflows,
skipped plants -- are scattered through five thousand lines of noise. The fix
is to drop both messages, or to count them and print one summary line after the
build, which is what the intake path already does for skipped plants and
dropped spikes.

Not fixed here because this pass was doc-only. Whoever fixes it should check
the `while` loop those messages sit in: it has no upper bound and relies on the
series having some non-null day, which `build()` establishes but the method
itself does not.
