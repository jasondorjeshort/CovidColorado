---
name: push-one
description: Review and push exactly the oldest unpushed CovidColorado commit to origin. Use when asked to push one commit, push just the oldest unpushed commit, advance origin/master by one reviewed commit, or review-before-pushing a single queued commit.
---

# Push One

Push the queue forward by one commit only, after reviewing that exact commit.
This card is deliberately more conservative than a normal `git push`: any
ambiguous branch state, non-linear history, or unresolved finding stops the
push. Remote movement is the one thing it absorbs on its own, by rebasing the
queue onto the moved origin — and only when that replays without conflict.

The review criteria and finding format are the ones in
`docs/skills/review-working-tree/SKILL.md`, under What to look for and
Recording what is not fixed now. This card owns the target selection, the
message checks below, the rule that a reviewed commit is pushed only when
nothing looks off, and when a finding is cheap enough to fix here rather than
record.

## Establish the target

- `git status --short` first. A dirty tree is allowed only when it is clearly
  unrelated to the commit under review. If a dirty tracked file overlaps the
  selected commit, or if staged changes make the review hard to reason about,
  stop before pushing.
- `git branch --show-current`; proceed only on `master` unless the user
  explicitly named another branch.
- `git rev-parse --abbrev-ref --symbolic-full-name '@{u}'`; proceed only when the
  upstream is `origin/master` for `master`, or `origin/<branch>` for an
  explicitly named branch. Do not set an upstream during this card.
- `git fetch origin` before deciding what is ahead. If fetch fails, stop. The
  point is to discover whether origin moved, not to reason from a stale
  remote-tracking ref.
- `git rev-list --count 'HEAD..@{u}'`. If it is not `0`, origin has commits
  that local `HEAD` does not. Rebase the unpushed queue onto it, and never
  merge: `git branch backup/push-one master`, then `git rebase '@{u}'`. A
  replay that completes without stopping is the tree a rebase by hand would
  have produced, so carry on — every rebased commit is still unpushed and gets
  reviewed like any other, and the report says the rebase happened. A replay
  that stops on a conflict is a Stop: `git rebase --abort`, confirm
  `git rev-parse master` still equals the backup ref, `git branch -D` it, and
  report the conflicting paths. Resolving a conflict is authoring code, which
  this card is not authorized to do silently; `docs/skills/pull/SKILL.md` is,
  so the recovery is `pull`, then this card again. The whole queue has to replay
  clean, not only the candidate: a conflict further up still leaves nothing
  pushable, because the card cannot push past it. Delete the backup ref once
  the clean replay has been confirmed with `git range-diff`, as the Amend
  disposition below does for its own.
- `git rev-list --reverse '@{u}..HEAD'`. If there are no commits, report that
  nothing is waiting. Otherwise the first line is the only candidate.
- `git rev-list --parents -n 1 <candidate>`. Proceed only if it prints exactly
  `<candidate> <upstream-sha>`. Anything else means the candidate is a merge,
  root, or not the direct child of upstream; stop.
- `git diff-tree --no-commit-id --name-only -r <candidate>`. A candidate whose
  whole change is one file added under `docs/active/findings/` may be a hold
  an earlier pass placed rather than work waiting to go out. Read that file.
  If it names a commit still unpushed, stop and say which entry holds the
  queue: pushing it would retire the hold while the finding is open. If it
  names nothing, or names a commit already pushed, the hold is spent — this is
  an ordinary doc commit and gets reviewed like any other. Key the test on
  what the entry says, never on the file list alone: a
  `docs/skills/review-oldest-java/SKILL.md` pass whose only product was a
  recorded finding commits one too, and a guard that cannot tell the two apart
  stops dead on one forever.
- A spent-hold entry that does not match its directory's own format — a
  `.txt` extension, no bold claim, a body that reads as a symptom someone
  noticed rather than a defect someone traced — is the maintainer filing by
  hand, and its shape is fixed, not recorded. That is an Amend, and a cheap
  one: rename the file to `.md`, open the body with the claim in bold, keep
  the rest as written, and give the commit a subject that says what the note
  records if its own does not. A missing `<file>` or `<commit>` anchor is not
  a defect in the entry — tracing a symptom to its site is the first step of
  `docs/skills/resolve-finding/SKILL.md`, not a precondition for filing, so do
  not go looking for the site here. Never Record a finding about a findings
  entry's format: that is a hold on a hold, and nothing it could say is worth
  a commit.

At this point, name the candidate hash and subject before reviewing it.

## Review the candidate

- `git show <candidate> --stat`, then `git show <candidate>` in full.
- If a hunk only makes sense against what it replaced, inspect
  `git show <candidate>^`.
- Check same-change obligations that the diff may imply: the owning `docs/`
  file, and Javadoc or comments on contracts the diff touched.
