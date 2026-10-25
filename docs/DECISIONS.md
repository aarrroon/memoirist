# Decisions

Settled choices for memoirist. **When another doc disagrees with this file, this file wins.**

Each entry says what was decided and why, so it can be revisited knowingly. Add new entries at the bottom with the next number. Don't delete an outdated entry; mark it "Superseded by D-xx".

| ID | Decision | Decided |
|---|---|---|
| D-01 | Project name is **memoirist** | 2026-10-25 |
| D-02 | Spring Boot 4.x on Java 21 | 2026-10-25 |
| D-03 | SeaweedFS locally, Cloudflare R2 in production | 2026-10-25 |
| D-04 | PostgreSQL 17 everywhere | 2026-10-25 |
| D-05 | Entry content is TipTap JSON plus derived plain text | 2026-10-25 |
| D-06 | Templates attach to entries, not weeks | 2026-10-25 |
| D-07 | Store UTC; each user has a timezone (default Melbourne) | 2026-10-25 |
| D-08 | Weeks are created automatically (Monday–Sunday) | 2026-10-25 |
| D-09 | Entries have a memory date and can be backdated | 2026-10-25 |
| D-10 | Loot Drops use a strict seal | 2026-10-25 |
| D-11 | Ownership checks and local-only dev user | 2026-10-25 |
| D-12 | Clients may generate entity UUIDs | 2026-10-25 |
| D-13 | Frontend library set | 2026-10-25 |
| D-14 | Testing and CI approach | 2026-10-25 |
| D-15 | Backend code style | 2026-10-25 |
| D-16 | Flyway migration rules | 2026-10-25 |
| D-17 | Media types and limits | 2026-10-25 |
| D-18 | AI providers deferred to Phase 3 | 2026-10-25 |

---

## D-01 Project name is "memoirist"
Replaces "Living Time Capsule" from the original drafts.
- Java base package `com.memoirist`
- Storage bucket `memoirist-media`
- Local dev user email `dev@memoirist.local`

