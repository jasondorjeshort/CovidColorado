---
name: review-oldest-java
description: Select CovidColorado's least-recently-touched tracked Java file and give it a focused maintenance review, including useful Javadoc and comments. Use for spare-time codebase review or when asked to review the oldest Java file. Takes an optional path to review that file instead of the queue head.
---

# Review Oldest Java

Use repository age as a queue for one focused maintenance pass. The goal is to
bring an overlooked file up to current standards, not to reward age with
comments that merely restate the code.

## Select the file

Run from the repository root:

```powershell
& docs/skills/review-oldest-java/scripts/find-oldest-java.ps1
```

The first row is the target. The script considers Java paths tracked in Git's
index and defines a path's last touch as the commit with the greatest committer
timestamp that names its current path. A rename therefore counts as a touch.
The oldest last touch wins; path order breaks timestamp ties. A tracked file
with no commit yet is reported separately and is not a candidate.

A path named in `docs/skills/review-oldest-java/reviewed.txt`, the review
ledger, leaves the queue permanently. This is one pass over every file, not a
rotation: a file that has had its pass is done, and no later commit to it puts
it back. So the ledger is an exclusion list, and the queue is whatever is left
in Git-touch order, oldest first.

Nothing compares the ledger date against anything, and that is deliberate.
Ordering on the later of "was committed" and "was reviewed" is what a repeating
queue would need, and it costs more than it buys: the date is a day and Git's
timestamp is an instant, so a pass committed after 18:00 in a UTC-6 zone lands
in the next UTC day and its own commit voids the line it had just written. The
date on each line is history, not a key.

Dead ledger lines — an untracked path, or a second line for a path already
recorded — are reported as sweepable and change nothing. A path is untracked
once its file is deleted or renamed, which is the only way an entry stops
meaning anything.

When the last unreviewed file is gone the selector says so and returns no rows.
That is the end of this pass, not a signal to start again. A different kind of
review later wants its own card and its own ledger file rather than a second
lap through this one.

After a handful of old helpers under `library/`, the queue runs into a large
block of `colorado/` files that all share one commit date and are therefore
ordered by path alone. That package is the program's first life — Colorado case
data by infection date — and nothing on the live path reaches it, so it is both
the oldest code in the tree and the least worth improving. Review such a file as
archaeology: read it, say what it was for and that it is unreachable, and write
the ledger line. Do not modernize dead code, and do not delete the package on
this card's authority — that is a decision the maintainer makes once, not
something a maintenance pass arrives at one file at a time. If a pass turns up a
reason the package should go, that is an entry under `docs/ideas/`, not an edit.
`colorado/CovidColorado.java` sits in that block and is the exception: it is the
program's `main`, so it gets a real review.

A path given as an argument replaces the selector's choice, so
`/review-oldest-java src/main/java/sewage/Abstract.java` reviews that file and
not the queue head. Age is a proxy for "nobody has looked at this lately", which
is a good proxy and a blind one: it cannot see that a file matters. The argument
is how a person supplies what age cannot, and with a dead package occupying much
of the queue it is how most useful passes here will start. Nothing after
selection changes — same bound, same validation, same ledger line, same
commit — so a named pass costs the queue exactly the one file it names.

Three arguments are refused rather than obeyed. A path that is not a tracked
Java file is a typo, not an instruction. A path already in `reviewed.txt` has
had its pass, and this card does not do second ones; say so and stop rather
than doing the work twice under a ledger that cannot record it. A dirty path
is the conflict described just below, reported the same way.

Do not name a file that is under active work, which is the one case where
jumping the queue is worse than leaving it alone. The ledger is an exclusion
list, so a pass does not review that file early, it retires it permanently —
at the moment it is changing most and a review of it is worth least. Age order
already defers whatever every commit touches, and that deferral is the
behaviour to leave working. The argument is for the opposite kind of file: one
that is quiet, live, and more load-bearing than its quietness suggests.

The selector's Status column shows whether the target carries staged or
unstaged work; check `git status --short -- <path>` again before editing if
time has passed. A dirty target is a blocker: do not overwrite it, and do not
quietly choose a different definition of "oldest." Report the conflict, name
the next clean row so a person can pass it as the argument, and stop — the
same report-and-stop every other blocker in this card gets, because a question
left waiting is a stall on an unattended run.

Record the selected path, full commit hash, date, and subject in the handoff so
the queue decision is reproducible. A named pass records the path and that it
was named, which is the whole of its selection reasoning.

Capture `git rev-parse HEAD` before reading the target. Before staging, compare
it with the current `HEAD`. If it changed, re-read the selected file and this
pass's diff against the new base; rerun the selector if an intervening commit
touched the candidate. Do not stage a diff that disappeared or changed under
concurrent work without first confirming what the new `HEAD` already resolved.

The queue advances the moment this pass's ledger line is written, whether or
not anything else changed: the selector reads `reviewed.txt` from the working
tree, not from `HEAD`. So the line is written last, after validation, and a
pass that dies between writing it and committing has retired a file with no
commit to say why. The selector warns when the ledger is dirty for exactly
that reason; treat the warning as the previous pass's unfinished commit to
complete, not as a queue to skip past. Never edit code merely to advance the
queue: recording an honest no-op is the supported way past a file that needs
nothing.

## Review the file

Read `CLAUDE.md`, list `docs/active/`, and use `docs/README.txt` to open only the
one or two documents that own the selected subsystem. Read the whole selected
file, its history, and its principal callers or implementations. For a small
interface or facade, the contract may live mostly in those related types;
review enough of them to document the selected file truthfully. Where the file
leans on a library's contract — the order a CSV parser hands back records, what
a null return means, which exception says what — read the library's source
rather than recalling it; Gradle's cache keeps the sources jar beside the
binary.

