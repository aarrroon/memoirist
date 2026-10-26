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
| 3 | 6 (expect to split into 6a, 6b and 6c) | AI biographer and reading paper diaries |
| 4 | 7 | PWA, offline, polish |
| 5 | 8 (expect 8a and 8b) | Groups and shared memories |
| 6 | 9 | Insights and coaching |

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
**Decide first:** Q18 (how the feed shows a week that is in several Eras, D-22). Record it in DECISIONS.md.

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
  - **Done when:** every row of API_CONTRACT §Eras works and is tested, including another user's era returning `404` and deleting an Era removing it from its weeks while their other Eras stay.

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
    - the feed returns only non-empty weeks, newest first, with their Eras and redacted entries
    - the query count for a page doesn't grow with the number of entries or media (assert with Hibernate statistics)
    - PUT (with `eraIds`), close and reopen work, a week can be in several Eras, and an Era owned by another user returns `404`

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
  - **Do:** infinite scroll over `GET /feed` (`useInfiniteQuery`), sticky era headers and week sections. A week in several Eras is grouped under `eras[0]`, with the others as chips (Q18).
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
  - **Do:** CRUD for eras and templates; assign a week to one or more Eras; settings for display name and timezone.
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

### Sprint 6 (split into 6a, 6b and 6c when planning)
**Decide first:** Q6 (provider, embedding dimension, consent wording). 6c (paper diaries) also needs Q12 (handwriting recognition approach and consent). Help me think needs Q14 (pause length), and Q17 (what withdrawing consent deletes) is settled before 6.9.
**Suggested split (D-21):** 6a = AI foundation and Help me think (6.1, 6.9, 6.10, 6.2, 6.11, 6.12). 6b = summaries, people and search (6.3, 6.4, 6.5, 6.13, 6.14, 6.15). 6c = paper diaries (6.6, 6.7, 6.8). Inside 6a, build 6.9 and 6.10 before 6.2. Paper diaries need 6.9 too, because reading a page needs the AI consent.

- [ ] **6.1 AI migration** (pgvector, `ai_metadata`, `ai_summaries`, characters, edges) with the chosen `vector(N)`.
- [ ] **6.2 Help me think: two-option questions** (`/new-migration`, `/new-endpoint`). The `ai_suggestions` table, `POST /suggestions` and `POST /suggestions/{id}/answer` for a saved entry or an unsaved draft, with whitelisted effects. **Done when:** a sealed entry returns `409`; a draft is read without being stored and its cards expire; answering applies the effect once and repeating the same answer is harmless; another user's suggestion is `404`; every question has exactly two options (anything else the model returns is dropped); and the response arrives in under 5 s on typical entries.
- [ ] **6.3 Weekly summary and embedding** via `WeeklyLogClosedEvent` and `@Async`. **Done when:** a test proves sealed entries are excluded from the summary input.
- [ ] **6.4 Cast of Characters and friendship map.**
- [ ] **6.5 Semantic insights query.**

**6c: reading paper diaries (D-20).** Photos of pages already work from Phase 1; this adds the optional "Read this page" step. It doesn't depend on the vector extension.

- [ ] **6.6 Page scans and the transcription provider** (`/new-migration`). Apply the page-scan migration (`media.purpose`, `media_transcriptions`), add the `TranscriptionProvider` interface with a fake adapter for `local` and `test`, and the one real adapter chosen in Q12. **Done when:** a Testcontainers test applies the migration; the fake adapter takes a transcription from `PENDING` to `READY`; and a provider error ends as `FAILED` with a short reason and no page content in the logs.
- [ ] **6.7 Transcription endpoints** (`/new-endpoint`). **Done when:** tests prove a sealed entry returns `409` and the provider is never called for it (use a spy); another user's entry or media returns `404`; asking again for a pending page returns the existing one; going over the daily limit returns `429`; and deleting the media or the entry removes its transcription.
- [ ] **6.8 Read-a-page UI.** A "Read this page" action on page photos, a progress state, a side-by-side review of the photo and its text, "Use text" (fills the caption and saves with `PUT /entries/{id}`), and a chip to accept the date found on the page. **Done when:** a Playwright flow with the fake provider uploads a page photo, reads it, uses the text and accepts the date, and the feed shows the entry in the right week.

**AI foundation, tags and Help me think (D-21).** Build 6.9 and 6.10 before 6.2.

