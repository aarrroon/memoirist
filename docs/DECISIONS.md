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
| D-19 | Groups are contribution-based; entries stay personal | 2026-10-26 |
| D-20 | Paper diaries: pages are media; reading them is optional and provider-agnostic | 2026-10-26 |
| D-21 | One AI layer: ask, generate and surface, never chat | 2026-10-27 |
| D-22 | Eras can overlap: a week can belong to several Eras | 2026-10-27 |

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
- Users can give a week a title, assign it to one or more Eras (D-22), and close or reopen it. There is no "create week" step.
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

## D-19 Groups are contribution-based; entries stay personal
Phase 5 adds private groups so friends can build shared memories, for example a trip. A group never owns an entry; it holds links to entries that members choose to add.
- A **group** has members (an owner plus the people they invite) and, optionally, trip dates and a shared unlock date.
- A **contribution** is a row in `group_entries` linking a group to one of the contributor's own entries. Nothing is copied. The entry stays in the author's journal and can be withdrawn at any time.
- **Personal endpoints don't change.** `GET /entries/{id}` for another member's entry still returns `404`, even when it sits in a shared group. Members read contributions only through `/groups/**`, which checks membership first. A non-member gets `404`. A member who lacks the role for an action (such as an owner-only edit) gets `403`, because the group's existence is no secret from them.
- **Redaction is unchanged.** Every contribution becomes a DTO through `EntryMapper`, so a sealed one is redacted exactly as in D-10. Media is presigned only after the membership check.
- A **group Loot Drop** is a group with an `unlock_date`. Contributing to it seals the entry until at least that date, using the existing seal rules: the contributor confirms the date first, and the group's date, like an entry's, can only move later.
- **Leaving, removal and account deletion** unlink the member's contributions. Their entries, and any Era they created from the trip, stay with them.
- **AI:** group content reaches an AI provider only from members who opted in, and never while sealed (extends D-10 and D-18).
- **Not a social network:** no followers, likes, public groups or discovery. Groups are small and invite-only.

**Why:**
- The private journal stays private. A person shares only what they choose to add.
- The Phase 1 rules (D-10, D-11, CLAUDE.md rules 3 and 4) keep working, because the personal endpoints never widen.
- Leaving or deleting is a simple unlink, and the seal and redaction code is reused instead of copied.

**Consequences:**
- Phase 1 needs no schema or API change. What keeps the door open is already a rule: one Entry→DTO path, object keys with presigned URLs, and user-scoped finders.
- Groups need real accounts (Phase 2). The shared story and the friendship map also need the AI work (Phase 3). Phase 5 can be built before or after Phase 4.
- Draft tables and endpoints are in DATABASE_SCHEMA.md and API_CONTRACT.md. Task 8.1 extends `.claude/rules/security.md` with the membership rule.
- Open questions Q8–Q11 are settled before Sprint 8.

## D-20 Paper diaries: pages are media; reading them is optional and provider-agnostic
People who write by hand can bring paper diaries in by photographing the pages.
- A **page scan** is an ordinary `media` image with `purpose = PAGE_SCAN`. The original photo is always kept and is the record. Text read from it is a derived suggestion that may be wrong.
- Pages go into ordinary entries. The page's date becomes the entry's memory date (backdating, D-09) and the entry is filed into its week as usual. Several pages can share one entry (10 media at most, D-17), and are read in `display_order`.
- **Transcription** (reading the handwriting) is a separate, optional, asynchronous step. The user taps "Read this page", the result is stored in `media_transcriptions`, and the user reviews it. Nothing reaches the caption until the user chooses to use it, and then it is saved with the normal `PUT /entries/{id}` and validated like any other content (D-05). There is no special write path into entry content.
- It runs behind a `TranscriptionProvider` interface, so the approach (a multimodal model, a dedicated handwriting service or on-device recognition) can change without touching the schema or the API. Each result records its provider and model, so a page can be read again later with a better one.
- **Privacy:** a page image leaves the system only when the user taps "Read this page", never for a sealed entry (extends D-10 and D-18), and never with location metadata. Transcripts are visible only to the entry's owner, including when the entry is in a group (D-19).
- **Seal last (D-10):** read the pages, then seal. A sealed entry can't be read.
- **Phase placement:** photos of pages work from Phase 1 with no changes. Transcription is Phase 3 (Sprint 6c), because it needs the AI provider decision. A camera "scan mode" (crop, straighten, several pages, offline) is Phase 4.

**Why:**
- Many people write by hand, and a photographed page is the lowest-friction way in.
- Keeping the photo means a wrong transcript never loses anything, and the handwriting itself is part of the nostalgia.
- Separating "store the page" from "read the page" lets the first part work from day one while the second waits for the AI decisions (D-18, Q6, Q12).

