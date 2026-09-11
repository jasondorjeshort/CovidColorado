**A pinned lineage with fewer than MIN_SEQUENCES sequences is folded into its
parent by Lapis before the pin can protect it.** `variants/Lapis.java`,
`create()`, the MIN_SEQUENCES fold and the `pins()` call after it, from
`087b72c`, which added the fold after `a850f73` had added the pins.

A pin exists to keep a lineage on the chart "no matter how small it is", as
`variants/VocSewage.java` puts it where the floor skips pinned lineages, and
`variants/LEnum.java` and docs/reference/lineages.txt describe pins the same
way. But VocSewage only ever sees what Lapis hands it, and Lapis's own fold,
which runs before `pins()` is even called, tests nothing but the sequence
count. A pinned lineage with fewer than twenty sequences of its own over the
fetched days has those sequences moved into its parent; it then reaches the
Voc either not at all or, if some descendant kept its own sequences, as an
ancestor whose residual after Voc build()'s subtraction is near zero. The pin
protects an empty line. This bites exactly when pins matter: a list added for
a wave in progress names lineages that are new, and with a few hundred open
US sequences a month since April 2026 a new lineage can sit under twenty for
weeks.

Latent today. Neither LEnum list starts inside the emitted window, so the
pinned set is empty; a replay of `Lapis.create()` against a copy of the
2026-09-10 LAPIS cache printed "0 pinned". Found by reading the order of the
two steps in `create()`.

What it would take: compute the pins before the fold and skip a pinned
lineage there (it would still lose days to MIN_WINDOW_SEQUENCES, which is
about the day's total and not the lineage). Not fixed in the review of
`variants/Lapis.java` that found it, because with nothing pinned no run can
show the change, and whether a pin should also override the fold is the
maintainer's call.
