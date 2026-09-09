---
name: commit
description: Commit CovidColorado changes according to this repository's commit discipline. Use when asked to commit, write a commit, stage changes, or prepare a final commit.
---

# Commit

Canonical procedure for committing here. `CLAUDE.md` keeps the constraints you
must know without looking anything up and points here for the detailed commit
workflow.

## Before staging

- `git status --short`. Decide which entries belong to the commit in hand; the
  working tree routinely carries unrelated work.
- Check `docs/active/`. If this commit lands work a file there describes, update
  it, or delete it when the work is done and its durable content has graduated
  into `docs/reference/`.
- Ask "does a doc describe what I just changed?" — `docs/README.txt` has the map.
  A change to behavior, invariants, or entry points updates the owning doc in the
  same commit. This is the step that historically slipped.
- Same question one level down: touched contracts get their Javadoc updated, and
  a comment that explains why a constant is the value it is gets updated with the
  constant.
- And one level outward: this program reads and writes paths that are hardcoded
  and outside the checkout — the chart output folder, the CDC and LAPIS caches
  under the system temp directory, the `pango-designation` checkout. A change to
  what it expects to find there belongs in the message, named exactly, because
  nothing in the tree will tell the next run that the shape of a file on disk
  moved. `CLAUDE.md` lists the paths.

## Stage

- Explicit `git add <path>` per file. Never `git add -A` or `git add .`.
- If a review *of this change* recorded a file under `docs/active/findings/`,
  stage it too. It reads as unrelated new work and is not —
  `docs/skills/review-working-tree/SKILL.md`, under Recording what is not fixed
  now, says why it rides along with the change its review came from. A file a
  `docs/skills/resolve-finding/SKILL.md` pass deleted is the same case running
  backwards: the deletion belongs to the commit carrying the fix that closed it.

## Validate

- `./gradlew compileJava -q`. There is no test suite, no formatter check and no
  linter in this repo, so compilation is the whole automated gate and it is a
  weak one: it proves the change is well-formed Java and nothing about what the
  program computes.
- Because it is that weak, a change to data ingestion or to chart building is
  validated by running the program: `./gradlew run`, which takes about four
  minutes and needs network. Read what it prints and look at the PNGs it wrote
  under the chart output folder. Skipping that on a change to `nwss/`,
  `sewage/`, `variants/` or `charts/` means the commit was validated by nothing.
- For a doc-only commit, `scripts/check-doc-paths.ps1` is the whole check.

## Review the staged diff

Read `git diff --cached` in full, judged as if someone else wrote it. Not a
formality: authoring and reviewing are different modes, and things invisible
while writing are obvious while reading. Look for

- leftover debug code, and in this repo especially a commented-out line or a
  `System.exit` left behind from narrowing a run down to one chart,
- comments that no longer match the code they sit above,
- claims in a comment or doc the diff does not actually support,
- half-renamed identifiers,
- files or hunks that do not belong to the change in hand.

Check the claims rather than reading them. Nearly everything this pass catches
reads plausibly — that is why it survived being written. A sentence asserting
what a method does, which doc owns a subsystem, or what a constant defaults to
is worth one grep. Expect some findings not to survive that check: drop those
and say so, rather than acting on a finding you could not confirm.

A claim that something does *not* exist is worth more than one. "Nothing calls
this", "no other caller does that", "this branch is unreachable" — each asserts
something about every file you did not look at, and one grep cannot establish
it. Count the matches, or enumerate the candidates and check each. Absence is
the claim that reads most authoritatively and is cheapest to get wrong: a search
that returned five files may have been truncated, scoped to one directory, or
spelled in a way the exceptions do not use. The dead `colorado/` package makes
this worse rather than better here — a name can have callers that are themselves
dead, and "nothing calls this" and "nothing live calls this" are different
claims.

Then decide per finding whether it blocks the commit. It is a judgement call,
not a gate:

  Fold in   Small, obvious, and inside the change. Fix it and commit.
  Split     A real cleanup the review turned up that is larger than the change
            in hand, and you are doing it now. Commit this one, then do that
            as its own commit — even in the same session. If you are not doing
            it now, it is a Record: between the two commits the finding exists
            only in your head, and a session that ends there loses it.
  Record    Real, and this pass is not fixing it — the difference from Split,
            which is fixing it, just not in this commit. A new file under
            `docs/active/findings/`, staged with this commit —
            `docs/skills/review-working-tree/SKILL.md`, under Recording what is
            not fixed now, owns the format and what earns an entry.
  Note      Too small to be worth an entry. Say so in the report and leave it.

The pass is not a licence to keep editing until the diff is perfect. The one
thing it must not do is ship a claim you did not check.

## Message

Subject is a complete sentence ending in a period, usually imperative, saying
what the change does rather than naming the area it touches — "Stop the
national baseline being recomputed once per plant.", not "Fix sewage bug".

The body is where the value is, and it is rarely one line. Explain why the
change is right: what the old behavior actually was, what was considered and
rejected, what looks untouched and was deliberately left alone. `git log` here
is the design record for decisions that never made it into a doc, so write for
someone reading it a year out. Wrap at about 80 columns.

End with the authoring agent's `Co-Authored-By` trailer.

## Report

Give the commit hash, the subject, what validation ran and what it said, and
what the staged-diff review found — including "nothing", rather than leaving it
implied.
