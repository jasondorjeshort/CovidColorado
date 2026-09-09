---
name: pull
description: Bring CovidColorado's local master up to date with origin and report what moved — fast-forward when local has nothing unpushed, rebase the unpushed queue onto origin when it does, and resolve the conflicts that rebase raises. Use when asked to pull, sync with origin, fetch the latest, resolve a rebase that push-one stopped on, or check whether origin is ahead. Never merges, stashes or discards.
---

# Pull

Advance the local branch to origin and say what arrived. Local commits that are
not yet on origin are replayed on top of it, because `master` is kept linear
here and `push-one` refuses to push anything else. When that replay conflicts,
this card resolves it: that is the one part of a pull that takes judgment, and
it is why the card exists at all. `push-one` and `push-all` also rebase onto a
moved origin but stop on any conflict rather than resolve it, so "run `pull`,
then push again" is the recovery from that Stop.

## Establish the state

- `git status --short`. Note every modified or untracked entry; none of it is
  yours to stash, discard or commit. A dirty tree is not itself a Stop — git
  refuses a fast-forward that would clobber a dirty tracked file and refuses to
  start a rebase over one at all, and that refusal is the Stop. Do not use
  `--autostash` to get past it; the user decides what happens to their work.
- `git branch --show-current`; proceed only on `master` unless the user
  explicitly named another branch.
- `git rev-parse --abbrev-ref --symbolic-full-name '@{u}'`; proceed only when
  the upstream is `origin/master` for `master`, or `origin/<branch>` for an
  explicitly named branch. Do not set an upstream during this card.
- `git fetch origin`. If it fails, stop and report the error verbatim.
- `git rev-parse HEAD` before anything moves; the report is written against it.
- `git rev-list --count 'HEAD..@{u}'` is how far behind; `git rev-list --count
  '@{u}..HEAD'` is how far ahead.
  - Behind `0`: nothing to pull. Report that, and the ahead count if it is not
    `0` — the user may want `push-one` next.
  - Behind non-zero, ahead `0`: fast-forward.
  - Both non-zero: rebase.

## Fast-forward

- `git merge --ff-only '@{u}'`.
- If it refuses because a local change would be overwritten, stop. Report the
  paths git names; the user decides whether to stash or commit them.
- Confirm `git rev-parse HEAD` now equals `git rev-parse '@{u}'`.

## Rebase

- Record what is about to be replayed: `git log --oneline '@{u}..HEAD'`. Every
  one of these subjects appears in the report whichever way the rebase ends.
- `git branch backup/pull master`, then `git rebase '@{u}'`. The backup ref is
  what makes the whole operation reversible: at any point, `git rebase --abort`
  followed by confirming `git rev-parse master` equals `backup/pull` is the
  tree the card started from.
- A replay that completes without stopping needs no judgment. Confirm it with
  `git range-diff 'backup/pull...master'` — every commit should read as
  unchanged apart from its base — and go to Finish.

## Resolving a conflict

The rebase has stopped on one local commit. `git status` names the paths;
`git diff --name-only --diff-filter=U` lists them bare. Resolve them all before
`git rebase --continue`, and expect the rebase to stop again on later commits.

- Understand both sides before touching either. `git log -1 REBASE_HEAD` is the
  local commit being replayed, and its message says what it meant to do.
  `git log --oneline 'backup/pull..@{u}'` is what origin added; narrowed with
  `-- <path>` for the conflicting file, it says what origin did to it and why. A
  resolution has to keep both intents, not pick one.
- The conflict is between two changes to the same lines, not a choice between
  files. `git checkout --ours` / `--theirs` throws one side away and is never
  the resolution here; when one side's change is genuinely obsoleted by the
  other's, say so in the report and be able to defend it from the two messages.
- A moved method is the common hard case in this repository: the code is still
  being pulled apart along the `nwss/` / `sewage/` / `variants/` / `charts/`
  seams, so origin has often moved a method to the class that owns the data it
  reads while the local commit edited it in place. The resolution is the local
  edit applied at the method's new home, and the old location left as origin
  left it. Read the moved method whole before deciding; a move that also
  changed a signature or a field's owner is not a pure move, and the local edit
  may not mean the same thing there.
- A deleted file is the other: origin resolved a finding and deleted its file
  under `docs/active/findings/`, the local commit edited it. Origin wins and
  the file stays deleted, unless the local edit recorded something the
  resolution did not fix — then it is a new finding, written fresh in the
  format `docs/skills/review-working-tree/SKILL.md` defines, not the old file
  restored.
- `docs/skills/review-oldest-java/reviewed.txt` does not conflict:
  `.gitattributes` marks it `merge=union`, so concurrent appends are kept. If it
  conflicts anyway, the attribute is broken; keep every line from both sides.
- When every path is resolved, `git add` each one and `git rebase --continue`.
  Do not amend the commit message unless the resolution changed what the
  commit does; if it did, the message has to say so, and the report has to say
  the message changed.
- After the last commit replays, and before Finish: if any resolved path is
  Java, run `./gradlew compileJava -q`. A resolution that does not compile is
  not a resolution. Fix it in the rebased commit that introduced it (an
  interactive rebase needs an editor an agent does not have;
  `git commit --fixup <hash>` then `git rebase --autosquash '@{u}'` does the
  same without one, and amending does when the commit is the tip)
  rather than adding a "fix build" commit on top — the queue is going to be
  pushed one reviewed commit at a time, and each one has to stand alone.
  Compiling is all this step claims. A resolution inside data ingestion or
  chart building is only really confirmed by `./gradlew run`, which takes about
  four minutes and needs network; say in the report whether it was run, and if
  it was not, that the resolution rests on reading alone.
- If a conflict cannot be resolved with confidence — two sides restructured the
  same code in incompatible ways, or a message does not explain a change well
  enough to know what it intended — stop: `git rebase --abort`, confirm
  `master` equals `backup/pull`, and report the paths, the local commit the
  replay stopped on, and what about the two sides could not be reconciled. An
  honest abort is a good outcome; a guessed resolution that pushes later is not.

## Finish

- `git range-diff 'backup/pull...master'`. Commits that replayed clean read as
  unchanged apart from base; commits that were resolved show exactly the
  resolution and nothing else. Anything else in the diff is a mistake to fix
  before going on.
- `git branch -D backup/pull` only once the range-diff is what it should be.
  After an abort, delete it after confirming `master` equals it.
- `git rev-list --count 'HEAD..@{u}'` is now `0`.

## Report

Lead with the range: `<old>..<new>` and the commit count, or the abort and the
paths it stopped on. Then `git log --oneline <old>..<new>` for what arrived
from origin in full — this is a pull, and the user wants to see what landed.
If a rebase happened, say so and list the replayed subjects separately from
the arrivals. For each conflict resolved, name the path, the two intents, and
what the resolution kept; say whether anything was compiled or run and what it
said.

Then call out, by name, any changed file that alters how the next piece of
work here should be done: `CLAUDE.md`, `AGENTS.md`, anything under
`docs/skills/`, `.claude/`, or `docs/active/`, and `build.gradle` or the
Gradle wrapper. These change the rules or the in-flight state, and a session
that does not know they moved will work from stale ones. The rest of the diff
is summarized by `git diff --stat <old> <new>`, not enumerated.

Do not run the program beyond what a conflict resolution required. If
`build.gradle`, a dependency or the wrapper moved, say so and leave the build
to whoever asks for it.
