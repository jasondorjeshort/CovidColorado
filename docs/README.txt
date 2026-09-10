COVIDCOLORADO DESIGN DOCS
=========================
Verified: 2026-09-09

This directory holds design notes for CovidColorado's subsystems. The audience
is mostly agents working on the code. The job of a doc here is to carry what the
code cannot: why a subsystem is shaped the way it is, what invariants it relies
on, and which door to walk through when you need to change it.

THE MAP below is the index. Read it first and open one or two docs, not ten.
CONVENTIONS at the bottom govern how these files are written and maintained;
follow them when you add or edit a doc.


PRECEDENCE
----------
The code wins. These docs explain intent; they do not define behaviour. If a doc
disagrees with the code, the code is right and the doc is stale — fix the doc in
the same change that discovered the conflict. Never "fix" code to match a doc
without checking that the doc was ever true.

Docs with no "Verified:" line have not been read against the code and may be
written in proposal tense about things that already exist, or in present tense
about things that no longer do. Treat those with extra suspicion.

The code wins over the docs; the data wins over the code. Most of what this
program does is arithmetic on files it did not write — a CDC wastewater export
whose columns have changed under it before, and a sequence-count API that
returns whatever it has. A doc that describes a shape those sources have is
making a claim about somebody else's file, so say where the claim came from and
when, and expect it to age faster than anything about the Java.

The root README.md is not part of this corpus and is not maintained against the
code. It describes the program's first life — Colorado case data by infection
date — which is the `colorado/` package, and that package is dead. Read it as
history.


HOW THIS DIRECTORY IS ORGANIZED
-------------------------------
Four design-doc subdirectories, by what kind of claim the doc makes:

  reference/   How a subsystem works today. Meant to be the bulk of the corpus.
  design/      Proposed work that does not exist yet, and the shape it should
               take. About the SOLUTION.
  active/      Work currently underway: goal, agreed approach, where it got to.
               About EXECUTION STATE, not solution shape. Plays by different
               rules — see ACTIVE WORK in CONVENTIONS. Markdown, deliberately,
               to mark the different contract.
  ideas/       Improvements nobody is working on: one file per idea, each with
               a concrete anchor. About WHAT WOULD BE BETTER, and nothing here
               has been agreed. Not a queue, and not where a defect goes.
               Markdown.

The first two are durable and must be kept true. active/ is the opposite:
provisional by design and DELETED when its work lands. ideas/ is a third
contract again — durable in the sense that nothing expires on its own, but
carrying no claim about the code, so it is neither kept true nor drained.

docs/skills/ is separate from the design-doc corpus. It holds short,
agent-facing procedure cards that CLAUDE.md can point every AI agent at without
making them specific to one tool. They are durable and must be kept true like
the reference docs — a procedure card that lies still gets followed. Being
Markdown with frontmatter, they are exempt from the HEADER and STYLE rules below
and are not listed in THE MAP. Apply the rest of these conventions by intent:
the code wins, stale procedures get fixed with the change that found them, and
path anchors should still pass scripts/check-doc-paths.ps1.

Each card is a directory holding a SKILL.md — docs/skills/commit/SKILL.md, not
a bare commit.md sitting in docs/skills/. That is the shape agent tooling reads
a skill in, and a card that grows a script or a reference file has somewhere to
put it. The directory name is the card's name and matches the name: in its
frontmatter.

Some cards are also exposed as Claude Code slash commands by pointer files
under .claude/commands/, one per card exposed. A pointer holds no procedure of
its own, only the path of the card to read, so the card stays the single copy.
That path is what the pointer is for, so check-doc-paths.ps1 scans .claude/ on
the same pass as docs/: a renamed card fails the check instead of silently
breaking its slash command. A pointer may also carry a model: line, which is
Claude-only frontmatter the Agent Skills spec does not define and so cannot sit
on the card itself; the card runs in the conversation that invoked it either
way.

An agent that discovers skills under .agents/skills/ can be pointed at the same
cards with a local symlink to docs/skills/. That link is gitignored rather than
committed, because it is a per-checkout convenience and a committed symlink does
not survive every Windows checkout. Either way the cards stay the one copy: the
exposure mechanism is a link or a pointer, never a second copy of the text.

The shape of this directory — the map, the conventions, the cards and the path
checker — is modelled on the dystopia repository, another of this maintainer's
projects, where it was worked out over a much larger corpus. Where a rule here
reads as heavier than a program this size needs, that is why; the rules that did
not carry over were dropped rather than kept as decoration.

THE MAP groups by subject area, because "how does a chart get built" should pull
together everything chart-adjacent. The path prefix on each entry tells you
which kind of doc it is.


=====================================================================
THE MAP
=====================================================================