- Check the message. The subject should be a complete sentence saying what the
  change does. The body's claims are the part worth checking, and the ones that
  go wrong are the concrete ones: what the old behavior was, what was
  deliberately left alone, file paths, method names, constants, and hashes.
  `docs/skills/commit/SKILL.md`, under Message, is what the message was written
  against.
- Compare dirty tracked paths from `git status --short` with
  `git diff-tree --no-commit-id --name-only -r <candidate>`. If they overlap,
  stop; the working tree may hide the reviewed state.
- For anything the review turns up, check whether it survives to `HEAD`:
  `git log <candidate>..HEAD -- <path>` for each file the finding names, then
  read those files as they stand now.

The candidate is the oldest unpushed commit, so the tree has usually moved well
past it, and an entry written against the candidate's tree can name a file a
later commit deleted or ask for a fix a later commit already made. The resolve
pass that picks it up then goes looking for a problem nobody has. Write the
entry against the tree it will be read in.

That is not the same as dropping it, and this is where this card parts from
`docs/skills/review-working-tree/SKILL.md`'s third resolution shape. What
decides is whether the candidate broke something. A defect it introduced blocks
even when a later commit repairs it, because pushing it alone puts the broken
state on origin, and the entry says that rather than asking for a fix already
landed. A candidate that merely added something a later commit went on to
extend has no such state to ship — v1 of a new thing is not a regression, and
treating it as one blocks nearly every queue, since almost everything is
refined later. Ask what the tree looked like before the candidate, not what it
looks like now.

What the finding's kind decides is only what is left to do — a defect in the
candidate's tree can be overtaken by later commits, a claim in its message
cannot, because nothing later can reach it.

That a message claim cannot be overtaken says where it can be fixed, not that
every one blocks. What blocks is a claim a reader would act on: a hash, a
path, a method name, what the old behaviour was, what was deliberately left
alone. A figure that is merely off — a file count, a total that does not add
up — where the `git log <candidate>..HEAD` check above shows nothing at `HEAD`
carrying the wrong number, is a Note: say it in the report with the correct
figure, and push. A Record there holds the whole queue on a number nobody
will act on, and the entry names no defect a resolve pass could reach.
Confirming such a figure is bounded the same way. A grep or a directory count
settles it; if settling it would take a full run of the program, state the
doubt in the report and leave it, because a claim that costs four minutes and a
network fetch to confirm was never going to be worth a rewrite either way.

Whether to amend the candidate from this card is decided by what the amend
costs, not by a blanket rule. The candidate may no longer be `HEAD`, so touching
it replays every later unpushed commit — but that warning reads as a deterrent
and frequently is not one. A finding confined to the message is the cheap case:
amending only the message leaves every later commit's tree byte-identical, so
the replay cannot conflict however many are stacked behind. Save a ref to the
old tip before rewriting — `git branch backup/<name> <branch>` — so that
`git range-diff <upstream>...backup/<name> <upstream>...HEAD` can show
afterwards that nothing but the message moved. Save one rather than reaching for
`ORIG_HEAD`, which `commit --amend` and `cherry-pick` do not set: a replay built
from those leaves whatever the last `rebase`, `merge`, `reset` or `am` put
there, and `range-diff` against a stale base prints a plausible comparison
rather than failing. `<branch>@{1}` is the fallback when no ref was saved, good
for as long as nothing else has moved the branch. A finding in the diff may
replay as cleanly as a message does or may conflict, and there is no way to know
short of attempting it. The saved ref is what makes attempting it cheap: abort
the replay — `--abort`, not a bare `reset --hard`, which strands the sequencer
state for the next replay to trip over — put the branch back at the ref, and the
finding is an expensive one after all, at the cost of the attempt. Delete the
backup branch once it can neither prove nor undo anything: after the amended
candidate is pushed, since restoring it would move the branch behind origin,
and after an aborted replay, since the ref and the branch already name the same
commit. `git branch -D backup/<name>` — `-d` refuses a ref the rewrite left
unmerged, and that refusal is not a warning worth heeding here.

So amend when the replay is cheap and the fix is small: correct it, re-derive
the candidate hash, and carry on to the push. Leave it when the replay is
expensive — that is a separate repair pass, and it waits for the user to ask.
Either way, say which of the two it was in the report and in any entry. A pass
that says only that the repair "would rewrite the commits behind it" has handed
the user a false dilemma, and they will reasonably keep a permanent wrong claim
rather than pay for a rewrite that was a one-token amend.

Where a fix lands is decided by whether the candidate caused it. A defect the
candidate introduced belongs in the amend, because pushing the candidate alone
puts the broken state on origin and no later commit can reach it. Something the
review merely noticed in passing is not the candidate's, and a trivial
correction to it can be its own commit at `HEAD`. A stale comment costs less to
correct than to describe, so correct it rather than recording it — and if it
sits in a file the candidate touched, commit it before pushing, since leaving it
dirty trips the overlap guard above and stops the push. Its own commit at `HEAD`
clears that guard as well as an amend would, and keeps a correction the
candidate did not cause out of the candidate.

