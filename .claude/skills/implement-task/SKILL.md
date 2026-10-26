---
name: implement-task
description: Implement one task from docs/SPRINT_PLAN.md end to end — plan, build, test, update docs, tick the box. Use when the user says "do task 2.3", "next task", "continue the sprint" or similar.
argument-hint: "[task id, e.g. 2.3 — omit for the next unchecked task]"
---

# Implement a sprint task

Task: **$ARGUMENTS**. If that is empty, use the first unchecked `- [ ]` task in `docs/SPRINT_PLAN.md`.

## 1. Load context (read narrowly)
- Read the task's block in `docs/SPRINT_PLAN.md`: **Read**, **Do** and **Done when**.
- Read only the doc sections it names, plus any D-xx or Q-x entries in `docs/DECISIONS.md` that it mentions.
- Read the README Timeline item for the task's phase, so the work matches what users are promised (D-27). For a screen or an end-to-end test, read the `docs/USER_FLOWS.md` flow it names.
- Check that earlier tasks in the same sprint are ticked. If one isn't, tell the user and ask whether to continue.
- If the task depends on an open question (Q-x) or flow gap (G-xx) that isn't decided, stop and ask, with the options, their trade-offs and a recommended default. Don't pick an answer silently.

## 2. Plan, then wait
Before editing anything, present:
- the files you'll create or change
- the tests you'll write and the commands you'll run to prove each **Done when** item
- doc updates (API_CONTRACT, DATABASE_SCHEMA, DECISIONS)
- anything that would change what the README promises, or in which phase (ask before going further)
- anything ambiguous, or where docs and code disagree, as questions

Wait for the user's go-ahead, unless they already said to proceed without asking.

## 3. Build in small steps
- Follow the rules in `.claude/rules/` (they load when you touch matching files) and the non-negotiables in `CLAUDE.md`.
- Use the `new-migration` skill for schema changes and `new-endpoint` for endpoints.
- Ask before adding any dependency that isn't named in the task or in TECH_STACK.md.

## 4. Verify every "Done when" item
- Run the tests and commands, and quote the decisive output lines (test summary, health check response, …).
- If something fails, fix it or report it plainly with the output. **Never tick a task with a failing or unrun check.** If a check can't run here (for example, there's no GitHub remote), say so explicitly.

## 5. Keep docs in sync
If a table, endpoint, rule or decision changed, update its doc in the same change. Run the `sync-docs` skill after endpoint or schema work, and `sync-readme` if the README changed.

## 6. Close out
- Ask the `spec-reviewer` subagent to review the change, and fix anything serious it finds (or tell the user why not).
- Tick the task (`- [x]`) in `docs/SPRINT_PLAN.md`.
- Update the "Current focus" line in `CLAUDE.md` to the next task.
- Summarise for the user:
  - what changed
  - what was verified, and how
  - anything left over or worth a follow-up
- Propose a commit message `Sprint <id>: <summary>`. Commit only if the user asks.