Look for correctness problems and stale contracts before documentation gaps.
Apply small, well-supported fixes that stay within the selected file and its
direct documentation. Put a real issue that is too large or uncertain for this
pass in its own file under `docs/active/findings/`, following
`docs/skills/review-working-tree/SKILL.md`.

A doc that describes a behaviour the code was built to have and does not
deliver is not a stale doc. `CLAUDE.md`'s rule that the code wins is for a doc
the code moved out from under; a mechanism that is present in the code and
cannot fire — a counter nothing can increment, a branch the wiring excludes —
is a defect in the code, and the doc is the record of what it was for. Record
it and leave the doc saying what was meant, rather than editing the intent
down to match the accident.

A contract that is claimed and not pinned is a gap of the same kind, and here
it usually cannot be closed. There is no test suite in this repo, so nothing
can hold a claim still; what a pass can do is make the claim and its reasoning
visible at the site, in a Javadoc line or a comment that says what the caller
must guarantee and why. Where the claim looks wrong rather than merely
unpinned, that is a finding. Do not stand up a test harness from inside a
maintenance pass — adding one is its own decision and its own change.

Concluding that a file needs nothing is a supported result, not a failed pass.
Age selects the quiet corners of the tree, so some of what surfaces is quiet
because it is genuinely finished, and some — everything in `colorado/` — is
quiet because it is dead. Reach that verdict the same way as any other: read
the file, its history, its callers, and the docs that own it first. A no-op is
earned by that reading and defended in the report — say what you checked and
why nothing was warranted. An unexplained no-op and a review padded out to
justify itself are the same failure pointed in opposite directions.

Bring touched code up to the Javadoc and comment rules in `CLAUDE.md`:

- Give the class or interface Javadoc that explains its role and important
  contracts.
- Document non-obvious public, protected, and package-facing method behavior,
  inputs, outputs, failures, null handling, threading, ownership, or
  compatibility semantics.
- Use implementation comments for rationale, invariants, and traps that cannot
  be expressed clearly in names or Javadoc — and in this codebase especially
  for why a magic number is that number, since the constants that shape the
  charts are the part a reader cannot re-derive.
- Remove or correct stale comments. Do not narrate declarations, control flow,
  or other facts already obvious from the code.
- Do not write an absence you have not counted. A javadoc saying nothing else
  does this is doing work for every future reader, so it is worth the
  enumeration before it is worth the sentence.

Update owning design docs only when the pass changes or discovers a stale
documented behavior, invariant, or entry point. Do not expand the task into
documenting every related class merely because the selected type has many
implementations.

## Verify and hand off

Review `git diff HEAD -- <selected and directly related paths>` using the
criteria in `docs/skills/review-working-tree/SKILL.md`. While iterating,
`./gradlew compileJava -q` is the cheap check and the only automated one there
is; the commit itself goes through `docs/skills/commit/SKILL.md`, whose
validation section says when a change needs `./gradlew run` instead. A pass
that touched no Java — a no-op, or one whose only product is a recorded
finding — is a doc-only commit, and the commit card's carve-out for those
applies: `scripts/check-doc-paths.ps1` is the whole check. Run
`scripts/check-doc-paths.ps1` whenever path-like documentation changed.

Every pass ends by appending its line to `reviewed.txt` — the ISO date, a
space, and the reviewed path — including a pass that found nothing and a pass
whose only product is a recorded finding. The line records that the review
happened, which is the fact that takes the file out of the queue.

A pass that deletes its target is the exception and writes no line. Both cases
above leave the file in the tree, so only a ledger line retires them; a deleted
file leaves the queue by ceasing to exist, and a line naming a path Git no
longer tracks is reported as sweepable the moment it is written. The commit
that removed the file is the better record of the review in any case: it says
what was read and why the file went, and it cannot go stale.

Invoking this card is the ask to commit the pass. A pass ends committed and
the tree ends clean, every time: this queue is meant to run unattended, and a
pass parked in the working tree stops every pass behind it.

- If the pass found nothing to change, commit the ledger line on its own using
  `docs/skills/commit/SKILL.md`. Put the verdict in the commit message: what
  was read, and why it warranted no change. A no-op whose reasoning exists
  nowhere is indistinguishable later from a pass that skipped the work.
- Otherwise commit what the pass changed together with its ledger line, using
  the same card. Do not split the ledger line, or documentation paired with a
  change, into a commit of its own.
- What a pass may change is already bounded, under Review the file: small,
  well-supported, and inside the selected file and its direct documentation.
  That bound is the whole protection here, so apply it when deciding to fix
  rather than after a diff exists. Confidence has to scale with reach — a
  comment, a javadoc, or a rename local to one method lands on a careful
  reading, while anything that changes what the program computes, stores, or
  draws lands only when the validation actually covers it. With no tests, that
  means a run of the program and a look at the output, and a change whose
  effect a run cannot show is a change this card should not be making.
- When a change does not clear that bar it does not go in the tree at all:
  record it under `docs/active/findings/` and commit the entry instead. A fix
  already written and then doubted is backed out and recorded the same way.
  Leaving it in the tree for a human to notice is not a third option — it
  reads as an unfinished pass rather than a decision, and it blocks the queue
  while it waits.
- The one pass that does not commit is one that cannot validate: a tree that
  did not compile on arrival. Report that and stop, having changed nothing. It
  is not this pass's mess to commit around.

Report the age evidence, or for a named pass the path and that the queue was
overridden, then what the review found and changed, validation performed, the
ledger line written, the commit hash, and any residual risk or recorded
finding. When the pass was a no-op, the report is where its justification
lives. When the pass declined a fix, say what it declined and why: the finding
file records the problem, but the judgement that left it there is recoverable
from nothing else.
