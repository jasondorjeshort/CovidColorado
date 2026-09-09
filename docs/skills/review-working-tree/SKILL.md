---
name: review-working-tree
description: Review the current CovidColorado working tree. Use when asked to review changes, inspect uncommitted work, look for blockers, or sanity-check a diff before moving forward. Owns the findings format the other review cards defer to.
---

# Review Working Tree

A pass over uncommitted work. Distinct from a generic code review in two ways
that matter here: the working tree routinely carries several unrelated changes
at once, and this repo has same-change obligations — the owning `docs/` file,
Javadoc, the comment that explains a constant — that a review of the code alone
will not catch.

## Read the change

- `git status --short` first, and treat it as the definition of the change set.
- `git diff HEAD` for tracked files. Plain `git diff` is not enough: it misses
  anything already staged.
- Read untracked files (`??`) directly. They will not appear in any diff, and a
  new-file change is often the whole substance of the work.
- Separate the change under review from unrelated work in the tree, and say
  which is which rather than reviewing them as one thing.
- `docs/active/` for context on work in flight; `docs/README.txt` when the diff
  touches a subsystem you do not know.

## What to look for

Bugs and behavioral regressions first, then the repo obligations that are easy
to miss:

- A behavior, invariant or entry point changed without its owning `docs/` file
  updated.
- A touched method whose Javadoc no longer describes it; a new class with none.
- Comments and doc claims the diff does not actually support, leftover debug
  code, half-renamed identifiers. In this repo, a commented-out line or a
  `System.exit` left behind from narrowing a run down to one chart.
- Whether the change is checkable at all. There is no test suite here, so a
  diff that changes what the program computes is confirmed by a run and by
  nothing else. Say which of the two a change got.
- Whether the code the diff touches is live. The `colorado/` package is dead —
  nothing on the `nwss.Nwss` path reaches it — so an edit there is either
  deliberate archaeology or a mistake about which half of the program is
  running, and the review should say which it read it as.

## Validate

- `./gradlew compileJava -q` is cheap and always relevant. It is also the whole
  automated gate: no tests, no formatter check, no linter.
- `./gradlew run` is the real check for anything touching data ingestion or
  chart building, at about four minutes and a network fetch. A review that
  wants it and does not have it says so instead of implying the compile
  covered it.
- For a doc-only change, `scripts/check-doc-paths.ps1` is the whole check.

## Reporting

Lead with findings, ordered by severity, each anchored to a file and line. Do
not open with a summary of the change. Verify before reporting, and retract
what does not survive — `docs/skills/commit/SKILL.md`, under Review the staged
diff, states that methodology once for all the review cards. If there are no
findings, say so directly, and name the residual risk.

Only edit during a review if the user asked for fixes, or the fix is small,
obvious, and inside the change being reviewed.

## Recording what is not fixed now

A finding that is real but not being fixed in this pass goes to
`docs/active/findings/` rather than into a chat message that will be gone by
tomorrow. This card owns the format; the other cards point here.

Record a finding when this pass is not going to fix it — which is what the top
of this section already says, and no sharper phrasing is worth losing it for.
Out of scope, pre-existing, ancient, or larger than you are taking on today:
those are not separate criteria, they are reasons a pass declines a fix, and
not one of them is a reason to drop the finding instead.

What decides is whether the fix happens, not which commit carries it. Fold in
and Split both fix the thing before the pass ends — a Split only moves it to
the next commit — so neither records, and an entry you write and delete
yourself inside one pass is churn rather than a record. A stale comment costs
less to correct than to describe, so correct it. Handing the call to the user
is not a third way of fixing it. A finding whose resolution is a judgement only
they can make ends the pass then and there, because the pass cannot proceed
without them, so it records and the entry names the choice. If the answer comes
straight back, the next pass deletes the entry; if it never comes, the finding
outlived the session that found it, which is the whole reason for writing it
down. Note stays for the trivial, what would not survive being read next month.
When it is a close call, record it: an entry that proves unnecessary costs one
file and is deleted by the commit that makes it so.

Alongside Note there is one more disposition, and it is not a finding at all: an
improvement the code is not wrong for lacking goes to `docs/ideas/`, whose
README owns that format and the rule that an idea with no concrete anchor earns
no file. The test is whether the code is wrong today — if it is, it is a finding
however small, and if it is not, the suggestion is an idea however good. Neither
is a place to put the other, and a pass that noticed something worth building
has somewhere to leave it rather than dropping it.

