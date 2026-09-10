**Every 2026-era lineage is an "Unknown strain" and lands in OTHERS, so the
strain charts are wrong.** `variants/Strain.java`, the enum constants and
`findStrain(String)`, in the tree as of the LAPIS work.

The buckets are hand-written and stop at BA.2.86, whose entry lists the
recombinants known when it was written (xdd, xdk, xdp and so on). Nothing
covers XFG, PQ, LF or the rest of what the LAPIS pull now returns: those are
recombinant roots in the alias key, so `Aliases.isAncestorInclusive` matches
none of the listed ancestors, `findStrain` throws its "Unknown strain" stack
trace and returns OTHERS. Around 141 of those print per run -- one per distinct
lineage, since the result is cached in `backwardsMap` -- and every per-strain
chart then shows today's variants as a single undifferentiated OTHERS line.

The fix is to add current buckets, which is a judgement about which lineages
deserve to be named strains rather than a mechanical change, and the same
judgement will be needed again next year. Not fixed here because this pass was
writing the reference docs, and because deciding the buckets is the
maintainer's call. Whoever takes it should consider whether the list can be
derived instead: a strain is a lineage with enough share and enough
descendants, and that is computable from the same data the merge loop in
`variants/VocSewage.java` already walks.
