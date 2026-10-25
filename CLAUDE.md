# memoirist

A private journaling PWA.
- Users capture messy text, photos and voice notes as Instagram-style entries.
- The app files entries into automatic weeks and user-named life "Eras".
- Users can seal entries as "Loot Drops" until a future date.
- In Phase 3, an AI biographer summarises each week.

## Current focus
**Phase 1, Sprint 1. Next task: 1.2** (machine prerequisites).
- Progress is tracked by the checkboxes in `docs/SPRINT_PLAN.md`. Update this line when a task is ticked.
- Build only what the current phase needs. Don't add tables, endpoints or libraries from later phases.

## Repo layout
| Path | What | Created in |
|---|---|---|
| `backend/` | Spring Boot 4 API, Java 21, Gradle Kotlin DSL, package `com.memoirist` | Sprint 2 |
| `frontend/` | React 19 + Vite + TypeScript + Tailwind v4 | Sprint 4 |
| `docker-compose.yml` | Postgres 17 (pgvector image) on :5432; SeaweedFS S3 on :8333 (filer UI :8888) | Sprint 1 (task 1.3) |
| `docs/` | Specs (see doc map below) | — |
| `.claude/` | `settings.json`, path-scoped `rules/`, `skills/`, `hooks/` | — |

## Commands
Run these from the repo root in Git Bash on Windows. Each one exists from the sprint shown.

```bash
docker compose up -d                                                      # 1.3: data services
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'   # 2.1: API on :8080, /swagger-ui.html
cd backend && ./gradlew test                                              # 2.1: Testcontainers needs Docker running
cd backend && ./gradlew spotlessApply                                     # 2.1: format Java
cd frontend && npm run dev                                                # 4.1: :5173, proxies /api to :8080
cd frontend && npm test && npm run lint && npm run typecheck              # 4.1
cd frontend && npm run gen:api                                            # 4.2: regenerate API types (backend must be running)
```

## Doc map: read what the task needs, not everything
| When you are… | Read |
|---|---|
| Starting any task | That task's block in `docs/SPRINT_PLAN.md` (it lists what else to read) |
| Changing tables or migrations | `docs/DATABASE_SCHEMA.md` |
| Adding or changing endpoints or DTOs | `docs/API_CONTRACT.md` |
| Choosing a library or infra pattern | `docs/TECH_STACK.md` |
| Unsure *why* a rule exists, or docs disagree | `docs/DECISIONS.md` (it wins over other docs) |
| Working on product behaviour or UX | `docs/PRD.md` (glossary in §3) |

`docs/product/` is market research. Don't load it for coding.

## Non-negotiables
Details live in `.claude/rules/`, which load when you touch matching files.
1. **Schema changes go through a new Flyway migration.** Never edit a committed migration; a hook blocks it. Keep `ddl-auto=validate`.
2. **Time is stored in UTC:** `TIMESTAMPTZ`/`Instant` for moments, `DATE`/`LocalDate` for calendar days, never `LocalDateTime`. "Today" and week boundaries use the user's timezone (`users.timezone`, default `Australia/Melbourne`). Inject `Clock`; don't call `now()` directly.
3. **Never take a user ID from the request.** Use `CurrentUserProvider`. Scope every lookup by ID to the current user; a resource the user doesn't own returns 404.
4. **Sealed Loot Drops never leak.** Every Entry→DTO conversion goes through `EntryMapper`. Sealed entries can't be read, edited, analysed or opened early.
5. **Store object keys only.** The API returns presigned URLs; no URLs or hosts in the database.
6. **Entry content is validated TipTap JSON.** Never store HTML, and never render it with `dangerouslySetInnerHTML`.
7. **`X-Dev-User-Id` works only in the `local` and `test` profiles.**
8. **Secrets live in `.env`.** Never read it, print it or commit it; `.env.example` lists the variable names.

## How to work here
- Start each sprint task with `/implement-task <id>`. It plans first and waits for approval.
- One task per session; start a fresh session (or `/clear`) between tasks.
- Write tests with the change and run them before saying it's done. Report failures plainly, with output.
- Ask before adding a dependency, deviating from the docs, or resolving a conflict between docs. Record the outcome in `docs/DECISIONS.md`.
- When an endpoint, table or decision changes, update its doc in the same change (`/sync-docs` checks for drift).
- Commit only when asked. Use one commit per task, with the message `Sprint <id>: <summary>`.
- Spring Boot 4, Hibernate 7, Jackson 3, React 19, Tailwind v4 and TipTap 3 are newer than many examples. Check current docs (the context7 plugin, if installed) before using an API you're unsure of.

## Project skills
- `/implement-task [id]`: plan, build, test, update docs and tick one task in SPRINT_PLAN.md.
- `/new-migration [description]`: add a Flyway migration safely.
- `/new-endpoint [resource]`: the backend endpoint recipe (contract → DTO → service → mapper → controller → tests).
- `/sync-docs`: find and fix drift between the code and API_CONTRACT.md or DATABASE_SCHEMA.md.
