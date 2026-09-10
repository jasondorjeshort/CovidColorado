# CLAUDE.md

Guidance for Claude Code and any other agent working in this repository. This
file holds what you must know without looking anything up; `docs/README.txt`
indexes everything else.

## What this program is

A single-author Java program that draws COVID charts. It downloads the CDC's
national wastewater dataset, normalizes roughly 2,500 treatment plants against a
national baseline, pulls SARS-CoV-2 lineage counts from cov-spectrum's LAPIS
API, and renders JFreeChart PNGs to a folder on this machine. There is no
server, no database and no UI: it is a batch job that runs for a few minutes and
writes images.

It has had two lives, and only one of them is running. The entry point is
`colorado/CovidColorado.java`, whose `main` builds an `nwss/Nwss.java` and calls
`read()` then `build()`. Everything reached from there — `nwss/`, `sewage/`,
`variants/`, `charts/`, `myjfreechart/`, `library/` — is the live program. The
rest of `colorado/` is the first life, Colorado case data by infection date,
kept but unreachable; the root `README.md` describes that era and is not
maintained. Do not extend dead code, and do not delete the package on your own
initiative either.

## Build, run, validate

    ./gradlew compileJava -q     the only cheap check there is
    ./gradlew run                the real one: about 4 minutes, needs network

There are no tests, no formatter check and no linter. Compiling proves the
change is well-formed Java and nothing about what it computes, so a change to
data ingestion or chart building is validated by running the program and looking
at what it printed and drew. Say which of the two a change got. For a doc-only
change, `scripts/check-doc-paths.ps1` is the whole check.

## Paths this program expects to find

All hardcoded, all outside the checkout, all on this machine:

- Chart output: `C:\Users\jdorj\Downloads\CovidCoCharts` — `charts/Charts.java`,
  `TOP_FOLDER`; everything is written under its `full` subfolder.
- Download cache: `CovidBackend` under the system temp directory (`%TEMP%`),
  created by a static block in `nwss/Nwss.java`. The CDC CSV and the LAPIS JSON
  live there with their own staleness windows (4 h and 24 h), so a second run
  in the same day fetches nothing.
- `pango-designation` checkout: `C:\Users\jdorj\Downloads\pango-designation` —
  `nwss/Nwss.java`, `GIT_LOCATION`. Every run `git pull`s it. Its
  `alias_key.json` and `lineages.csv` are read directly.
- IrfanView: `C:\Program Files\IrfanView\i_view64.exe` — `library/OpenImage.java`,
  `IRFANVIEW`. A run ends by opening the charts it queued in one thumbnail
  window, through a list it writes to the download cache as `open-charts.txt`.
  Without IrfanView the charts are still written and the launch prints a
  stack trace.
- Optional hand-exported cov-spectrum CSVs in `C:\Users\jdorj\Downloads`, named
  in `variants/Voc.java`. These are a fallback for the GISAID-backed data the
  open LAPIS endpoint does not carry, and a run without them is normal.

A change to what the program expects to find at any of these says so in the
commit message, exactly. Nothing in the tree will tell the next run.

## Data sources

- CDC wastewater, dataset `j9g8-acpt`, fetched as CSV from data.cdc.gov. The
  predecessor datasets were archived in September 2025 and this one replaced
  them; the columns have moved before and will again.
- cov-spectrum LAPIS, the open GenBank-backed endpoint, in `variants/Lapis.java`.
  Thinner than the GISAID pages, hence the CSV fallback above.
- `pango-designation`, for the lineage alias tree and the designated-lineage
  list.

## Editing files here

Use the Read/Edit/Write tools for file changes rather than `sed`, `awk` or shell
heredocs. Those rewrite a whole file to make a two-line change, which is how a
stray edit reaches a line nobody read.

Line endings are mixed and it does not matter much. `core.autocrlf=true`, so the
index is LF throughout and a fresh checkout is CRLF, but a file a tool has
rewritten stays LF until git next touches it, so the tree ends up a mix of
both. Git normalizes on the way in, so the diff is honest either way and the
"LF will be replaced by CRLF" warnings on commit are noise. Do not reformat a
file to settle its endings; that is a whole-file diff bought for nothing.

## Git and commits

Only commit when explicitly asked — work is held uncommitted until it is ready,
which is deliberate. The exceptions are the cards that carry their own narrow
authority: invoking `/review-oldest-java` is the ask to commit the pass it
completes, including the `reviewed.txt` ledger line every pass writes; invoking
`/push-one` is the ask to amend the candidate when its card judges the replay
cheap and to commit the `docs/active/findings/` entry it writes after declining
a push; invoking `/resolve-all-findings` is the ask to commit and push each fix
its judge approves. That authority covers nothing wider. When asked, commit
directly to `master` — that is the active development line, pushed to origin and
kept linear — and do not create a branch unless told to.

Every commit gets a staged-diff review and a same-change docs check. Both are
chronically forgotten and neither is optional; `docs/skills/commit/SKILL.md` is
the procedure and owns the detail, so read it when you commit rather than
working from memory. Commit messages end with the authoring agent's
`Co-Authored-By` trailer.

## docs/

`docs/` holds design notes, split into `reference/` (how it works today),
`design/` (proposed, not yet built), `active/` (work currently underway,
including `active/findings/` for defects a review recorded and did not fix), and
`ideas/` (improvements nobody is working on, one anchored file each — not a
queue). `reference/` and `design/` are empty so far and are being populated.

**Read `docs/README.txt` first.** It is the index and it carries the conventions
these docs follow — the code wins over a doc, a doc is updated in the same change
as the code it describes, no inventories, anchors named in path form and checked
by `scripts/check-doc-paths.ps1`. Do not keep a duplicate index in this file; the
copy is what goes stale.

**Check `docs/active/` before starting a piece of work.** Those files record
projects in flight: the goal, the approach already agreed, and where the work got
to. Starting cold on something already underway is the failure this prevents.

## Skills

Repo-local procedure cards live under `docs/skills/<name>/SKILL.md`, one
directory per card — the layout both Claude and Codex expect, so neither has to
be told about the other's. When a request sounds like a named repository
procedure, check `docs/skills/` for a matching directory name or frontmatter
description and read that card before acting. `.claude/commands/<name>.md`
exposes a card as a Claude Code slash command; a pointer holds no procedure of
its own, only the path of the card to read, so the card stays the single copy.

## Working style

Coding is delegated to opus subagents; orchestration stays in the main session.
The main session reads the request, decides what is to be done, and hands the
writing to a subagent with enough of the brief to work from — then reviews what
comes back against the diff rather than against the report. A reader who starts
from the diff sees what the writer took for granted, and with no test suite here
that reading is most of what stands between a wrong change and origin.

## Code style

Tabs, as the existing source uses. Javadoc on classes and on non-obvious public
behaviour; implementation comments for rationale and traps, and especially for
why a constant is the number it is — the thresholds and windows that shape the
charts are the part a reader cannot re-derive. Do not narrate control flow.
Remove or correct a stale comment rather than working around it.
