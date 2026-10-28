# Sprint Plan: memoirist

> The build order. Each task is sized for one Claude Code session: start it with `/implement-task <id>`. A task is done only when every **Done when** item has been checked by running something. Tick the box (`- [x]`) when it is.

**Rules for every task**
- Build only what the current phase needs: no tables, endpoints or libraries from later phases.
- Write tests with the task, not later.
- Update API_CONTRACT.md, DATABASE_SCHEMA.md and DECISIONS.md in the same change when the task changes them.
- If the docs are unclear or disagree with each other, ask before guessing.
- If a task would change what the README promises, or in which phase, ask first (D-27).

| Phase | Sprints | Theme (README name first) |
|---|---|---|
| 1 | 1–4 | The core journal: infrastructure, backend API, React UI (local MVP) |
| 2 | 5 | Online, with accounts: cloud deployment, auth, sharing, installing the app |
| 3 | 6 | Memories with friends: groups, sealed trips and trip Eras |
| 4 | 7 (expect to split into 7a, 7b and 7c) | The AI biographer, reading paper diaries, and the shared trip story |
| 5 | 8 | On your phone: offline, scan mode, polish |
| 6 | 9 | Looking back with you: insights and coaching |

The phases follow D-31: groups come before the AI biographer, and the phone extras come after it.

---

## Phase 1: The core journal (local MVP)

### Sprint 1: Setup and local infrastructure

- [x] **1.1 Claude Code setup and specs in Markdown**
  - CLAUDE.md, `.claude/` (settings, rules, skills, migration-guard hook), `docs/*.md` converted from the Gemini drafts with D-01…D-18 applied, `.gitignore`, `.env.example`.

- [x] **1.2 Machine prerequisites**
  - **Do:**
    - Install JDK 25 (for example, Eclipse Temurin 25) and make sure `java` is on `PATH`.
    - Confirm Docker Desktop is running and Node is 22 or later.
    - Copy `.env.example` to `.env`.
    - context7 (up-to-date library docs) is already set up in `.mcp.json`; nothing to install.
  - **Done when:** `java -version` reports 25, `docker info` succeeds, `node -v` is 22 or later, and `.env` exists (and is gitignored).

- [ ] **1.3 Docker Compose: Postgres and SeaweedFS**
  - **Read:** TECH_STACK §1, D-03, D-04.
  - **Do:**
    - Root `docker-compose.yml` with two services:
      - `pgvector/pgvector:pg17` on 5432.
      - SeaweedFS on S3 port 8333 and filer UI port 8888, with a pinned image tag. Don't publish its internal 8080.
    - Named volumes, health checks, and credentials from `.env`.
    - SeaweedFS's S3 credentials come from environment variables mapped from `.env` (D-03). Commit no identity file.
  - **Done when:**
    - `docker compose up -d` shows both services healthy.
    - `psql` (or `docker compose exec postgres psql`) connects to database `memoirist`.
    - An S3 call with the `.env` credentials (for example, the `amazon/aws-cli` container with `--endpoint-url`) can create and list a bucket.
    - The same call without signing (`--no-sign-request`) is rejected with `403`.

### Sprint 2: Backend foundation

- [ ] **2.1 Spring Boot 4 project**
  - **Read:** TECH_STACK §2 (Layout), D-02, D-15.
  - **Do:**
    - `/backend` with the Gradle wrapper and Kotlin DSL, a Java 25 toolchain (and a Gradle version that supports Java 25), and Spring Boot 4.x (latest patch).
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
  - **Do:** `db/migration/V1__core_schema.sql` and `db/seed-local/R__seed_dev_user.sql`, exactly as specified, including `users.birthday` and `entries.reply_to_entry_id` (D-33), `soundtrack` on `entries`, `weekly_logs` and `eras` (D-35), `eras.cover_media_id` (D-36), `media.purpose` (D-20), `entries.unlock_seen_at` (D-10), `entries.deleted_at` (D-30), `entries.version` (D-23), `entries.date_unknown` (D-34) and the 1900 date check (D-09). There's no week–Era table: Eras are worked out from their dates (D-22).
  - **Done when:** a Testcontainers test applies the migrations to an empty database and finds the dev user; local boot shows V1 in `flyway_schema_history`.

- [ ] **2.4 Entities and repositories**
  - **Read:** `.claude/rules/backend.md`, DATABASE_SCHEMA, D-12.
  - **Do:**
    - Entities: `User`, `Era`, `Template`, `WeeklyLog`, `Entry`, `Media`, `Tag` (with `entry_tags` as the link). There's no week–Era entity (D-22).
    - Two base classes (D-12): `BaseEntity` (UUID `id`, `Persistable<UUID>`) and `TimestampedEntity` (adds `@CreationTimestamp` and `@UpdateTimestamp` `Instant`s). `Media` extends `BaseEntity` and keeps `uploadedAt`; the rest extend `TimestampedEntity`.
    - `Entry.version` is a `@Version` field (D-23).
    - JSONB fields use `@JdbcTypeCode(SqlTypes.JSON)`.
    - `Entry.media` uses `@OrderBy("displayOrder ASC")` and `@BatchSize(size = 50)`.
    - Repositories with user-scoped finders (`findByIdAndUserId`, …). Entry finders leave out entries in Recently deleted (D-30).
  - **Done when:** the context loads with `validate`; a repository test proves a user-scoped finder doesn't return another user's row; saving an entity with a preset ID issues an INSERT with no SELECT first; and the entry finders skip entries in Recently deleted.

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
  - **Done when:** tests cover `400` with `fieldErrors`, `404`, `409`, `409 entry-stale` (from an optimistic-locking failure), `413` and `500`.

