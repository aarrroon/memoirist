# memoirist

A private journaling PWA: an Instagram-style feed of your own entries that files itself into weeks and life "Eras", with an AI biographer later. `README.md` says what users get and when (its Timeline).
- **Phase 1, the core journal (now):** entries with text, photos, voice notes and diary-page photos; optional titles, tags, templates (with a starter gallery) and Spotify soundtracks (D-06, D-35); photo dump (D-34); weeks filed automatically, and weeks joining Eras by date, with Era covers (D-22, D-36); Loot Drops sealed until a future date, with quick unlock dates, replies and a first letter (D-10, D-33); a feed with *On this day* (D-32), a week summary and an all-entries timeline; deleted entries wait 30 days in Recently deleted (D-30). Local only, with a seeded dev user.
- **Phase 2, online:** accounts, the installable app and an app lock, expiring share links, PDF and data export, unlock emails, the yearly letter, product metrics (D-31, D-32, D-37, D-38).
- **Phase 3, memories with friends:** private groups built from members' own entries, sealed trips and trip Eras (D-19).
- **Phase 4, the AI biographer:** Help me think, tag suggestions, summaries of the weeks and Eras the user closes (D-28), friend profiles and a friendship map, search by word or by feeling (D-29), reading handwriting on a tap (D-20), the shared trip story and the weekly email. One AI layer, never a chat (D-21), gentle with hard topics (D-39).
- **Phase 5, on your phone:** offline capture, scan mode, dictation, inline photos, a printable zine, push notifications.
- **Phase 6, looking back:** opt-in insight cards, highlight reels, predictions, year in review, a gift of memories, and an optional writing coach (D-21).

## Which doc wins (D-27)
- `README.md` wins on **what** users get and **which phase** it ships in.
- `docs/DECISIONS.md` wins on **how** it works. Every other doc follows these two.
- If they disagree, stop and ask the user. Record the answer with `/record-decision`, and edit the README only with the user's approval.

## Current focus
**Phase 1, Sprint 1. Next task: 1.2** (machine prerequisites).
- Progress is tracked by the checkboxes in `docs/SPRINT_PLAN.md`. Update this line when a task is ticked; a Stop hook checks it.
- Build only what the current phase needs. Don't add tables, endpoints or libraries from later phases.

## Repo layout
| Path | What | Created in |
|---|---|---|
| `backend/` | Spring Boot 4 API, Java 25, Gradle Kotlin DSL, package `com.memoirist` | Sprint 2 |
| `frontend/` | React 19 + Vite + TypeScript + Tailwind v4 | Sprint 4 |
| `docker-compose.yml` | Postgres 17 (pgvector image) on :5432; SeaweedFS S3 on :8333 (filer UI :8888) | Sprint 1 (task 1.3) |
| `docs/` | Specs (see doc map below) | — |
| `.claude/` | `settings.json`, path-scoped `rules/`, `skills/`, `agents/`, `hooks/` | — |

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
| Checking what users are promised, or which phase something belongs to | `README.md` (Timeline) |
| Starting any task | That task's block in `docs/SPRINT_PLAN.md` (it lists what else to read) |
| Planning or closing a feature, or unsure which doc owns a fact | `docs/WORKFLOW.md` (index of all docs: `docs/README.md`) |
| Changing tables or migrations | `docs/DATABASE_SCHEMA.md` |
| Adding or changing endpoints or DTOs | `docs/API_CONTRACT.md` |
| Choosing a library or infra pattern | `docs/TECH_STACK.md` |
| Unsure *how* or *why* something works, or docs disagree | `docs/DECISIONS.md` (wins on how; open questions are at the end) |
| Working on product behaviour or UX | `docs/PRD.md` (glossary in §3) |
| Building a screen, or writing an end-to-end test | `docs/USER_FLOWS.md` (draft; flow IDs `F-xx` name the Playwright tests) |

`docs/product/` is market research. Don't load it for coding.
`docs/design/` holds design work. Until `docs/design/DESIGN.md` exists, it's only early options (`DIRECTIONS.md`); don't build UI from them.

