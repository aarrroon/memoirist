# Tech Stack and Architecture: memoirist

> How the system is built. Decisions with reasons live in [DECISIONS.md](DECISIONS.md); this file describes the resulting design. Pin exact library versions when each project is scaffolded, and check current docs because several majors (Spring Boot 4, Tailwind 4, React 19, TipTap 3) are newer than many examples online.

## Phases at a glance

| Phase | Backend | Frontend | Infrastructure |
|---|---|---|---|
| 1. The core journal (local MVP) | Java 25, Spring Boot 4.x, Spring Web/Data JPA/Validation, Flyway, springdoc-openapi, AWS SDK v2 (S3), Actuator; Spotify's Web API for soundtrack search (D-35) | React 19, Vite, TypeScript, Tailwind v4, Motion, TipTap, TanStack Query, Zustand, React Router, Embla, openapi-fetch, shadcn/ui (Radix), Sonner, dnd-kit, Vaul, react-day-picker, browser-image-compression, yet-another-react-lightbox, react-error-boundary; `exifr` for photo dates once approved (D-34) | Docker Compose: Postgres 17 (pgvector image) and SeaweedFS |
| 2. Online, with accounts | Spring Security (JWT), share links, unlock and yearly-letter emails, the app lock, product events | Auth screens, share view, `vite-plugin-pwa` (installable app), the lock screen | Vercel, Render, Neon (Postgres 17), Cloudflare R2, GitHub Actions |
| 3. Memories with friends | A `group` package: membership check, invites, contributions. Reuses `EntryMapper` and the seal rules | Group screens, group feed | — |
| 4. The AI biographer | Spring AI, pgvector (HNSW), Postgres full-text search, Spring events + `@Async`; one shared AI layer (D-21); a `TranscriptionProvider` for reading handwriting (7c); the shared trip story and the weekly email | Help me think, tag suggestions, Wrap up card, Era summaries and the closing ritual, Cast of Characters with friend profiles, friendship map, search, read-a-page review, shared story | AI providers (decided at Phase 4) |
| 5. On your phone | Endpoints for sync if needed | Dexie, dictation, inline media node, zine export, scan mode, unlock notifications (web push) | — |
| 6. Looking back with you | A `Retriever` over entry embeddings, scheduled and event-driven insight jobs, the writing coach | Insights inbox, highlight reels, predictions, year in review, a gift of memories, coach card, AI switches | — |

## 1. Local development

**Machine prerequisites**
- Windows with Git Bash
- Docker Desktop
- JDK 25 (for example, Eclipse Temurin 25)
- Node 22 or later (Node 24 is installed)
- IntelliJ IDEA or VS Code

**Running locally:** Docker Compose runs the data services, and the apps run on the host. `.env` (copied from `.env.example`) supplies credentials to Compose and to the backend's `local` profile, via `spring.config.import: optional:file:../.env[.properties]`.

