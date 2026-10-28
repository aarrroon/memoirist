# Overnight report: Phase 1, Sprints 1–3

> An unattended build of tasks 1.2 to 3.17 ([prompt](prompts/overnight-phase-1.md)), started 2026-10-29. Every task's close-out step updates this file in the task's own commit, so it shows how far the run got even if it was interrupted. The full summary is posted in the chat when the run ends. How each task was built: [build journal](build-journal/README.md).

## Preflight
| Step | Result |
|---|---|
| Other sessions | *Design Examples* and *Document Setup - Read Files - Learning* were running at 00:46 (Melbourne). Both had finished by 00:52, and nothing was written to the working tree after 00:49. |
| Pause | The usage limit stopped this session from about 00:50 to 04:40. |
| Their uncommitted changes | Committed on `main` as `87ffdba` *Validate and enhance the design mocks, with sprint-readiness doc updates*: 6 design mocks, 8 sprint-readiness docs (D-12, D-23, D-34, D-36; tasks 3.11–3.16 renumbered) and 6 build-journal and prompt files, all listed in the commit body. `main` was **not** pushed. |
| `java -version` | 25.0.5 (Temurin) ✓. This session's `JAVA_HOME` is stale (a missing `jdk-22` folder), see *Needs attention* |
| `docker info` | Docker Desktop 29.9.2 ✓ |
| `gh auth status` | Logged in as `aarrroon` ✓ |
| `.env` | Exists and is gitignored ✓ (never read) |
| Working tree | Clean on `main` after the commit above ✓ |
| Branches | `phase-1` created from `main` and pushed. Sprint branches are `phase-1-sprint-N`, not `phase-1/sprint-N`, see *Needs attention* |

## Tasks
| Task | Status | Commit | What it built | Journal |
|---|---|---|---|---|
| 1.2 Machine prerequisites | done | `Sprint 1.2: Verify machine prerequisites and document JAVA_HOME` | Verified JDK 25, Docker, Node 24, an ignored `.env` and context7; added a `JAVA_HOME` troubleshooting row to DEVELOPMENT.md §7 | [1.2](build-journal/sprint-1/1.2-machine-prerequisites.md) |
| 1.3 Docker Compose: Postgres and SeaweedFS | pending | | | |
| 2.1 Spring Boot 4 project | pending | | | |
| 2.2 Configuration and profiles | pending | | | |
| 2.3 V1 migration and local seed | pending | | | |
| 2.4 Entities and repositories | pending | | | |
| 2.5 Current user resolution | pending | | | |
| 2.6 Global error handling | pending | | | |
| 2.7 CI: backend | pending | | | |
| 3.1 Storage service | pending | | | |
| 3.2 `/users/me` | pending | | | |
| 3.3 Eras CRUD | pending | | | |
| 3.4 Templates CRUD | pending | | | |
| 3.5 Entries: create, read, update, delete, seal, redaction | pending | | | |
| 3.6 Media | pending | | | |
| 3.7 Weekly logs and feed | pending | | | |
| 3.8 Tags | pending | | | |
| 3.9 Entry list for the timeline | pending | | | |
| 3.10 Opened Loot Drops | pending | | | |
| 3.11 Recently deleted | pending | | | |
| 3.12 On this day | pending | | | |
| 3.13 Loot Drop replies and the birthday | pending | | | |
| 3.14 Soundtracks and Spotify search | pending | | | |
| 3.15 Era covers | pending | | | |
| 3.16 Local sample content | pending | | | |
| 3.17 API review | pending | | | |

## Pull requests
| Sprint | Branch | Base | PR | State |
|---|---|---|---|---|
| 1. Setup and local infrastructure | `phase-1-sprint-1` | `phase-1` | | |
| 2. Backend foundation | `phase-1-sprint-2` | `phase-1-sprint-1` | | |
| 3. Phase 1 API | `phase-1-sprint-3` | `phase-1-sprint-2` | | |

## Assumptions
Made instead of asking, each from a documented default or the doc that wins (D-27). Settle any you disagree with using `/record-decision`.

| # | Task | From | Assumption |
|---|---|---|---|
| 1 | 1.2 | SPRINT_PLAN 1.2; `.env.example` history (`c45c99b`) | `.env` has every variable `.env.example` lists, judged from timestamps and git history without opening it (the only later change to `.env.example` was a comment line). Task 1.3 tests the values |
| 2 | 1.2 | `.claude/skills/implement-task/SKILL.md` step 7 | The build-journal index table and `sprint-1/README.md` are updated after the sprint's last task (1.3), not per task |

## Needs attention
- **`JAVA_HOME` is stale in Claude Code, not wrong in Windows** (task 1.2, [journal §8](build-journal/sprint-1/1.2-machine-prerequisites.md#8-what-went-wrong)). This session inherited `JAVA_HOME` = `C:\Users\<you>\…\jdk-22`, a folder that no longer exists, so `./gradlew` would stop with `JAVA_HOME is set to an invalid directory`. Windows' saved (Machine) value is already `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\`; Claude Code was started before it changed and kept the old copy. **Fix:** quit Claude Code completely, reopen it and check `echo $JAVA_HOME`; if it's still old, sign out of Windows and back in. No variable needs changing. Until then the run sets `JAVA_HOME` to JDK 25 in each Gradle command, and WORKFLOW.md §5 check 2 doesn't pass.
- **Leftover `PATH` entry** (task 1.2): the machine `PATH` still lists the missing `jdk-22\bin` folder. Harmless (it sits after JDK 25's), but you could remove it in System variables → Path.
- **For task 2.1** (task 1.2, via context7): Java 25 needs Gradle 9.1.0 or later, both to run Gradle and as a toolchain. Use the Testcontainers version Spring Boot manages; the Docker engine accepts API 1.40 or newer.
- **Not memoirist's** (task 1.2): another project's Ollama container is stuck in a restart loop (`Restarting (1)`), and `docker info` warns that cgroup v1 support is deprecated (removal by May 2029). Neither blocks this project; neither was touched.
- **Branch names:** git can't hold both `phase-1` and `phase-1/sprint-1` (a branch name can't also be a folder of branches), so the sprint branches are `phase-1-sprint-1`, `phase-1-sprint-2` and `phase-1-sprint-3`. They still match the `phase-1*` push rule. WORKFLOW.md §5 and `docs/prompts/overnight-phase-1.md` name the impossible pair; worth fixing before the next overnight run.

## Questions for the morning
