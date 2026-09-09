---
name: resolve-all-findings
description: Work off docs/active/findings/ oldest-first, one reviewed pass at a time, until the directory is at rest. Each pass resolves one entry in a fresh agent, has the invoking conversation judge the diff, and commits and pushes what it approves. Use when asked to drain the findings queue, resolve all findings, or work through the findings unattended.
---

# Resolve All Findings

Drain `docs/active/findings/` by repeatedly running the
`docs/skills/resolve-finding/SKILL.md` procedure with a review between each
pass and the next. This card is a loop controller in the way
`docs/skills/push-all/SKILL.md` is: `resolve-finding` remains the authority for
the pass that resolves an entry, `docs/skills/commit/SKILL.md` for the commit,
and `docs/skills/push-all/SKILL.md` for the push. What this card adds is the
split into two roles, the judgement between them, and the authority to commit
and push what the judgement approves.

The queue is oldest-first. The file names carry the date they were recorded,
so a plain listing is the order; `docs/skills/review-working-tree/SKILL.md`,
under Recording what is not fixed now, is why the names are shaped that way.
The README stays and is never a candidate.

## Two roles

The **judge** is the conversation that invoked this card. It picks the entry,
briefs the resolver, reads the diff the resolver produces, and decides. It
does not write the fix.

The **resolver** is a fresh agent with its own context, started for one pass
and briefed for one entry. It runs `resolve-finding` on that entry and stops
where that card stops, with the fix and the deleted entry in the working tree
and nothing committed.

The split exists for the review. A commit's staged-diff review is a
self-review when the same context wrote the change, and a self-review cannot
see what it took for granted while writing. A reader who starts from the
diff sees it. That is worth more here than in a repo with tests, because
there is no test suite standing behind either role: the judge's reading is
most of what stands between a wrong fix and origin. So the resolver is a fresh
agent and not a fork of the judge — a fork carries the judge's reasoning in
with it, which is the independence the review depends on. This card runs in the
invoking conversation as every pointer does, and starts one fresh agent per
pass.

The judge runs on the strongest model available, because judging is the gate
and its errors are the ones that ship. The resolver's model is picked per
entry: a docs-only or mechanical entry needs less than one that touches the
normalization, the lineage arithmetic or a chart's axes, and when it is unclear
which kind an entry is, the stronger choice costs less than a wrong fix. In
Claude Code the pointer's `model:` line sets the judge; the resolver's model is
named when it is spawned. Where the runtime has no way to start a fresh agent,
this card cannot run as written; do the three cards by hand, one entry at a
time, rather than running both roles in one context under this card's
authority.

## Loop

- Read the three cards this one controls before starting. Note
  `git status --short` and `git rev-parse HEAD`: the tree may carry unrelated
  work, and this card's commits must not sweep it in.
- Pick the oldest entry not deferred in this run. If there is none, stop;
  that is the end condition, not a failure.
- Read the entry. Where it poses a choice between resolutions, decide it now
  and say so in the brief: an unattended run cannot wait on an answer, and
  the choice is the judge's unless the entry says it is the maintainer's. An
  entry that says that is deferred without being touched — it already asks
  the question, and rewriting it to ask the same thing is churn.
- Brief the resolver with the entry's path, the choice if one was made, and
  the report it must return: what the entry claimed, whether it still held,
  the design of the fix, every file touched, what validation was run and what
  it said, and `git status --short`. Tell it to read CLAUDE.md and the card,
  and that it does not commit.