- [ ] **6.9 AI consent, settings and the feature gate** (`/new-migration`, `/new-endpoint`). `user_settings`, `GET` and `PUT /users/me/settings`, `POST` and `DELETE /users/me/ai-consent`, `DELETE /users/me/ai-data`, and a `FeatureGate` that reads configuration. **Done when:** tests prove every AI endpoint returns `403 consent-required` until consent is given and again after it is withdrawn; turning on a feature that isn't available returns `403 feature-unavailable`; unknown settings keys return `400`; the client can't set `available`; and deleting AI data removes everything derived while leaving entries untouched.
- [ ] **6.10 `AiContentSource` and invalidation.** The single reader of entry text, tags and AI facts for AI, plus the listener that reacts when an entry is edited, sealed, unlocked or deleted. **Done when:** tests prove sealed entries never come out of the source (and do once unlocked); editing, sealing or deleting an entry deletes the cards that cite it and marks the summaries and profiles built from it stale; and a stale summary is never used as input to another.
- [ ] **6.11 Tags** (`/new-migration`, `/new-endpoint`). `tags` and `entry_tags`, the tag endpoints, `tags` on `Entry` (empty when sealed, through `EntryMapper`), browsing by tag, and the `TAG` kind of suggestion. **Done when:** tags are scoped to the user; names are unique ignoring case; deleting a tag untags its entries; a sealed entry's tags are redacted and can't be changed; and an AI tag suggestion adds the tag only after the user taps.
- [ ] **6.12 Help me think UI.** The pause nudge and button in the editor, a two-button question card, tag chips, and the settings switch. **Done when:** a Playwright flow with the fake provider types, pauses, sees the button, taps it, answers a question and accepts a tag; nothing is sent before the tap; and turning the nudge off removes the prompt.

**Era summaries and friend profiles (D-21).** Need 6.3 and 6.4.

- [ ] **6.13 Era summaries** (`/new-migration`, `/new-endpoint`). Built from the Era's weekly summaries, on request and when an Era ends. **Done when:** tests prove an Era with no weekly summaries returns `409 not-enough-content`; a stale weekly summary makes the Era summary stale; rebuilding replaces it; and deleting the Era deletes its summary.
- [ ] **6.14 Friend profiles** (`/new-migration`, `/new-endpoint`). Stats (mentions, first and last mention, Eras) plus an AI-written profile, made automatically once someone is mentioned in several entries, with rename, notes, hide and merge. **Done when:** tests prove stats come from the database and not the AI; a rebuild never overwrites the user's notes; merging two people moves their mentions and keeps the old name as an alias; hidden people stay out of the map; and another user's character is `404`.
- [ ] **6.15 Era and Cast UI.** An Era page with its summary and a Cast page with profile cards, editing and merging. **Done when:** component tests cover the empty, stale and failed states, and a Playwright flow shows a summary and a profile built with the fake provider.

## Phase 4: Mobile, offline and polish

### Sprint 7
**Decide first:** Q7 (dictation approach) and Q13 (bulk notebook import).

- [ ] **7.1 Installable PWA** (`vite-plugin-pwa`).
- [ ] **7.2 Offline capture** with Dexie plus a sync queue (client UUIDs, idempotent creates).
- [ ] **7.3 Voice-to-text dictation.**
- [ ] **7.4 Inline photo node in TipTap** (stores a `mediaId`); extend the backend content allow-list to match.
- [ ] **7.5 Zine export.**
- [ ] **7.6 Scan mode** (D-20). A camera screen for diary pages: edge detection, crop, straighten and brighten, "add another page", and a file-picker fallback. Pages are saved as JPEG (D-17), uploaded with `purpose = PAGE_SCAN`, and ride the offline queue from 7.2, which means uploads must be safe to retry (extend D-12 with an optional client-supplied media `id` when 7.2 is planned). Ask before adding an image-processing library. **Done when:** on a phone, two photographed pages end up as one backdated entry with two page scans; the same flow works offline and syncs later; and the file-picker path works without a camera.

## Phase 5: Groups and shared memories

### Sprint 8 (split into 8a and 8b when planning)
**Start after:** Phase 2 (real accounts). 8b also needs Phase 3. The sprint can run before or after Phase 4.
**Decide first:** Q8–Q11, plus the consent wording from Q6 and Q9 for 8b. Record each in DECISIONS.md, then review the Phase 5 drafts in DATABASE_SCHEMA.md and API_CONTRACT.md.

