# Developing memoirist

> Setup and day-to-day commands for working on memoirist. For what the app is, see the [README](../README.md). For how it's built, see [TECH_STACK.md](TECH_STACK.md).
>
> **Status:** `docker-compose.yml` (task 1.3) and the backend project (`backend/`, tasks 2.1 to 2.5) exist. The backend reads its configuration (`local` and `test` profiles), creates the Phase 1 tables and the dev user in Postgres when it starts, knows who is calling (the dev-user header, task 2.5) and answers its health check, but it has no API endpoints yet. The frontend (`frontend/`) doesn't exist yet. Each section says which sprint task creates what it describes. Commands marked with a task number work once that task is done.

## 1. Prerequisites

| Tool | Version | Check | Notes |
|---|---|---|---|
| Git (with Git Bash on Windows) | any recent | `git --version` | Run every command here in Git Bash |
| Docker Desktop | recent | `docker info` | Must be **running** for Compose and for backend tests (Testcontainers) |
| JDK | 25 | `java -version` | For example Eclipse Temurin 25 (`winget install EclipseAdoptium.Temurin.25.JDK`). Make sure `java` is on `PATH` |
| Node.js | 22 or later | `node -v` | npm comes with it |
| Editor | — | — | IntelliJ IDEA (backend) and/or VS Code |
| Claude Code | latest | — | Optional, but the repo is set up for it (section 6) |

You don't need to install Gradle: the project ships the Gradle wrapper (`backend/gradlew`). The first `./gradlew` command downloads Gradle itself, checks it against the SHA-256 in `gradle/wrapper/gradle-wrapper.properties`, and then downloads the dependencies, so allow a few minutes. Gradle compiles and tests on JDK 25 (a Gradle toolchain), but the wrapper itself starts with `$JAVA_HOME`, or with `java` on `PATH` when `JAVA_HOME` isn't set (section 7).

## 2. First-time setup

```bash
git clone <repo-url> memoirist
cd memoirist
cp .env.example .env              # local-only values; never commit .env
docker compose up -d              # task 1.3: Postgres + SeaweedFS
docker compose ps                 # both services should be "healthy"
```

Then, once the apps exist:

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'   # task 2.1
cd frontend && npm ci && npm run dev                                       # task 4.1
```

Open http://localhost:5173. In Phase 1 there's no login; you're the seeded dev user automatically.

## 3. Services and ports

| Service | URL | Notes |
|---|---|---|
| Frontend (Vite) | http://localhost:5173 | Proxies `/api` to the backend, so there's no CORS locally |
| Backend API | http://localhost:8080/api/v1 | |
| Swagger UI | http://localhost:8080/swagger-ui.html | Try every endpoint in the browser |
| OpenAPI JSON | http://localhost:8080/v3/api-docs | Source for `npm run gen:api` |
| Health | http://localhost:8080/actuator/health | Should say `UP` |
| Postgres | `localhost:5432`, database `memoirist` | Credentials in `.env`. `docker compose exec postgres psql` opens a SQL shell. The tables appear when the backend first starts (Flyway, task 2.3) |
| SeaweedFS S3 | http://localhost:8333 | Bucket `memoirist-media`, created at backend startup. Requests must be signed with the `.env` keys; unsigned ones get `403` |
| SeaweedFS file browser | http://localhost:8888 | See uploaded files. It has **no login**: anyone who can reach it can read, add and delete files |

Compose publishes Postgres and SeaweedFS on `127.0.0.1` only, so other machines on your network can't reach them. That matters most for the file browser.

## 4. Everyday commands

### Data services (task 1.3)
```bash
docker compose up -d          # start; both are "healthy" in docker compose ps within about 10 seconds
docker compose ps             # status
docker compose exec postgres psql   # SQL shell in the memoirist database (\q to quit)
docker compose logs -f        # follow logs (Ctrl+C to stop following)
docker compose stop           # stop, keeping data (SeaweedFS takes about 17 seconds to stop cleanly)
docker compose down -v        # DELETE all local data (database and uploads)
```

- Data lives in two named volumes, `memoirist_postgres-data` and `memoirist_seaweedfs-data`, so it survives `stop`, restarts and recreated containers. Only `down -v` deletes it.
- The services don't restart on their own (no restart policy): after a reboot or a Docker Desktop restart, run `docker compose up -d` again.
- SeaweedFS's image tag is pinned; [DECISIONS.md D-03](DECISIONS.md#d-03-object-storage-seaweedfs-locally-cloudflare-r2-in-production) (Version) names it and says why. When it starts, its log prints the S3 access key ID, never the secret key; trim that line before you share its logs.

### Backend (tasks 2.1 and 2.2)
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'   # run the API
./gradlew test                                              # all tests (Docker must be running)
./gradlew test --tests '*EntryServiceTest'                  # one test class
./gradlew spotlessApply                                     # format Java
./gradlew build                                             # compile, format check, test
```