- [ ] **2.7 CI: backend**
  - **Do:** `.github/workflows/ci.yml` with a backend job that runs `./gradlew build` on JDK 25 with Testcontainers.
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
  - **Read:** D-07, API_CONTRACT §Users.
  - **Done when:** tests prove GET and PATCH work; an unknown timezone returns `400`; a `displayName` that is blank or over 100 characters returns `400`. (`birthday` is task 3.13.)

- [ ] **3.3 Eras CRUD** (`/new-endpoint`)
  - **Read:** D-22, API_CONTRACT §Eras.
  - **Done when:** every row of API_CONTRACT §Eras works and is tested (soundtracks are tested in 3.14 and covers in 3.15), including:
    - another user's era returns `404`
    - deleting an Era leaves its weeks, their entries and their other Eras untouched
    - a new Era holds the existing weeks its dates overlap
    - changing its dates changes which weeks it holds
    - `GET /eras/{id}` counts only the entries inside its dates, so an Era from Saturday to Sunday doesn't count the rest of the week; sealed entries and entries in Recently deleted aren't counted (D-10, D-30)
    - a week's `eras` list puts pinned Eras first, then the latest start (D-22)

- [ ] **3.4 Templates CRUD** (`/new-endpoint`)
  - **Done when:** every row of §Templates works and is tested; subheading limits are enforced.

- [ ] **3.5 Entries: create, read, update, delete, seal, redaction**
  - **Read:** D-05, D-08, D-09, D-10, D-12, D-30, API_CONTRACT §Entries.
  - **Do:** `WeekCalendar` (weeks always start on Monday, D-08), `EntryContentValidator`, `ContentTextExtractor`, `EntryMapper` (the only Entry→DTO path), and `EntryService` seal rules.
  - **Done when:** tests prove:
    - an optional title is saved, and a blank title is `400`
    - the first entry of a week creates the week, and the second reuses it
    - an entry written at 23:30 on Sunday Melbourne time lands in that Melbourne week, even though it's Sunday morning in UTC
    - a daylight-saving week boundary works
    - backdating works; a future date, or one before 1900-01-01, returns `400`
    - changing the date moves the entry to the matching week and clears `dateUnknown`; `dateUnknown: true` on create is saved and shown, sealed or not (D-34)
    - disallowed nodes and `javascript:` links return `400`
    - a sealed entry is redacted on GET, PUT and seal responses (the feed and week detail are tested in 3.7)
    - editing a sealed entry returns `409`, as does moving its seal earlier
    - a `PUT` with an older `version` returns `409 entry-stale` and changes nothing; a current one saves and returns the next `version` (D-23)
    - deleting an entry, sealed or not, moves it to Recently deleted, after which `GET`, `PUT` and `seal` return `404` (restore, erase and the sweep are task 3.11)
    - another user's entry returns `404`
    - replaying an existing ID returns `200`; another user's ID, or one in Recently deleted, returns `409`

- [ ] **3.6 Media**
  - **Read:** API_CONTRACT §Media, D-17, D-20.
  - **Do:** upload (type, size, count and `purpose` checks), reorder (deferrable unique), delete with gap closing, and stored-file cleanup when an entry is erased (D-30).
  - **Done when:** tests cover the `415`, `413`, item-limit and sealed (`409`) cases; a `PAGE_SCAN` that isn't an image (`415`) and an unknown purpose (`400`); a reorder swap; and stored objects disappearing after deleting a media item and after erasing an entry, while deleting an entry keeps them (D-30).

- [ ] **3.7 Weekly logs and feed**
  - **Read:** API_CONTRACT §Feed and §Weekly logs, TECH_STACK §2 (Weeks and the feed), D-22.
  - **Done when:**
    - the feed and week detail (`GET /weekly-logs/{id}`) show sealed entries redacted (D-10)
    - the feed returns only non-empty weeks, newest first, with their Eras and redacted entries; entries in Recently deleted don't count, so a week whose entries are all deleted is left out (D-30)
    - the query count for a page doesn't grow with the number of entries or media (assert with Hibernate statistics)
    - PUT (the title), close and reopen work
    - a week's `eras` are every Era whose dates overlap it, latest start first, and change as soon as an Era's dates change (D-22)

- [ ] **3.8 Tags** (`/new-endpoint`)
  - **Read:** D-10, D-26, API_CONTRACT §Tags and §Entries.
  - **Do:** tag CRUD; `tagIds` on `POST` and `PUT /entries`; `tags` on `Entry` through `EntryMapper`.
  - **Done when:** tests prove tags are scoped to the user; names are unique ignoring case (creating a duplicate returns the existing tag); renaming to an existing name is `409`; deleting a tag untags its entries; more than 20 tags is `400`; another user's tag ID is `404`; a sealed entry's tags are hidden and can't be changed (`409`); and `entryCount` doesn't count sealed entries.

