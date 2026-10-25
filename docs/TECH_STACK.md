# Tech Stack and Architecture: memoirist

> How the system is built. Decisions with reasons live in [DECISIONS.md](DECISIONS.md); this file describes the resulting design. Pin exact library versions when each project is scaffolded, and check current docs because several majors (Spring Boot 4, Tailwind 4, React 19, TipTap 3) are newer than many examples online.

## Phases at a glance

| Phase | Backend | Frontend | Infrastructure |
|---|---|---|---|
| 1. Local MVP | Java 21, Spring Boot 4.x, Spring Web/Data JPA/Validation, Flyway, springdoc-openapi, AWS SDK v2 (S3), Actuator | React 19, Vite, TypeScript, Tailwind v4, Motion, TipTap, TanStack Query, Zustand, React Router, Embla, openapi-fetch | Docker Compose: Postgres 17 (pgvector image) and SeaweedFS |
| 2. Cloud and auth | Spring Security (JWT), share links | Auth screens, share view | Vercel, Render, Neon (Postgres 17), Cloudflare R2, GitHub Actions |
| 3. AI biographer | Spring AI, pgvector (HNSW), Spring events + `@Async` | Confirmation chips, Cast of Characters, friendship map | AI provider (decided at Phase 3) |
| 4. Mobile and offline | Endpoints for sync if needed | `vite-plugin-pwa`, Dexie, dictation, inline media node, zine export | — |

## 1. Local development

**Machine prerequisites**
- Windows with Git Bash
- Docker Desktop
- JDK 21 (for example, Eclipse Temurin)
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

Don't publish SeaweedFS's internal volume-server port to the host. It defaults to 8080 and clashes with the backend. Confirm the exact SeaweedFS flags for S3 credentials against the docs for the version you pin.

## 2. Backend (`/backend`)

### Layout
Gradle Kotlin DSL, base package `com.memoirist`, organised by feature:

```
com.memoirist
├── MemoiristApplication.java   # sets JVM default timezone to UTC before SpringApplication.run
├── common/        # errors (ProblemDetail), PageResponse, Clock bean, base entity, config
├── user/          # User, CurrentUserProvider, DevHeaderCurrentUserProvider (local/test), /users/me
├── era/
├── template/
├── week/          # WeeklyLog, WeekCalendar (date → Monday in user tz), feed endpoint
├── entry/         # Entry, EntryMapper (the ONLY Entry→DTO path), content validation + text extraction, seal rules
├── media/
└── storage/       # StorageService, StorageProperties, local bucket bootstrap
```

Each feature folder contains:
- `XController`: thin; HTTP only.
- `XService`: transactions, ownership, business rules.
- `XRepository`: Spring Data, with user-scoped finders.
- `X`: the JPA entity.
- `XMapper`.
- `dto/`: Java records.

### Profiles
| Profile | Used for | Notable settings |
|---|---|---|
| `local` | Running on your machine against Docker Compose | Imports `../.env`; Flyway locations `db/migration` + `db/seed-local`; dev-header user provider; creates the bucket at startup if missing |
| `test` | Testcontainers integration tests | Same Flyway locations as `local`; dev-header provider |
| `prod` | Phase 2 deployment | Flyway `db/migration` only; JWT provider; R2 settings from the environment |

### Cross-cutting rules (details in `.claude/rules/`)
- **Time:**
  - `TimeZone.setDefault(UTC)` in `main`.
  - `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.
  - A `java.time.Clock` bean is injected wherever "now" or "today" matters, so seals and dates are testable.
  - `WeekCalendar` converts a memory date into its Monday in the user's zone.
- **Schema:** Flyway only, with `ddl-auto=validate`. JSONB columns are mapped with `@JdbcTypeCode(SqlTypes.JSON)`. Entities with client-assignable IDs implement `Persistable<UUID>`.
- **Current user:** `CurrentUserProvider.currentUserId()`.
  - Phase 1 implementation: `DevHeaderCurrentUserProvider` (`@Profile({"local","test"})`). It reads `X-Dev-User-Id` and defaults to `00000000-0000-0000-0000-000000000001`.
  - Phase 2 adds a JWT-backed implementation for `prod`.
- **Ownership:** repositories expose `findByIdAndUserId`-style methods; services never load by bare ID. Not owned means `404`.
- **Errors:** one `@RestControllerAdvice` returns RFC 9457 `ProblemDetail` (the successor to RFC 7807) with the types in API_CONTRACT.md.
- **OpenAPI:** springdoc, with tags per resource ("Entries", "Eras", …).
- **Multipart limits:** `spring.servlet.multipart.max-file-size=25MB`, `max-request-size=30MB`. Spring's default of 1 MB would reject phone photos.

### Entries, content and Loot Drops
- **Content:** `EntryContentValidator` checks the TipTap JSON against an allow-list and the 200 KB cap. `ContentTextExtractor` walks the JSON and produces `content_text`.
  - Allowed nodes: `doc`, `paragraph`, `text`, `heading` (levels 1–3), `bulletList`, `orderedList`, `listItem`, `blockquote`, `hardBreak`, `horizontalRule`.
  - Allowed marks: `bold`, `italic`, `strike`, `code`, `link` (protocols `http`, `https`, `mailto`).
- **Redaction:** `EntryMapper.toResponse(entry, now)` is the only place an `Entry` becomes a DTO. If `unlockDate` is after `now`, it nulls `contentJson`, `contentText` and `templateId`, empties `media`, and sets `isLocked = true`. Feed, week detail, create, update, seal, share links (Phase 2) and AI (Phase 3) all go through it.
- **Seal guard:** `EntryService` rejects edits to content, date, template and media on sealed entries with `409`. The unlock date may only move later.
- **Unlock notifications (future):** `unlock_notified` plus a partial index lets a `@Scheduled` job find newly unlocked entries cheaply.

### Weeks and the feed
- **Creating an entry:**
  1. Resolve the memory date (default: today in the user's zone; reject future dates).
  2. Compute its Monday.
  3. `INSERT … ON CONFLICT (user_id, start_date) DO NOTHING`, then select the week.
  4. Insert the entry.
- **Feed:** `GET /feed` loads a page of non-empty weeks (newest first), then their entries in one query, then their media in batched queries (`@BatchSize(50)` / `IN (…)`). Two collections are never fetch-joined at once (avoids `MultipleBagFetchException` and N+1 queries).

### Storage
- **Clients:**
  - `S3Client` (uploads and deletes) talks to `storage.endpoint`.
  - `S3Presigner` signs GET URLs for `storage.public-endpoint`, the address the browser can reach.
  - Locally both are `http://localhost:8333`. If the backend ever runs inside Docker, `endpoint` becomes `http://seaweedfs:8333` while `public-endpoint` stays on localhost.
