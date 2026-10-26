---
name: sprint-ready
description: Read-only check that a sprint (or a feature) in docs/SPRINT_PLAN.md is ready to start — README items with no task, open questions and gaps that are due, untestable "Done when" items, missing or conflicting doc sections, sealed-entry leaks, rules and the Current focus line. Run before starting a sprint, e.g. "/sprint-ready 3".
argument-hint: "[sprint number, e.g. 3, or a feature ID such as P1-04 — omit for the current sprint]"
disable-model-invocation: true
allowed-tools: Read, Grep, Glob, Bash(sed -n *), Bash(git log *), Bash(git diff *)
---

# Is the sprint ready?

Sprint: **$ARGUMENTS**. If empty, use the sprint containing the first unchecked task. If it's a feature ID (`Pn-xx`), check that feature's brief in `docs/features/` and the tasks it lists.

## Live context
- First unchecked task: !`sed -n '/^- \[ \]/{p;q}' docs/SPRINT_PLAN.md`
- "Decide first" lines: !`sed -n '/Decide first/p' docs/SPRINT_PLAN.md`
- Current focus in CLAUDE.md: !`sed -n '/Next task/p' CLAUDE.md`
- README last changed: !`git log -1 --format="%h %ad %s" --date=short -- README.md`

This is a **read-only** check. Don't edit any file.

1. **Read the sprint**: every task block (**Read**, **Do**, **Done when**) and the sprint's "Decide first" line. Read the README's Timeline items for the sprint's phase, the open-questions table in `docs/DECISIONS.md`, and the gaps table at the end of `docs/USER_FLOWS.md`.

2. **Check each task and the sprint as a whole:**

   | Check | Problem if… |
   |---|---|
   | README coverage | A README Timeline item for this phase has no task in this sprint or an earlier one; or a task builds something the README puts in a later phase or no longer promises (D-27) |
   | Open questions | A task, or the "Decide first" line, depends on a Q-x that is still open |
   | Question timing | A Q-x or G-xx whose "Needed by" is this sprint or earlier is missing from "Decide first"; or an open question's answer would change a table or endpoint built by an earlier, unticked task (for example the V1 migration in task 2.3) |
   | Done when | An item can't be checked by running something (e.g. "works well", "looks good") |
   | Read links | A referenced doc section, D-xx, Q-x or F-xx doesn't exist |
   | Contract | Endpoints the task builds are missing from `docs/API_CONTRACT.md`, or lack status codes or limits |
   | Schema | Tables or columns the task needs are missing from `docs/DATABASE_SCHEMA.md` |
   | Flows | A UI task doesn't name its F-xx flow in `docs/USER_FLOWS.md`, or its Done when misses the flow's edge cases |
   | Sealed leaks | A list, count, filter, search or export the task builds doesn't say how sealed entries are handled (D-10) |
   | Conflicts | Two docs say different things about something the task touches (README wins on what and when, DECISIONS.md on how; flag the loser) |
   | Order | The task needs something an earlier unticked task would create |
   | Scope | The task pulls in something from a later phase |
   | Rules | `.claude/rules/` doesn't list a package or feature folder the sprint creates |
   | Focus | CLAUDE.md's "Current focus" doesn't name the first unchecked task |

3. **Report:**
   - A verdict: **Ready**, **Ready with warnings**, or **Blocked**.
   - A table: `Task | Problem | Where | Suggested fix`.
   - The questions the user must answer, each with its options, their trade-offs and a recommended default.

4. Offer to ask those questions interactively (AskUserQuestion) and record the answers with `/record-decision`. Don't fix anything without the user's go-ahead.