A recorded finding is a hard block on this candidate, not permission to push it
unchanged later. When a repair rewrites the candidate after a failed pass, drop
the unpushed push-one Record commit that named the old candidate hash; do not
resolve it with a separate deletion commit at the queue tip. A fix-forward later
in the queue does not make the oldest commit pushable by itself.

The block is this card's, not the queue's. Rewriting is one way out and not the
only one: the user may judge the finding not worth rewriting every commit
behind it for, and direct a push that ships the candidate unfixed — usually by
pushing up to the commit before the Record marker, which leaves the marker held
locally. That is theirs to decide, and this card should not re-derive a
rewrite-or-nothing dead end every time it runs. What the decision does require
is that the entry stop claiming something false. An entry saying to fix the
candidate before it can be pushed is stale the moment the candidate is pushed,
because the message can no longer be amended without rewriting pushed history.
Regrade it to name where the correction can still land — a `docs/` sentence, a
comment, a Javadoc line — rather than deleting it. The finding is still true;
only its remedy moved.

When there is nowhere for it to land — the only copy of the wrong claim is the
pushed message itself, or the file that carried it is gone by `HEAD` — there is
no defect in the tree for the entry to name, and `docs/active/findings/` holds
only those. Then the entry goes, not by a deletion commit but by dropping its
unpushed Record commit while it is still the tip: `git status` clean, confirm
`HEAD` is that commit, `git reset --hard HEAD~1`. Say in the report what was
wrong and that the pushed message is the only place it survives, so the
correction is on the record somewhere even though nothing in the tree can
carry it.

## Disposition

  Push      No findings remain, no entry under `docs/active/findings/` names
            this candidate, the branch state still passes the final
            re-check below, and the candidate is still the direct child of the
            upstream ref. Run `git push origin <candidate>:refs/heads/<branch>`.
            Never use a command that can push more than this one commit.
  Amend     A finding in the candidate whose fix is small and whose replay is
            cheap, per Review the candidate. Save a ref to the old tip,
            correct it, replay the later unpushed commits, re-derive the
            candidate hash, and continue to the push. Follow
            `docs/skills/commit/SKILL.md` for the amended message and its
            staged-diff review. Confirm against that saved ref, with
            `git range-diff`, that only the intended commit moved before going
            on. Keep the backup ref until the amended candidate is safely
            pushed, then `git branch -D` it.
  Record    A real finding exists and this pass is not fixing it. Write one
            new file under `docs/active/findings/`, using the format and
            naming owned by `docs/skills/review-working-tree/SKILL.md`. Say
            the candidate hash in the entry. Leave the push unattempted. This
            disposition authorizes only the commit of the single file it just
            wrote, so the finding does not leave the tree dirty for the next
            push-one pass: stage only that file, verify the staged diff adds
            it and nothing else, follow `docs/skills/commit/SKILL.md` for the
            doc-only commit, and do not stage or commit anything else. This
            commit is a queue-blocking marker for as long as its entry names
            an unpushed commit. Unlike an ordinary review-finding entry, it
            may be resolved by a later unpushed-history repair dropping this
            marker commit, because the fix may belong in a candidate tree that
            does not contain the entry's file yet. If the candidate is
            instead pushed unfixed, that repair never happens: the marker stays
            and the entry is regraded in place, per Review the candidate — or
            the marker is dropped, when the regrade finds nowhere for the
            correction to land — and the hold lapses either way. A card cannot
            keep a commit local; only a standing finding can hold the queue.
  Stop      Branch state changed, origin advanced and the queue did not
            rebase onto it cleanly, ancestry is not linear, the candidate is a
            merge or a findings-only commit whose entry still
            names an unpushed commit, the working tree overlaps the candidate,
            the review cannot be completed, or the tool environment cannot
            confirm exactly what would be pushed.
  Note      A trivial observation that does not merit an entry. Report it, and
            continue only if it is genuinely not a finding.

Re-check the branch state immediately before the push. After an Amend the old
candidate hash is no longer in the branch, so re-derive `<candidate>` from the
last of these four before comparing anything against it:

- `git rev-parse '@{u}'`
- `git rev-parse <candidate>^`
- `git rev-list --count 'HEAD..@{u}'`
- `git rev-list --reverse '@{u}..HEAD'`

The upstream SHA must still equal the candidate parent, behind count must still
be `0`, and the candidate must still be the first unpushed commit. If any value
changed, stop. This re-check does not rebase: the rebase above runs once, before
the review, because the review has to read the candidate as it will be pushed.
Origin moving again between review and push means starting the pass over.

## Reporting

Lead with the review result. If the push happened, include the pushed hash, its
subject, and the exact ref update. If the push did not happen, say which guard
stopped it and whether a finding was recorded under
`docs/active/findings/`.
