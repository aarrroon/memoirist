# Tech Stack and Architecture: memoirist

> How the system is built. Decisions with reasons live in [DECISIONS.md](DECISIONS.md); this file describes the resulting design. Pin exact library versions when each project is scaffolded, and check current docs because several majors (Spring Boot 4, Tailwind 4, React 19, TipTap 3) are newer than many examples online.

## Phases at a glance

| Phase | Backend | Frontend | Infrastructure |
|---|---|---|---|
| 1. Local MVP | Java 21, Spring Boot 4.x, Spring Web/Data JPA/Validation, Flyway, springdoc-openapi, AWS SDK v2 (S3), Actuator | React 19, Vite, TypeScript, Tailwind v4, Motion, TipTap, TanStack Query, Zustand, React Router, Embla, openapi-fetch, shadcn/ui (Radix), Sonner, dnd-kit, Vaul, react-day-picker, browser-image-compression, yet-another-react-lightbox, react-error-boundary | Docker Compose: Postgres 17 (pgvector image) and SeaweedFS |
| 2. Cloud and auth | Spring Security (JWT), share links | Auth screens, share view | Vercel, Render, Neon (Postgres 17), Cloudflare R2, GitHub Actions |
| 3. AI biographer | Spring AI, pgvector (HNSW), Spring events + `@Async`; one shared AI layer (D-21); a `TranscriptionProvider` for reading handwriting (6c) | Help me think, tags, Era summaries, Cast of Characters with friend profiles, friendship map, read-a-page review | AI providers (decided at Phase 3) |
| 4. Mobile and offline | Endpoints for sync if needed | `vite-plugin-pwa`, Dexie, dictation, inline media node, zine export, scan mode | — |
| 5. Groups and shared memories | A `group` package: membership check, invites, contributions. Reuses `EntryMapper`, the seal rules and Spring AI | Group screens, group feed, shared story | — |
| 6. Insights and coaching | A `Retriever` over entry embeddings, scheduled and event-driven insight jobs, the writing coach | Insights inbox, highlight reels, coach card, AI switches | — |

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
├── tag/           # Tag, entry tags (D-26)
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
- **Redaction:** `EntryMapper` is the only place an `Entry` becomes a DTO, both the full `Entry` (`toResponse(entry, now)`) and the list row `EntrySummary` (`toSummary(entry, now)`). If `unlockDate` is after `now`, it nulls `title`, `contentJson`, `contentText`, `templateId`, `previewLine` and the media counts, empties `media` and `tags`, and sets `isLocked = true`. Feed, week detail, create, update, seal, share links (Phase 2) and AI (Phase 3) all go through it.
- **Seal guard:** `EntryService` rejects edits to content, date, template and media on sealed entries with `409`. The unlock date may only move later.
- **Unlock notifications (future):** `unlock_notified` plus a partial index lets a `@Scheduled` job find newly unlocked entries cheaply.