## Non-negotiables
Details live in `.claude/rules/`, which load when you touch matching files.
1. **Schema changes go through a new Flyway migration.** Never edit a committed migration; a hook blocks it. Keep `ddl-auto=validate`.
2. **Time is stored in UTC:** `TIMESTAMPTZ`/`Instant` for moments, `DATE`/`LocalDate` for calendar days, never `LocalDateTime`. "Today" and week boundaries use the user's timezone (`users.timezone`, default `Australia/Melbourne`). Inject `Clock`; don't call `now()` directly.
3. **Never take a user ID from the request.** Use `CurrentUserProvider`. Scope every lookup by ID to the current user; a resource the user doesn't own returns 404.
4. **Sealed Loot Drops never leak.** Every Entry→DTO conversion goes through `EntryMapper`. Sealed entries can't be read, edited, analysed or opened early, and never show up in counts, filters or search (D-10).
5. **Store object keys only.** The API returns presigned URLs; no URLs or hosts in the database.
6. **Entry content is validated TipTap JSON.** Never store HTML, and never render it with `dangerouslySetInnerHTML`.
7. **`X-Dev-User-Id` works only in the `local` and `test` profiles.**
8. **Secrets live in `.env`.** Never read it, print it or commit it; `.env.example` lists the variable names.

## How to work here
- Start each sprint task with `/implement-task <id>` in a fresh session (or after `/clear`). One task per session. It plans first and waits for approval.
- Write tests with the change and run them before saying it's done. Report failures plainly, with output.
- Ask before adding a dependency, deviating from the docs, or resolving a conflict between docs.
- Ask the user to decide things as a short list of interactive questions (AskUserQuestion): each option with its trade-offs, and a recommended default based on stated reasons, not guesses. Record the answer with `/record-decision`.
- When an endpoint, table or decision changes, update its doc in the same change (`/sync-docs` checks the code against the API and schema docs). After the README changes, run `/sync-readme`.
- Commit only when asked. Use one commit per task, with the message `Sprint <id>: <summary>`.
- Spring Boot 4, Hibernate 7, Jackson 3, React 19, Tailwind v4 and TipTap 3 are newer than many examples. Check current docs (the context7 MCP) before using an API you're unsure of.

## Learning (the user's goal alongside shipping)
The user wants to grow as an engineer while building this: better solution choices, new syntax and patterns, and reviewing code they didn't type. Details: `docs/LEARNING.md`.
- **Before every coding task**, `/implement-task` asks whether they want to write one piece themselves, offering one that transfers to their job (see About me in `docs/LEARNING_LOG.md`) and one pattern they haven't seen. Respect "not this time".
- When choosing between approaches, name the alternatives and why one wins, in a sentence or two.
- The first time a pattern appears (records, `@Transactional`, TanStack Query…), explain it in one sentence.
- After a task, suggest `/review-with-me`, then `/log-learning`.
- **Unattended or `learn-later` style:** never wait for the user. Leave practice blocks (`TODO(human) PRACTICE` + the answer in a marked block) as `.claude/output-styles/learn-later.md` describes.

## Project tools
The full toolkit, with when to use each, is in `docs/CURRENT_SKILLS.md`. When you add, remove or rename a skill, subagent, hook, rule or prompt, update that file in the same change.
- `/implement-task [id]`: plan, build, test, update docs and tick one task in SPRINT_PLAN.md.
- `/new-migration [description]`: add a Flyway migration safely.
- `/new-endpoint [resource]`: the backend endpoint recipe (contract → DTO → service → mapper → controller → tests).
- `/sync-docs`: find and fix drift between the code and API_CONTRACT.md or DATABASE_SCHEMA.md.
- `/sync-readme`: find and fix drift between the README and the docs.
- `/sprint-ready [n]`: read-only check that a sprint has no blocking questions or doc gaps. Run before starting a sprint.
- `/parallel-check [P1-04 P1-06]`: read-only check whether features can be built in parallel; reserves migration numbers and writes each session's kickoff note.
- `/record-decision [decision]`: record or update a D-entry, close its Q-x or G-xx, and update every doc that assumed otherwise.
- `/review-with-me [scope]`: walk the user through a change, then quiz and grade their review.
- `/teach [topic]`: a short lesson grounded in this codebase, with a tiny exercise.
- `/log-learning [notes]`: add an entry to `docs/LEARNING_LOG.md` and update its skills tracker.
- `spec-reviewer` subagent: read-only review of a change against the README, specs and rules. Use it after each task.
- Hooks: a PreToolUse hook blocks edits to committed migrations; a Stop hook flags docs that lag behind the code, the README or the sprint plan; a SessionStart hook prints the next task and anything blocking it.
- context7 MCP (`.mcp.json`): current library docs.
