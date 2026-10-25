# Sprint Plan: memoirist

> The build order. Each task is sized for one Claude Code session: start it with `/implement-task <id>`. A task is done only when every **Done when** item has been checked by running something. Tick the box (`- [x]`) when it is.

**Rules for every task**
- Build only what the current phase needs: no tables, endpoints or libraries from later phases.
- Write tests with the task, not later.
- Update API_CONTRACT.md, DATABASE_SCHEMA.md and DECISIONS.md in the same change when the task changes them.
- If the docs are unclear or disagree with each other, ask before guessing.

| Phase | Sprints | Theme |
|---|---|---|
| 1 | 1–4 | Local MVP: infrastructure, backend API, React UI |
| 2 | 5 | Cloud deployment, auth, sharing |
| 3 | 6 (expect to split into 6a and 6b) | AI biographer |
| 4 | 7 | PWA, offline, polish |

---

## Phase 1: Local MVP

### Sprint 1: Setup and local infrastructure

- [x] **1.1 Claude Code setup and specs in Markdown**
  - CLAUDE.md, `.claude/` (settings, rules, skills, migration-guard hook), `docs/*.md` converted from the Gemini drafts with D-01…D-18 applied, `.gitignore`, `.env.example`.

- [ ] **1.2 Machine prerequisites**
  - **Do:**
    - Install JDK 21 (for example, Eclipse Temurin) and make sure `java` is on `PATH`.
    - Confirm Docker Desktop is running and Node is 22 or later.
    - Copy `.env.example` to `.env`.
    - Optional: install the context7 plugin for up-to-date library docs.
  - **Done when:** `java -version` reports 21, `docker info` succeeds, `node -v` is 22 or later, and `.env` exists (and is gitignored).

- [ ] **1.3 Docker Compose: Postgres and SeaweedFS**
  - **Read:** TECH_STACK §1, D-03, D-04.
  - **Do:**
    - Root `docker-compose.yml` with two services:
      - `pgvector/pgvector:pg17` on 5432.
      - SeaweedFS on S3 port 8333 and filer UI port 8888. Don't publish its internal 8080.
    - Named volumes, health checks, and credentials from `.env`.
    - Commit any SeaweedFS S3 identity config needed for local credentials. It holds dev-only values.
  - **Done when:**
    - `docker compose up -d` shows both services healthy.
    - `psql` (or `docker compose exec postgres psql`) connects to database `memoirist`.
    - An S3 call with the `.env` credentials (for example, the `amazon/aws-cli` container with `--endpoint-url`) can create and list a bucket.

### Sprint 2: Backend foundation

- [ ] **2.1 Spring Boot 4 project**
  - **Read:** TECH_STACK §2 (Layout), D-02, D-15.
  - **Do:**
    - `/backend` with the Gradle wrapper and Kotlin DSL, a Java 21 toolchain, and Spring Boot 4.x (latest patch).
    - Starters: web, data-jpa, validation, actuator.
    - Libraries: Flyway (+ `flyway-database-postgresql`), PostgreSQL driver, springdoc-openapi (the Boot-4-compatible line), AWS SDK v2 `s3`.
    - Test dependencies: spring-boot-starter-test and Testcontainers (postgresql, junit-jupiter).
    - Spotless.
  - **Done when:** `./gradlew build` passes; the app starts with `--spring.profiles.active=local`; `/actuator/health` is `UP`; `/swagger-ui.html` loads.

- [ ] **2.2 Configuration and profiles**
  - **Read:** TECH_STACK §2 (Profiles, Cross-cutting rules), D-07.
  - **Do:**
    - `application.yml`, `application-local.yml` and `application-test.yml`.
    - `ddl-auto=validate`, `hibernate.jdbc.time_zone=UTC`, UTC default in `main`, and a `Clock` bean.
    - Multipart limits of 25 MB per file and 30 MB per request.
    - A `StorageProperties` `@ConfigurationProperties` record with `endpoint`, `public-endpoint`, `region`, `bucket`, `access-key`, `secret-key`, `path-style` and `presign-ttl`.
    - Flyway locations per profile.
    - The `local` profile imports `../.env`.
  - **Done when:** a test asserts the JVM default zone is UTC and the `Clock` bean exists; the app boots locally against Compose.

