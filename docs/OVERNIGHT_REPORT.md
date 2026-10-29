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
| 1.2 Machine prerequisites | done | `47c29ff` *Sprint 1.2: Verify machine prerequisites and document JAVA_HOME* | Verified JDK 25, Docker, Node 24, an ignored `.env` and context7; added a `JAVA_HOME` troubleshooting row to DEVELOPMENT.md §7 | [1.2](build-journal/sprint-1/1.2-machine-prerequisites.md) |
| 1.3 Docker Compose: Postgres and SeaweedFS | done | `3447288` *Sprint 1.3: Add Docker Compose for Postgres and SeaweedFS* | Root `docker-compose.yml`: Postgres 17 (pgvector) and SeaweedFS 4.48 on 127.0.0.1 with health checks, named volumes and `.env` credentials; unsigned S3 gets `403`; V1 SQL shown to apply cleanly | [1.3](build-journal/sprint-1/1.3-docker-compose.md) |
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
| 1 | 1.2 | SPRINT_PLAN 1.2; `.env.example` history (`c45c99b`) | `.env` has every variable `.env.example` lists, judged from timestamps and git history without opening it (the only later change to `.env.example` was a comment line). Task 1.3 confirmed the five variables Compose uses: Compose started, and a signed S3 call and a password login over TCP worked. The rest are first read by the backend's `local` profile (task 2.2 on) |
| 2 | 1.2 | `.claude/skills/implement-task/SKILL.md` step 7 | The build-journal index table and `sprint-1/README.md` are updated after the sprint's last task (1.3), not per task |
| 3 | 1.3 | SPRINT_PLAN 1.3; TECH_STACK §1 (ports, no interface) | Every published port (5432, 8333, 8888) listens on `127.0.0.1` only. The filer on 8888 has no login (it reads, writes and deletes), and Postgres has a development password. `host.docker.internal` still reaches loopback-bound ports on Docker Desktop. Documented in DEVELOPMENT.md §3 and TECH_STACK §1 |
| 4 | 1.3 | TECH_STACK §1 ("Pin the image tag"); D-03 | SeaweedFS pinned to `chrislusf/seaweedfs:4.48`, not the newest 4.51 or 4.50 (= `latest`): those hold the first write on a fresh server for about 15 s (seaweedfs#12145), which every Testcontainers run would pay, and there's no plain 4.49 image. The reason lives in D-03 (Version) |
| 5 | 1.3 | D-03; SeaweedFS 4.48 `auth_credentials.go` | D-03's "an identity file makes SeaweedFS ignore the environment variables" (from the wiki) treated as out of date: 4.48 merges them. Explanation corrected in place; the decision (env vars only, no identity file, no `-s3.config`) is unchanged |
| 6 | 1.3 | TECH_STACK §2 Profiles; DEVELOPMENT §3 | Compose creates no bucket. `memoirist-media` is created by the backend's `local` profile (task 3.1), so SeaweedFS has no buckets until then |
| 7 | 1.3 | D-04; SPRINT_PLAN 1.3; `.claude/rules/backend.md` | `pgvector/pgvector:pg17` kept as a floating tag (17.11 with pgvector 0.8.7 today), matching the docs and the test image, rather than pinning a minor version or digest |
| 8 | 1.3 | Overnight brief (credentials from `.env`) | `POSTGRES_DB` is read from `.env` too, all five variables use `${VAR:?}` so Compose refuses to start on an empty one, and `PGUSER`/`PGDATABASE` are set from the same values so `docker compose exec postgres psql` needs no flags |
| 9 | 1.3 | `.claude/skills/implement-task/SKILL.md` step 7; assumption 2 | The Sprint 1 overview (`build-journal/sprint-1/README.md`) and its index row were written in task 1.3, Sprint 1's last task, then completed in the sprint's close-out commit (*Sprint 1: add the build journal overview*) with the commit hashes, the test strategy and the lessons |
| 10 | 1.3 | DATABASE_SCHEMA.md Conventions; SPRINT_PLAN 2.3; the run brief (SQL edits only on failure) | V1's two inline `UNIQUE` constraints keep Postgres's default names (`users_email_key`, `media_object_key_key`), not `uq_<table>_<cols>`. Task 2.3 copies V1 as written, so once it's committed a rename needs a V2 migration |

## Needs attention
- **`JAVA_HOME` is stale in Claude Code, not wrong in Windows** (task 1.2, [journal §8](build-journal/sprint-1/1.2-machine-prerequisites.md#8-what-went-wrong)). This session inherited `JAVA_HOME` = `C:\Users\<you>\…\jdk-22`, a folder that no longer exists, so `./gradlew` would stop with `JAVA_HOME is set to an invalid directory`. Windows' saved (Machine) value is already `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\`; Claude Code was started before it changed and kept the old copy. **Fix:** quit Claude Code completely, reopen it and check `echo $JAVA_HOME`; if it's still old, sign out of Windows and back in. No variable needs changing. Until then the run sets `JAVA_HOME` to JDK 25 in each Gradle command, and WORKFLOW.md §5 check 2 doesn't pass.
- **Leftover `PATH` entry** (task 1.2): the machine `PATH` still lists the missing `jdk-22\bin` folder. Harmless (it sits after JDK 25's), but you could remove it in System variables → Path.
- **For task 2.1** (task 1.2, via context7): Java 25 needs Gradle 9.1.0 or later, both to run Gradle and as a toolchain. Use the Testcontainers version Spring Boot manages; the Docker engine accepts API 1.40 or newer. A Java toolchain (and possibly Daemon JVM criteria) lets the build pick its own JDK instead of relying on `JAVA_HOME`, although the wrapper still needs a valid `JAVA_HOME` (or `java` on `PATH`) to start; `./gradlew -q javaToolchains` lists the JDKs Gradle finds.
- **Not memoirist's** (task 1.2): another project's Ollama container is stuck in a restart loop (`Restarting (1)`), and `docker info` warns that cgroup v1 support is deprecated (removal by May 2029). Neither blocks this project; neither was touched.
- **Review D-03** (task 1.3; DECISIONS.md lines 76–79): its explanation was corrected (SeaweedFS 4.48 merges an identity file with the `.env` keys; the wiki says it ignores them), and a Version bullet now pins `chrislusf/seaweedfs:4.48` because of seaweedfs#12145. Move on once a release fixes it, and re-check the unsigned `403`. The decision itself is unchanged.
- **For task 3.1** (task 1.3): the Testcontainers SeaweedFS should use the same `4.48` tag (D-03, Version; also in `.claude/rules/backend.md`). 4.50 and 4.51 would add about 15 s to the first upload of every test run.
- **V1 constraint names** (task 1.3, assumption 10): if you want `uq_users_email` and `uq_media_object_key`, that's now a V2 migration.
- **Docker kills containers about 3 s after SIGTERM** on this machine, not the documented 10 s, for both `docker stop` and `docker compose stop` (task 1.3, [journal §8](build-journal/sprint-1/1.3-docker-compose.md#8-what-went-wrong)). SeaweedFS is covered by `stop_grace_period: 30s` (clean stop in about 17–19 s); other projects' containers may be killed on stop. Docker Desktop's settings weren't opened.
- **The SeaweedFS filer on `127.0.0.1:8888` has no login** and allows reads, writes and deletes, while S3 on 8333 rejects unsigned requests. Keep the loopback binding. Not yet checked: whether it sends permissive CORS headers, which would let a web page in your browser reach it.
- **Session-output slips** (task 1.3, journal §8): SeaweedFS logs `name=admin-<first 8 characters of the access key ID>` at startup, and the builder's first scrub missed that prefix, so it appeared in the session's tool output (not the repo, not the journal; the key ID, not the secret). The builder also ran `env | grep -c '^COMPOSE_'` once, which the run's rules forbid; it printed only `0`.
- **Not verified by the review** (task 1.3, journal §9): a first start on empty volumes with the final file (`down -v` needs approval), the `winpty` troubleshooting fix, and the Mermaid diagram rendering. The Sprint 1 overview's diagram was redrawn from `docker-compose.yml` in the sprint's close-out and wasn't rendered either; GitHub renders it in the pull request's file view.
- **Disk and Docker state:** task 1.3 pulled about 2 GB of images: `chrislusf/seaweedfs:4.48`, `pgvector/pgvector:pg17` and `amazon/aws-cli:2.39.1`. The aws-cli image was only for checks: `docker rmi amazon/aws-cli:2.39.1` removes it. It also added two named volumes, `memoirist_postgres-data` and `memoirist_seaweedfs-data`. The two Compose services (`memoirist-postgres-1`, `memoirist-seaweedfs-1`) were left running for Sprint 2; every throwaway container was removed.
- **Branch names:** git can't hold both `phase-1` and `phase-1/sprint-1` (a branch name can't also be a folder of branches), so the sprint branches are `phase-1-sprint-1`, `phase-1-sprint-2` and `phase-1-sprint-3`. They still match the `phase-1*` push rule. WORKFLOW.md §5 (line 158) and `docs/prompts/overnight-phase-1.md` (line 24) name the impossible pair; worth fixing before the next overnight run.

## Questions for the morning
