**The new CDC intake silently reroutes two jurisdictions: the Virgin Islands
land in "Other" and New York City folds into New York state.**
`src/main/resources/regions.csv` against `nwss/StateNames.java`, in the tree as
of the migration to dataset j9g8-acpt.

`StateNames` maps all 53 codes the dataset actually carries, so nothing is
dropped on the way in. The mismatch is one step later, in `nwss/Regions.java`,
which looks the full state name up in `regions.csv`:

`vi` maps to "U.S. Virgin Islands" and `regions.csv` has no row of that name,
so every Virgin Islands plant falls to the "Other" region. The national totals
are unaffected -- "Other" is a real region and is charted -- but a reader of the
regional charts will not guess that is where those plants went. `StateNames`
also maps `as`, `mp` and `pr`, which have no rows either. None of the three is
in the 2026-09-10 download, but any that appears will land in "Other" the same
way, so they belong to the same decision.

The "New York City,Northeast" row is now unreachable from the other direction:
the dataset has no `nyc` code, so the `{"nyc", "New York City"}` entry in
`StateNames` never fires and NYC plants arrive as `ny`. They are folded into New
York state, which is arguably right for the new data and is certainly a change
from the old datasets, where NYC reported separately.

Neither crashes and neither loses a plant. Not fixed now because what to do is
the maintainer's call about how the regions should read: add a Virgin Islands
row (Overseas, where Guam already is?), or accept the fallback and drop the
dead NYC entries so the pair of files stops implying a split that no longer
exists.
