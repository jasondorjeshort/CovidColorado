---
name: push-all
description: Push CovidColorado's unpushed commit queue one reviewed commit at a time until there is nothing left to push or push-one hits a blocker. Use when asked to push all queued commits, drain the push queue, or keep pushing one reviewed commit at a time until something stops the queue.
---

# Push All

Advance the branch by repeatedly running the `docs/skills/push-one/SKILL.md`
procedure. This card is only a loop controller: `push-one` remains the authority
for candidate selection, review, amendments, recording findings, and the exact
push command.

## Loop

- Read `docs/skills/push-one/SKILL.md` before starting, and follow it in full
  for each iteration.
- Run one complete `push-one` pass. If it pushes a commit, record the pushed
  hash and subject, then start a fresh pass from the top of `push-one`.
- Do not infer that the next commit is safe because the previous pass was safe.
  Re-run the branch, upstream, ancestry, working-tree, candidate, review, and
  final pre-push checks each time.
- Stop as soon as a `push-one` pass does anything other than push exactly one
  commit: no unpushed commits, a branch or upstream guard, a dirty-tree overlap,
  a findings-only hold, a review finding that was recorded, a declined or
  expensive fix, fetch failure, a rebase onto a moved origin that conflicted,
  unclear tool state, or any other Stop/Record/Note that prevents the push.
  A rebase that replayed clean is not a stop: `push-one` carries on through
  it, and the loop does too.
- Never replace the loop with a multi-commit `git push`, range push, force push,
  merge, or upstream change. The only rebase is the one `push-one` runs onto a
  moved origin; no pass rebases for any other reason. Every successful mutation
  to origin is still exactly one
  `git push origin <candidate>:refs/heads/<branch>` authorized by that
  iteration's `push-one` pass.

## Reporting

Report the commits pushed in order, with hash and subject. Then name the stop
condition from the final pass. If nothing was pushed, lead with the blocker or
the fact that there was nothing waiting.

If a pass recorded a finding under `docs/active/findings/`, say that the queue
stopped on that marker and include the candidate hash it names.
