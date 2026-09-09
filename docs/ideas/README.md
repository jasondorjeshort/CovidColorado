# Improvement ideas nobody is working on

One file per idea: something that would be better than what is here now, with
a concrete anchor. Not a defect. Something the code gets wrong today is a
defect however small it is, and it goes to `docs/active/findings/` — this
directory is never where a bug goes to be softened into a suggestion. What
belongs here is the other thing a pass turns up: a computation three chart
builders could share, a figure a chart could carry and does not, a hardcoded
path that would be better read from somewhere, a rough edge that is a choice
rather than a mistake.

The body is the entry and nothing else — no heading, no frontmatter, no
checkbox. A bold one-line claim of what would be better, then the anchor: the
class, file or chart it is about, named in path form. Then what it would take,
in a sentence or two, and why it is not a defect: either what the code does now
is not wrong, or the improvement is a choice somebody still has to make. An
idea with no concrete anchor does not earn a file. "The charts could be
prettier" is a mood, and a directory of moods is worth nothing to whoever opens
it in six months.

Files are named `<yyyy-mm-dd>-<slug>.md`, the date being when the idea was
recorded, so a plain listing shows how long each has sat. The name is the
idea: the slug states the claim in a few words, the way a findings slug does,
because the listing is this directory's only index and a reader should get the
gist of every entry without opening one —
`2026-09-09-the-chart-output-folder-cannot-be-set-without-editing-source.md`,
not `2026-09-09-charts-path.md`. One file each is also what lets two sessions
record on the same day without touching the same lines, which a shared list
cannot.

**Record an idea:** is how the maintainer files one. When that phrase turns up
in conversation, write the entry from their words rather than transcribing
them — find the anchor, say what it would take, fill it out past the one line
they gave you — and ask only when the anchor is genuinely unclear.

## Not a queue

Nothing drains this directory. No card works it, no pass is scheduled against
it, and an entry sitting here for months is the resting state rather than a
stall. That is the whole difference from `docs/active/findings/`, which holds
defects and has a drain loop pointed at it:
`docs/skills/resolve-all-findings/SKILL.md` works that one oldest-first and
every entry there is meant to leave. Nothing of the kind exists here, and
building one would turn this into a second findings directory holding things
nobody agreed to do.

Nor is it a substitute for `docs/active/`. An idea becomes work when the
maintainer promotes it into an active file, and that promotion is the moment
somebody decided to build it; until then nobody has. The rest are deleted.
Filing here is a way of not losing a thought, not a way of handing it to
anyone.

## When a doc already has a home for it

A reference or design doc may carry its own forward-looking section. An idea
about a subsystem whose doc has one may go there instead, and is usually better
there, sitting next to the thing it is about. This directory is for the rest:
ideas with no such home, and whatever an agent files during a pass.