reference/
----------
  reference/wastewater.txt
    Wastewater intake and the national baseline: `nwss/`, `sewage/`.
    Where the CDC data comes from and what the 2025 dataset migration did to
    the reader; why one normalization column is picked per plant and held;
    the spike cap; the iterative baseline that gives every plant a normalizer
    and makes the axis a percentage of the pandemic peak; how plants are
    weighted into counties, states, regions and the nation.
    Open when: touching intake, the baseline loop, or any aggregate's numbers.

  reference/lineages.txt
    Lineage data: `variants/`.
    The two pipelines, LAPIS and the hand-exported CSVs, and why the old one
    is kept; what each of the LAPIS constants is protecting against; the
    inclusive-count invariant the child subtraction depends on; how the merge
    loop cuts hundreds of lineages down to a legend; where a fit starts.
    Open when: changing what lineages are shown, or chasing a prevalence that
    looks wrong.

  reference/charts.txt
    Chart building: `charts/`, `myjfreechart/`.
    The rule that a sewage chart is named by the object it is built from, and
    what that means for adding one; why the directory tree is made up front;
    the axis bounds and the failures each one prevents.
    Open when: adding or changing a chart, or a chart came out empty, broken
    or in the wrong place.

The one subsystem still without an entry, so the hole is visible and
claimable: the shared helpers under `library/`.

design/
-------
Nothing yet. Proposals go here rather than into a reference doc written in
future tense; CONVENTIONS, under WHEN A DESIGN LANDS, says what happens to one
that gets built.

ACTIVE WORK
-----------
Not indexed here by name — the directory listing IS the index, and entries are
too short-lived to be worth a map entry each. Check it directly:

  ls docs/active/

docs/active/findings/ is the exception and is permanent. It holds defects a
review turned up and did not fix, one file each, and its README owns what that
means.

IMPROVEMENT IDEAS
-----------------
Like active/, the listing is the index — the entries are named so a reader gets
the gist without opening one. Only the README is indexed here:

  docs/ideas/README.md
    What an improvement idea is, and why it is not a finding.
    One file per idea with a concrete anchor; the entry format; the phrase
    "Record an idea:" that files one; and why nothing drains the directory,
    which is the opposite of findings.
    Open when: filing an improvement that is not a defect, or wondering
    whether something you noticed belongs here or in active/findings/.

WORK QUEUES
-----------
There are three, and they are different things. docs/active/ holds work already
underway. docs/active/findings/ holds defects, and is drained oldest-first by
docs/skills/resolve-all-findings/SKILL.md. docs/ideas/ holds improvements nobody
agreed to build, and nothing drains it.

An agent may write into all three and must not confuse them. A defect goes to
findings/ however small it is; an improvement goes to ideas/ however good it is;
neither goes into the other, and neither goes into a chat message. Work in
flight goes to active/, and the file is deleted when the work lands. Nothing
here is a personal notepad, and there is no fourth list.


=====================================================================
CONVENTIONS
=====================================================================

Information proliferates far more easily than it gets pruned, so the rules below
are biased toward cutting.

UPDATE WITH THE CODE
  Docs are part of the change, not an afterthought. If a code change alters
  behavior, invariants, or entry points that a doc describes, update the doc in
  the same change — THE MAP above says which doc owns each area. This is the
  discipline that slips first, everywhere, always.

  A routine same-change update does not require re-verifying the whole doc;
  fix the part you know changed and leave the Verified date alone. Bump
  Verified only when you actually re-read the doc against the code. And when
  a change renames or moves code, run scripts/check-doc-paths.ps1.

WHAT BELONGS IN A DOC
  Keep, in priority order:
    1. Rationale. Why it is this way, what was rejected, what looks like a bug
       and is deliberate. Unrecoverable from code; never goes stale on its own.
       In this program that is mostly the constants: why a lineage under twenty
       sequences is folded into its parent, why the LAPIS window lags ten days.
       A number in the source is a fact; why it is that number is not.
    2. Invariants and contracts. What a caller must already hold, what a method
       promises about nulls, "never do X". State these precisely — when they rot
       they are dangerous, not just wrong.
    3. Entry points. The one or two doors into a subsystem. An agent cannot grep
       for a flow whose starting point it cannot name.
  Cut:
    4. Inventories. Lists of files, fields, methods, chart types, enum members.
       The code enumerates these better, and a stale list actively misleads: a
       reader trusts "the three chart kinds are X, Y, Z" and misses the fourth.

  When you want to write an inventory, write the rule that generates it instead.
  Not "the charts are X, Y and Z" but "every chart is built from a sewage.Abstract
  and named after it, which is why adding one takes no registration step".

