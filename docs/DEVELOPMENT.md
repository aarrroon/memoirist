# Developing memoirist

> Setup and day-to-day commands for working on memoirist. For what the app is, see the [README](../README.md). For how it's built, see [TECH_STACK.md](TECH_STACK.md).
>
> **Status:** the backend (`backend/`), frontend (`frontend/`) and `docker-compose.yml` don't exist yet. Each section says which sprint task creates what it describes. Commands marked with a task number work once that task is done.

## 1. Prerequisites

| Tool | Version | Check | Notes |
|---|---|---|---|
| Git (with Git Bash on Windows) | any recent | `git --version` | Run every command here in Git Bash |
| Docker Desktop | recent | `docker info` | Must be **running** for Compose and for backend tests (Testcontainers) |
| JDK | 21 | `java -version` | For example Eclipse Temurin 21. Make sure `java` is on `PATH` |
| Node.js | 22 or later | `node -v` | npm comes with it |
| Editor | — | — | IntelliJ IDEA (backend) and/or VS Code |
| Claude Code | latest | — | Optional, but the repo is set up for it (section 6) |

You don't need to install Gradle: the project ships the Gradle wrapper (`./gradlew`).

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
| Postgres | `localhost:5432`, database `memoirist` | Credentials in `.env` |
| SeaweedFS S3 | http://localhost:8333 | Bucket `memoirist-media`, created at backend startup |
| SeaweedFS file browser | http://localhost:8888 | See uploaded files |

## 4. Everyday commands

### Data services (task 1.3)
```bash
docker compose up -d          # start
docker compose ps             # status
docker compose logs -f        # follow logs (Ctrl+C to stop following)
docker compose stop           # stop, keeping data
docker compose down -v        # DELETE all local data (database and uploads)
```

### Backend (task 2.1)
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'   # run the API
./gradlew test                                              # all tests (Docker must be running)
./gradlew test --tests '*EntryServiceTest'                  # one test class
./gradlew spotlessApply                                     # format Java
./gradlew build                                             # compile, format check, test
```

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
| Port 5432, 8080 or 5173 already in use | Another Postgres or app is running. Stop it, or find it with `netstat -ano \| findstr :5432` |
| Backend fails with a Flyway checksum error | A committed migration was changed. Revert it and add a new migration instead. Last resort for local data only: `docker compose down -v` |
| Backend fails "schema-validation: missing column" | Entity and migrations disagree. Add a migration; don't change `ddl-auto` |
| Photos don't load after the tab sat open a while | Presigned links expire after 60 minutes; refocusing the tab refetches them. If they still fail, check SeaweedFS is running |
| `./gradlew: Permission denied` or `bad interpreter` | Line endings: run `git add --renormalize .` (`.gitattributes` keeps `gradlew` as LF) |
| Tests hang or fail to start containers | Docker isn't running, or it's low on memory. Give Docker Desktop at least 4 GB |
| Frontend types out of date | Start the backend, then `npm run gen:api` |