**8a: groups and Loot Drops (no AI)**

- [ ] **8.1 Groups, members and the access rule** (`/new-migration`, `/new-endpoint`). Apply the groups migration, add the group endpoints with one membership check that every group endpoint uses, and extend `.claude/rules/security.md` with the membership rule (D-19). **Done when:** tests prove a non-member gets `404` on every group endpoint, a member gets `403` on owner-only actions, the only owner can't leave, and leaving unlinks the member's contributions without deleting their entries.
- [ ] **8.2 Invite links.** Hashed tokens, expiry, revoke and accept. **Done when:** the raw token appears only in the create response, expired, revoked and unknown tokens return `404`, and accepting twice is harmless.
- [ ] **8.3 Contributions and the group feed.** **Done when:** tests prove a member reads another member's contribution only through `/groups/**` while `GET /entries/{id}` still returns `404`; a withdrawn contribution disappears from the group while the entry survives; and a non-member never gets a presigned media URL.
- [ ] **8.4 Group Loot Drops.** **Done when:** tests prove contributing to a locked group needs the matching `confirmUnlockDate` and seals the entry until at least the group's date; nobody, including the author and the group owner, can read a sealed contribution early; the group's date can't move earlier (`409 group-sealed`); and members can see who has contributed but not what.
- [ ] **8.5 Trips as Eras.** **Done when:** joining a dated group creates an Era in the member's own timeline; contributing adds that week to it; leaving keeps the Era but unlinks it; and deleting the Era doesn't change membership.
- [ ] **8.6 Groups UI.** Group list and create, invite sheet, group feed with author names, an "Add to group" action on entries (with the unlock-date confirmation for locked groups), a group vault card with a countdown, and the trip Era in the feed. **Done when:** a Playwright flow with two accounts creates a group, invites, has both people contribute, and shows both entries in the group feed.

**8b: AI-powered (needs Phase 3)**

- [ ] **8.7 Shared story.** Per-member consent, then a story built from consenting members' contributions. **Done when:** tests prove sealed entries and contributions from members who haven't opted in are excluded from the input; fewer than two contributors returns `409 not-enough-contributions`; and withdrawing, opting out or leaving deletes the stored story.
- [ ] **8.8 Real friendship map.** Link characters to group members. **Done when:** tests prove a character can only be linked to a member of a group the caller belongs to (anything else is `404`), linking exposes nothing of that member's journal, and the map shows linked friends.

## Phase 6: Insights and coaching

### Sprint 9
**Start after:** Phase 3 (the AI layer, weekly summaries and Era summaries). It is independent of Phases 4 and 5.
**Decide first:** Q15 (paid features) and Q16 (how often insights appear, and where). Record each in DECISIONS.md, then review the Phase 6 drafts in DATABASE_SCHEMA.md and API_CONTRACT.md.

- [ ] **9.1 Entry embeddings and retrieval** (`/new-migration`). `entry_embeddings`, and a `Retriever` that searches only the caller's own entries and summaries. Sealed entries are never embedded and are removed when sealed. **Done when:** tests prove results never include another user's data, sealed entries or stale summaries; an entry that unlocks becomes searchable; and a multi-user test shows per-user results aren't cut short by the vector index.
- [ ] **9.2 Proactive insights** (`/new-endpoint`). Pattern spotters, growth reminders and "last time you felt like this", as cards with sources, a weekly cap, duplicate protection, and feedback and dismiss. **Done when:** tests prove a card always cites at least one source; nothing is made without opt-in; the weekly cap and `dedupe_key` hold; and a dismissed insight doesn't come back.
- [ ] **9.3 Highlight reels** (`/new-endpoint`). On request and when an Era ends. A reel stores entry IDs only and is built through `EntryMapper` when it is read. **Done when:** a test proves an entry sealed after the reel was made disappears from it, and a photo-only entry still appears.
- [ ] **9.4 Writing coach.** Off by default, behind a switch and the `FeatureGate`, with a daily cap, and most requests return nothing. **Done when:** tests prove nothing is sent while the switch is off, going over the cap returns `429`, a low-scoring draft returns an empty list, and a card can be dismissed with feedback.
- [ ] **9.5 Insights UI.** An insights inbox, reels, the coach card in the editor, and settings for each switch and the weekly cap. **Done when:** a Playwright flow with the fake provider shows an inbox card with its sources, dismisses it, and opens a reel.