- [ ] **2.3 V1 migration and local seed**
  - **Read:** DATABASE_SCHEMA (Phase 1 tables, V1 SQL, seed).
  - **Do:** `db/migration/V1__core_schema.sql` and `db/seed-local/R__seed_dev_user.sql`, exactly as specified.
  - **Done when:** a Testcontainers test applies the migrations to an empty database and finds the dev user; local boot shows V1 in `flyway_schema_history`.

- [ ] **2.4 Entities and repositories**
  - **Read:** `.claude/rules/backend.md`, DATABASE_SCHEMA, D-12.
  - **Do:**
    - Entities: `User`, `Era`, `Template`, `WeeklyLog`, `Entry`, `Media`.
    - All extend a base class with `@CreationTimestamp` and `@UpdateTimestamp` `Instant`s and implement `Persistable<UUID>`.
    - JSONB fields use `@JdbcTypeCode(SqlTypes.JSON)`.
    - `Entry.media` uses `@OrderBy("displayOrder ASC")` and `@BatchSize(size = 50)`.
    - Repositories with user-scoped finders (`findByIdAndUserId`, …).
  - **Done when:** the context loads with `validate`; a repository test proves a user-scoped finder doesn't return another user's row; saving an entity with a preset ID issues an INSERT with no SELECT first.

- [ ] **2.5 Current user resolution**
  - **Read:** D-11, `.claude/rules/security.md`.
  - **Do:** a `CurrentUserProvider` interface and `DevHeaderCurrentUserProvider` (`@Profile({"local","test"})`).
  - **Done when:** tests prove that:
    - the header is honoured
    - a missing header falls back to the dev user
    - a malformed header returns `400 malformed-request`
    - in a non-local profile, the dev provider bean doesn't exist

- [ ] **2.6 Global error handling**
  - **Read:** API_CONTRACT (Errors).
  - **Do:** a `@RestControllerAdvice` mapping exceptions to the `ProblemDetail` types listed in API_CONTRACT (`timestamp`, `fieldErrors`), with no stack traces in responses.
  - **Done when:** tests cover `400` with `fieldErrors`, `404`, `409`, `413` and `500`.

- [ ] **2.7 CI: backend**
  - **Do:** `.github/workflows/ci.yml` with a backend job that runs `./gradlew build` on JDK 21 with Testcontainers.
  - **Done when:** the workflow passes on GitHub. If no remote exists yet, the YAML validates and the job's commands pass locally.

### Sprint 3: Phase 1 API

- [ ] **3.1 Storage service**
  - **Read:** TECH_STACK §2 (Storage), D-03, D-17.
  - **Do:**
    - `StorageService`: put, delete and presign, with `S3Client` using `endpoint` and `S3Presigner` using `public-endpoint`.
    - Path-style access.
    - A local-profile bucket bootstrap.
    - After-commit object deletion.
  - **Done when:** a Testcontainers test (SeaweedFS) uploads, presigns (URL host = public endpoint; GET returns the bytes) and deletes.

- [ ] **3.2 `/users/me`**
  - **Done when:** GET and PATCH work; an invalid timezone returns `400`; tests pass.

- [ ] **3.3 Eras CRUD** (`/new-endpoint`)
  - **Done when:** every row of API_CONTRACT §Eras works and is tested, including another user's era returning `404` and deleting an era leaving its weeks era-less.

- [ ] **3.4 Templates CRUD** (`/new-endpoint`)
  - **Done when:** every row of §Templates works and is tested; subheading limits are enforced.

