# Workflow: building memoirist with Claude Code

> How work moves from plan to shipped code, and how the docs stay in sync while it does. For setup and commands see [DEVELOPMENT.md](DEVELOPMENT.md).

## 1. The units of work

| Unit | Example | Where it's defined | Sessions |
|---|---|---|---|
| **Phase** | Phase 1: The core journal | [README Timeline](../README.md#timeline) and [PRD.md §5](PRD.md) | Many |
| **Feature** | `P1-04` Loot Drops | `docs/features/` (brief per feature) | A few |
| **Task** | `3.5` Entries API | [SPRINT_PLAN.md](SPRINT_PLAN.md) | One |

Features are created by running [prompts/split-into-features.md](prompts/split-into-features.md). Briefs for a phase are written when that phase is next, once its open questions are settled.

## 2. Workflow for each feature

Work on one feature at a time. Use a fresh session (or `/clear`) for each **task** inside it.

| Step | What you do | Claude tools |
|---|---|---|
| 1. Check it's ready | Ask whether the feature is ready. Fix any gaps it finds, and settle its open questions | `/sprint-ready`, `/record-decision` |
| 2. Spec first | In **Plan mode**, read the brief and update the specs it touches (API_CONTRACT, DATABASE_SCHEMA, USER_FLOWS). Commit them on their own: `Spec P1-04: …` | Plan mode, context7 |
| 3. Build, task by task | One session per sprint task. Approve the plan, then let it build and test | `/implement-task 3.5`, `/new-endpoint`, `/new-migration` |
| 4. Review | Ask for a review against the specs, then a bug review | `spec-reviewer` subagent, `/code-review` |
| 5. Sync and close the task | Make sure the docs match the code, tick the task, then commit: `Sprint 3.5: …` | `/sync-docs` (the Stop hook reminds you) |
| 6. Finish the feature | Run the brief's "Done when" demo, set its status to `Done`, and add a changelog line to the brief | — |

### Step by step: exactly what to type
Copy the text in each block into Claude Code. Replace `P1-04`, `3.5` and so on with your feature and task. Every tool named here is described in [CURRENT_SKILLS.md](CURRENT_SKILLS.md).

#### Once, before your first feature
1. New session in **Plan mode**. Paste the whole prompt from [prompts/split-into-features.md](prompts/split-into-features.md). Approve the build-order report and the feature table, then let it write `docs/features/`.
2. Commit the result: *"Commit the planning docs as 'Planning baseline'."*

#### A. Prepare the feature (one session, Plan mode, no code)
```text
Prepare feature P1-04 for implementation; no code.
1) Run /sprint-ready for it and list blocking questions. Ask me each one and record my answers with /record-decision.
2) Use context7 to confirm any library APIs the feature depends on.
3) Draft the spec changes to API_CONTRACT, DATABASE_SCHEMA and USER_FLOWS.
4) Have the spec-reviewer subagent review those spec changes.
5) Show me the findings and the final spec diff, and wait for my approval before committing as "Spec P1-04: …".
```
Building two features at once? First run `/parallel-check P1-04 P1-06`, then start one worktree session per feature and paste its kickoff note at the top of every session for that feature.

#### B. Build each task (a new session for every task)
1. Start the task:
   ```text
   /implement-task 3.5
   ```
   First it offers you a piece to write yourself (or "not this time"). Then read the plan it shows you. Approve it or ask for changes. It builds, leaves your piece as `TODO(human)` if you chose one, writes tests, runs them and quotes the results.
2. Get an independent review:
   ```text
   Use the spec-reviewer subagent to review my uncommitted changes for task 3.5. Show me the findings before fixing anything.
   ```
   Tell it which findings to fix. Ask for the review again until nothing serious remains.
3. Optional bug hunt: `/code-review`. Optional, but worth it for learning: `/review-with-me`, then `/log-learning` ([LEARNING.md](LEARNING.md)).
4. Check the docs and commit:
   ```text
   Run /sync-docs, then commit as "Sprint 3.5: <summary>".
   ```
5. Type `/clear` or open a new session, then repeat B for the next task in the feature.

#### C. Finish the feature
```text
Feature P1-04 is complete. Run the "Done when" check from its brief and quote the result, run /sync-docs across the branch, have the spec-reviewer subagent review the whole branch since main, then set its status to Done in docs/features/README.md, add a changelog line to the brief, and update Current focus in CLAUDE.md.
```
If you worked on a branch, merge it into `main` now.

#### When something comes up mid-way
| Situation | Type |
|---|---|
| Claude asks a question you've now answered | `/record-decision "<the answer>"` |
| You're unsure how a library works today | "Use context7 to check …" |
| The docs and code disagree | `/sync-docs`, then answer its questions |
| You edited the README | `/sync-readme`, then answer its questions |
| The session got long or confused | `/clear`, then restart the current step |
| You want to go back to what you had | Ask Claude to rewind, or use git to restore the last commit |

### Getting the most out of Claude
- **Fresh context beats long chats.** CLAUDE.md, the path-scoped rules and the brief bring back what Claude needs, so a new session loses nothing.
- **Plan before code.** Correcting a plan is cheap; correcting code isn't.
- **Make it prove things.** Every "Done when" item is something Claude can run. Ask it to quote the test output.
- **Use the right tool.** Subagents for independent review, context7 for current library docs, skills for recipes you repeat. Pasted the same instructions twice? Turn them into a skill.
- **Run features in parallel only when they touch different files.** Check with `/parallel-check P1-04 P1-06`: it finds overlaps, reserves a migration number for each feature, gives a merge order, and writes a kickoff note to paste into each worktree session.
- **Ask, don't let it guess.** When docs disagree or a question is open, Claude should stop and ask, as a short list of questions with a recommended answer for each. Answer with `/record-decision`.
- **Check what Claude knows.** `/context` shows what's loaded, `/memory` opens CLAUDE.md and Claude's auto memory, and `/doctor prompt-audit` finds outdated or conflicting instructions. [CURRENT_SKILLS.md](CURRENT_SKILLS.md) lists what loads in every session.

## 3. Documents that evolve across phases

There is **one living file per topic**, not a copy per phase.
- Each spec has a section per phase, with a status: **Draft** (a later phase, not reviewed yet), **Planned** (reviewed and ready to build), or **Built** (matches the code).
- **Earlier phases stay open.** A later phase never edits committed SQL: it adds a new migration. When a decision changes, its D-entry is updated in place with an **Updated** date, and a new topic gets a new D-entry (D-22, overlapping Eras, and D-26, tags moved into Phase 1, are examples). Git keeps the old wording.
- **History comes from git, not duplicate files.** Tag each finished phase:
  ```bash
  git tag phase-1-done
  git show phase-1-done:docs/DATABASE_SCHEMA.md   # the schema as it was then
  ```
- **Each feature brief keeps a changelog** of what changed in that feature and why.

## 4. Keeping everything in sync

### Every fact has one home
| Fact | Lives in | Everywhere else |
|---|---|---|
| What users get, and in which phase | The project [README](../README.md), which wins on what and when (D-27) | PRD and SPRINT_PLAN follow it |
| How and why something works | [DECISIONS.md](DECISIONS.md), which wins on how (D-27) | Links to the D-entry |
| Tables and columns | [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md), then the migrations once built | Links |
| Endpoints and shapes | [API_CONTRACT.md](API_CONTRACT.md), then `/v3/api-docs` once built | Links; frontend types are generated, never hand-written |
| What the user does | [USER_FLOWS.md](USER_FLOWS.md) | Links to `F-xx` |
| What gets built, and in what order | Feature briefs and [SPRINT_PLAN.md](SPRINT_PLAN.md) | Never restate specs |
| How it's built | [TECH_STACK.md](TECH_STACK.md) and `.claude/rules/` | Links |
| Commands | [DEVELOPMENT.md](DEVELOPMENT.md); CLAUDE.md keeps a short copy | Update both together |

**Link, never copy.** A brief or a task points to a section; it doesn't repeat its contents.

### What keeps it honest
| Tool | Catches |
|---|---|
| `/record-decision` | A settled question not reflected in every doc that assumed otherwise |
| `/sync-docs` | Drift between the code and the API contract or schema |
| `/sync-readme` | The README promising something the docs don't plan, or the reverse |
| Stop hook | Code changed but its spec didn't; the README changed but the product docs didn't; Current focus fell behind |
| SessionStart hook | A new session not knowing the next task or what blocks it |
| `spec-reviewer` | A change that contradicts the specs or the security rules |
| `/sprint-ready` | Missing sections, open questions or conflicts before you start |
| `/parallel-check` | Two features that would edit the same files or claim the same migration number |
| Habit | Commit spec changes before the code that implements them; run `/sync-docs` at the end of every feature |

### IDs used across the docs
| Prefix | Meaning | Defined in |
|---|---|---|
| `D-xx` | Decision | DECISIONS.md |
| `Q-xx` | Open question | DECISIONS.md |
| `G-xx` | Gap found while drafting flows | USER_FLOWS.md |
| `F-xx` | User flow (also names its Playwright test) | USER_FLOWS.md |
| `Pn-xx` | Feature | docs/features/ |
| `n.n` | Sprint task | SPRINT_PLAN.md |
| `R-xx` | Recommendation from the feature review | product/FEATURE_REVIEW.md |

## 5. Overnight runs
Several sprints can be built unattended, each as a stacked PR you review in the morning. The prompt is [prompts/overnight-phase-1.md](prompts/overnight-phase-1.md).

**Checklist before you start it:**
| # | Check |
|---|---|
| 1 | Working tree clean and pushed on `main` |
| 2 | `java -version` reports 25 and `JAVA_HOME` points to JDK 25 (reopen the terminal and Claude Code after installing) |
| 3 | Docker Desktop running, and set to start when you sign in |
| 4 | `.env` exists |
| 5 | GitHub CLI installed and logged in: `gh auth status` |
| 6 | `/sprint-ready` run for each sprint in scope, with every question answered, so the run uses your answers rather than the defaults |
| 7 | Windows sleep set to *Never* while plugged in |
| 8 | In Claude Code: auto mode on, *continue when the usage limit resets* on, and `/output-style learn-later` |

**How it behaves:**
- One fresh agent per task, with the same gates as by hand: checks pass, `spec-reviewer` clean, docs synced, then a commit and push.
- Branches: `phase-1` from `main`, then `phase-1/sprint-1`, `phase-1/sprint-2`… each from the one before. Each sprint's PR targets the previous sprint's branch, so its diff is only that sprint.
- It never stops for a question or a failure (only for a failed preflight check). It uses the documented defaults, marks failing work `[needs-attention]` and opens that sprint's PR as a draft, and lists everything in `docs/OVERNIGHT_REPORT.md` and in its final message.
- It never merges, never pushes to `main` and never force-pushes. Project settings allow pushes only to `phase-1*` branches.

**In the morning:**
1. Read the report: assumptions, needs-attention items, practice blocks.
2. Review each sprint PR in order, ideally with `/review-with-me <branch>` and then `/log-learning`.
3. Merge sprint 1 into `phase-1`, retarget sprint 2's PR to `phase-1` (`gh pr edit <n> --base phase-1`), and so on. When the phase is reviewed, open a PR from `phase-1` to `main`.
4. Settle each assumption you disagree with using `/record-decision`, and ask Claude to apply it on the right branch.
5. Switch back to the Learning style for daytime work: `/output-style learning`.

## 6. Commits and milestones
- Spec changes: `Spec P1-04: …`
- One commit per task: `Sprint 3.5: …`
- When a phase is finished: tag it (`phase-1-done`), and check that every spec section for that phase says **Built**.