- **Path-style access:** on for SeaweedFS (`forcePathStyle(true)`). R2 uses `https://<account-id>.r2.cloudflarestorage.com` with region `auto`.
- **Database:** stores only `object_key = users/{userId}/entries/{entryId}/{mediaId}.{ext}`. Never a URL or host.
- **Presigned GET URLs:** last 60 minutes and are generated when mapping media. The frontend refetches before they expire.
- **Upload flow:**
  1. Validate type and size.
  2. Put the object.
  3. Insert the row. If the insert fails, delete the object (best effort).
- **Deletion:** before deleting an entry, week, era cascade or media row, the service collects the affected object keys. It deletes the objects in an after-commit listener (`@TransactionalEventListener(phase = AFTER_COMMIT)`) so a rolled-back transaction never loses files. Later, a periodic sweep can catch stragglers.

## 3. Frontend (`/frontend`)

```
src/
├── api/          # schema.d.ts (generated — never hand-edit), client.ts (openapi-fetch), queryKeys.ts
├── features/
│   ├── feed/     # FeedPage, WeekSection, sticky era headers
│   ├── entry/    # EntryCard, MediaCarousel, CaptureModal, SealDialog, SealedCard, VoiceRecorder
│   ├── eras/
│   ├── templates/
│   └── settings/
├── components/   # shared UI primitives
├── lib/time.ts   # all date/time formatting + "today" in the user's timezone
├── stores/       # Zustand: UI-only state (open modals, drafts)
└── main.tsx, router.tsx
```

- **Data:**
  - `npm run gen:api` generates TypeScript types from `http://localhost:8080/v3/api-docs`.
  - `openapi-fetch` makes typed calls.
  - TanStack Query owns all server data. `staleTime` stays below the 60-minute presign TTL (for example, 30 minutes), and queries refetch on window focus.
- **State:** Zustand holds UI state only.
- **Time:** every timestamp shown and every "today" is computed in the user's timezone from `GET /users/me`, using `@date-fns/tz`.
- **Editor:** TipTap with StarterKit plus Link. Its extensions must match the backend allow-list. Read-only captions are rendered from JSON (TipTap's static renderer or a small JSON→React renderer), never through `dangerouslySetInnerHTML`.
- **Media:** an Embla carousel shows images and an audio player in `displayOrder`. Voice notes use `MediaRecorder` with the first supported type of `audio/webm;codecs=opus` or `audio/mp4`.
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
- Phase 3 adds HNSW (`vector_cosine_ops`) on summary embeddings and GIN on `entries.ai_metadata` if queries need it.

## 5. Phase 2 deployment notes
- **Frontend (Vercel):** use a rewrite of `/api/*` to the Render backend so the browser sees one origin (no CORS, simpler cookies).
- **Backend (Render):** deploy as a Docker image. The health check path is `/actuator/health`. Free instances sleep when idle (open question Q5).
- **Database (Neon):** Postgres 17. Flyway runs on startup.
- **Media (R2):** a private bucket; credentials come from Render environment variables.
- **CI (GitHub Actions):** backend tests (Testcontainers on `ubuntu-latest`), plus frontend lint, typecheck, unit tests and build.

## 6. Phase 3 and 4 notes
- **Phase 3:**
  - Spring AI (the Boot 4 line). The provider and the `vector(N)` dimension are decided first (D-18).
  - Closing a week publishes `WeeklyLogClosedEvent`. An `@Async` listener builds the summary and embedding. Sealed entries are excluded.
- **Phase 4:**
  - `vite-plugin-pwa`.
  - Dexie stores pending creates keyed by client UUID, and a sync queue replays them.
  - A TipTap inline image node stores `mediaId` and resolves the URL at render time.
