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
| 2.1 Spring Boot 4 project | done | *Sprint 2.1: Add the Spring Boot 4 backend project* | `backend/`: Gradle 9.8.2 wrapper with checksums, Kotlin DSL, Java 25 toolchain, Spring Boot 4.1.2 with the task's starters, springdoc 3.2.0, AWS SDK `s3`, Spotless and a Testcontainers smoke test; boots with the `local` profile, health `UP`, Swagger UI loads | [2.1](build-journal/sprint-2/2.1-spring-boot-project.md) |
| 2.2 Configuration and profiles | done | *Sprint 2.2: Add configuration, profiles and storage settings* | UTC set in `main` and a `Clock` bean, `ddl-auto=validate`, UTC JDBC, open-in-view off, 25/30 MB uploads, `application-local.yml` (`../.env`, Flyway locations), a test profile, a validated `StorageProperties` record and a shared `@IntegrationTest`; 23 tests pass and the app boots against Compose | [2.2](build-journal/sprint-2/2.2-configuration-and-profiles.md) |
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
| 1. Setup and local infrastructure | `phase-1-sprint-1` | `phase-1` | [#1](https://github.com/aarrroon/memoirist/pull/1) | Ready for review (not a draft). Its GitGuardian check fails on a false positive, see *Needs attention* |
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
| 11 | 2.1 | SPRINT_PLAN 2.1 vs the Spring Boot 4.0 migration guide | The sprint plan's "web" and "spring-boot-starter-test" are Boot 3 names. The build uses Boot 4's `spring-boot-starter-webmvc` and one `spring-boot-starter-<technology>-test` per starter, each of which brings `spring-boot-starter-test`, as the migration guide says. The sprint plan wasn't edited |
| 12 | 2.1 | D-14 vs the Spring Boot 4.1.2 BOM | D-14's "JUnit 5" was merely out of date: Boot 4.1.2 manages JUnit Jupiter 6.0.3. The wording was corrected in place (Updated 2026-10-29); the decision (JUnit and Testcontainers, no H2) is unchanged |
| 13 | 2.1 | SPRINT_PLAN 2.2; TECH_STACK §1 | Configuration is the minimum that boots: `application.yml` (the app's name) and `application-local.yml` (the `optional:file:../.env[.properties]` import TECH_STACK §1 specifies, and the datasource from `DB_URL`, `POSTGRES_USER` and `POSTGRES_PASSWORD`). Task 2.2 owns the rest |
| 14 | 2.1 | D-15 (Spotless, no formatter named) | Spotless runs palantir-java-format (4-space indents, 120 columns), pinned to 2.102.0, Spotless 8.10.4's default, so a Spotless upgrade can't reformat the code. See *Questions for the morning* |
| 15 | 2.1 | `.gitattributes` (`* text=auto`) and `core.autocrlf=true` | Spotless keeps its default line-ending policy, which follows git: CRLF in a Windows working copy, LF on Linux. A Java file saved with LF on Windows fails `spotlessCheck` until `./gradlew spotlessApply` |
| 16 | 2.1 | The run brief (wrapper from Initializr or services.gradle.org); 1.2's journal §10 | The wrapper came from Spring Initializr after its jar's SHA-256 matched Gradle 9.8.2's published sum, and `distributionSha256Sum` was added. No Daemon JVM criteria file: a portable one needs the foojay resolver plugin, which isn't approved |
| 17 | 2.1 | springdoc's FAQ ("pick the last stable version"); its 3.2.0 release notes | springdoc 3.2.0 (built on Boot 4.1.2, released 2026-10-24) over 3.1.1 (built on Boot 4.1.0) |
| 18 | 2.1 | JEP 451; Mockito's javadoc §0.3 | Mockito, already in `spring-boot-starter-test`, is loaded as a `-javaagent` for the test JVM, so the JDK doesn't warn about (and future JDKs don't block) Mockito attaching itself. Build configuration, not a new dependency |
| 19 | 2.1 | `.claude/skills/implement-task/SKILL.md` step 7; assumption 2 | As for Sprint 1, the build-journal index table and `sprint-2/README.md` are written after the sprint's last task (2.7) |
| 20 | 2.1 | SPRINT_PLAN 2.2 (owns the app's configuration) | `management.endpoint.health.show-components=always` is set only in the smoke test's `@SpringBootTest` properties, so it can prove that the database is one of the health checks. The app's own health settings are left to 2.2 |
| 21 | 2.2 | SPRINT_PLAN 2.2 Done-when vs how Spring tests run | The test JVM runs with `-Duser.timezone=UTC` (build.gradle.kts), so tests match production. Because that flag hides a missing line in `main`, `MemoiristApplicationTest` runs the real `main()` from Australia/Melbourne under `SpringApplication.withHook` and fails if `main` doesn't set UTC (mutation-checked) |
| 22 | 2.2 | SPRINT_PLAN 2.2 (silent); 2.1 handoff | `spring.jpa.open-in-view=false`, so services map to DTOs inside their transactions and a stray lazy load throws. Recorded in TECH_STACK §2 only; no D-entry. See *Questions for the morning* |
| 23 | 2.2 | TECH_STACK §1 (`optional:`) vs the 2.1 handoff on the misleading error | `optional:file:../.env[.properties]` kept as specified. The datasource and storage keys moved into `application.yml` as `${DB_URL:}` and similar empty defaults, so a missing `.env` fails with `'url' attribute is not specified` or by storage property name, not the old text trap |
| 24 | 2.2 | TECH_STACK §2 Storage; `.claude/rules/security.md` | `StorageProperties` requires all eight settings with no defaults, limits `presign-ttl` to 1 to 60 minutes (so `PT60H` stops the app) and checks the bucket name against S3's rules |
| 25 | 2.2 | Task brief; `.claude/rules/backend.md` | A composed `@IntegrationTest` annotation (SpringBootTest, RestTestClient, `test` profile, Testcontainers import) instead of a base class, so all integration tests share one app context and one Postgres container. `show-components` moved from the smoke test to `application-test.yml` |
| 26 | 2.2 | SPRINT_PLAN 2.2 (lists `application-test.yml`, not where) | `application-test.yml` lives in `src/test/resources`, so it never ships (checked in the bootJar). Its storage endpoint is `http://storage.invalid:8333`, so a test that forgets to provide storage fails instead of reaching a developer's SeaweedFS |

## Needs attention
- **GitGuardian fails on PR #1, a false positive** (Sprint 1 close-out). Its *Security Checks* flag `docker-compose.yml` line 15 (commit `3447288`) as a "Generic Password", but the line is `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}`, a Compose placeholder that reads the value from `.env`. No password is in the repo, and `.env` was never committed. **Fix:** in GitGuardian's dashboard, resolve incident 39228980 as a false positive, then re-run the check. Later PRs may be flagged the same way wherever a password is read from `.env` by name; GitGuardian's ignore settings can stop that, which is your call.
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
- **Two dependencies skipped** (task 2.1): the `org.gradle.toolchains.foojay-resolver-convention` Gradle plugin, considered for a portable Daemon JVM criteria file, and `org.springframework.boot:spring-boot-docker-compose`, considered as a way to reach the Compose database without the `.env` import. Neither is approved, and neither is needed: the Java 25 toolchain and the `local` profile's `.env` import do the job.
- **Windows git habits** (task 2.1): this checkout has `core.autocrlf=true` and `core.filemode=false`. A Java file written with LF fails `spotlessCheck` until `./gradlew spotlessApply` converts it to CRLF (git still stores LF); the auto-format hook that CURRENT_SKILLS.md plans would remove that step. A new shell script needs `git add --chmod=+x`, or Linux CI can't run it. `backend/gradlew` is committed as `100755`: the spec review's major finding, fixed in the close-out.
- **Stopping `bootRun` can leave the app running** (task 2.1, in DEVELOPMENT.md §7): killing `./gradlew` or closing its terminal can leave the app's JVM, a child of the Gradle daemon, on port 8080. `netstat -ano | grep -E ':8080 .*LISTENING'` shows its PID and `taskkill //PID <pid> //F` stops it; Gradle then reports `BUILD FAILED`, which is expected after a kill.
- **A missing `.env` gives a misleading error** (task 2.1, in DEVELOPMENT.md §7): with the `local` profile, Spring Boot leaves an unresolved `${DB_URL}` as text, so startup fails with `'url' must start with "jdbc"`. It usually means the app wasn't started from `backend/`. Task 2.2 could drop `optional:` from the import for a clear error at once, but TECH_STACK §1 specifies `optional:`, so that would be a decision.
- **Expected noise in the logs** (task 2.1): Flyway's `No migrations found` warning until task 2.3's V1, and the empty `flyway_schema_history` table it created in the local database (harmless: V1 applies on top); `spring.jpa.open-in-view is enabled by default` (task 2.2 may set it); springdoc's warning that `/v3/api-docs` and `/swagger-ui.html` are on by default (the Phase 2 `prod` profile should turn them off or protect them); and, once per test run, `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes…`, caused by Mockito's inline mock maker and harmless ([journal §8](build-journal/sprint-2/2.1-spring-boot-project.md#8-what-went-wrong), item 13).
- **For task 3.1** (task 2.1): the AWS SDK's `s3` module brings two HTTP clients, `apache5-client` and `netty-nio-client`. The storage task may want to exclude the one it doesn't use.
- **Not verified** (task 2.1, [journal §9](build-journal/sprint-2/2.1-spring-boot-project.md#9-review)): whether the pinned versions are still the newest releases (springdoc 3.2.0 was five days old when pinned, though it's built against Boot 4.1.2 exactly), and the journal's claims from outside sources (Initializr, the Boot 4.0 migration guide, springdoc's release notes). `spotlessCheck` on a Linux checkout is expected to pass; task 2.7's first CI run is the proof. The close-out re-ran the build, the app and the `curl` checks, and compared the wrapper's two checksums with Gradle's published ones: all passed.
- **Another session's uncommitted work** (task 2.1): `docs/design/explorations/` has changes that aren't from this run (edited mocks and screenshots, and six new `feed-sealed-375.jpg` screenshots). The run left them unstaged and untouched.
- **Known gaps in `StorageProperties`** (task 2.2, [journal §8](build-journal/sprint-2/2.2-configuration-and-profiles.md#8-what-went-wrong)): `STORAGE_PUBLIC_ENDPOINT=localhost:8333` (no scheme) passes validation, and an unparseable value such as a bad boolean shows only the first conversion error; validation errors (missing, blank, out of range) are listed together. Documented in TECH_STACK §2, not built.
- **Unconfirmed text** (task 2.2, journal §9): DEVELOPMENT.md §7's troubleshooting row quotes `Binding to target com.memoirist.storage.StorageProperties failed`, but the captured failure shows only the `Property / Value / Origin / Reason` lines, so the header wording is unconfirmed. The `local` profile (`.env` import, Flyway locations) has no automated test, only the manual boot and `/actuator/env` probe.
- **Interrupted first attempt** (task 2.2): the first attempt stopped after the first local boot; a second session finished from its notes and re-ran the build. The open-in-view WARN from 2.1 is gone; the remaining startup WARNs are Flyway's `No migrations found` (until 2.3) and springdoc's defaults note. No setup is needed from you, no dependencies were skipped and the README needs no change.
- **Another project's container** (task 2.2): `universalbook_backend-ollama-1` is crash-looping on this machine. It isn't memoirist's and was left alone.

## Questions for the morning
- **Keep or swap two build choices from task 2.1?** (spec review finding 2, [journal §9](build-journal/sprint-2/2.1-spring-boot-project.md#9-review)). No doc named either before the task, and the builder recorded both in TECH_STACK §2 itself:
  - **palantir-java-format 2.102.0**, the formatter Spotless runs (D-15 names Spotless, not a formatter): 4-space indents and 120 columns. It sets the style of every Java file, so swapping it later means a commit that reformats everything, and each task adds files. The alternatives were google-java-format (2-space indents) and Eclipse's formatter (needs a settings file).
  - **The `io.spring.dependency-management` plugin**, which applies Spring Boot's BOM and the AWS SDK's. Gradle's own `platform()` could do the same with nothing extra, but its versions are constraints that a transitive dependency can raise, while the plugin forces them, so a swap means checking the resolved versions again.

  To keep them, record them with `/record-decision` (the formatter under D-15). To swap either, say which and it's a small build change.
- **Record `open-in-view=false` as a decision?** (task 2.2, spec review minor finding). It makes every later service map to DTOs inside its transaction. TECH_STACK §2 is its only home today; run `/record-decision` to give it a D-entry, or leave it.