One file per finding, `docs/active/findings/<yyyy-mm-dd>-<slug>.md`, the date
being the day it was recorded and the slug a few words from the claim. The
body is the entry and nothing else — no heading, no frontmatter, no checkbox,
since an entry is never ticked, only deleted:

  **Short claim.** `<file>`, and where in it, from `<commit>`.
  What is wrong and what it would take. Why it was not fixed then.

The anchor is what a review can supply. A symptom the maintainer files by
hand — a plant's normalized curve is wrong, a lineage share that should sum to
one does not — carries the claim and the body and no site, and that is a valid
entry: tracing it is the first step of the pass that resolves it, not a
precondition for filing. A reviewer who meets one shaped wrongly, with the
wrong extension or no bold claim, fixes the shape in place and moves on. A
finding against an entry's own format is never worth a file.

One file each is what lets two sessions record on the same day without
touching the same lines: a shared list conflicts on every concurrent append,
and that conflict is the whole cost of a single-file shape. The date prefix is
what keeps a plain listing in age order, so how long an entry has waited is
visible without opening it. Before writing, skim the listing for a sibling
naming the same site: with no shared file there is no merge to surface a
duplicate, so a second review of the same commit produces one silently unless
the writer looks.

A section name beats a line number wherever the line would rot first, which is
most of the time — an entry is meant to outlive the reflow that moves it. An
entry recorded alongside uncommitted work has no commit to name yet: say what
the change was instead. Watch the hash when the pass amends: the commit you
read stops existing, so name the one the amend produced, not the one the
finding was found in.

When the entry is committed depends on what was under review. A finding on a
commit already made is committed immediately — folded into the amend when that
commit is being amended anyway, and otherwise its own commit, because there is
no work in flight for it to ride along with; that is the Record disposition in
`docs/skills/push-one/SKILL.md`, which states the bound on it. A finding from a
working-tree review just joins the tree it was found in and lands with that
work: the review was part of the work, so the entry belongs to the same commit
rather than being unrelated modification held out of it. A Record from a
working-tree review needs no special authority and grants none: writing the
entry is the whole disposition, and the commit it eventually rides in is one
the user was going to ask for anyway.

Resolving an entry deletes its file, in the same commit as the fix: the entry
documents an open problem, so the commit that closes the problem deletes the
documentation of it. That is the same-change rule the repo already runs on for
docs and Javadoc. An entry only partly addressed is rewritten in place in that
same commit instead, so what it claims stays true; it keeps its name and date,
since those record when the finding was first seen, not when it was last
edited.
`docs/skills/resolve-finding/SKILL.md` is the pass that does this, one entry
at a time and without committing: the fix and the deletion are left in the tree
for the commit that carries both.

The one exception is a `docs/skills/push-one/SKILL.md` Record commit. It marks
an unpushed candidate that was not pushed, and the later repair may need to
rewrite a candidate whose tree does not contain the entry's file. In that
repair, the entry is resolved by dropping the unpushed Record commit that
added it, rather than by creating a separate deletion commit at the queue
tip. If the candidate is pushed unfixed instead, that repair never happens:
the marker commit stays, and the entry is regraded in place to name where the
correction can still land, since the message is no longer amendable.

Resolution is a fix, or the finding graduating to where it belongs — a code
comment, a Javadoc line, a `docs/` sentence. Often it is the comment and
nothing else: a great many findings turn out to be working as intended, or
constrained by something invisible from where the reader is standing, and the
entry is resolved by writing that down at the site where the next person will
hit it. That is a real resolution and not a dodge — the knowledge lands
somewhere durable and the entry stops being the only copy.

A third shape leaves nothing behind: an entry the code has moved out from under
is deleted and only deleted, because the site it named is gone or no longer does
what the entry described. That is not a lesser close than the comment, and the
two are not interchangeable — `docs/skills/resolve-finding/SKILL.md`, under
Check it still holds, owns the test. Writing a sentence anyway, so the pass has
something to show for itself, leaves a durable note about a problem nobody has.

Whichever shape it takes, a resolved entry never stays in the directory as a
record that it happened. `git log` is that record.

The directory itself is permanent, and that is where it parts from the
`active/` contract: `docs/active/findings/README.md` stays when the last entry
goes, so git keeps the directory and a reader finds it in its resting state
rather than absent. A directory holding only its README says outright that
nothing is recorded, so it is the resting state and not a stalled project, and
the Started date and staleness rule the other files under `docs/active/` carry
do not apply to it. Entries may sit there a long time — a finding nobody has
reached is still worth having written down, and that is the point of writing it
down. What the directory must not become is somewhere things are filed to stop
thinking about them: every entry is something a review judged real and worth
returning to.
