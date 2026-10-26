---
name: sync-readme
description: Bring the memoirist docs in line with README.md, the source of truth for what users get and in which phase (D-27) — PRD, SPRINT_PLAN, DECISIONS, USER_FLOWS, API_CONTRACT, DATABASE_SCHEMA, TECH_STACK and CLAUDE.md. Use after the README changes, when a hook says the README is newer than the docs, or when asked whether the docs match the README.
argument-hint: "[optional: a README section to focus on]"
allowed-tools: Read, Grep, Glob, Bash(git diff *), Bash(git log *)
---

# Sync the docs with the README

Focus: **$ARGUMENTS** (empty means the whole README).

## Live context
- Uncommitted README changes: !`git diff HEAD --stat -- README.md`
- Recent README commits: !`git log -3 --format="%h %ad %s" --date=short -- README.md`

The README wins on **what** users get and **which phase** it ships in; `docs/DECISIONS.md` wins on **how** (D-27).

1. **Read** `README.md` in full, and its diff: `git diff HEAD -- README.md`, or the last commit that changed it if nothing is uncommitted. List every promise and Timeline item, and what was added, reworded or removed.

2. **Map each one** to the docs that describe it:
   - PRD: §3 glossary, §4 core loop, §5 roadmap, §6 out of scope
   - SPRINT_PLAN: a task in the right phase
   - DECISIONS: the D-entry that says how it works, and any open question about it
   - USER_FLOWS, API_CONTRACT, DATABASE_SCHEMA and TECH_STACK where the feature touches them
   - CLAUDE.md's product summary

3. **Report before changing anything:** a table `README says | Docs say (file:line) | Proposed fix | Needs a decision?`. Also list features the docs plan that the README no longer mentions. Wording removed from the README is a signal, not proof: ask rather than assume.

4. **Ask the decisions** with AskUserQuestion: one question per item, each option with its trade-offs, and the recommended one first with its reason.

5. **Apply** after the user's go-ahead: record decisions with the `record-decision` skill, then update every affected doc in its existing style. Edit the README itself only with the user's approval.

6. **Verify:** grep `README.md`, `docs/`, `CLAUDE.md` and `.claude/` for the old wording and terms, and report what changed in each file. Don't commit.