**Consequences:**
- Phase 1 needs no schema or API change. A page photo is just a photo.
- The Phase 3 migration adds `media.purpose` and `media_transcriptions`. Drafts are in DATABASE_SCHEMA.md and API_CONTRACT.md.
- An applied transcript lives in `content_text`, so weekly summaries, the Cast of Characters and search (Phase 3) include paper diaries with no extra work.
- Q12 (recognition approach and consent) is settled before Sprint 6c, and Q13 (bulk notebook import) before Sprint 7.

## D-21 One AI layer: ask, generate and surface, never chat
The AI features share one layer instead of each building their own: Help me think, tags, Era summaries and friend profiles (Phase 3), and proactive insights and the writing coach (Phase 6).
- **Three modes.** *Ask*: short questions and tag suggestions answered with one tap ("Help me think"). *Generate*: stored narratives such as weekly summaries, Era summaries and friend profiles. *Surface*: cards drawn from the user's history, such as patterns, growth reminders and highlight reels. The AI never opens a chat. Everything it offers is a card the user can ignore.
- **Suggestions.** Questions, tag suggestions, writing insights and insight cards are rows in `ai_suggestions`, told apart by `kind` (a string checked in the service, so a new kind needs no migration). A question or tag suggestion always has exactly two options. The server stores what each option would do; the client sends only the option the user chose, and the server then applies a whitelisted effect (add a tag, record a fact). Nothing the AI says changes data on its own.
- **Drafts.** Help me think and the writing coach can work on text that isn't saved yet. The draft is processed in memory and never stored; only the resulting cards are, and they expire. A card made from a draft is answered once the entry is saved, by naming its id.
- **One reader.** Every AI feature gets entry text, tags and AI facts from a single `AiContentSource`, which leaves out sealed entries, applies group consent (D-19) and returns plain text with ids. No other code loads entries for AI. This puts the sealed rule (D-10, D-18, D-20) in one place.
- **Sources and invalidation.** Every AI output records its provider, model and the sources it was built from. Editing, sealing, unlocking or deleting an entry deletes the cards that cite it and marks the summaries and profiles built from it stale, so they are rebuilt without it. A stale narrative is never used as input to another. This also covers an old entry that is sealed after the AI has already read it.
- **Consent and toggles.** One AI consent (with its wording version) is stored per user, and every AI endpoint needs it (`403 consent-required`), including reading pages (D-20). Each feature also has a user toggle. Whether a feature is available at all goes through a `FeatureGate`, which reads configuration today and can read a plan later, so a feature can become paid without changing the API (Q15).
- **Providers stay swappable.** Chat, embedding and transcription are separate interfaces (D-18, D-20). Entry text is treated as data, never as instructions: model output is parsed against a schema, and unknown fields and effects are dropped.
- **Phase placement:** Help me think, tags, Era summaries and friend profiles are Phase 3. Proactive insights and the writing coach are Phase 6 (Sprint 9), because they need built-up history, entry-level retrieval and an answer on paid features.
- **The positioning stays:** "a biographer, not a therapist". Insight cards and the writing coach are opt-in, capped and dismissible, and never conversational.

**Why:**
- These features share the same risks: leaking sealed content, sending data without consent, and derived text outliving its source. Solving each once is safer than solving it five times.
- One suggestions model gives questions, tags, coach nudges and insight cards the same lifecycle, feedback and expiry. A new kind of card is a new string and a new renderer, not a new table and endpoint.
- Two-option questions and whitelisted effects keep the AI a helper, not an editor.

**Consequences:**
- Replaces the Phase 3 draft endpoints `analyze` and `ai-confirm` with `POST /suggestions` and `POST /suggestions/{id}/answer`. Nothing was built yet.
- Phase 1 needs no schema or API change. In Phase 3, `EntryMapper` gains `tags` (empty when sealed).
- New tables are drafted in DATABASE_SCHEMA.md (`user_settings`, `tags`, `entry_tags`, `ai_suggestions`, `era_summaries`, and `entry_embeddings` in Phase 6), with the endpoints in API_CONTRACT.md. `ai_summaries` gains `is_stale`, `provider` and `model`.
- Templates (D-06) already cover custom subheadings, so they need no change.
- Open questions Q14–Q17 are settled before Sprint 6 (Q14, Q17) and Sprint 9 (Q15, Q16).

