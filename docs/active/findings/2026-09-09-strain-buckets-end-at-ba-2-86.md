**The strain buckets stop at BA.2.86, so the per-strain charts misfile 2026
lineages three different ways.** `variants/Strain.java`, the enum constants and
`findStrain(String)`, in the tree as of the LAPIS work.

The buckets are hand-written, and a lineage goes to the most specific one whose
listed ancestor it descends from. With what the LAPIS pull returns today that
sorts into three failures, visible on the national relative-fit-strain chart as
exactly three lines:

- XFG and XFJ, with their aliases SW and RW, are recombinant roots no bucket
  lists. `findStrain` prints its "Unknown strain" stack trace -- around 140 a
  run, one per distinct lineage since the answer is cached in `backwardsMap` --
  and returns OTHERS. They are most of today's sequences, so OTHERS runs at 60
  to 90 percent.
- BA.3.2's descendants (RD, RE, RS, RT, RU and the S* aliases under them) are
  labelled "BA.1". `BA_1` lists `ba.3` beside `ba.1`, which in 2022 grouped a
  minor BA.3 with its sibling. BA.3.2 is now a clade of its own and charts
  under a name that died out in 2022.
- JN.1's descendants (LF, RF) and XDV's (PQ, NB, SV) are both in `BA_2_86`,
  which lists `ba.2.86` and `xdv`. That ancestry is correct, but it is one line
  for two clades that are competing with each other.

The fix is to add current buckets and drop `ba.3` from `BA_1`, which is a
judgement about which lineages deserve to be named strains rather than a
mechanical change, and the same judgement will be needed again next year. Not
fixed here because deciding the buckets is the maintainer's call. Whoever takes
it should consider whether the list can be derived instead: a strain is a
lineage with enough share and enough descendants, and that is computable from
the same data the merge loop in `variants/VocSewage.java` already walks.