- [ ] **3.9 Entry list for the timeline**
  - **Read:** D-10, D-24, D-25, API_CONTRACT §EntrySummary and `GET /entries`.
  - **Do:** `GET /entries` with paging and an optional `tagId`, returning `EntrySummary` rows with `title`, `previewLine`, media counts, `firstMediaKind` and tags.
  - **Done when:** tests prove:
    - ordering (newest memory date first, then newest created)
    - the tag filter, which never returns sealed entries
    - `previewLine` is the caption's first non-heading line, capped at 120 characters, falling back to the first heading
    - `firstMediaKind` names the first media item
    - a sealed entry's title, preview, counts, media kind and tags are hidden
    - the query count per page doesn't grow with the number of entries

- [ ] **3.10 Opened Loot Drops**
  - **Read:** D-10, API_CONTRACT §Entries.
  - **Do:** `GET /entries/opened` and `POST /entries/{entryId}/mark-seen`; sealing an entry clears `unlock_seen_at`.
  - **Done when:** tests with a fixed `Clock` prove an entry appears once its unlock time passes, disappears after mark-seen, appears again after being sealed and unlocking again, and never appears while sealed (mark-seen returns `409`); another user's entry returns `404`.

- [ ] **3.11 Recently deleted** (D-30, `/new-endpoint`)
  - **Read:** D-30, API_CONTRACT §Entries.
  - **Do:** `GET /entries/deleted`, `POST /entries/{entryId}/restore` and `/erase`, and a sweep that erases entries deleted more than 30 days ago, with their stored files. The sweep is a service method that is safe to repeat; a daily `@Scheduled` job only calls it, so Sprint 5 can start it another way (Q5).
  - **Done when:** tests with a fixed `Clock` prove:
    - a deleted entry is left out of the feed, its week, the timeline, tag counts and filters, and the Opened strip, and comes back in all of them when restored
    - restoring keeps its media, tags and seal, and a sealed entry stays redacted in the list
    - erasing, and the sweep after 30 days, remove the entry and its stored files, and the sweep leaves younger ones alone
    - an empty entry is erased at once and never listed
    - restore and erase return `404` for an entry that isn't in Recently deleted, or is another user's

- [ ] **3.12 On this day** (D-32, `/new-endpoint`)
  - **Read:** D-10, D-30, D-32, API_CONTRACT §Entries.
  - **Do:** `GET /entries/on-this-day`: the user's entries from the same day and month in earlier years, newest year first, as `EntrySummary` rows.
  - **Done when:** tests with a fixed `Clock` prove:
    - entries from earlier years on the same day are listed, and this year's aren't
    - sealed and deleted entries never appear
    - entries from 29 February appear on 28 February in a year that isn't a leap year
    - "the same day" follows the user's timezone
    - another user's entries never appear

- [ ] **3.13 Loot Drop replies and the birthday** (D-33, `/new-endpoint`)
  - **Read:** D-10, D-33, API_CONTRACT §Users and §Entries.
  - **Do:** `birthday` on `GET` and `PATCH /users/me`; `replyToEntryId` on `POST` and `PUT /entries`; `replyToEntryId` and `replyIds` on `Entry`.
  - **Done when:** tests prove:
    - a reply can point only at the user's own Loot Drop that has opened; a sealed one or an entry that was never sealed is `400`, and another user's is `404`
    - `replyIds` lists an opened Loot Drop's replies
    - deleting either entry leaves the other
    - a birthday in the future is `400`
    - a sealed reply is redacted like any entry

- [ ] **3.14 Soundtracks and Spotify search** (D-35, `/new-endpoint`)
  - **Read:** D-29, D-35, API_CONTRACT §Entries, §Weekly logs, §Eras and §Music.
  - **Do:**
    - `soundtrack` (a checked Spotify URI) on entries, weeks and Eras.
    - A `SpotifyProperties` record and a `MusicSearchService` that gets an app-only token with the client credentials flow, reuses it until it expires, and calls Spotify's search.
    - `POST /music/search`.
  - **Done when:** tests against a stubbed Spotify (never the real one) prove:
    - a malformed URI is `400`
    - a sealed entry's soundtrack is hidden
    - search returns at most 10 results
    - missing settings, or a `429` from Spotify, return `503 music-search-unavailable`
    - the token is fetched once and reused
    - search terms appear in none of our URLs and in no log line, including `RestClient` request logging (the call to Spotify itself carries them, over HTTPS)

- [ ] **3.15 Era covers** (D-36)
  - **Read:** D-10, D-30, D-36, API_CONTRACT §Eras.
  - **Do:** `coverMediaId` on `POST` and `PUT /eras`, and a presigned `cover` on the Era shape.
  - **Done when:** tests prove:
    - a cover must be an image from one of the user's own entries; anything else, or a photo from a sealed entry, is `400`; another user's, or one from an entry in Recently deleted, is `404`
    - the cover is hidden while its entry is sealed or in Recently deleted
    - removing the photo clears the cover

