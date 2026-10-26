---
name: sprint-ready
description: Check whether a sprint in docs/SPRINT_PLAN.md is ready to start — blocking open questions, vague "Done when" items, missing or conflicting doc sections. Read-only report; run before starting a sprint.
argument-hint: "[sprint number, e.g. 3 — omit for the current sprint]"
disable-model-invocation: true
allowed-tools: Read, Grep, Glob
---

# Is the sprint ready?

Sprint: **$ARGUMENTS**. If empty, use the sprint containing the first unchecked task in `docs/SPRINT_PLAN.md`.

This is a **read-only** check. Don't edit any file.

1. **Read the sprint.** Every task block in it: **Read**, **Do**, **Done when**.

2. **Check each task:**

   | Check | Problem if… |
   |---|---|
   | Open questions | The task, or the sprint's "Decide first" line, depends on a Q-x in `docs/DECISIONS.md` that is still open |
   | Done when | An item can't be checked by running something (e.g. "works well", "looks good") |
   | Read links | A referenced doc section, D-xx or Q-x doesn't exist |
   | Contract | Endpoints the task builds are missing from `docs/API_CONTRACT.md`, or lack status codes or limits |
   | Schema | Tables or columns the task needs are missing from `docs/DATABASE_SCHEMA.md` |
   | Conflicts | Two docs say different things about something the task touches (DECISIONS.md wins; flag the loser) |
   | Order | The task needs something an earlier unticked task would create |
   | Scope | The task pulls in something from a later phase |

3. **Report:**
   - A verdict: **Ready**, **Ready with warnings**, or **Blocked**.
   - A table: `Task | Problem | Where | Suggested fix`.
   - The questions the user must answer, each phrased plainly with a recommended default.

4. Offer to fix the doc problems (with `/record-decision` for answered questions). Don't fix anything without the user's go-ahead.