## D-22 Eras can overlap
Chapters of life overlap: a trip happens inside a year at university, and a relationship runs across several jobs. So a week can belong to more than one Era.
- A week is linked to its Eras through a join table, `weekly_log_eras`. This replaces the single `weekly_logs.era_id` column in the Phase 1 design.
- Era date ranges may overlap (they always could). Assignment is still manual by default (Q1), and a week can be in as many Eras as the user likes, up to 10.
- Deleting an Era removes only its links. The weeks, and their other Eras, stay.
- The API shows a week's Eras as a list (`eras`), and `PUT /weekly-logs/{id}` takes `eraIds`.
- The feed lists each week once. Which Era it is grouped under is open question Q18; the default is the Era that started most recently, with the others shown as chips on the week.
- Trip Eras (D-19) are ordinary Eras linked to a group. A week where the user adds to a trip joins the Trip Era and stays in its other Eras.
- Anything counted per Era counts a week once for each Era it is in: Era summaries, the friendship map and a person's mentions per Era (D-21).

**Why:**
- "Trips become chapters" (Phase 5) needs a short Era inside a long one.
- A join table from the start is a small change while nothing is built. Moving a single `era_id` column into a join table later would need a data migration and a breaking API change.

**Consequences:**
- Amends D-08 and the Phase 1 design: the V1 migration, the `WeeklyLog` shape, `PUT /weekly-logs/{id}`, and tasks 3.3, 3.7, 4.4, 4.9 and 8.5.
- Phase 1 stays about the same size: one join table instead of one column.
- Open question Q18 is settled before Sprint 3.

---

## Open questions

Decide each one before the phase that needs it, then turn it into a D-entry.

| # | Question | Needed by | Default if nobody decides |
|---|---|---|---|
| Q1 | Should a new week join the Eras whose dates contain it automatically? | Sprint 3 | No: users assign eras manually |
| Q2 | Auth details: JWT in an httpOnly cookie or an `Authorization` header; expiry and refresh; Argon2id or BCrypt; email verification; password reset; login rate limiting | Sprint 5 | — |
| Q3 | Account deletion and data export (privacy obligations) | Sprint 5 | — |
| Q4 | PDF export: which library, and in Phase 2 or Phase 4? | Sprint 5 | — |
| Q5 | Render's free tier sleeps and takes about a minute to wake; is that acceptable against the "loads in 2 seconds" goal? | Sprint 5 | — |
| Q6 | AI chat model, embedding provider and dimension; consent and retention wording for sending entries to an AI provider | Sprint 6 | — |
| Q7 | Voice-to-text: Chrome's Web Speech API sends audio to Google. Acceptable, or transcribe on the server? | Sprint 7 | — |
| Q8 | Group shape: one group per occasion (a trip), or one long-lived friend group that holds several trips? | Sprint 8 | One group per occasion |
| Q9 | Shared story and consent: what each member agrees to before their contributions reach an AI provider, and what happens to a stored story when someone withdraws or leaves (see also Q6) | Sprint 8 (8b) | Opt-in per member per group; the stored story is deleted when anyone withdraws, opts out or leaves, and can be rebuilt |
| Q10 | Trips shorter than a week: an Era holds whole weeks (Q1), so a weekend trip adds its entire week to the Trip Era (the week stays in its other Eras too, D-22). Accept that, or label individual entries with the trip instead? | Sprint 8 | Accept whole weeks |
| Q11 | Invites and roles: expiring link only, or also email or username invites? Can any member invite, or only the owner? | Sprint 8 | The owner creates expiring links only |
| Q12 | Handwriting recognition: which approach (a multimodal AI model, a dedicated handwriting service or on-device), which languages, the cost per page and any daily limit, and the consent and retention wording for sending page images to a provider (see Q6). The consent record itself is covered by D-21 | Sprint 6 (6c) | No provider chosen: pages stay photos and "Read this page" is hidden |
| Q13 | Notebook import: should Phase 4 add bulk import of a whole notebook (many pages at once, one entry per page or per day, a review queue), or only single and multi-page entries? | Sprint 7 | Single and multi-page entries only |
| Q14 | Help me think timing: how long a pause before the button appears, and whether the nudge is on by default | Sprint 6 | 10 seconds, on by default. The user can change the delay or turn the nudge off, and the AI is never called until the button is tapped |
| Q15 | Paid features: which are paid (the writing coach? proactive insights?), how we learn a user's plan, and whether any have a free allowance | Sprint 9 | Everything is free and sits behind toggles; `FeatureGate` reads configuration |
| Q16 | Proactive insights: how often they appear, where (inbox only, or banners and notifications), and which kinds are on by default | Sprint 9 | Opt-in, inbox only, at most 3 a week |
| Q17 | Withdrawing AI consent: does it also delete the summaries, profiles and cards already made? | Sprint 6 | Withdrawing stops all AI use at once. Existing results stay until the user chooses "delete AI-generated content", which removes everything derived |
| Q18 | Overlapping Eras in the feed: a week can be in several Eras (D-22) but is listed once. Which Era is it grouped under, and are the others shown as chips? | Sprint 3 | The Era that started most recently, with the others as chips on the week |