- [ ] **3.16 Local sample content**
  - **Read:** D-11, TECH_STACK §2 (Profiles).
  - **Do:** a seeder that runs only in the `local` profile, behind a property (for example `memoirist.sample-content=true`). It creates overlapping Eras, tags, a template and a few weeks of entries: one started from the template, a backdated diary page, a photo-only entry, a sealed Loot Drop, an entry with a soundtrack and an entry in Recently deleted, and one Era has a cover. A few generated placeholder images are uploaded through `StorageService`. Running it twice changes nothing.
  - **Done when:** on an empty local database with the property on, `GET /feed` returns the sample content, and a test proves the seeder bean doesn't exist in the `test` profile.

- [ ] **3.17 API review**
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
    - React Router routes: `/` (feed), `/entries` (all-entries timeline), `/weeks/:weekId` (week summary), `/eras`, `/templates`, `/tags`, `/settings`, `/recently-deleted`.
    - The app shell.
    - A Zustand UI store.
    - `lib/time.ts` (formatting in the user's zone, "today" in the user's zone).
  - **Done when:** time utilities have unit tests covering a Melbourne daylight-saving change and a non-Melbourne zone.

- [ ] **4.4 Feed**
  - **Do:** infinite scroll over `GET /feed` (`useInfiniteQuery`), sticky era headers and week sections. A week in several Eras is grouped under `eras[0]`, with the others as chips; when `eras[0]` sits wholly inside another of the week's Eras, the week shows as a section nested inside that Era, one level deep (D-22).
  - **Done when:** the feed shows the local sample content (3.16); there are empty, loading and error states; component tests pass.

- [ ] **4.5 Entry card**
  - **Do:** an Embla carousel (images and an audio player; keyboard-operable) and a read-only caption rendered from JSON (no `dangerouslySetInnerHTML`).
  - **Done when:** component tests cover image-only, caption-only and mixed entries.

- [ ] **4.6 Capture modal**
  - **Do:**
    - An optional title field and a TipTap editor (StarterKit + Link with a protocol allow-list).
    - A template picker that inserts headings above any existing text (D-23); template headings left empty are removed on save (D-06).
    - A memory-date picker (default today, never in the future, not before 1900).
    - A tag picker that can create a new tag inline.
    - Multi-file upload with reorder; failed items stay with retry or remove (D-23). A photo can be marked as a diary page (D-20).
    - A draft kept in local storage, and a keep-or-discard prompt on close (D-23).
    - The flow: create (client UUID) → upload → reorder.
  - **Done when:** creating an entry with a title, a tag and two photos shows it in the feed in the right week; save is disabled for an empty entry; an empty template prompt isn't saved; a photo marked as a diary page is saved as a page; a backdated save shows a toast that links to its week; a reload keeps the draft; validation errors from the API are shown on the right fields; a `409 entry-stale` keeps the typed text and offers to load the latest version (D-23).

- [ ] **4.7 Voice notes**
  - **Do:** a `MediaRecorder` recorder that picks `audio/webm;codecs=opus` or `audio/mp4`.
  - **Done when:** a recording uploads and plays back in Chrome. Note whether Safari or iOS could be tested.

