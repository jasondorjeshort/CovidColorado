---
name: resolve-finding
description: Fix one entry from docs/active/findings/ and delete its file. Use when asked to work off the findings list, close a finding, or fix something a review recorded and did not fix.
---

# Resolve Finding

Takes one file out of `docs/active/findings/`, fixes what it records, and
deletes it. One per pass by design: the entries are unrelated to each other, so
a pass that closes three of them produces a change nobody can review as one
thing.

`docs/skills/review-working-tree/SKILL.md`, under Recording what is not fixed
now, owns the directory — its format, the naming rule, what earns an entry,
and what counts as resolving one. This card owns only the pass that does the
resolving.

## Pick one

An argument names the entry: match it loosely against the file names and the
bold claims. With no argument, take one without deliberating — the first that
catches your eye is fine. Do not read the directory through looking for the
best candidate: the entries are unrelated, so which one you take is not a
decision worth a pass's attention. Say which you took before you start changing
anything.

Take the whole entry rather than the half of it you can reach. If what it asks
turns out to be bigger than this pass, leave it untouched and take another,
saying that you did — picking is cheap so that abandoning a bad draw is cheap
too. When the argument named the entry, there is nothing to re-draw: stop and
say so instead. Where part of the fix does land and the rest genuinely cannot,
the entry is updated in place to what is still wrong instead of deleted — that
is `docs/skills/review-working-tree/SKILL.md`'s rule, and the reason for it is
that an entry claiming more than is true misleads the next reader worse than no
entry would.

## Check it still holds

An entry describes what was true when it was written, which may be months back.
Read the code it names and confirm the problem is still there before fixing
anything. `git show <hash>` on the commit it names when the entry only makes
sense against the change that produced it. An entry filed by hand as a symptom
names no code yet; finding the site is this pass's first step. When the claim
is about a number the program produces rather than about the code, confirming
it means a run: the CDC and LAPIS caches under the system temp directory make
a second run cheap, and the numbers a chart is built from are what the entry is
actually about.

A finding can also be resolved by concluding it is not a bug — either it no
longer holds, or it was working as intended all along. Neither is a wasted
pass. What they leave behind is what differs. A finding the code has moved out
from under is deleted and nothing else, because there is nothing left to say at
the site. Working as intended is the other case, and there the reason it works
is usually not visible from the code — a reviewer read that code and concluded
otherwise, which is how the entry came to exist. So the resolution is the
comment, Javadoc line or `docs/` sentence that makes the reason visible, and
then the deletion; `docs/skills/review-working-tree/SKILL.md` says why that
counts as a real resolution. Deleting on a working-as-intended verdict and
leaving nothing behind is the shape to avoid: the next review reads the same
code and records the same finding.

## Fix it

Scope is the entry and nothing else. The code is often unfamiliar — an earlier
pass declined this fix rather than making it, and an entry can sit for months —
so read `docs/README.txt` for the doc that owns the area, and `docs/active/`
for whether it is already somebody's work in flight.

The repo's same-change obligations apply as to any change: the owning `docs/`
file, and Javadoc or comments on the contracts touched. CLAUDE.md carries those.

Then `git rm` the entry's file, in the same edit as the fix rather than after
it. When it was the last one, the README stays and the directory with it —
`docs/skills/review-working-tree/SKILL.md` says why.

## Validate

`./gradlew compileJava -q`, which is the whole automated gate this repo has.
When the fix touched data ingestion or chart building, that gate proves
nothing about it: run `./gradlew run` — four minutes and a network fetch — and
read what it prints and what it drew. When the resolution touched only `docs/`,
`scripts/check-doc-paths.ps1` is the whole check. A comment or Javadoc line
added to Java source still takes the compile, since a malformed one is a
compile error like any other.

## Stop there

Do not commit. The fix and the deleted entry stay in the working tree —
CLAUDE.md's rule that work is held uncommitted until it is asked for applies
unchanged here, and this card is not one of the cards that carries its own
commit authority.

Report what the entry claimed, whether it still held, what the fix was, what
validation said, and what is now sitting uncommitted.