| Service | Port | Notes |
|---|---|---|
| Postgres 17 (`pgvector/pgvector:pg17`) | 5432 | Database `memoirist` |
| SeaweedFS S3 API | 8333 | Path-style S3 endpoint; bucket `memoirist-media` |
| SeaweedFS filer UI | 8888 | Browse uploaded files |
| Backend (Spring Boot) | 8080 | Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`, health at `/actuator/health` |
| Frontend (Vite) | 5173 | Proxies `/api` to `:8080`, so there's no CORS in development |

**Compose (`docker-compose.yml`, task 1.3):**
- Postgres runs `pgvector/pgvector:pg17`. SeaweedFS runs `chrislusf/seaweedfs:4.48` as `weed server -s3`: the master, a volume server, the filer and the S3 gateway in one process. The tag stays pinned, and D-03 says why; the SeaweedFS test container (task 3.1) uses the same tag.
- Both services have health checks and named volumes. Every port is published on `127.0.0.1` only, because the filer UI has no login.
- Don't publish SeaweedFS's internal volume-server port to the host. It defaults to 8080 and clashes with the backend.
- SeaweedFS takes its S3 credentials from `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`, which Compose maps from `.env` (D-03). Compose refuses to start if either is empty. With both set, 4.48 rejects unsigned requests with `403` (checked in task 1.3).

## 2. Backend (`/backend`)

### Layout
Gradle Kotlin DSL, base package `com.memoirist`, organised by feature:

```
com.memoirist
├── MemoiristApplication.java   # sets JVM default timezone to UTC before SpringApplication.run
├── common/        # errors (ProblemDetail), PageResponse, Clock bean, BaseEntity and TimestampedEntity (D-12), config
├── user/          # User, CurrentUserProvider, DevHeaderCurrentUserProvider (local/test), /users/me
├── era/           # Era, covers (D-36)
├── template/
├── tag/           # Tag, entry tags (D-26)
├── music/         # Spotify search for soundtracks: SpotifyProperties, MusicSearchService (D-35)
├── week/          # WeeklyLog, WeekCalendar (date → first day of its week in user tz), a week's Eras from their dates (D-22), feed endpoint
├── entry/         # Entry, EntryMapper (the ONLY Entry→DTO path), content validation + text extraction, seal rules, opened Loot Drops, Recently deleted and its sweep (D-30), On this day and Loot Drop replies (D-32, D-33)
├── media/
├── storage/       # StorageService, StorageProperties, local bucket bootstrap
└── dev/           # local-profile-only sample content (task 3.16)
```

Each feature folder contains:
- `XController`: thin; HTTP only.
- `XService`: transactions, ownership, business rules.
- `XRepository`: Spring Data, with user-scoped finders.
- `X`: the JPA entity.
- `XMapper`.
- `dto/`: Java records.

### Build (task 2.1)
Exact versions are pinned in `backend/build.gradle.kts`, and Gradle's own in `backend/gradle/wrapper/gradle-wrapper.properties`. What's there, and why:
- **Gradle wrapper** 9.x (Java 25 needs Gradle 9.1.0 or later), with `distributionSha256Sum`, so the wrapper checks the Gradle zip it downloads. A Java toolchain compiles and runs the tests on JDK 25, whichever JDK starts Gradle (D-02).
- **Spring Boot 4** with the `io.spring.dependency-management` plugin, which applies Boot's BOM. Boot 4 has one starter per technology: `spring-boot-starter-webmvc` (Boot 3's `-web`), `-data-jpa`, `-validation`, `-actuator` and `-flyway`, plus `flyway-database-postgresql`. Flyway runs only with `spring-boot-starter-flyway`: `flyway-core` on its own no longer turns on its auto-configuration.
- **springdoc-openapi 3.x**, the line for Boot 4. **AWS SDK v2** `s3`, versioned by the SDK's own BOM, because Boot doesn't manage it.
- **Tests:** one `spring-boot-starter-<technology>-test` per starter; each brings `spring-boot-starter-test` (JUnit 6, AssertJ, Mockito) and that technology's test support (`@WebMvcTest`, `@DataJpaTest`, `RestTestClient`, …). Testcontainers 2 (`testcontainers-postgresql`, `testcontainers-junit-jupiter`): its `org.testcontainers.postgresql.PostgreSQLContainer` accepts `pgvector/pgvector` images as they are. Mockito is loaded as a Java agent when the test JVM starts, because since Java 21 the JDK warns when a library attaches one itself, and a future release will refuse it (JEP 451). HTTP behaviour that needs no database is tested in a web slice: `@WebMvcTest(controllers = …)` with `@AutoConfigureRestTestClient` loads Spring MVC and only the beans the test `@Import`s. A controller that exists only for a test is a nested `@TestComponent`, which component scanning skips, so it never reaches the app or another test.
- **Spotless** with a pinned palantir-java-format: `./gradlew build` runs `spotlessCheck`, and `./gradlew spotlessApply` fixes the formatting.
- **Two Jacksons:** springdoc's swagger-core puts Jackson 2 (`com.fasterxml.jackson.databind`) on the classpath. The app's JSON is Jackson 3 (`tools.jackson.databind`; the annotations stay in `com.fasterxml.jackson.annotation`), so import Jackson 3 types.

### Profiles
| Profile | Used for | Notable settings |
|---|---|---|
| `local` | Running on your machine against Docker Compose | Imports `../.env`; Flyway locations `db/migration` + `db/seed-local`; dev-header user provider; creates the bucket at startup if missing; optional sample content (task 3.16) |
| `test` | Testcontainers integration tests | Same Flyway locations as `local`; dev-header provider |
| `prod` | Phase 2 deployment | Flyway `db/migration` only; JWT provider; R2 settings from the environment |

**Where a setting lives (task 2.2):**
- `application.yml` holds what is true in every profile (JPA, multipart, the Flyway schema location), and names the environment variables the app reads (`DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` and the `STORAGE_*` set). Each is written `${VAR:}`, with an empty default: a missing variable then fails at startup with the property's name. Without the `:`, Spring Boot keeps the text `${VAR}` as the value, and it passes a not-blank check.
- `application-local.yml` says where those variables come from on your machine (`../.env`, via `optional:file:../.env[.properties]`) and adds `db/seed-local` to Flyway.
- `src/test/resources/application-test.yml` holds the test values. It stays out of the app's jar. The database comes from Testcontainers, so it sets no datasource.
- `prod` (Phase 2) then needs no mapping: its environment provides the same variable names.
- **Precedence**, highest first: command-line arguments, Java system properties (`-D`), the OS environment, `.env`, the profile file, `application.yml`. A list such as `spring.flyway.locations` is replaced by the profile's, never merged with the shorter list below it.

### Cross-cutting rules (details in `.claude/rules/`)
- **Time:**
  - `TimeZone.setDefault(UTC)` is the first line of `main`. The Gradle test JVM starts in UTC too (`-Duser.timezone=UTC`), so tests run as production does, on any machine. `MemoiristApplicationTest` checks `main` itself from another zone.
  - `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.
  - A `java.time.Clock` bean (`common/ClockConfig`, `Clock.systemUTC()`) is injected wherever "now" or "today" matters, so seals and dates are testable. Its zone is UTC, not the JVM's; "today" for a user is `LocalDate.now(clock.withZone(userZone))`.
  - `WeekCalendar` converts a memory date into the first day of its week in the user's zone. The first day is always Monday, held as one constant (D-08).
