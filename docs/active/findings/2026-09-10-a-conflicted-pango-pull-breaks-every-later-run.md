**A pango-designation pull that conflicts leaves markers in the files every later run reads.** `library/GitUpdater.java`, the `git pull` command line in update(), from `4af9078`.

update() runs `git pull --no-rebase origin`, so when the checkout's history has
diverged from origin's, git merges. If that merge conflicts, git writes
conflict markers into the working tree and fails, and the run goes on to read
them: a conflict in alias_key.json most likely ends the run in
`variants/Aliases.java` build(), which exits the JVM on a file it cannot parse,
and one in lineages.csv puts the marker lines in as rows. Every later pull then
refuses with "Pulling is not possible because you have unmerged files" and
exits 128, so the checkout stays broken until someone repairs it by hand. The
checkout has no local commits today, so this needs origin to rewrite master's
history, or a commit made in the checkout by hand.

The fix is `--ff-only` in place of `--no-rebase`. A fast-forward, which is
every pull the checkout sees now, is unchanged; a diverged checkout would get
"Not possible to fast-forward", exit non-zero and be left untouched, which is
the stale-but-consistent state every other failed pull already leaves. It would
also stop merging any commit kept in the checkout on purpose, so it changes
what the pull does and is the maintainer's call. Not fixed in the review of
GitUpdater that found it, for that reason.

Verified with a throwaway probe compiling the current GitUpdater against
scratch repositories under the system temp directory: a clone with a local
commit changing the same line as origin's ended with markers in the file, and
a second update() on it printed the unmerged-files error and status 128. What Aliases and Lineages then do is from reading their code, not
from a run.