- `bootRun` with the `local` profile reads `../.env`, so run it from `backend/`. It's ready when the log says `Started MemoiristApplication` (about 20 seconds); then http://localhost:8080/actuator/health says `UP`. Stop it with Ctrl+C.
- Settings come from several places. From highest to lowest: command-line arguments, Java system properties (`-D`), the OS environment, `.env`, then the profile file (`application-local.yml`) and `application.yml`. So `STORAGE_PRESIGN_TTL=PT30M ./gradlew bootRun ...` overrides `.env` for one run without editing it. [TECH_STACK §2](TECH_STACK.md#profiles) says which file holds what.
- On start, Flyway applies the migrations in `src/main/resources/db/migration/` and, in the `local` and `test` profiles, the dev-user seed in `db/seed-local/` (task 2.3). To see what it did: `docker compose exec -T postgres psql -c 'select version, description, script, success from flyway_schema_history'`. The seed's row has an empty `version`, because a repeatable migration has none. A second start logs `Schema "public" is up to date`.
- **Who you are (task 2.5):** in the `local` and `test` profiles every request is the seeded dev user. To act as another user, send `X-Dev-User-Id: <uuid>` (8-4-4-4-12 hexadecimal digits, sent once); anything else, even an empty value, is `400 malformed-request`. In any other profile the header does nothing. No endpoint reads it yet, so there's nothing to try it on until `/users/me` (task 3.2). [API_CONTRACT.md](API_CONTRACT.md#who-is-the-current-user) has the rules.
- The app and the tests run in UTC. Log lines end in `Z` whatever your machine's zone is; add 10 hours (11 in daylight saving time) for Melbourne time.
- `./gradlew build` fails when a Java file isn't formatted (`spotlessCheck`); `./gradlew spotlessApply` fixes it. The formatter is palantir-java-format: 4-space indents, 120 columns.
- Tests print each result as `PASSED` or `FAILED`; the full report is `build/reports/tests/test/index.html`. Each test run starts its own Postgres container (`pgvector/pgvector:pg17`) and leaves the Compose database alone. Integration tests use the `@IntegrationTest` annotation, which gives them the `test` profile and one shared app and container per run, so each extra test class costs milliseconds, not another 30-second start. `MigrationsTest` is the exception: it runs Flyway by hand and starts a second container of its own, and so does `DevProviderAbsentInProdTest` (it starts the app under the `prod` profile). Plain unit tests and `@WebMvcTest` slices such as `CurrentUserHttpTest` need no Docker.

### Frontend (task 4.1)
```bash
cd frontend
npm ci                  # install exactly what package-lock.json says
npm run dev             # dev server on :5173
npm test                # Vitest
npm run lint            # ESLint
npm run typecheck       # TypeScript
npm run build           # production build
npm run gen:api         # regenerate API types (backend must be running) — task 4.2
npx playwright test     # end-to-end smoke tests — task 4.17
```

## 5. Working on the code

The full plan-to-code workflow, and how the docs stay in sync, is in [WORKFLOW.md](WORKFLOW.md). In short:

- **Pick up the next task** from [SPRINT_PLAN.md](SPRINT_PLAN.md). Each one lists what to read and how to know it's done.
- **Database changes** are always a new Flyway migration in `backend/src/main/resources/db/migration/`. Never edit one that's committed (a hook blocks it). Update [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) in the same change.
- **API changes** start in [API_CONTRACT.md](API_CONTRACT.md); then run `npm run gen:api` so the frontend types follow.
- **Which doc wins:** the [README](../README.md) on what users get and in which phase; [DECISIONS.md](DECISIONS.md) on how it works (D-27). Record decisions with `/record-decision`.
- **Screens and end-to-end tests** follow [USER_FLOWS.md](USER_FLOWS.md); design work lives in [design/](design/).
- **Secrets** stay in `.env`. New variables go in `.env.example` with a placeholder.
- **Commits:** one per task, message `Sprint <id>: <summary>`.

## 6. Working with Claude Code

The repo is set up so Claude follows the project's rules automatically.
- [CURRENT_SKILLS.md](CURRENT_SKILLS.md) lists every skill, subagent, hook, rule and prompt, and what Claude reads at the start of every session.
- [WORKFLOW.md](WORKFLOW.md) says which tool to use when, with text to copy.

A typical task: start a fresh session, run `/implement-task`, approve the plan, let it build and test, ask spec-reviewer to check, then commit.

Personal, uncommitted settings go in `.claude/settings.local.json` or `CLAUDE.local.md` (both gitignored).

## 7. Troubleshooting

| Problem | Likely cause and fix |
|---|---|
| `failed to connect to the docker API` | Docker Desktop isn't running. Start it and wait until it says it's running |
| Port 5432, 8333, 8888, 8080 or 5173 already in use | Another Postgres, app or Compose project (check `docker ps`) is using it. Stop it, or find it with `netstat -ano \| findstr :5432` |
| Port 8080 still answers after you closed the terminal that ran `./gradlew bootRun` | The app runs in its own Java process, started by the Gradle daemon, so closing or killing the terminal can leave it running. Find its PID with `netstat -ano \| findstr :8080`, then `taskkill //PID <pid> //F` in Git Bash (`taskkill /PID <pid> /F` in cmd). Next time, stop it with Ctrl+C in that terminal |
| The backend fails to start with `Failed to configure a DataSource: 'url' attribute is not specified` (`local` profile) | `.env` wasn't loaded. The `local` profile reads `../.env` relative to the working directory, so start it from `backend/` (an IDE run configuration needs `backend/` as its working directory), and check that `.env` has `DB_URL`. A missing `.env` is skipped without a warning (`optional:`, TECH_STACK §1), so the first sign is the first missing setting. `application.yml` gives an unset variable an empty value (`${DB_URL:}`), which is why the message names the datasource rather than showing a literal `${DB_URL}` |
| The backend stops with `Binding to target com.memoirist.storage.StorageProperties failed` | One of the `STORAGE_*` values in `.env` is missing, empty or invalid. The message lists each bad property with its value and where it came from (for example `System Environment Property "STORAGE_PRESIGN_TTL"`) and the reason. Fix that line, or unset the OS environment variable that overrides it. The presign TTL must be 1 to 60 minutes (`PT60M`), and the bucket name must follow S3's rules |
| `docker compose` says `required variable POSTGRES_PASSWORD is missing a value` (or another variable) | `.env` is missing, or it lacks that variable or leaves it empty. Copy `.env.example` to `.env`, or add the line. Compose requires all five variables it uses, so that SeaweedFS never starts without its keys (D-03) |
| `password authentication failed`, or a role or database that `does not exist`, after you changed `POSTGRES_USER`, `POSTGRES_PASSWORD` or `POSTGRES_DB` in `.env` | Postgres applies them only when its volume is first created. Put the old values back, or change the database to match (for a new password: `docker compose exec postgres psql`, then `\password <user>`), or start again with `docker compose down -v`, which deletes all local data |
| `docker compose exec postgres psql` says `the input device is not a TTY` | Git Bash's own window (mintty) isn't a Windows console. Prefix the command with `winpty`, or use Windows Terminal. For a one-off query, `docker compose exec -T postgres psql -c 'select 1'` needs no terminal |
| S3 calls fail with `InvalidAccessKeyId` or `SignatureDoesNotMatch` | The client's keys differ from SeaweedFS's. SeaweedFS reads them from `.env` when its container is created: after editing `.env`, run `docker compose up -d`, which recreates it with the new keys (the old ones stop working), and restart the backend if it's running, so both sides use the new keys |
| Backend fails with a Flyway checksum error | A migration was changed after your local database applied it. If it's committed, revert it and add a new migration instead. If it was never committed, you can keep editing it, but your local database no longer matches: reset it with `docker compose down -v`, which deletes all local data. (Line endings don't matter: Flyway's checksum ignores LF versus CRLF.) |
| Backend fails "schema-validation: missing column" | Entity and migrations disagree. Add a migration; don't change `ddl-auto` |
| Photos don't load after the tab sat open a while | Presigned links expire after 60 minutes; refocusing the tab refetches them. If they still fail, check SeaweedFS is running |
| `./gradlew: Permission denied` | `gradlew` lost its executable bit (Git on Windows doesn't track it unless told to). Run `chmod +x gradlew` locally; to fix it for everyone, `git update-index --chmod=+x backend/gradlew` and commit |
| `./gradlew: /bin/sh^M: bad interpreter` | Line endings: run `git add --renormalize .` (`.gitattributes` keeps `gradlew` as LF) |
| `spotlessCheck` fails, and its diff shows every line changing from `\n` to `\r\n` | Spotless follows git's line-ending rules. With `core.autocrlf=true` (Git for Windows' default) git checks text files out with CRLF, so Spotless expects CRLF too, and a file saved with LF fails. Run `./gradlew spotlessApply`; git still stores the file with LF |
| `./gradlew` says `JAVA_HOME is set to an invalid directory`, or `./gradlew --version` shows a JVM other than 25 | The wrapper starts Gradle with `$JAVA_HOME/bin/java`; it uses `java` on `PATH` only when `JAVA_HOME` is unset. Point `JAVA_HOME` at the JDK 25 folder, then restart the terminal, your IDE and Claude Code, because a running program keeps the environment it started with. `echo $JAVA_HOME` should show the JDK 25 folder |
| Tests hang or fail to start containers | Docker isn't running, or it's low on memory. Give Docker Desktop at least 4 GB |
| Frontend types out of date | Start the backend, then `npm run gen:api` |