### Weeks and the feed
- **Creating an entry:**
  1. Resolve the memory date (default: today in the user's zone; reject future dates).
  2. Compute its Monday.
  3. `INSERT … ON CONFLICT (user_id, start_date) DO NOTHING`, then select the week.
  4. Insert the entry.
- **Feed:** `GET /feed` loads a page of non-empty weeks (newest first), then their entries in one query, then their media in batched queries (`@BatchSize(50)` / `IN (…)`). Two collections are never fetch-joined at once (avoids `MultipleBagFetchException` and N+1 queries). A week's Eras (a join table, D-22) come from one more batched query.

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
│   ├── week/     # WeekSummaryPage with one bar per entry (D-25)
│   ├── timeline/ # TimelinePage: every entry, one row each, tag filter (D-25)
│   ├── entry/    # EntryCard, MediaCarousel, CaptureModal, SealDialog, SealedCard, VoiceRecorder, displayTitle()
│   ├── eras/
│   ├── templates/
│   ├── tags/     # TagPicker, TagsPage (D-26)
│   └── settings/
├── components/   # shared UI primitives
│   └── ui/       # shadcn/ui components (copied in, owned by us, restyled to DESIGN.md)
├── lib/time.ts   # all date/time formatting + "today" in the user's timezone
├── lib/images.ts # compress photos before upload (browser-image-compression)
├── stores/       # Zustand: UI-only state (open modals); the capture draft is also persisted to localStorage (D-23)
└── main.tsx, router.tsx
```

- **Data:**
  - `npm run gen:api` generates TypeScript types from `http://localhost:8080/v3/api-docs`.
  - `openapi-fetch` makes typed calls.
  - TanStack Query owns all server data. `staleTime` stays below the 60-minute presign TTL (for example, 30 minutes), and queries refetch on window focus.
- **State:** Zustand holds UI state only.
- **Time:** every timestamp shown and every "today" is computed in the user's timezone from `GET /users/me`, using `@date-fns/tz`.
- **Editor:** TipTap with StarterKit plus Link. Its extensions must match the backend allow-list. Read-only captions are rendered from JSON (TipTap's static renderer or a small JSON→React renderer), never through `dangerouslySetInnerHTML`.
- **UI building blocks (D-13):**
  - Dialogs, menus, popovers and tabs come from shadcn/ui (Radix underneath), so focus handling and keyboard support are built in. They live in `components/ui/` and are restyled to DESIGN.md.
  - Toasts use Sonner (saved, upload failed, sealed). Dates use react-day-picker with future days disabled where D-09 requires.
  - On phones the capture view opens as a Vaul bottom sheet; on desktop, a dialog.
  - Each feed card and each page section sits inside a `react-error-boundary`, so one broken card doesn't blank the app.
- **Media:**
  - An Embla carousel shows images and an audio player in `displayOrder`. Tapping a photo opens yet-another-react-lightbox (full screen, pinch-zoom).
  - In capture, the staging strip is reordered with dnd-kit (pointer, touch and keyboard).
  - Before upload, `lib/images.ts` compresses photos to roughly 2–3 MB with browser-image-compression. Page scans (D-20) use a gentler setting so handwriting stays readable. Audio is uploaded as recorded.
  - Voice notes use `MediaRecorder` with the first supported type of `audio/webm;codecs=opus` or `audio/mp4`.
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

## 6. Phase 3, 4 and 5 notes
- **Phase 3:**
  - Spring AI (the Boot 4 line). The provider and the `vector(N)` dimension are decided first (D-18).
  - Closing a week publishes `WeeklyLogClosedEvent`. An `@Async` listener builds the summary and embedding. Sealed entries are excluded.
  - **Paper diaries (D-20, tasks 6.6–6.8):**
    - **Provider port:** `TranscriptionProvider` takes image bytes and a content type and returns text, a language, an optional detected date and an optional confidence. The adapter is chosen by configuration, like `StorageProperties`. `local` and `test` use a fake adapter, so tests never touch the network. A real adapter can wrap Spring AI's multimodal support, a dedicated handwriting service or an on-device engine. Swapping one changes no table or endpoint.
    - **Flow:** `POST /entries/{id}/transcriptions` inserts `PENDING` rows and publishes an event after commit. An `@Async` listener re-checks that the entry isn't sealed, loads the image through `StorageService`, downscales it, strips location metadata, calls the provider, and saves `READY` or `FAILED` in its own transaction. A sweep marks rows stuck in `PENDING` as `FAILED` (`timeout`). The client polls `GET /entries/{id}/transcriptions` while any row is pending.
    - **Content:** a transcript never writes itself into an entry. The client builds the caption from the text and saves it with the normal `PUT /entries/{id}`, so the content allow-list (D-05) is the only way in.
    - **Limits:** a transcript is capped at 50 KB per page, and there is a per-user daily page limit (configurable; see Q12). Logs carry IDs and status only, never page text or images.
    - **Ownership:** `media_transcriptions` is owned through `media`, as `media` is through `entries`. Group endpoints (D-19) never return it.
    - **Left open on purpose:** other kinds of captured material (printed pages, tickets, whiteboards) are new `purpose` values that reuse the same pipeline. A bulk notebook import (Q13) can be a separate batch table that points at ordinary entries. On-device recognition is just another adapter.
  - **AI layer (D-21, tasks 6.2 and 6.9–6.15):**
    - **Ports:** chat, embedding and transcription are separate interfaces, each with a fake adapter for `local` and `test`. Whatever an adapter makes is stored with its provider and model.
    - **`AiContentSource`:** the only code that loads entry text, tags and AI facts for AI. It skips sealed entries, applies group consent (D-19) and returns plain text with IDs. A test should fail if AI code reads entries any other way (ArchUnit is one option; adding it needs approval).
    - **Gate:** every AI endpoint calls `FeatureGate` in order: consent, the user's switch, then availability. It reads configuration today and can read a plan later.
    - **Suggestions:** `POST /suggestions` builds a prompt from the saved entry or the draft and asks the model for output that matches a JSON schema. Anything that doesn't fit is dropped: unknown fields, unknown effects, or a number of options other than two. Effects come from a short whitelist (add a tag, record a fact) and run only after the user's tap. Entry text is treated as data, never as instructions. A draft is processed in memory and never stored.
    - **Narratives:** weekly summaries, Era summaries and friend profiles are built by `@Async` jobs. An Era summary reads the Era's weekly summaries, not raw entries. Each has an `is_stale` flag, a provider and a model.
    - **Invalidation:** `EntryService` publishes an event when an entry is edited, sealed, unlocked or deleted. Listeners delete the cards that cite it (found through a GIN index on `sources`), mark the week's summary, the summaries of all the week's Eras and the profiles of people mentioned in it as stale, and rebuild them in the background. Stale narratives are excluded as input to other AI work. The unlock job (`unlock_notified`) publishes the same event.
    - **Cost and abuse:** per-feature daily limits (`429`), cheap checks before any model call, and logs that carry IDs, kinds and token counts but never text.
    - **Left open on purpose:** a new kind of card is a new `kind` string, a generator and a renderer. A new kind of narrative is a table with real foreign keys and the same three columns (`is_stale`, provider, model). Another provider is another adapter.
- **Phase 4:**
  - `vite-plugin-pwa`.
  - Dexie stores pending creates keyed by client UUID, and a sync queue replays them.
  - A TipTap inline image node stores `mediaId` and resolves the URL at render time.
  - **Scan mode (D-20, task 7.6):** a camera screen for diary pages in `features/scan/` (edge detection, crop, straighten, brighten) with a file-picker fallback. Pages upload with `purpose = PAGE_SCAN` and ride the offline queue. The image-processing approach is chosen when the task starts, and a new dependency needs approval first.
- **Phase 5 (D-19):**
  - A new `group` package follows the same controller, service and repository split. No new libraries are expected: invite tokens reuse the share-link pattern, and the shared story reuses Spring AI.
  - **Access rule:** personal endpoints stay owner-only. Every `/groups/**` endpoint starts with one membership check (`404` for a non-member, `403` for a member without the role). Nothing else decides who may see a contribution.
  - **Contributions are links** (`group_entries`), so an author's edits show up in the group and withdrawing is deleting the link.
  - **Output:** a contribution becomes a DTO only through `EntryMapper`, and media is presigned only after the membership check. A sealed contribution is therefore redacted without any group-specific code.
  - **Group Loot Drop:** contributing to a group with a future `unlock_date` seals the entry through the existing seal path, so there is still one definition of "sealed".
  - **Leaving and deleting:** deleting a membership row deletes that member's `group_entries` rows through the composite foreign key. A service step also unlinks the member's trip Era and deletes the group's stored story.
  - **Shared story:** an `@Async` job like the weekly summary. Its input excludes sealed entries and members without `ai_consent`.
- **Phase 6 (D-21):**
  - **Retrieval:** a `Retriever` searches the caller's own entry chunks and summaries by vector similarity. It never returns sealed or stale material, and it returns the sources it used. A card must cite at least one source or it is dropped. Filtering one user's rows out of an HNSW index can return too few results when many users share a table, so task 9.1 checks the pinned pgvector version's iterative index scans (or partitioning by user) with a multi-user test.
  - **Triggers:** a weekly job in each user's timezone, an Era ending, and a saved entry that closely matches earlier moments. All of them respect the user's weekly cap and each card's `dedupe_key`.
  - **Highlight reels** store entry IDs only and are built through `EntryMapper` when read, so an entry sealed later drops out.
  - **Writing coach:** a draft is sent only while its switch is on. A cheap pre-check decides whether to call the model at all, and most calls return nothing.
  - **Plans:** `FeatureGate` is the only place that knows about plans (Q15).
