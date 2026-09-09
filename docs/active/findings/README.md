# Review findings not yet fixed

One file per finding: something real a review turned up and did not fix. Not
a backlog of ideas: an entry names a defect that is in the code now, whether
traced to a line by a review or only observed from outside by the maintainer —
a normalized curve that is visibly wrong, a lineage share that does not add
up — not something that would be good to build. An idea does not earn a file
here; it earns one under `docs/ideas/`, whose README owns that format. The two
are kept apart because only one of them is a queue: an entry here names a
defect and is meant to leave, and `docs/skills/resolve-all-findings/SKILL.md`
drains them oldest-first, while nothing drains the ideas directory and nobody
has agreed to build what is in it. Mixing them would put work nobody committed
to in front of the loop that closes bugs.
`docs/skills/review-working-tree/SKILL.md`, under Recording what is not fixed
now, owns the file format, the naming rule, and what earns a file here.

That card also owns how a finding is resolved and when. The part worth knowing
without looking it up: a resolved finding is deleted, never ticked, and this
README stays behind when the last one goes, so an otherwise empty directory is
the resting state and not a stalled project.
`docs/skills/resolve-finding/SKILL.md` is the pass that takes one file from
here and closes it.

Files are named `<yyyy-mm-dd>-<slug>.md`, the date being when the finding was
recorded, so a plain listing shows how long each has waited.
