# AGENTS.md

This file provides guidance to Codex and other agents working in this repository.

**All guidance lives in [CLAUDE.md](CLAUDE.md). Read that file.** It covers what
the program is, build and run commands, validation, the hardcoded paths it reads
and writes outside the checkout, commit policy, and code style, and it applies to
every agent regardless of which tool is driving.

Design notes for individual subsystems live in `docs/`; start at
`docs/README.txt`, which indexes them and states the conventions they follow.

Repository procedures live in `docs/skills/<name>/SKILL.md`, one directory per
card — committing, pushing, pulling, reviewing, working off findings. When a
request sounds like a named repository procedure, read the card before acting
rather than improvising the steps.

Do not reintroduce duplicated guidance here; add it to CLAUDE.md instead. Two
files saying the same thing means one of them is lying, and the copy is always
the one that goes stale.