ANCHORS
  Name few code anchors, and name them exactly, in path form
  (nwss/Nwss.java, not "the downloader"). Precise anchors are checkable:

    scripts/check-doc-paths.ps1

  extracts every path-shaped token from these docs, and from the .claude/
  pointer files, and reports ones that no longer exist. Run it after renaming or
  moving code, and after editing a doc.

  If a doc deliberately names a path that does not exist — a proposed file, a
  deleted one, a runtime artifact outside the checkout — put [no-check: reason]
  anywhere in that paragraph. The marker suppresses the whole blank-line-
  delimited paragraph, so it does not matter which wrapped line it lands on.
  The paths this program reads and writes at runtime are the common case: the
  chart output folder, the caches under the system temp directory, the
  pango-designation checkout. None of them are in the tree, and a doc naming one
  needs the marker.

  docs/active/ paths need no marker: those files are deleted when the work they
  track lands, so a durable doc naming one is naming something expected to come
  and go rather than an anchor that rotted. The skip is blanket, so the
  permanent README of findings/ rides along in it and resolves on its own
  anyway.

LENGTH
  A reference doc over roughly 300 lines is a signal, not an achievement. It
  usually means one of two things has crept in: an inventory, or the record of a
  deliberation that has since been settled. Both should be cut. Split only when
  a doc genuinely covers two subsystems.

WHEN A DESIGN LANDS
  Rewrite the doc as reference and move it to reference/. Do not tag sections
  "(IMPLEMENTED)" and leave the proposal in place — that is how a corpus ends up
  describing in future tense things that already exist. The reader wants to know
  how it works, not how it was decided to work.

ACTIVE WORK
  docs/active/*.md tracks work in flight. CHECK IT BEFORE STARTING ANYTHING —
  a project may already be underway, with an approach already agreed.

  One file per project. What earns its place is the context that otherwise
  exists only in a conversation: the goal, the approach that was AGREED and
  what it was chosen over, decisions made mid-flight, and where the work got
  to. Not a second TODO list — active/ holds the state of work already
  underway, and nothing else.

  docs/active/findings/ is an exception, twice over. It holds real findings a
  review turned up and did not fix, one file each, deleted as they are resolved
  rather than kept as a record of having happened — because there is no project
  to attach them to. And it is permanent: its README stays when the last entry
  goes, since deleting a place the next review recreates is churn that tells a
  reader nothing. So nothing there carries a Started date and the staleness rule
  below does not apply; a directory holding only its README is the resting
  state. docs/skills/review-working-tree/SKILL.md, under Recording what is not
  fixed now, owns the format, the naming rule, and what earns an entry.

  Prefer a DERIVED progress check over a maintained checklist. A list that is a
  side effect of the work cannot drift out of date the way a hand-ticked one
  does — point at the tree, or at THE MAP's list of subsystems with no doc,
  rather than carrying checkboxes. Reach for checkboxes only when nothing about
  the work is observable from the tree.

  Lifecycle:

    an entry in ideas/  ->  docs/active/foo.md  ->  commits + reference docs,
         (idea)             (work starts)           file DELETED (work lands)

  Promotion is the only way out of ideas/ that leads anywhere: the maintainer
  moves an idea into an active file when they want it built, and deleting it
  is the other outcome. Nothing walks that arrow on its own.

  The deletion is the load-bearing part. Durable content graduates into
  reference/; the active file does not become a changelog and does not linger.
  A file here carries a Started date: if it is weeks old and untouched, the
  project stalled — resume it or delete it. Do not let this directory become a
  third pile of stale text, which is the exact failure the rest of these
  conventions exist to prevent.

  These files are exempt from the Verified line and the plain-text style rule.
  They are provisional by contract, which is why they are .md and live in their
  own directory rather than alongside the durable docs.

IDEAS
  docs/ideas/*.md are exempt from the Verified line and the plain-text style
  rule for the same reason active/ files are: they make no claim about how the
  code works, so there is nothing to verify and nothing to keep true. Their
  anchors are still checked, so name a path exactly. docs/ideas/README.md owns
  the entry format and the naming rule; do not restate either here.

ONE DOC PER SUBSYSTEM
  Prefer a section in an existing doc over a new file. A new doc is justified
  when a distinct subsystem has no home, not when an existing doc is long — if
  it is long, cut it.

HEADER
  Every doc starts with a title, a Verified line, and a Code line:

    WASTEWATER INTAKE
    =================
    Verified: 2026-09-09
    Code: nwss/

  Verified means someone read the doc against the code on that date and it was
  true. Bump it only when you actually did that. A typo fix does not bump it.

  A doc that has not been through that reading yet omits the Verified line
  entirely (see PRECEDENCE) — an absent line is honest, an unearned date is not.
  Structural work on a doc (moving, splitting, retitling) does not earn one.

ADDING OR REMOVING A DOC
  Update THE MAP above in the same change. The map is the only index; CLAUDE.md
  points here and deliberately does not keep its own list, because a duplicate
  list is what goes stale.

STYLE
  Plain text, wrapped at about 80 columns. ALL-CAPS section headings underlined
  with dashes. No markdown — these are read as text.