- [ ] **4.8 Loot Drops in the UI**
  - **Do:** a seal dialog (pick a day; it opens at 09:00 in the user's timezone, D-23; then a confirmation repeating the date and time), a sealed vault card with a live countdown, a `409` that surfaces as a friendly message, and an *Opened* strip at the top of the feed (D-10).
  - **Done when:** sealing an entry turns it into a vault card; the countdown is correct in the user's zone; once the unlock time passes, the entry shows in the Opened strip until it is opened or dismissed.

- [ ] **4.9 Eras, templates and settings pages**
  - **Do:** CRUD for eras and templates, with an Era page that lists its weeks and counts only the entries inside its dates (D-22); a gallery of starter templates that adds an editable copy with one tap (D-06); settings for display name, timezone and birthday (D-33); a *show first in the feed* pin on each Era (D-22).
  - **Done when:** changing the timezone changes the displayed times; changing an Era's dates regroups the feed; pinning an Era moves its shared weeks under it; adding a starter template creates an editable copy; component tests pass.

- [ ] **4.10 Week summary view**
  - **Read:** D-25, USER_FLOWS F-15.
  - **Do:** tapping a week in the feed opens `/weeks/:weekId`: title, dates, Eras, counts, a few thumbnails, tags, then one bar per entry (display title, day, media icons, lock if sealed). Tapping a bar opens the entry. Leave a slot at the top for the Phase 4 AI summary.
  - **Done when:** component tests cover a mixed week, a week with a sealed entry (its bar shows only the lock and unlock date), and the display-title fallbacks (title → first non-heading line → *Photo* / *Diary page* / *Voice note*).

- [ ] **4.11 All-entries timeline and tags UI**
  - **Read:** D-24, D-25, D-26, USER_FLOWS F-16 and F-17.
  - **Do:** `/entries` lists every entry newest first with infinite scroll over `GET /entries`, one row each (display title, date, icons, tags), filterable by tag; a `/tags` page to rename and delete tags.
  - **Done when:** filtering by a tag shows only its unsealed entries; sealed rows show no title or tags; renaming a tag updates everywhere; component tests pass.

- [ ] **4.12 Recently deleted screen** (D-30)
  - **Read:** USER_FLOWS F-09 and F-18.
  - **Do:** a delete confirmation that says the entry can be restored for 30 days, and a `/recently-deleted` screen over `GET /entries/deleted` with *restore* and *erase now*, reached from settings and the timeline.
  - **Done when:** component tests cover a normal and a sealed entry in the list; restoring puts the entry back in its week in the feed; *erase now* asks for confirmation; and an entry deleted from the feed shows up in the list.

- [ ] **4.13 Photo dump and photo dates** (D-34)
  - **Read:** D-12, D-13, D-17, D-34, USER_FLOWS F-19.
  - **Do:**
    - A *photo dump* action. It reads each photo's own date before shrinking it (with `exifr`, approved on 2026-10-28, D-13) and groups the photos by day in the user's timezone.
    - It creates one backdated entry per day, reusing the capture upload flow; a day with more than 20 photos becomes several entries.
    - Undated photos go into one entry dated today, created with `dateUnknown: true`; the card shows a *date unknown, fix it* badge (D-34).
    - In capture, a single photo's own date is offered as the memory date.
  - **Done when:** component tests cover:
    - photos from three days becoming three entries
    - a 25-photo day becoming two entries
    - undated photos giving one entry with the *date unknown* badge, which goes once the date is changed
    - a failed upload retried without duplicates (client ids, D-12)
    - the date suggestion for one photo, accepted and dismissed

- [ ] **4.14 On this day strip and Era covers** (D-32, D-36)
  - **Do:** an *On this day* strip at the top of the feed, beside the Opened strip, over `GET /entries/on-this-day`; Era covers as title pages in the feed's sticky headers and on Era pages; and a cover picker in the Era form.
  - **Done when:** component tests cover the strip with and without memories, a cover on its Era's header, and no cover while its entry is sealed.

- [ ] **4.15 Soundtrack picker and player** (D-35)
  - **Do:** a soundtrack field on entries, weeks and Eras: paste a Spotify link (turned into a URI), or search through `POST /music/search` when it's available. Spotify's player shows as a placeholder until tapped, and only then loads.
  - **Done when:** component tests cover pasting a link, picking a search result, search being unavailable (paste only), and no request to Spotify before the player is tapped.

- [ ] **4.16 Loot Drop extras and the first letter** (D-33)
  - **Read:** USER_FLOWS F-01, F-06 and F-07.
  - **Do:**
    - Suggested unlock dates in the seal dialog: in a month, in a year, and on the next birthday once a birthday is set.
    - *Reply to past you* on an opened Loot Drop, with links both ways.
    - A skippable first letter, sealed for a month, at the end of the first-run screen.
  - **Done when:** component tests cover:
    - each suggested date opening at 09:00 in the user's timezone
    - the birthday choice hidden when no birthday is set
    - a reply showing what it answers
    - skipping the first letter

- [ ] **4.17 End-to-end smoke test and CI: frontend**
  - **Do:**
    - A Playwright smoke test: create an entry with a photo, then seal another.
    - A CI frontend job (lint, typecheck, test, build).
    - Check the HEIC→JPEG behaviour on iOS if possible (D-17).
  - **Done when:** Playwright passes locally against Compose and the dev servers; CI is green.

---

## Phase 2: Online, with accounts

### Sprint 5
**Decide first:** Q2 (auth details and the email service), Q3 (account export and deletion), Q4 (PDF library), Q5 (cold starts and background jobs), Q19 (share-link privacy), Q20 (storage and backups), Q21 (devices and sessions) and Q22 (encryption wording). Record each in DECISIONS.md.

- [ ] **5.1 Auth migration and JWT provider.** Apply the V{n} auth migration and add a JWT `CurrentUserProvider` for `prod`. **Done when:** controllers are unchanged; tests prove an unauthenticated request returns `401` and the dev header is ignored in `prod`.
- [ ] **5.2 Register and login screens.**
- [ ] **5.3 Share links.** Hashed tokens, expiry and revoke; a public read-only view with redaction. **Done when:** a test proves a share link to a week containing a sealed entry never exposes that entry's content or media.
- [ ] **5.4 PDF export of a week** (per Q4).
- [ ] **5.5 Account export and deletion** (per Q3), including stored files.
- [ ] **5.6 Production deploy.** Neon, R2 (private bucket), Render (Docker, health check `/actuator/health`), Vercel (with an `/api` rewrite), environment secrets. **Done when:** the deployed app passes the Playwright smoke test.
- [ ] **5.7 Unlock emails** (D-10). An email when a Loot Drop opens, on by default with a setting to turn it off. It needs the email service from Q2 and the job approach from Q5. **Done when:** a test with a fixed `Clock` sends one email per unlocked entry, never before it unlocks and never twice.
- [ ] **5.8 Installable app** (D-31; ask before adding `vite-plugin-pwa`, D-13). A web app manifest, icons and a service worker that caches the app shell, so memoirist installs to the home screen and opens full-screen. Offline writing stays in Phase 5. **Done when:** Chrome's install check passes on the deployed app, and it installs and opens full-screen on an Android phone and through Safari's *Add to Home Screen* on an iPhone.
- [ ] **5.9 App lock** (D-37; the mechanism comes from Q2). An optional lock in Settings: it locks after a few minutes in the background and unlocks with the device's fingerprint, face or passcode (WebAuthn) or a PIN. **Done when:** with the lock on, coming back after the timeout shows only the lock screen until it's unlocked; nothing from the journal shows behind it; and turning it off removes it.
- [ ] **5.10 Yearly letter** (D-32). A yearly date in Settings (the birthday by default), an email and an in-app card at 09:00 on that day, and a letter sealed until the same date next year. **Done when:** a test with a fixed `Clock` sends one email per user per year, on the right day in their timezone and never twice; the card shows only on that day; and the letter opens at 09:00 on the next yearly date.
- [ ] **5.11 Product metrics** (D-38, `/new-migration`). The `product_events` table, events for signing up, creating an entry, sealing a Loot Drop and closing a week (accepting an invite joins in Phase 3), and the five queries in a documented SQL file. **Done when:** tests prove each action records exactly one event with no content, and deleting an account deletes its events.

## Phase 3: Memories with friends

### Sprint 6
**Start after:** Phase 2 (real accounts). Groups need no AI; the shared story comes with the AI in Phase 4 (task 7.17, D-31).
**Decide first:** Q8, Q11 and Q26. Record each in DECISIONS.md, then review the Phase 3 drafts in DATABASE_SCHEMA.md and API_CONTRACT.md.

- [ ] **6.1 Groups, members and the access rule** (`/new-migration`, `/new-endpoint`). Apply the groups migration, add the group endpoints with one membership check that every group endpoint uses, and extend `.claude/rules/security.md` with the membership rule (D-19). **Done when:** tests prove a non-member gets `404` on every group endpoint, a member gets `403` on owner-only actions, the only owner can't leave, and leaving unlinks the member's contributions without deleting their entries.
- [ ] **6.2 Invite links.** Hashed tokens, expiry, revoke and accept. **Done when:** the raw token appears only in the create response, expired, revoked and unknown tokens return `404`, and accepting twice is harmless.
- [ ] **6.3 Contributions, the group feed and starting from an entry** (D-19). **Done when:** tests prove a member reads another member's contribution only through `/groups/**` while `GET /entries/{id}` still returns `404`; a withdrawn contribution disappears from the group while the entry survives; a contribution whose entry is in Recently deleted is left out (D-30); a non-member never gets a presigned media URL; and starting a group from an entry makes that entry its first contribution.
- [ ] **6.4 Group Loot Drops.** **Done when:** tests prove contributing to a locked group needs the matching `confirmUnlockDate` and seals the entry until at least the group's date; nobody, including the author and the group owner, can read a sealed contribution early; the group's date can't move earlier (`409 group-sealed`); and members can see who has contributed but not what.
- [ ] **6.5 Trips as Eras.** **Done when:** joining a dated group creates an Era with the trip's dates in the member's own timeline; it shows only their entries inside those dates (D-22); leaving keeps the Era but unlinks it; and deleting the Era doesn't change membership.
- [ ] **6.6 Groups UI.** Group list and create, a *share with friends* action that starts a group from an entry, invite sheet, group feed with author names, an "Add to group" action on entries (with the unlock-date confirmation for locked groups), a group vault card with a countdown, and the trip Era in the feed. **Done when:** a Playwright flow with two accounts creates a group, invites, has both people contribute, and shows both entries in the group feed.

## Phase 4: The AI biographer

### Sprint 7 (split into 7a, 7b and 7c when planning)
**Decide first:** Q6 (provider, embedding dimension, consent wording). 7c (paper diaries) also needs Q12 (handwriting recognition approach and consent). Help me think needs Q14 (pause length), Q17 (what withdrawing consent deletes) is settled before 7.9, Q23 (summary staleness and status) before 7.3, Q28 (Era summaries for weeks an Era only partly covers) before 7.13, and Q9 (consent for the shared story) before 7.17. Every AI task follows D-39 on hard topics.
**Suggested split (D-21):** 7a = AI foundation and Help me think (7.1, 7.9, 7.10, 7.2, 7.11, 7.12). 7b = summaries, people, search, the shared story and the weekly email (7.3, 7.4, 7.5, 7.13, 7.14, 7.15, 7.16, 7.17, 7.18). 7c = paper diaries (7.6, 7.7, 7.8). Inside 7a, build 7.9 and 7.10 before 7.2. Paper diaries need 7.9 too, because reading a page needs the AI consent.

- [ ] **7.1 AI migration** (pgvector, `ai_metadata`, `ai_summaries`, characters, edges) with the chosen `vector(N)`.
- [ ] **7.2 Help me think: two-option questions** (`/new-migration`, `/new-endpoint`). The `ai_suggestions` table, `POST /suggestions` and `POST /suggestions/{id}/answer` for a saved entry or an unsaved draft, with whitelisted effects. **Done when:** a sealed entry returns `409`; a draft is read without being stored and its cards expire; answering applies the effect once and repeating the same answer is harmless; another user's suggestion is `404`; every question has exactly two options (anything else the model returns is dropped); and the response arrives in under 5 s on typical entries.
- [ ] **7.3 Weekly summary and embedding** via `WeeklyLogClosedEvent` and `@Async`. Closing a week is the only trigger (D-28). **Done when:** tests prove sealed entries are excluded from the summary input, and a week that is still open never gets a summary.
- [ ] **7.4 Cast of Characters and friendship map.**
- [ ] **7.5 Search by word and by feeling** (D-29, `/new-migration`, `/new-endpoint`). The full-text index, `POST /search/words` and `POST /search/feeling`. **Done when:** tests prove sealed entries never match; another user's entries never appear; search by feeling returns only closed weeks; and search terms appear in no URL or log line.

**7c: reading paper diaries (D-20).** Photos of pages already work from Phase 1; this adds the optional "Read this page" step. It doesn't depend on the vector extension.

- [ ] **7.6 Page scans and the transcription provider** (`/new-migration`). Apply the page-transcription migration (`media_transcriptions`; `media.purpose` is already in V1, D-20), add the `TranscriptionProvider` interface with a fake adapter for `local` and `test`, and the one real adapter chosen in Q12. **Done when:** a Testcontainers test applies the migration; the fake adapter takes a transcription from `PENDING` to `READY`; and a provider error ends as `FAILED` with a short reason and no page content in the logs.
- [ ] **7.7 Transcription endpoints** (`/new-endpoint`). **Done when:** tests prove a sealed entry returns `409` and the provider is never called for it (use a spy); another user's entry or media returns `404`; asking again for a pending page returns the existing one; going over the daily limit returns `429`; and deleting the media or the entry removes its transcription.
- [ ] **7.8 Read-a-page UI.** A "Read this page" action on page photos, a progress state, a side-by-side review of the photo and its text, "Use text" (fills the caption and saves with `PUT /entries/{id}`), and a chip to accept the date found on the page. **Done when:** a Playwright flow with the fake provider uploads a page photo, reads it, uses the text and accepts the date, and the feed shows the entry in the right week.

**AI foundation, tags and Help me think (D-21).** Build 7.9 and 7.10 before 7.2.

- [ ] **7.9 AI consent, settings and the feature gate** (`/new-migration`, `/new-endpoint`). `user_settings` (a switch for every AI feature, one for the Wrap up reminder and one for the weekly email), `GET` and `PUT /users/me/settings`, `POST` and `DELETE /users/me/ai-consent`, `DELETE /users/me/ai-data`, and a `FeatureGate` that reads configuration. **Done when:** tests prove every AI endpoint returns `403 consent-required` until consent is given and again after it is withdrawn; turning on a feature that isn't available returns `403 feature-unavailable`; unknown settings keys return `400`; the client can't set `available`; and deleting AI data removes everything derived while leaving entries untouched.
- [ ] **7.10 `AiContentSource` and invalidation.** The single reader of entry text, tags and AI facts for AI, plus the listener that reacts when an entry is edited, sealed, unlocked, deleted or restored (D-30). **Done when:** tests prove sealed entries never come out of the source (and do once unlocked); editing, sealing or deleting an entry deletes the cards that cite it and marks the summaries and profiles built from it stale; and a stale summary is never used as input to another.
- [ ] **7.11 Tag suggestions.** Manual tags already exist (task 3.8, D-26); this adds the `TAG` kind of suggestion, drawn from the user's existing tags first. **Done when:** an AI tag suggestion adds the tag (with `source = AI`) only after the user taps; a suggestion never creates a tag the user didn't accept; and sealed entries get none.
- [ ] **7.12 Help me think UI.** The pause prompt and button in the editor, a two-button question card, tag chips, and the settings switch. **Done when:** a Playwright flow with the fake provider types, pauses, sees the button, taps it, answers a question and accepts a tag; nothing is sent before the tap; and turning the pause prompt off removes it.

**Era summaries and friend profiles (D-21).** Need 7.3 and 7.4.

- [ ] **7.13 Closing an Era: its ritual and its summary** (D-28, `/new-migration`, `/new-endpoint`). `eras.is_closed`, `POST /eras/{id}/close` and `/reopen`, the ritual (a *last word* entry on the Era's last day, and an optional letter sealed for 5 years), and the Era summary built from its weekly summaries. **Done when:** tests prove closing an Era closes its open weeks and writes their summaries before the Era summary; closing sets a missing end date; the last word is an ordinary entry dated the Era's last day; the letter is sealed until 09:00 five years on unless the user picks another date; an Era with nothing to summarise still closes but gets no summary; a stale weekly summary makes the Era summary stale and it is rebuilt; closing or reopening twice is harmless; and deleting the Era deletes its summary.
- [ ] **7.14 Friend profiles** (`/new-migration`, `/new-endpoint`). Stats (mentions, first and last mention, Eras) plus an AI-written profile, made automatically once someone is mentioned in several entries, with rename, notes, hide and merge. **Done when:** tests prove stats come from the database and not the AI; a rebuild never overwrites the user's notes; merging two people moves their mentions and keeps the old name as an alias; hidden people stay out of the map; and another user's character is `404`.
- [ ] **7.15 Era and Cast UI.** An Era page with its summary and a *Close Era* action that says how many weeks are still open, then offers the last word and the letter; a Cast page with profile cards, editing and merging; and the friendship map. **Done when:** component tests cover the empty, stale and failed states (see Q23), and a Playwright flow shows a summary and a profile built with the fake provider.
- [ ] **7.16 Wrap up card and reminder** (D-28). The card at the top of the feed for the latest finished week that's still open and for Eras that have ended, a read endpoint for it (`GET /wrap-up`), and the gentle reminder with its switch. **Done when:** component tests cover a card for last week, a card after a month away, an ended Era, no card when everything is closed, and the reminder showing at most once a week and never when it's switched off.

**The shared story and the weekly email (D-31, D-32).**

- [ ] **7.17 Shared story** (D-19, needs Phase 3's groups). Each member's opt-in (`PUT /groups/{groupId}/members/me`), then a story built from consenting members' contributions. **Done when:** tests prove sealed entries and contributions from members who haven't opted in are excluded from the input; fewer than two contributors returns `409 not-enough-contributions`; and withdrawing, opting out or leaving deletes the stored story.
- [ ] **7.18 Weekly email** (D-32). At 09:00 on Monday in each user's timezone, after a week with entries, an email that points to the week's story or to Wrap up, with no entry content; on by default, with its own switch (7.9). **Done when:** a test with a fixed `Clock` sends one email per user per week, none after an empty week and none when it's switched off, and the email holds no title, caption or summary text.

## Phase 5: On your phone

### Sprint 8
**Decide first:** Q7 (dictation approach), Q13 (bulk notebook import), Q24 (voice-note transcription) and Q25 (writing time). Installing the app came earlier, in task 5.8 (D-31).

- [ ] **8.1 Offline capture** with Dexie plus a sync queue (client UUIDs, idempotent creates).
- [ ] **8.2 Voice-to-text dictation.**
- [ ] **8.3 Inline photo node in TipTap** (stores a `mediaId`); extend the backend content allow-list to match.
- [ ] **8.4 Zine export.**
- [ ] **8.5 Scan mode** (D-20). A camera screen for diary pages: edge detection, crop, straighten and brighten, "add another page", and a file-picker fallback. Pages are saved as JPEG (D-17), uploaded with `purpose = PAGE_SCAN`, and ride the offline queue from 8.1, which means uploads must be safe to retry (extend D-12 with an optional client-supplied media `id` when 8.1 is planned). Ask before adding an image-processing library. **Done when:** on a phone, two photographed pages end up as one backdated entry with two page scans; the same flow works offline and syncs later; and the file-picker path works without a camera.
- [ ] **8.6 Unlock notifications** (D-10). An opt-in push notification from the installed PWA when a Loot Drop opens. **Done when:** on a phone with the app installed and notifications allowed, an entry sealed a few minutes ahead produces one notification when it opens.

## Phase 6: Looking back with you

### Sprint 9
**Start after:** Phase 4 (the AI layer, weekly summaries and Era summaries). It is independent of Phases 3 and 5.
**Decide first:** Q15 (paid features), Q16 (how often insights appear, and where) and the mood part of Q25. Record each in DECISIONS.md, then review the Phase 6 drafts in DATABASE_SCHEMA.md and API_CONTRACT.md.

- [ ] **9.1 Entry embeddings and retrieval** (`/new-migration`). `entry_embeddings`, and a `Retriever` that searches only the caller's own entries and summaries. Sealed entries are never embedded and are removed when sealed. **Done when:** tests prove results never include another user's data, sealed entries or stale summaries; an entry that unlocks becomes searchable; and a multi-user test shows per-user results aren't cut short by the vector index.
- [ ] **9.2 Proactive insights** (`/new-endpoint`). Pattern spotters, growth reminders and "last time you felt like this", as cards with sources, a weekly cap, duplicate protection, and feedback and dismiss. **Done when:** tests prove a card always cites at least one source; nothing is made without opt-in; the weekly cap and `dedupe_key` hold; and a dismissed insight doesn't come back.
- [ ] **9.3 Highlight reels** (`/new-endpoint`). On request and when an Era is closed. A reel stores entry IDs only and is built through `EntryMapper` when it is read. **Done when:** a test proves an entry sealed after the reel was made disappears from it, and a photo-only entry still appears.
- [ ] **9.4 Writing coach.** Off by default, behind a switch and the `FeatureGate`, with a daily cap, and most requests return nothing. **Done when:** tests prove nothing is sent while the switch is off, going over the cap returns `429`, a low-scoring draft returns an empty list, and a card can be dismissed with feedback.
- [ ] **9.5 Insights UI.** An insights inbox, reels, the coach card in the editor, and settings for each switch and the weekly cap. **Done when:** a Playwright flow with the fake provider shows an inbox card with its sources, dismisses it, and opens a reel.
- [ ] **9.6 Predictions.** A sealed yes-or-no question to future you ("Still at this job in a year?"), built on Loot Drops (D-10). When it opens, the user answers again and sees whether they were right, with a running score. Settle how it's scored and shown in a D-entry when Sprint 9 is planned. **Done when:** a prediction stays sealed like any Loot Drop until it opens, and the score counts only predictions that have opened and been answered.
- [ ] **9.7 A gift of memories.** A highlight reel of the user's moments with one person (9.3, the Cast of Characters), turned into a page they can share with an expiring link (5.3), for example for that person's birthday. **Done when:** sealed and deleted entries never appear on the shared page, and revoking the link takes it down.
- [ ] **9.8 Year in review.** Each December, a page of the year's chapters, people and best photos, built on highlight reels (9.3), plus a card to share that shows no private content. **Done when:** sealed and deleted entries never appear, and the shareable card holds only counts and the year.