## D-02 Spring Boot 4.x on Java 21
**Why:** open-source support for Spring Boot 3.5 ended on 2026-06-30.
**Consequences:** Boot 4 brings Spring Framework 7, Hibernate 7, Jackson 3 and the Boot-4 line of springdoc-openapi; Phase 3 uses the Spring AI line built for Boot 4. Many online examples (and much of Claude's training data) are Boot 3. Check current docs before copying a pattern.

## D-03 Object storage: SeaweedFS locally, Cloudflare R2 in production
**Why:**
- MinIO's community edition stopped publishing Docker images in October 2025 and is now unmaintained.
- AWS's free tier for accounts created after 2025-07-15 is a 6-month credit, not a permanent allowance.
- R2 has a permanent free tier (10 GB of storage, no egress fees) and speaks the S3 API.

**Consequences:** one storage implementation (AWS SDK v2 `S3Client` + `S3Presigner`). Switching providers only changes endpoint, region, credentials and path-style settings. RustFS (MinIO-like, 1.0 in September 2026) is the fallback if SeaweedFS causes trouble.

## D-04 PostgreSQL 17 everywhere
Locally the image is `pgvector/pgvector:pg17`; production is Neon on Postgres 17. The original drafts said both "15+" and `pg16`. One major version in both places avoids surprises. pgvector is installed in the image but only enabled in Phase 3.

## D-05 Entry content is TipTap JSON plus derived plain text
- `entries.content_json` (JSONB) holds the TipTap/ProseMirror document. It is `NULL` when an entry has no caption (photo-only posts are allowed).
- `entries.content_text` (TEXT) is plain text the server extracts from the JSON. AI, search and previews use it.
- The server validates content: root node is `doc`, only allow-listed node and mark types, link protocols limited to `http`, `https` and `mailto`, at most 200 KB.
- The frontend renders content from JSON and never inserts HTML strings.

**Why:**
- Storing HTML would allow stored XSS on Phase 2 public share pages.
- In Phase 4, inline images store a `mediaId`, never a URL, so expiring presigned links never get baked into content.

## D-06 Templates attach to entries
`entries.template_id` references the template used. When an entry is created, its subheadings are copied into the editor as headings. Editing or deleting a template later never changes existing entries. `weekly_logs` has no template column. This follows the PRD's "pick Spiralling while writing" flow.

## D-07 Time: store UTC, each user has a timezone
- Instants are `TIMESTAMPTZ` in the database and `Instant` in Java, serialised as ISO-8601 with `Z`.
- Calendar dates are `DATE` / `LocalDate` / `YYYY-MM-DD`.
- `LocalDateTime` is never used.
- `users.timezone` holds an IANA zone and defaults to `Australia/Melbourne`.
- "Today", week boundaries and every displayed time use the user's timezone, not the server's and not the browser's.

## D-08 Weeks are automatic
- A week runs Monday to Sunday in the user's timezone.
- Creating an entry finds or creates the week containing its memory date. The database enforces uniqueness on `(user_id, start_date)`; the service inserts with `ON CONFLICT DO NOTHING` and then selects the row.
- Users can give a week a title, assign it to an era, and close or reopen it. There is no "create week" step.
- Weeks with no entries are hidden from the feed.

## D-09 Entries have a memory date
`entries.entry_date` defaults to today in the user's timezone and can be set to an earlier date (backdating). It cannot be later than today. `created_at` still records when the entry was actually written. Changing the date moves the entry into the matching week.

## D-10 Loot Drops use a strict seal
An entry is **sealed** while `unlock_date > now`. While sealed:
- every response hides `contentJson`, `contentText`, `templateId` and `media`, and sets `isLocked: true`
- content, memory date, template and media cannot change (`409 entry-sealed`)
- the entry cannot be analysed by AI and is excluded from weekly AI summaries
- the unlock date can only move later; the entry can always be deleted

Seal last: create the entry, upload media, then call `POST /entries/{id}/seal`. The UI asks the user to confirm the unlock date before sealing. Share links follow the same rules.

## D-11 Ownership and the dev user
- Controllers never receive a user ID from the request body, query or path. The current user comes from `CurrentUserProvider`.
- Every lookup by ID is scoped to the current user. Another user's resource returns `404` (not `403`), so its existence isn't revealed.
- Phase 1 resolves the user from the `X-Dev-User-Id` header, defaulting to `00000000-0000-0000-0000-000000000001`. That provider exists only in the `local` and `test` profiles.
- The dev user is seeded from `db/seed-local`, a Flyway location only those profiles load, so it never reaches production.
- Phase 2 adds a JWT-based provider with no controller changes.

## D-12 Client-generated UUIDs
Create requests for eras, templates and entries may include an `id` (UUID v4). If the `id` already exists for the same user, the server returns the existing resource with `200` (a safe retry). If it exists for someone else, the server returns `409`. Without an `id`, the server generates one.
**Why:** Phase 4 offline capture can create entries before reaching the server, with no ID remapping.
**Consequence:** JPA entities with assigned IDs implement `Persistable<UUID>` so `save()` inserts directly instead of selecting first.

## D-13 Frontend library set
React 19, Vite, TypeScript, Tailwind CSS v4, Motion (`motion` package), TipTap, plus:

| Need | Library |
|---|---|
| Server data and caching | TanStack Query |
| UI state | Zustand (UI state only, never server data) |
| Routing | React Router |
| Typed API client | `openapi-typescript` (types generated from the backend's OpenAPI) + `openapi-fetch` (replaces Axios) |
| Carousel | Embla Carousel |
| Icons | `lucide-react` |
| Dates and timezones | `date-fns` + `@date-fns/tz` |
| Forms | `react-hook-form` + `zod` |
| API mocks in tests | MSW |

These were chosen during setup as sensible defaults. Change them freely, but record the change here.

## D-14 Testing and CI
- **Backend:** JUnit 5 + Testcontainers against real Postgres (`pgvector/pgvector:pg17`) and SeaweedFS. No H2, because it can't emulate JSONB, `TIMESTAMPTZ` behaviour or deferrable constraints.
- **Frontend:** Vitest + React Testing Library + MSW. Playwright covers a few end-to-end flows from Sprint 4.
- **CI:** GitHub Actions runs backend tests from Sprint 2 and frontend checks from Sprint 4.
- Tests are written with each task, not saved for the end of a sprint.

## D-15 Backend code style
- Gradle Kotlin DSL; Spotless for formatting.
- Packages by feature: `com.memoirist.entry`, `com.memoirist.era`, and so on.
- DTOs are Java records.
- No Lombok and no MapStruct. Mappers are hand-written so Loot Drop redaction stays explicit in one place.

## D-16 Flyway migration rules
- Files are named `V{n}__{snake_case_description}.sql` with sequential `n` and no phase numbers in the name. One logical change per file.
- Never edit a committed migration. A PreToolUse hook blocks it. Fix mistakes with a new migration.
- `spring.jpa.hibernate.ddl-auto=validate` in every profile.
- The dev seed is a repeatable migration (`R__seed_dev_user.sql`) in `db/seed-local`.

## D-17 Media types and limits
- Images: `image/jpeg`, `image/png`, `image/webp`.
- Audio: `audio/webm`, `audio/mp4` (what iOS Safari records), `audio/mpeg`.
- At most 25 MB per file and 10 media items per entry.
- No video in Phases 1–3.
- HEIC is not accepted. iOS converts photos to JPEG when the file picker's `accept` list excludes HEIC; verify this in Sprint 4.

## D-18 AI providers deferred to Phase 3
The chat model, the embedding provider and therefore the `vector(N)` dimension are decided when Phase 3 starts. Nothing in Phases 1–2 depends on them. Earlier drafts named `gpt-4o-mini`, `text-embedding-3-small` and `claude-3-haiku`; treat those as outdated.

---

## Open questions

Decide each one before the phase that needs it, then turn it into a D-entry.

| # | Question | Needed by | Default if nobody decides |
|---|---|---|---|
| Q1 | Should a new week join the era whose dates contain it automatically? | Sprint 3 | No: users assign eras manually |
| Q2 | Auth details: JWT in an httpOnly cookie or an `Authorization` header; expiry and refresh; Argon2id or BCrypt; email verification; password reset; login rate limiting | Sprint 5 | — |
| Q3 | Account deletion and data export (privacy obligations) | Sprint 5 | — |
| Q4 | PDF export: which library, and in Phase 2 or Phase 4? | Sprint 5 | — |
| Q5 | Render's free tier sleeps and takes about a minute to wake; is that acceptable against the "loads in 2 seconds" goal? | Sprint 5 | — |
| Q6 | AI chat model, embedding provider and dimension; consent and retention wording for sending entries to an AI provider | Sprint 6 | — |
| Q7 | Voice-to-text: Chrome's Web Speech API sends audio to Google. Acceptable, or transcribe on the server? | Sprint 7 | — |