- [ ] **3.5 Entries: create, read, update, delete, seal, redaction**
  - **Read:** D-05, D-08, D-09, D-10, D-12, API_CONTRACT §Entries.
  - **Do:** `WeekCalendar`, `EntryContentValidator`, `ContentTextExtractor`, `EntryMapper` (the only Entry→DTO path), and `EntryService` seal rules.
  - **Done when:** tests prove:
    - the first entry of a week creates the week, and the second reuses it
    - an entry written at 23:30 on Sunday Melbourne time lands in that Melbourne week, even though it's Sunday morning in UTC
    - a daylight-saving week boundary works
    - backdating works, and a future date returns `400`
    - changing the date moves the entry to the matching week
    - disallowed nodes and `javascript:` links return `400`
    - a sealed entry is redacted on GET, PUT, seal and feed responses, and in week detail
    - editing a sealed entry returns `409`, as does moving its seal earlier
    - a sealed entry can be deleted
    - another user's entry returns `404`
    - replaying an existing ID returns `200`; another user's ID returns `409`

- [ ] **3.6 Media**
  - **Read:** API_CONTRACT §Media, D-17.
  - **Do:** upload (type, size and count checks), reorder (deferrable unique), delete with gap closing, and stored-file cleanup when an entry is deleted.
  - **Done when:** tests cover the `415`, `413`, item-limit and sealed (`409`) cases; a reorder swap; and stored objects disappearing after deleting a media item and after deleting an entry.

- [ ] **3.7 Weekly logs and feed**
  - **Read:** API_CONTRACT §Feed and §Weekly logs, TECH_STACK §2 (Weeks and the feed), Q1.
  - **Done when:**
    - the feed returns only non-empty weeks, newest first, with era and redacted entries
    - the query count for a page doesn't grow with the number of entries or media (assert with Hibernate statistics)
    - PUT, close and reopen work, and an era owned by another user returns `404`

- [ ] **3.8 API review**
  - **Do:** run `/sync-docs`; check that every endpoint has its tag and documented error responses in Swagger.
  - **Done when:** no drift remains between `/v3/api-docs` and API_CONTRACT.md.

### Sprint 4: React UI

- [ ] **4.1 Scaffold the frontend**
  - **Read:** TECH_STACK §3, D-13.
  - **Do:**
    - Vite + React 19 + TypeScript in `/frontend`.
    - Tailwind v4 (CSS-first config).
    - ESLint + Prettier, Vitest + React Testing Library + MSW.
    - A Vite proxy from `/api` to `:8080`.
    - Scripts: `dev`, `build`, `test`, `lint`, `typecheck`, `gen:api`.
  - **Done when:** `npm run build`, `npm test`, `npm run lint` and `npm run typecheck` all pass.

- [ ] **4.2 API client and data layer**
  - **Do:**
    - `gen:api` (openapi-typescript writes `src/api/schema.d.ts`).
    - An openapi-fetch client that sends `X-Dev-User-Id` only in dev.
    - A TanStack Query provider and a query-key factory, with `staleTime` below 60 minutes.
  - **Done when:** a typed `useMe()` hook renders the dev user's name against the running backend; a component test uses MSW.

- [ ] **4.3 Routing, shell and time utilities**
  - **Do:**
    - React Router routes: `/` (feed), `/eras`, `/templates`, `/settings`.
    - The app shell.
    - A Zustand UI store.
    - `lib/time.ts` (formatting in the user's zone, "today" in the user's zone).
  - **Done when:** time utilities have unit tests covering a Melbourne daylight-saving change and a non-Melbourne zone.

- [ ] **4.4 Feed**
  - **Do:** infinite scroll over `GET /feed` (`useInfiniteQuery`), sticky era headers and week sections.
  - **Done when:** the feed shows seeded data; there are empty, loading and error states; component tests pass.

- [ ] **4.5 Entry card**
  - **Do:** an Embla carousel (images and an audio player; keyboard-operable) and a read-only caption rendered from JSON (no `dangerouslySetInnerHTML`).
  - **Done when:** component tests cover image-only, caption-only and mixed entries.

