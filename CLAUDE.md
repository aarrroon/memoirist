# memoirist

A private journaling PWA.
- Users capture messy text, photos and voice notes as Instagram-style entries.
- The app files entries into automatic weeks and user-named life "Eras".
- Users can seal entries as "Loot Drops" until a future date.
- In Phase 3, an AI biographer summarises weeks and Eras, profiles friends and offers two-option "Help me think" questions (D-21).
- Photos of paper diary pages work from Phase 1; Phase 3 can also read the handwriting (D-20).
- In Phase 5, friends can add entries to private groups for shared trips (D-19).
- In Phase 6, opt-in insight cards and an optional writing coach (D-21).

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
Run these from the repo root in Git Bash on Windows. Each one exists from the sprint shown. The full list, ports and troubleshooting are in `docs/DEVELOPMENT.md`; when a command changes, update both.

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
| Planning or closing a feature, or unsure which doc owns a fact | `docs/WORKFLOW.md` (index of all docs: `docs/README.md`) |
| Changing tables or migrations | `docs/DATABASE_SCHEMA.md` |
| Adding or changing endpoints or DTOs | `docs/API_CONTRACT.md` |
| Choosing a library or infra pattern | `docs/TECH_STACK.md` |
| Unsure *why* a rule exists, or docs disagree | `docs/DECISIONS.md` (it wins over other docs) |
| Working on product behaviour or UX | `docs/PRD.md` (glossary in §3) |
| Building a screen, or writing an end-to-end test | `docs/USER_FLOWS.md` (draft; flow IDs `F-xx` name the Playwright tests) |

`docs/product/` is market research. Don't load it for coding.
`docs/design/` holds design work. Until `docs/design/DESIGN.md` exists, it's only early options (`DIRECTIONS.md`); don't build UI from them.

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
The full toolkit, with when to use each, is in `docs/CURRENT_SKILLS.md`. When you add, remove or rename a skill, subagent, hook or prompt, update that file in the same change.
- `/implement-task [id]`: plan, build, test, update docs and tick one task in SPRINT_PLAN.md.
- `/new-migration [description]`: add a Flyway migration safely.
- `/new-endpoint [resource]`: the backend endpoint recipe (contract → DTO → service → mapper → controller → tests).
- `/sync-docs`: find and fix drift between the code and API_CONTRACT.md or DATABASE_SCHEMA.md.
- `/sprint-ready [n]`: read-only check that a sprint has no blocking questions or doc gaps. Run before starting a sprint.
- `/parallel-check [P1-04 P1-06]`: read-only check whether features can be built in parallel; reserves migration numbers and writes each session's kickoff note.
- `/record-decision [decision]`: add a D-entry, close its Q-x, update docs that assumed otherwise.
- `spec-reviewer` subagent: read-only review of a change against the specs and security rules. Use it after each task.
- A Stop hook reminds Claude to sync docs when API or schema code changed but the spec docs didn't.
- context7 MCP (`.mcp.json`): use it to check current library docs.