- **Schema:** Flyway only, with `ddl-auto=validate` in every profile (D-16). JSONB columns are mapped with `@JdbcTypeCode(SqlTypes.JSON)`. Entities with client-assignable IDs implement `Persistable<UUID>`.
- **Entities (task 2.4):** see [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md#entity-classes-task-24) for the class behind each table.
  - **Base classes (D-12):** `BaseEntity` holds the id, which is set in the constructor, and `Persistable<UUID>`, so `save()` of a new entity is one INSERT and no SELECT. `TimestampedEntity` adds `createdAt` and `updatedAt`. `equals` and `hashCode` use the id.
  - **JSON libraries:** `spring.jpa.properties.hibernate.type.json_format_mapper` names `Jackson3JsonFormatMapper`. Left alone, Hibernate 7.4 picks Jackson 2 (springdoc brings it in), which can't read the Jackson 3 `JsonNode` that `Entry.contentJson` is.
  - **Foreign keys are plain ids** (`userId`, `weeklyLogId`, `templateId`, `replyToEntryId`, `coverMediaId`), not associations, because no code walks them: services compare ids and look the other side up through its own user-scoped repository. The associations are the ones read together with their owner: `Entry.media` (`@OneToMany`, ordered, batched, no cascade) with `Media.entry` (`LAZY`), and `Entry.tags` (`@ManyToMany` through `entry_tags`).
  - **`entry_tags.source`:** the join table is mapped without an entity, so `source` and `created_at` take their column defaults (`'USER'`, `NOW()`). Phase 4 needs an `EntryTag` entity to record `'AI'`.
  - **Deleting an entry** relies on the foreign key's `ON DELETE CASCADE` for its media rows (tested: Hibernate never touches `media`); the `entry_tags` rows go by cascade or by Hibernate's own delete (not asserted). The service deletes the stored files first (see Storage).
- **Current user (task 2.5):** `CurrentUserProvider.currentUserId()` in `user/`. It answers for the request on the calling thread, so a background job has no current user.
  - Phase 1 implementation: `DevHeaderCurrentUserProvider` (`@Profile({"local","test"})`). It reads `X-Dev-User-Id` and defaults to `00000000-0000-0000-0000-000000000001` (`DEV_USER_ID`, which must match the seed; a test checks it).
  - It is a singleton that gets the request by constructor-injecting `HttpServletRequest`. Spring injects a proxy that finds the calling thread's request on every use, so concurrent requests never see each other's header. Outside a request the proxy throws `IllegalStateException`.
  - The header is parsed strictly: 8-4-4-4-12 hexadecimal digits, sent once. `UUID.fromString` alone is too forgiving (it turns `1-1-1-1-1` into a different, valid UUID). An empty header is malformed, not "absent", so a client bug can't fall back to the dev user's data. A failure throws `MalformedRequestException`, which becomes `400 malformed-request` and never repeats the bad value.
  - In any other profile the bean doesn't exist. Until Phase 2 adds the JWT-backed implementation for `prod`, that profile has no `CurrentUserProvider` at all, so anything that needs one fails at startup instead of letting everyone in.
- **Ownership:** repositories expose `findByIdAndUserId`-style methods; services never load by bare ID. Not owned means `404`. To make that a compile error and not a habit, every repository extends Spring Data's bare `Repository<X, UUID>`, not `JpaRepository`, and declares only `save`, `delete` and its user-scoped finders, so no `findById`, `findAll` or `deleteById` exists to call. The one exception is `UserRepository.findById`, because the id there is the user's own. A new query is added to the repository when a service needs it.
- **Errors:** one `@RestControllerAdvice` returns RFC 9457 `ProblemDetail` (the successor to RFC 7807) with the types in API_CONTRACT.md. `common/GlobalExceptionHandler` exists from task 2.5 and handles `MalformedRequestException` only; task 2.6 adds the other types to it. Every problem carries `type` (`urn:memoirist:problem:<suffix>`), `title`, `status`, `detail`, `instance` (the request path, filled in by Spring) and `timestamp` (from the `Clock`, serialised as an ISO-8601 `Z` string).
- **OpenAPI:** springdoc, with tags per resource ("Entries", "Eras", …).
- **Multipart limits:** `spring.servlet.multipart.max-file-size=25MB`, `max-request-size=30MB`. Spring's default of 1 MB would reject phone photos.
- **Open session in view is off** (`spring.jpa.open-in-view=false`). Spring Boot leaves it on by default, which keeps a database connection open until the response is written and lets a controller trigger lazy-loading queries. With it off, an entity is read only inside its service's transaction, and the service maps it to a DTO there (`EntryMapper` for entries, so a sealed Loot Drop never reaches the controller as an entity); a lazy field touched outside the transaction fails loudly instead of running another query.
- **Configuration is typed and checked at startup:** settings with several values are `@ConfigurationProperties` records (not `@Value`), scanned by `@ConfigurationPropertiesScan` on `MemoiristApplication` and annotated `@Validated`, so a missing or wrong value stops the app with a message naming the property, its value and where it came from. `StorageProperties` (below) is the first.

### Entries, content and Loot Drops
- **Content:** `EntryContentValidator` checks the TipTap JSON against an allow-list and the 200 KB cap. `ContentTextExtractor` walks the JSON and produces `content_text` and `preview_line` (the first non-empty line that isn't a heading, or the first heading if there's nothing else, D-24).
  - Allowed nodes: `doc`, `paragraph`, `text`, `heading` (levels 1–3), `bulletList`, `orderedList`, `listItem`, `blockquote`, `hardBreak`, `horizontalRule`.
  - Allowed marks: `bold`, `italic`, `strike`, `code`, `link` (protocols `http`, `https`, `mailto`).
- **Redaction:** `EntryMapper` is the only place an `Entry` becomes a DTO, both the full `Entry` (`toResponse(entry, now)`) and the list row `EntrySummary` (`toSummary(entry, now)`). If `unlockDate` is after `now`, it nulls `title`, `contentJson`, `contentText`, `templateId`, `previewLine`, `firstMediaKind`, `soundtrack`, `replyToEntryId` and the media counts, empties `media`, `tags` and `replyIds`, and sets `isLocked = true`. Feed, week detail, create, update, seal, share links (Phase 2) and group views (Phase 3) all go through it. AI input doesn't: it comes only from `AiContentSource`, which skips sealed entries (D-21).
- **Counts and filters:** queries that count or filter by content (tag filters, tag counts, search, people stats) leave sealed entries out in the query itself (D-10).
- **Stale edits (D-23):** `Entry` has a `@Version` field. `EntryService` compares the `version` in a `PUT` with the loaded one and throws `EntryStaleException` if they differ; Hibernate's own check catches a race between that load and the commit. Both become `409 entry-stale`.
- **Seal guard:** `EntryService` rejects edits to title, content, date, template, tags and media on sealed entries with `409`. The unlock date may only move later.
- **Opened Loot Drops (D-10):** sealing clears `unlock_seen_at`, and `GET /entries/opened` finds unlocked, unseen entries through a partial index. `unlock_notified` is a separate flag for the background unlock job (unlock emails from Phase 2, refreshing AI results from Phase 4), which has its own partial index.
- **On this day (D-32):** `GET /entries/on-this-day` matches the month and day of `entry_date` in earlier years, in the user's timezone, leaving out sealed and deleted entries in the query. In a year that isn't a leap year, 29 February matches 28 February.
- **Replies (D-33):** `reply_to_entry_id` may point only at the user's own Loot Drop that has opened (checked in `EntryService`). `replyIds` comes from one query on the partial index.
- **Recently deleted (D-30):** `DELETE` sets `deleted_at`. Entry queries leave deleted rows out by default: `Entry` carries Hibernate's `@SQLRestriction("deleted_at IS NULL")`, which is added to every JPQL and derived query, every load by id and every join to `Entry` (the media of a deleted entry is not found either), so deleted entries drop out of every list, count, filter, share link, group view, unlock email and AI input, and a new finder can't forget the filter. Only the Recently deleted list, restore, erase and the sweep read them, through native queries, which Hibernate does not rewrite (`EntryRepository.findDeletedByIdAndUserId` and `findDeletedByUserIdOrderByDeletedAtDesc`; the sweep adds its own). A native query that is not about Recently deleted must add `deleted_at IS NULL` itself. A daily sweep erases entries deleted more than 30 days ago, and their files go as described under Storage.

### Weeks and the feed
- **Creating an entry:**
  1. Resolve the memory date (default: today in the user's zone; reject future dates and dates before 1900-01-01).
  2. Compute its Monday.
  3. `INSERT … ON CONFLICT (user_id, start_date) DO NOTHING`, then select the week.
  4. Insert the entry.
- **A week's Eras (D-22)** are worked out from dates, never stored: an Era holds a week if it starts on or before the week's Sunday and has no end date or ends on or after its Monday. An Era's page counts only the entries whose memory date is inside its dates. Creating an Era or changing its dates needs no other writes.
- **Feed:** `GET /feed` loads a page of non-empty weeks (newest first; entries in Recently deleted don't count, D-30), then their entries in one query, then their media in batched queries (`@BatchSize(50)` / `IN (…)`). Two collections are never fetch-joined at once (avoids `MultipleBagFetchException` and N+1 queries). The weeks' Eras come from one more query for the user's Eras, matched to each week by date (D-22).

### Storage
- **Clients:**
  - `S3Client` (uploads and deletes) talks to `storage.endpoint`.
  - `S3Presigner` signs GET URLs for `storage.public-endpoint`, the address the browser can reach.
  - Locally both are `http://localhost:8333`. If the backend ever runs inside Docker, `endpoint` becomes `http://seaweedfs:8333` while `public-endpoint` stays on localhost.
- **Path-style access:** on for SeaweedFS (`forcePathStyle(true)`). R2 uses `https://<account-id>.r2.cloudflarestorage.com` with region `auto`.
- **Database:** stores only `object_key = users/{userId}/entries/{entryId}/{mediaId}.{ext}`. Never a URL or host.
- **Presigned GET URLs:** last 60 minutes and are generated when mapping media. The frontend refetches before they expire.
- **`StorageProperties`** (`storage.*`, task 2.2) is a validated record: `endpoint` and `publicEndpoint` (URIs), `region`, `bucket` (S3's naming rules), `accessKey`, `secretKey`, `pathStyle` and `presignTtl` (an ISO-8601 duration, 1 to 60 minutes, so a typo such as `PT60H` stops the app). All eight are required. Its `toString()` masks the secret key. The local and test values come from `.env` and `application-test.yml`. Validation checks that `publicEndpoint` parses as a URI, not that it has a scheme: `localhost:8333` passes.
- **Upload flow:**
  1. Validate type and size.
  2. Put the object.
  3. Insert the row. If the insert fails, delete the object (best effort).
- **Deletion:** before erasing an entry (deleting one only moves it to Recently deleted, D-30), deleting a media row or deleting an account, the service collects the affected object keys. It deletes the objects in an after-commit listener (`@TransactionalEventListener(phase = AFTER_COMMIT)`) so a rolled-back transaction never loses files. Later, a periodic sweep can catch stragglers.

### Music (D-35)
- `SpotifyProperties` holds `client-id` and `client-secret`, from `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` in `.env`. Without them, search is off and `POST /music/search` returns `503 music-search-unavailable`.
- `MusicSearchService` gets an app-only token with Spotify's client credentials flow, keeps it until it expires, and calls Spotify's search (at most 10 results) with Spring's `RestClient`, so no new library is needed. A `429` from Spotify becomes `503 music-search-unavailable`.
- Soundtracks are stored as Spotify URIs and checked against `spotify:(track|album|playlist):[A-Za-z0-9]{22}`.
- Tests stub Spotify and never call it.

## 3. Frontend (`/frontend`)

```
src/
├── api/          # schema.d.ts (generated — never hand-edit), client.ts (openapi-fetch), queryKeys.ts
├── features/
│   ├── feed/     # FeedPage, WeekSection, sticky era headers with covers (D-36), OpenedStrip (D-10), OnThisDayStrip (D-32)
│   ├── week/     # WeekSummaryPage with one bar per entry (D-25)
│   ├── timeline/ # TimelinePage: every entry, one row each, tag filter (D-25)
│   ├── entry/    # EntryCard, MediaCarousel, CaptureModal, PhotoDump (D-34), SealDialog with suggested dates, SealedCard, reply to past you (D-33), VoiceRecorder, displayTitle()
│   ├── music/    # SoundtrackPicker (paste or search) and SoundtrackPlayer, which loads Spotify only when tapped (D-35)
│   ├── eras/     # Era pages and the cover picker (D-36)
│   ├── templates/ # TemplatesPage and the starter gallery (D-06)
│   ├── tags/     # TagPicker, TagsPage (D-26)
│   └── settings/ # display name, timezone, birthday (D-33)
├── components/   # shared UI primitives
│   └── ui/       # shadcn/ui components (copied in, owned by us, restyled to DESIGN.md)
├── lib/time.ts   # all date/time formatting + "today" in the user's timezone
├── lib/images.ts # read a photo's own date (exifr, D-34), then compress it before upload (browser-image-compression)
├── stores/       # Zustand: UI-only state (open modals); the capture draft is also persisted to localStorage (D-23)
└── main.tsx, router.tsx
```

- **Data:**
  - `npm run gen:api` generates TypeScript types from `http://localhost:8080/v3/api-docs`.
  - `openapi-fetch` makes typed calls.
  - TanStack Query owns all server data. `staleTime` stays below the 60-minute presign TTL (for example, 30 minutes), and queries refetch on window focus.
- **State:** Zustand holds UI state only.
- **Time:** every timestamp shown and every "today" is computed in the user's timezone from `GET /users/me`, using `@date-fns/tz`.
- **Editor:** TipTap with StarterKit plus Link. Its extensions must match the backend allow-list. Choosing a template inserts its headings above any existing text, and headings left empty are removed on save (D-06). Read-only captions are rendered from JSON (TipTap's static renderer or a small JSON→React renderer), never through `dangerouslySetInnerHTML`.
- **UI building blocks (D-13):**
  - Dialogs, menus, popovers and tabs come from shadcn/ui (Radix underneath), so focus handling and keyboard support are built in. They live in `components/ui/` and are restyled to DESIGN.md.
  - Toasts use Sonner (saved, upload failed, sealed). Dates use react-day-picker with future days disabled where D-09 requires.
  - On phones the capture view opens as a Vaul bottom sheet; on desktop, a dialog.
  - Each feed card and each page section sits inside a `react-error-boundary`, so one broken card doesn't blank the app.
- **Media:**
  - An Embla carousel shows images and an audio player in `displayOrder`. Tapping a photo opens yet-another-react-lightbox (full screen, pinch-zoom).
  - In capture, the staging strip is reordered with dnd-kit (pointer, touch and keyboard).
  - Before upload, `lib/images.ts` reads each photo's own date, because compression drops it; capture offers it as the memory date, and a photo dump groups photos by it (D-34). It then compresses photos to roughly 2–3 MB with browser-image-compression. Photos marked as diary pages (`purpose = PAGE_SCAN`, D-20) use a gentler setting so handwriting stays readable. Audio is uploaded as recorded.
  - Voice notes use `MediaRecorder` with the first supported type of `audio/webm;codecs=opus` or `audio/mp4`.
  - Soundtracks show a placeholder until tapped; only then does the page load Spotify's embedded player (D-35). A content security policy, if one is added, must allow Spotify's embed frame.
- **Capture flow:**
  1. `POST /entries` with a client UUID.
  2. Upload media.
  3. Reorder.
  4. Optionally `POST /entries/{id}/seal` after a confirmation dialog.
- **Styling:**
  - Tailwind v4 is configured in CSS (`@theme` in `src/index.css`), not `tailwind.config.js`.
  - Motion is imported from `motion/react` and honours `prefers-reduced-motion`.

## 4. Database

**Conventions:**
- UUID primary keys.
- `TIMESTAMPTZ` for instants and `DATE` for calendar dates.
- Named constraints.
- JSONB for documents.
- Full table specifications are in [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md).

**Indexes:**
- B-tree indexes on foreign keys and on chronological access paths.
- A partial index for pending unlocks.
- Phase 4 adds HNSW (`vector_cosine_ops`) on summary embeddings and GIN on `entries.ai_metadata` if queries need it.

## 5. Phase 2 deployment notes
- **Frontend (Vercel):** use a rewrite of `/api/*` to the Render backend so the browser sees one origin (no CORS, simpler cookies).
- **Backend (Render):** deploy as a Docker image. The health check path is `/actuator/health`. Free instances sleep when idle (open question Q5).
- **Database (Neon):** Postgres 17. Flyway runs on startup.
- **Media (R2):** a private bucket; credentials come from Render environment variables.
- **CI (GitHub Actions):** backend tests (Testcontainers on `ubuntu-latest`), plus frontend lint, typecheck, unit tests and build.
- **Installable app (D-31, task 5.8):** `vite-plugin-pwa` builds the web app manifest and a service worker that caches the app shell. Offline writing waits for Phase 5.
- **Emails (D-32):** unlock, yearly-letter and (from Phase 4) weekly emails carry dates, counts and a link only, never entry or summary text. Scheduled sends follow Q5's job approach and are safe to repeat.
- **Product events (D-38, task 5.11):** written by the services in the same transaction as the action they count. No third-party analytics.

## 6. Notes for Phases 3 to 6
- **Phase 3 (D-19):**
  - A new `group` package follows the same controller, service and repository split. No new libraries are expected: invite tokens reuse the share-link pattern.
  - **Access rule:** personal endpoints stay owner-only. Every `/groups/**` endpoint starts with one membership check (`404` for a non-member, `403` for a member without the role). Nothing else decides who may see a contribution.
  - **Contributions are links** (`group_entries`), so an author's edits show up in the group and withdrawing is deleting the link. An entry in Recently deleted is left out of group views (D-30).
  - **Starting from an entry (D-19):** `POST /groups` with an `entryId` creates the group and its first contribution in one transaction.
  - **Trip Eras** are defined by the trip dates like any other Era, and show only the entries inside them (D-22).
  - **Output:** a contribution becomes a DTO only through `EntryMapper`, and media is presigned only after the membership check. A sealed contribution is therefore redacted without any group-specific code.
  - **Group Loot Drop:** contributing to a group with a future `unlock_date` seals the entry through the existing seal path, so there is still one definition of "sealed".
  - **Leaving and deleting:** deleting a membership row deletes that member's `group_entries` rows through the composite foreign key. A service step also unlinks the member's trip Era (and, from Phase 4, deletes the group's stored story).
- **Phase 4:**
  - Spring AI (the Boot 4 line). The provider and the `vector(N)` dimension are decided first (D-18).
  - Closing a week publishes `WeeklyLogClosedEvent`, the only trigger for a weekly summary (D-28). An `@Async` listener builds the summary and embedding. Sealed entries are excluded.
  - **Closing an Era (D-28):** the client first saves the ritual's last word and optional letter as ordinary entries (the letter sealed, by default for 5 years). Then `POST /eras/{id}/close` closes the Era's open weeks (each publishes `WeeklyLogClosedEvent`) and marks the Era closed. The Era summary job runs once those week summaries are done, and builds from them. `GET /wrap-up` backs the Wrap up card and the gentle reminder.
  - **Search (D-29):** word search uses a GIN full-text index over title and `content_text`, filtered in the query to the caller's unsealed entries. Search by feeling compares an embedding of the query with the `ai_summaries` embeddings of closed weeks. Search terms travel in request bodies and are never logged.
  - **Paper diaries (D-20, tasks 7.6–7.8):**
    - **Provider port:** `TranscriptionProvider` takes image bytes and a content type and returns text, a language, an optional detected date and an optional confidence. The adapter is chosen by configuration, like `StorageProperties`. `local` and `test` use a fake adapter, so tests never touch the network. A real adapter can wrap Spring AI's multimodal support, a dedicated handwriting service or an on-device engine. Swapping one changes no table or endpoint.
    - **Flow:** `POST /entries/{id}/transcriptions` inserts `PENDING` rows and publishes an event after commit. An `@Async` listener re-checks that the entry isn't sealed, loads the image through `StorageService`, downscales it, strips location metadata, calls the provider, and saves `READY` or `FAILED` in its own transaction. A sweep marks rows stuck in `PENDING` as `FAILED` (`timeout`). The client polls `GET /entries/{id}/transcriptions` while any row is pending.
    - **Content:** a transcript never writes itself into an entry. The client builds the caption from the text and saves it with the normal `PUT /entries/{id}`, so the content allow-list (D-05) is the only way in.
    - **Limits:** a transcript is capped at 50 KB per page, and there is a per-user daily page limit (configurable; see Q12). Logs carry IDs and status only, never page text or images.
    - **Ownership:** `media_transcriptions` is owned through `media`, as `media` is through `entries`. Group endpoints (D-19) never return it.
    - **Left open on purpose:** other kinds of captured material (printed pages, tickets, whiteboards) are new `purpose` values that reuse the same pipeline. A bulk notebook import (Q13) can be a separate batch table that points at ordinary entries. On-device recognition is just another adapter.
  - **AI layer (D-21, tasks 7.2 and 7.9–7.16):**
    - **Ports:** chat, embedding and transcription are separate interfaces, each with a fake adapter for `local` and `test`. Whatever an adapter makes is stored with its provider and model.
    - **`AiContentSource`:** the only code that loads entry text, tags and AI facts for AI. It skips sealed entries, applies group consent (D-19) and returns plain text with IDs. A test should fail if AI code reads entries any other way (ArchUnit is one option; adding it needs approval).
    - **Gate:** every AI endpoint calls `FeatureGate` in order: consent, the user's switch, then availability. It reads configuration today and can read a plan later.
    - **Suggestions:** `POST /suggestions` builds a prompt from the saved entry or the draft and asks the model for output that matches a JSON schema. Anything that doesn't fit is dropped: unknown fields, unknown effects, or a number of options other than two. Effects come from a short whitelist (add a tag, record a fact) and run only after the user's tap. Entry text is treated as data, never as instructions. A draft is processed in memory and never stored.
    - **Narratives:** weekly summaries, Era summaries and friend profiles are built by `@Async` jobs. An Era summary reads the Era's weekly summaries, not raw entries (Q28 covers weeks it only partly overlaps). Each has an `is_stale` flag, a provider and a model.
    - **Invalidation:** `EntryService` publishes an event when an entry is edited, sealed, unlocked, deleted or restored (D-30). Listeners delete the cards that cite it (found through a GIN index on `sources`), mark the week's summary, the summaries of the Eras whose dates contain it and the profiles of people mentioned in it as stale, and rebuild them in the background. Stale narratives are excluded as input to other AI work. The unlock job (`unlock_notified`) publishes the same event.
    - **Cost and abuse:** per-feature daily limits (`429`), cheap checks before any model call, and logs that carry IDs, kinds and token counts but never text.
    - **Hard topics (D-39):** prompts ask for a gentle tone and forbid patterns or cards about self-harm, abuse or grief. Each rule has a test with the fake provider.
    - **Left open on purpose:** a new kind of card is a new `kind` string, a generator and a renderer. A new kind of narrative is a table with real foreign keys and the same three columns (`is_stale`, provider, model). Another provider is another adapter.
  - **Shared story (D-19, task 7.17):** an `@Async` job like the weekly summary. Its input excludes sealed entries and members without `ai_consent`. Withdrawing, opting out or leaving deletes the stored story.
  - **Weekly email (D-32, task 7.18):** a job at 09:00 on Monday in each user's timezone, for users with entries in the week just finished. It links to the week's story or to Wrap up, carries no entry content, and respects its switch.
- **Phase 5:**
  - Dexie stores pending creates keyed by client UUID, and a sync queue replays them. The installable shell came in Phase 2 (task 5.8).
  - A TipTap inline image node stores `mediaId` and resolves the URL at render time.
  - **Unlock notifications (D-10, task 8.6):** opt-in web push from the installed PWA when a Loot Drop opens.
  - **Scan mode (D-20, task 8.5):** a camera screen for diary pages in `features/scan/` (edge detection, crop, straighten, brighten) with a file-picker fallback. Pages upload with `purpose = PAGE_SCAN` and ride the offline queue. The image-processing approach is chosen when the task starts, and a new dependency needs approval first.
- **Phase 6 (D-21):**
  - **Retrieval:** a `Retriever` searches the caller's own entry chunks and summaries by vector similarity. It never returns sealed or stale material, and it returns the sources it used. A card must cite at least one source or it is dropped. Filtering one user's rows out of an HNSW index can return too few results when many users share a table, so task 9.1 checks the pinned pgvector version's iterative index scans (or partitioning by user) with a multi-user test.
  - **Triggers:** a weekly job in each user's timezone, an Era being closed, and a saved entry that closely matches earlier moments. All of them respect the user's weekly cap and each card's `dedupe_key`.
  - **Highlight reels** store entry IDs only and are built through `EntryMapper` when read, so an entry sealed later drops out.
  - **Writing coach:** a draft is sent only while its switch is on. A cheap pre-check decides whether to call the model at all, and most calls return nothing.
  - **Plans:** `FeatureGate` is the only place that knows about plans (Q15).
  - **Predictions, A gift of memories and Year in review (tasks 9.6–9.8)** build on Loot Drops, highlight reels and share links, and are designed when Sprint 9 is planned.