- [ ] **4.6 Capture modal**
  - **Do:**
    - A TipTap editor (StarterKit + Link with a protocol allow-list).
    - A template picker that inserts headings.
    - A memory-date picker (default today, never in the future).
    - Multi-file upload with reorder.
    - The flow: create (client UUID) → upload → reorder.
  - **Done when:** creating an entry with two photos shows it in the feed in the right week; validation errors from the API are shown on the right fields.

- [ ] **4.7 Voice notes**
  - **Do:** a `MediaRecorder` recorder that picks `audio/webm;codecs=opus` or `audio/mp4`.
  - **Done when:** a recording uploads and plays back in Chrome. Note whether Safari or iOS could be tested.

- [ ] **4.8 Loot Drops in the UI**
  - **Do:** a seal dialog (pick a date, then a confirmation repeating the date), a sealed vault card with a live countdown, and a `409` that surfaces as a friendly message.
  - **Done when:** sealing an entry turns it into a vault card; the countdown is correct in the user's zone.

- [ ] **4.9 Eras, templates and settings pages**
  - **Do:** CRUD for eras and templates; assign a week to an era; settings for display name and timezone.
  - **Done when:** changing the timezone changes the displayed times; component tests pass.

- [ ] **4.10 End-to-end smoke test and CI: frontend**
  - **Do:**
    - A Playwright smoke test: create an entry with a photo, then seal another.
    - A CI frontend job (lint, typecheck, test, build).
    - Check the HEIC→JPEG behaviour on iOS if possible (D-17).
  - **Done when:** Playwright passes locally against Compose and the dev servers; CI is green.

---

## Phase 2: Cloud, auth and sharing

### Sprint 5
**Decide first:** Q2 (auth details), Q3 (account export and deletion), Q4 (PDF), Q5 (cold starts). Record each in DECISIONS.md.

- [ ] **5.1 Auth migration and JWT provider.** Apply the V{n} auth migration and add a JWT `CurrentUserProvider` for `prod`. **Done when:** controllers are unchanged; tests prove an unauthenticated request returns `401` and the dev header is ignored in `prod`.
- [ ] **5.2 Register and login screens.**
- [ ] **5.3 Share links.** Hashed tokens, expiry and revoke; a public read-only view with redaction. **Done when:** a test proves a share link to a week containing a sealed entry never exposes that entry's content or media.
- [ ] **5.4 PDF export of a week** (per Q4).
- [ ] **5.5 Account export and deletion** (per Q3), including stored files.
- [ ] **5.6 Production deploy.** Neon, R2 (private bucket), Render (Docker, health check `/actuator/health`), Vercel (with an `/api` rewrite), environment secrets. **Done when:** the deployed app passes the Playwright smoke test.

## Phase 3: AI biographer

### Sprint 6 (split into 6a and 6b when planning)
**Decide first:** Q6 (provider, embedding dimension, consent wording).

- [ ] **6.1 AI migration** (pgvector, `ai_metadata`, `ai_summaries`, characters, edges) with the chosen `vector(N)`.
- [ ] **6.2 Per-entry analysis and one-tap confirmations.** **Done when:** analysing a sealed entry returns `409`; the response arrives in under 5 s on typical entries.
- [ ] **6.3 Weekly summary and embedding** via `WeeklyLogClosedEvent` and `@Async`. **Done when:** a test proves sealed entries are excluded from the summary input.
- [ ] **6.4 Cast of Characters and friendship map.**
- [ ] **6.5 Semantic insights query.**

## Phase 4: Mobile, offline and polish

### Sprint 7
**Decide first:** Q7 (dictation approach).

- [ ] **7.1 Installable PWA** (`vite-plugin-pwa`).
- [ ] **7.2 Offline capture** with Dexie plus a sync queue (client UUIDs, idempotent creates).
- [ ] **7.3 Voice-to-text dictation.**
- [ ] **7.4 Inline photo node in TipTap** (stores a `mediaId`); extend the backend content allow-list to match.
- [ ] **7.5 Zine export.**
