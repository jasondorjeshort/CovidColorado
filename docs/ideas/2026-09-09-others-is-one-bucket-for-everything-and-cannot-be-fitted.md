**A per-strain or per-root "others" would give the leftovers a line worth
drawing.** `variants/VocSewage.java` `build()`, where a lineage with no parent
is folded into `others` -- the TODO on that branch says the same thing.

"others" is the one variant that can never be merged away: it carries no
lineage, so `findLineageToRemove` filters it out of the candidate list before
it is ever judged. Everything with no parent ends up there -- rare recombinant
roots dropped by `variants/Lapis.java`, plus whatever the child subtraction
leaves over -- and the result is one line mixing unrelated lineages, whose fit
is meaningless where it exists at all and whose legend entry ends up without
the growth figure every other entry carries.

Splitting it per strain, or per recombinant root, would make each bucket a
thing that can trend: a fit over "the XFG-descended leftovers" means something,
a fit over "everything unclassifiable" does not. It needs a decision about
which split, and `variants/Strain.java` would have to be current for the
per-strain version to be worth anything.

Not a defect. A single "others" is the correct arithmetic -- the shares still
sum to one -- and the chart is not showing anything false. It is just showing
one line where several would carry information.
