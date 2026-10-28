# Prompt: build Phase 1 overnight

Before pasting, do the checklist in [WORKFLOW.md §5 "Overnight runs"](../WORKFLOW.md#5-overnight-runs). Then start a **new session in auto mode**, run `/output-style learn-later`, and paste everything below the line.

To include Sprint 4 (the React UI), change the **Scope** line. Sprint 4 needs `npm install`, which still asks for approval, and a design (`docs/design/DESIGN.md`), so it's best run on a night after you've picked one.

---

Build memoirist's Phase 1 while I'm asleep. **Use a workflow**: one fresh agent per sprint task, run one after another. One agent per task is fine (about 30–60 agents in total). Don't wait for me at any point; I'll read your report in the morning.

**Scope:** Sprints 1, 2 and 3 (tasks 1.2 to 3.17). Skip any task that's already ticked.

## Before starting (preflight)
0. **Wait for other sessions to finish.** Other Claude sessions share this folder (for example *Design Examples*). Before touching git:
   - List my sessions and wait while any **other** session in this folder is running. Check every 5 minutes.
   - Then wait until `git status --porcelain` stays the same for 10 minutes in a row, so nothing is still being written.
   - If other sessions left uncommitted changes, commit them on `main` as their own commit, `With a relevant commit message relating to validating and enhancing design mocks`, listing the files in the body. Don't push `main`. Mention it in the report.
   - If something is still running after 2 hours, start anyway and list it in the report.
1. Ask the app to keep the computer awake for the run.
2. Check: `java -version` reports 25, `docker info` succeeds, `gh auth status` is logged in, `.env` exists (check that it exists; never read it), and the working tree is clean on `main` (after step 0). **This is the only reason to stop:** if any check fails, nothing can be built or verified, so stop before doing anything and tell me which check failed.
3. Create the branch `phase-1` from `main` and push it (`git push -u origin phase-1`).

## Branches and pull requests (stacked)
- Sprint 1: create `phase-1/sprint-1` from `phase-1`. Each later sprint branches from the previous sprint's branch: `phase-1/sprint-2` from `phase-1/sprint-1`, and so on.
- After a sprint's last task, push its branch and open a PR with `gh pr create`. Sprint 1's PR targets `phase-1`; each later sprint's PR targets the previous sprint's branch, so each PR shows only that sprint's diff.
- Open a sprint's PR as a **draft** (`--draft`) if any of its tasks needs attention (below).
- Always push with an explicit branch: `git push origin <branch>`. Never push to `main`, never force-push, and never merge a PR.
- The PR description links the sprint's build journal (`docs/build-journal/sprint-N/README.md`) and lists: each task and what it built, the tests run with their results, the practice blocks (file:line and what each teaches), the assumptions made, and anything that needs my attention.

## Each task (one fresh agent)
Each agent runs the `/implement-task <id>` process for one task, unattended:
- Follow `.claude/output-styles/learn-later.md`: don't ask me anything; write complete, working code, and mark one or two pieces per task as practice blocks (`TODO(human) PRACTICE` with the answer in a marked block). Skip practice blocks for tasks with nothing worth learning (1.2, config only).
- **Gates, in order, before the task counts as done:**
  1. Every "Done when" item is checked by running something, with the decisive output quoted. Tests pass with the answers in place.
  2. The `spec-reviewer` subagent reviews the task's changes; fix everything it marks blocker or major.
  3. Docs are in step: run `/sync-docs` after endpoint or schema work.
  4. Tick the task in `docs/SPRINT_PLAN.md`, update CLAUDE.md's "Current focus", and commit as `Sprint <id>: <summary>` with no Claude attribution. Push the sprint branch.
- Pass each agent the previous tasks' commit messages and any assumptions made so far, so it builds on them.

## The build journal (I'll study it in detail)
I want to learn **how** Sprints 1–3 were built, step by step, so every task gets a journal entry. Follow `docs/build-journal/README.md` and `docs/build-journal/TEMPLATE.md`:
- **Every task** (1.2 included) writes `docs/build-journal/sprint-N/N.M-short-name.md` and commits it in the same commit as the task. The entry covers: the goal and why it comes now; what was read and what it settled; the plan and its order; **every real decision with the alternatives and why they lost**; a file-by-file walkthrough; the test strategy and how to run the tests myself; evidence for each "Done when" item; **everything that went wrong, including failed attempts**; the review findings; the concepts and syntax to learn; the practice blocks; how it transfers to work at a bank; and 5–8 check-yourself questions with folded answers.
- **Keep notes while working,** from the first file read, so the entry records what really happened. Don't tidy away dead ends: they're the most useful part.
- **After each sprint's last task**, write `docs/build-journal/sprint-N/README.md`: what the sprint set out to do, how its tasks fit together, a Mermaid diagram of the architecture at the end of the sprint, the test strategy as a whole (what's unit-tested, what's integration-tested and why), how to run everything, and the lessons. Update the table in `docs/build-journal/README.md`.
- Write for me: a full-stack engineer who knows Java, Spring Boot, React and PostgreSQL, and wants the *why* behind each choice and better recall of the syntax (About me in `docs/LEARNING_LOG.md`).
- The journal is part of the work, not optional: a task isn't done until its entry is written. If time or context runs short, keep the decisions, what went wrong and the tests; trim the rest.
- **Task 1.3 extra check:** before ticking it, run the V1 SQL and the seed from `docs/DATABASE_SCHEMA.md` against the running Postgres in a throwaway database, and fix the docs if they fail (then 2.3 uses the fixed SQL).

## Never stop for these: work around them and note them
| Situation | What to do |
|---|---|
| An open question (Q-x) or flow gap (G-xx) | Use its "Default if nobody decides" from `docs/DECISIONS.md` or `docs/USER_FLOWS.md`. Don't record a decision; list it as an **assumption** |
| Two docs disagree | README wins on what and when, DECISIONS.md on how (D-27). Follow the winner, don't edit the losing doc, and list it |
| Tests still failing after 3 fix attempts | Commit the work with `[needs-attention]` at the end of the commit title, record the failing test and its output, mark the PR as a draft, and carry on with the next task |
| A library or dependency not named in the docs | Don't add it. Build it with what's approved, or leave the smallest stub with a `TODO(human) DECIDE:` comment, and list it |
| Anything that would change the README | Don't change it; list what you'd change and why |
| A secret or external account is needed (for example Spotify in task 3.14) | Use the fake or local adapter the docs describe, keep the real one behind configuration, and list what I need to set up |
| A push or `gh` command fails | Keep working locally on the branch, retry at the end, and list it |

## Running log
Keep `docs/OVERNIGHT_REPORT.md` up to date as you go (commit it with each task), so nothing is lost if the session is compacted or interrupted: one line per task (done, or needs attention), plus the assumptions and questions.

## When everything is done
Post the report in the chat:
1. A table: each sprint, its PR link, its tasks, done or needs attention.
2. **Assumptions** you made instead of asking me, each with the Q-x, G-xx or doc it came from.
3. **Needs my attention:** failing tests, skipped dependencies, README changes you'd suggest, and things I need to set up.
4. **Practice blocks** to try, grouped by sprint.
5. **Where to start studying:** the build journal entries worth reading first, and why.
6. **Merge order** for the morning, and the commands to retarget each PR after I merge the one before it.
