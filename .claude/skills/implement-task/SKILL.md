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
- Check that the tasks above it in its sprint are ticked; go by position in the file, not by number. If one isn't, tell the user and ask whether to continue.
- If the task depends on an open question (Q-x) or flow gap (G-xx) that isn't decided, stop and ask, with the options, their trade-offs and a recommended default. Don't pick an answer silently.

## 2. Offer a hands-on piece (learning)
The user is building this project to grow as an engineer (`docs/LEARNING.md`).
- Read the **About me** section and the newest entries of `docs/LEARNING_LOG.md`.
- If the task has no code worth learning from (installs, config only), say so in one line and skip to step 3.
- Otherwise ask with AskUserQuestion: *"Do you want to write part of this task yourself?"* Offer two pieces from **this** task plus *"Not this time"*:
  - one that **transfers to their job** (match the About me section), and
  - one that's a **pattern, idea or syntax they haven't seen** (check the log's skills tracker so you don't repeat one at *Can do*).
- For each piece give: what they'd write (one sentence), what they'd learn, roughly how long (15–45 min), and why it matters in this codebase. Recommend one, with the reason.
- Pick pieces with a real decision in them (validation rules, a query, a test, a transaction boundary, a mapper with redaction), never boilerplate. Avoid pieces where a mistake could leak a sealed entry unless the tests would catch it.
- If they choose a piece, the plan in step 3 names it; build everything around it, leave a single `TODO(human)` with the context, the task and the trade-offs to weigh, then wait. Review their version, explain any changes you'd make and why, and agree fixes before continuing.
- If they say *"Not this time"*, keep any `TODO(human)` pieces from the Learning output style small and occasional.

## 3. Plan, then wait
Before editing anything, present:
- the hands-on piece, if one was chosen
- the files you'll create or change
- the tests you'll write and the commands you'll run to prove each **Done when** item
- doc updates (API_CONTRACT, DATABASE_SCHEMA, DECISIONS)
- anything that would change what the README promises, or in which phase (ask before going further)
- anything ambiguous, or where docs and code disagree, as questions

Wait for the user's go-ahead, unless they already said to proceed without asking.

## 4. Build in small steps
- Follow the rules in `.claude/rules/` (they load when you touch matching files) and the non-negotiables in `CLAUDE.md`.
- Use the `new-migration` skill for schema changes and `new-endpoint` for endpoints.
- Ask before adding any dependency that isn't named in the task or in TECH_STACK.md.

## 5. Verify every "Done when" item
- Run the tests and commands, and quote the decisive output lines (test summary, health check response, …).
- If something fails, fix it or report it plainly with the output. **Never tick a task with a failing or unrun check.** If a check can't run here (for example, there's no GitHub remote), say so explicitly.

## 6. Keep docs in sync
If a table, endpoint, rule or decision changed, update its doc in the same change. Run the `sync-docs` skill after endpoint or schema work, and `sync-readme` if the README changed.

## 7. Close out
- Ask the `spec-reviewer` subagent to review the change, and fix anything serious it finds (or tell the user why not).
- Tick the task (`- [x]`) in `docs/SPRINT_PLAN.md`.
- Update the "Current focus" line in `CLAUDE.md` to the next task.
- Summarise for the user:
  - what changed
  - what was verified, and how
  - anything left over or worth a follow-up
- Suggest `/review-with-me` before committing, then `/log-learning` to record what they practised.
- Propose a commit message `Sprint <id>: <summary>`. Commit only if the user asks.