- Judge from the diff, not from the report. Read the whole diff yourself.
  Then, in order:
  - The scope is the entry and nothing else, and every path touched was clean
    when the run started. A path that was dirty at the start and is now part
    of the fix is a stop for this entry: the fix would land mingled with
    foreign work.
  - The claim the fix rests on is true of the code — read the site, not the
    resolver's description of it. A resolver that concluded working-as-
    intended left the reason at the site, per `resolve-finding`.
  - The same-change obligations are met: the owning `docs/` file, Javadoc or
    comments on touched contracts, and any other file in `docs/` that named the
    entry and now describes it as open. Grep the entry's slug across `docs/`
    for that last one; a status list in `docs/active/` is the one to miss.
  - The change does not quietly move what the program expects to find outside
    the checkout — the chart output folder, the caches under the system temp
    directory, the `pango-designation` checkout. When it does, the message
    says so exactly, per `docs/skills/commit/SKILL.md` under Before staging,
    because nothing in the tree will tell the next run.
  - Validation was actually run, and was the right one. `./gradlew compileJava
    -q` proves the change is well-formed Java and nothing else; a fix inside
    data ingestion or chart building needs `./gradlew run` and a look at what
    it printed. A report that claims a run without quoting anything from it
    gets that one correction before the commit.
- A fix that is right, or right after one round of correction that the judge
  can state exactly, is approved: the resolver applies the correction if
  there was one, runs `commit` staging only the paths the pass touched, then
  runs `push-all`. One round is the bound. A second round means the judge is
  designing the fix, and a fix the judge designed is one the judge cannot
  review.
- Before `push-all`, and again before each push it makes, check that nobody
  else has pushed to origin in the last fifteen minutes: `git fetch origin`,
  then `git log origin/master --since="15 minutes ago"` read with each
  commit's session trailer. A commit there that this run did not push
  itself means another session is working through a queue of its own, and a
  commit landed between two of its pushes is one it has to rebase over and
  re-review in the middle of a pass. So skip the push for this pass — do not
  sleep, poll, or otherwise wait for the window to clear — and go straight to
  the next pass. The commit stays local, and the tree is clean, so nothing is
  lost: the next pass's `push-all` re-runs this check and, once the newest
  foreign commit is fifteen minutes old, pushes everything queued in order
  through `push-one`'s own pre-push re-check as written. Unpushed commits
  accumulating this way are not a blocker; a run that ends with some still
  local reports them, and the next run's first `push-all` takes them.
- A fix that is wrong past one round, or an entry the resolver reports as
  larger than a pass, or one that cannot be confirmed without evidence nobody
  has, is deferred. Discard the resolver's changes first — restore the paths
  it reported touching, and the entry's file, which were clean at the start —
  so nothing half-made stays in the tree. `resolve-finding` deletes the entry
  with `git rm`, so restoring that one takes the index and not only the working
  tree. Then, when the pass learned something the entry does not say, rewrite
  the entry in place to what is now known and what it waits on, keeping its
  name and date; commit that file alone and push it. When nothing was learned,
  touch nothing. Either way the entry is skipped for the rest of this run and
  the loop moves to the next.
- A `push-all` stop that is anything but "nothing waiting" or the busy-origin
  skip above ends the run. Report it as the stop condition rather than
  starting the next pass on top of an unpushed commit; an unattended run must
  not build a queue behind a blocker. A busy origin is not a blocker — the
  queue behind it drains on its own once the window passes — which is why it
  is the one stop the loop continues through.

Every pass starts from the top. The tree state, `HEAD`, and the listing are
re-read each time, because another session may have pushed, recorded an
entry, or resolved one while the last pass ran.

## Stop and report

Report the entries resolved, in order, each with the commit hash and subject
its fix landed in. Then the entries deferred, each with why and whether its
file was rewritten. Then any commits still local because origin was busy at
every push attempt, oldest first, so whoever reads the report knows a push is
owed. Then the stop condition: the directory at rest, every remaining entry
deferred, or a `push-all` blocker with the candidate it names.

## Authority

This card commits and pushes: each approved fix through `commit` and
`push-all`, and a deferred entry's rewrite the same way. CLAUDE.md, under Git
and commits, lists this among the cards that carry commit authority. It covers
nothing wider — not the unrelated work the tree carried at the start, and not
a fix the judge would have to write itself.
