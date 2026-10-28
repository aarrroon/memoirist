# Decisions

Settled choices for memoirist, and the questions still open.

- **Which doc wins (D-27):** the [README](../README.md) wins on what users get and in which phase it ships. This file wins on how things work. Other docs follow both. If the README and this file disagree, stop and ask.
- **How this file works:** each entry states the decision as it stands now. When a decision changes, update its entry in place and set the **Updated** date (git keeps the old wording). A new topic gets the next number. Numbers are never reused; a dropped decision keeps a one-line entry saying why, so links still work.
- Record decisions with `/record-decision`. Open questions are at the end.

| ID | Decision | Decided | Updated |
|---|---|---|---|
| D-01 | Project name is **memoirist** | 2026-10-25 | |
| D-02 | Spring Boot 4.x on Java 25 | 2026-10-25 | 2026-10-28 |
| D-03 | SeaweedFS locally, Cloudflare R2 in production | 2026-10-25 | 2026-10-29 |
| D-04 | PostgreSQL 17 everywhere | 2026-10-25 | 2026-10-28 |
| D-05 | Entry content is TipTap JSON plus derived plain text | 2026-10-25 | 2026-10-28 |
| D-06 | Templates attach to entries, not weeks; starter templates | 2026-10-25 | 2026-10-28 |
| D-07 | Store UTC; each user has a timezone (default Melbourne) | 2026-10-25 | |
| D-08 | Weeks are created automatically (Monday–Sunday) | 2026-10-25 | 2026-10-28 |
| D-09 | Entries have a memory date: backdated, never future, not before 1900 | 2026-10-25 | 2026-10-27 |
| D-10 | Loot Drops use a strict seal, and opening one is surfaced | 2026-10-25 | 2026-10-28 |
| D-11 | Ownership checks and local-only dev user | 2026-10-25 | |
| D-12 | Clients may generate entity UUIDs | 2026-10-25 | 2026-10-29 |
| D-13 | Frontend library set | 2026-10-25 | 2026-10-28 |
| D-14 | Testing and CI approach | 2026-10-25 | |
| D-15 | Backend code style | 2026-10-25 | |
| D-16 | Flyway migration rules | 2026-10-25 | |
| D-17 | Media types and limits | 2026-10-25 | 2026-10-28 |
| D-18 | AI providers are chosen at Phase 4 | 2026-10-25 | 2026-10-28 |
| D-19 | Groups are contribution-based; entries stay personal | 2026-10-26 | 2026-10-28 |
| D-20 | Paper diaries: pages are marked media; reading them is on tap | 2026-10-26 | 2026-10-28 |
| D-21 | One AI layer: ask, generate and surface, never chat | 2026-10-27 | 2026-10-28 |
| D-22 | Eras can overlap and are defined by their dates | 2026-10-27 | 2026-10-28 |
| D-23 | Capture and sealing behaviour (drafts, empty entries, partial uploads, 9 am unlocks, stale edits) | 2026-10-27 | 2026-10-29 |
| D-24 | Entries have an optional title; how lists name an entry | 2026-10-27 | |
| D-25 | Week summary view and an all-entries timeline | 2026-10-27 | 2026-10-28 |
| D-26 | Manual tags in Phase 1; AI tag suggestions in Phase 4 | 2026-10-27 | 2026-10-28 |
| D-27 | The README wins on what and when; this file wins on how | 2026-10-27 | |
| D-28 | Closing a week or an Era writes its summary; closing an Era is a ritual | 2026-10-27 | 2026-10-28 |
| D-29 | Search by word and by feeling | 2026-10-27 | 2026-10-28 |
| D-30 | Deleted entries wait 30 days in Recently deleted | 2026-10-27 | |
| D-31 | Build order: groups before the AI biographer; installing at launch | 2026-10-28 | |
| D-32 | Bringing people back: On this day, a yearly letter and a weekly email | 2026-10-28 | |
| D-33 | Loot Drop extras: suggested dates, a reply to past you and a first letter | 2026-10-28 | |
| D-34 | Photo dump: photos filed by the day they were taken | 2026-10-28 | 2026-10-29 |
| D-35 | Soundtracks: Spotify links, and search through our server | 2026-10-28 | |
| D-36 | Era covers | 2026-10-28 | 2026-10-29 |
| D-37 | App lock | 2026-10-28 | |
| D-38 | Product metrics: counts only, never content | 2026-10-28 | |
| D-39 | Hard topics and the AI | 2026-10-28 | |

---

## D-01 Project name is "memoirist"
- Java base package `com.memoirist`
- Storage bucket `memoirist-media`
- Local dev user email `dev@memoirist.local`

## D-02 Spring Boot 4.x on Java 25
- **Spring Boot 4.x** for the backend.
- **Java 25**, the current long-term-support (LTS) release, everywhere: on your machine (Eclipse Temurin 25), in the Gradle toolchain, in CI and in the Docker image. Gradle must be a version that runs and compiles Java 25.

**Why:**
- Open-source support for Spring Boot 3.5 ended on 2026-06-30.
- Java 25 was chosen over 21 on 2026-10-28, before any code existed. It's the newest LTS, so it gets free updates for longer, and it's where companies are moving next. Everything written for Java 21 also runs on 25.

**Consequences:** Boot 4 brings Spring Framework 7, Hibernate 7, Jackson 3 and the Boot-4 line of springdoc-openapi; Phase 4 uses the Spring AI line built for Boot 4. Many online examples (and much of Claude's training data) are Boot 3. Check current docs before copying a pattern.

## D-03 Object storage: SeaweedFS locally, Cloudflare R2 in production
**Why:**
- MinIO's community edition stopped publishing Docker images in October 2025 and is now unmaintained.
- AWS's free tier for accounts created after 2025-07-15 is a 6-month credit, not a permanent allowance.
- R2 has a permanent free tier (10 GB of storage, no egress fees) and speaks the S3 API.

**Consequences:** one storage implementation (AWS SDK v2 `S3Client` + `S3Presigner`). Switching providers only changes endpoint, region, credentials and path-style settings. RustFS (MinIO-like, 1.0 in September 2026) is the fallback if SeaweedFS causes trouble.

**Local credentials:** SeaweedFS's S3 gateway reads its one identity from environment variables. Compose maps `STORAGE_ACCESS_KEY` and `STORAGE_SECRET_KEY` from `.env` to `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. No identity file is committed, and nothing passes `-s3.config` or stores identities in the filer, because either makes SeaweedFS ignore the environment variables. The image tag is pinned.
- **Why:** the keys have one home (`.env`), as Postgres's do, and the backend sees the same variables locally and on R2. A config file only pays off with several identities.
- **Watch out:** when no credentials load, SeaweedFS allows every request unsigned. Task 1.3 checks that an unsigned request gets `403`.

## D-04 PostgreSQL 17 everywhere
Locally the image is `pgvector/pgvector:pg17`; production is Neon on Postgres 17. One major version in both places avoids surprises. pgvector is installed in the image but only enabled in Phase 4.

## D-05 Entry content is TipTap JSON plus derived plain text
- `entries.content_json` (JSONB) holds the TipTap/ProseMirror document. It is `NULL` when an entry has no caption (photo-only posts are allowed).
- `entries.content_text` (TEXT) is plain text the server extracts from the JSON. AI, search and previews use it.
- The server validates content: root node is `doc`, only allow-listed node and mark types, link protocols limited to `http`, `https` and `mailto`, at most 200 KB.
- The frontend renders content from JSON and never inserts HTML strings.

**Why:**
- Storing HTML would allow stored XSS on Phase 2 public share pages.
- In Phase 5, inline images store a `mediaId`, never a URL, so expiring presigned links never get baked into content.

## D-06 Templates attach to entries; starter templates
- `entries.template_id` references the template used. Choosing a template inserts its subheadings into the editor as headings, above any text already written (D-23).
- When the entry is saved, template headings the user left empty are removed, so skipping a prompt leaves nothing behind.
- Editing or deleting a template later never changes existing entries. `weekly_logs` has no template column.
- **Starter templates (Phase 1):** the Templates screen offers a small gallery of ready-made templates: *Trip day*, *Spiralling check-in*, *Night out*, *Letter to future me* and *My week*. One tap adds an editable copy to the user's own templates with `POST /templates`. The gallery lives in the app, so it needs no table and can change without a migration.

## D-07 Time: store UTC, each user has a timezone
- Instants are `TIMESTAMPTZ` in the database and `Instant` in Java, serialised as ISO-8601 with `Z`.
- Calendar dates are `DATE` / `LocalDate` / `YYYY-MM-DD`.
- `LocalDateTime` is never used.
- `users.timezone` holds an IANA zone and defaults to `Australia/Melbourne`.
- "Today", week boundaries and every displayed time use the user's timezone, not the server's and not the browser's.

## D-08 Weeks are automatic
- A week runs Monday to Sunday in the user's timezone.
- Creating an entry finds or creates the week containing its memory date. The database enforces uniqueness on `(user_id, start_date)`; the service inserts with `ON CONFLICT DO NOTHING` and then selects the row.
- There is no "create week" step. Users can give a week a title, and close or reopen it; from Phase 4, closing a week writes its summary (D-28). A week belongs to every Era whose dates overlap it (D-22).
- Weeks with no entries are hidden from the feed, and so are weeks whose only entries are in Recently deleted (D-30).
- **Every week starts on Monday, for every user.** There is no setting for a Sunday start. `WeekCalendar` holds Monday as one constant, so the rule lives in one place.

**Why (week start, answers Q27):** one fixed week keeps weeks, Era overlaps, wrap-up cards and summaries simple and predictable, and V1 already checks that a week starts on a Monday. A per-user start would mean re-filing a user's weeks whenever they changed it.

## D-09 Entries have a memory date
`entries.entry_date` defaults to today in the user's timezone and can be set to an earlier date (backdating). It can't be later than today, or earlier than 1900-01-01 (`400`), so a typo such as 0202 can't create a week in the year 202. `created_at` still records when the entry was actually written. Changing the date moves the entry into the matching week.

## D-10 Loot Drops use a strict seal
An entry is **sealed** while `unlock_date > now`. While sealed:
- every response hides `title`, `contentJson`, `contentText`, `templateId`, `tags`, `soundtrack`, `replyToEntryId`, `replyIds` and `media`, and sets `isLocked: true`
- it never counts: tag filters, tag counts, search results and every other count or filter leave it out, so nothing hints at what it's about. Unfiltered lists (the feed, a week, the all-entries timeline) still show it as a locked row
- its title, content, memory date, template, tags, soundtrack, `replyToEntryId` and media can't change (`409 entry-sealed`)
- it can't be analysed by AI and is left out of AI summaries
- the unlock date can only move later; the entry can always be deleted, and it stays sealed in Recently deleted (D-30)

Seal last: create the entry, upload media, then call `POST /entries/{id}/seal`. The UI asks the user to confirm the unlock date before sealing. Share links follow the same rules.

**Opening:** a Loot Drop stays in the week of its memory date, which may be far down the feed by the time it opens. So an **Opened** strip at the top of the feed lists Loot Drops that have unlocked and that the user hasn't opened or dismissed yet. `entries.unlock_seen_at` records that (cleared whenever the entry is sealed), with `GET /entries/opened` and `POST /entries/{id}/mark-seen`. Phase 2 adds an email when a Loot Drop opens, and Phase 5 a push notification. `unlock_notified` stays the background job's own flag. Suggested unlock dates and a reply to past you are in D-33.

## D-11 Ownership and the dev user
- Controllers never receive a user ID from the request body, query or path. The current user comes from `CurrentUserProvider`.
- Every lookup by ID is scoped to the current user. Another user's resource returns `404` (not `403`), so its existence isn't revealed.
- Phase 1 resolves the user from the `X-Dev-User-Id` header, defaulting to `00000000-0000-0000-0000-000000000001`. That provider exists only in the `local` and `test` profiles.
- The dev user is seeded from `db/seed-local`, a Flyway location only those profiles load, so it never reaches production.
- Phase 2 adds a JWT-based provider with no controller changes.

## D-12 Client-generated UUIDs
Create requests for eras, templates and entries may include an `id` (UUID v4). If the `id` already exists for the same user, the server returns the existing resource with `200` (a safe retry). If it exists for someone else, the server returns `409`. An `id` that belongs to one of the user's entries in Recently deleted also returns `409`; the user restores that entry instead (D-30). Without an `id`, the server generates one.
**Why:** Phase 5 offline capture can create entries before reaching the server, with no ID remapping.
**Consequence:** JPA entities with assigned IDs implement `Persistable<UUID>` so `save()` inserts directly instead of selecting first. Two base classes in `common/` carry this:
- `BaseEntity` holds the UUID `id` and implements `Persistable<UUID>`.
- `TimestampedEntity` extends it with `@CreationTimestamp createdAt` and `@UpdateTimestamp updatedAt` (`Instant`s). Every entity uses it except `Media`, which extends `BaseEntity` and keeps its own `uploaded_at`: the `media` table has no `created_at` or `updated_at`, and `ddl-auto=validate` would reject them.

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
| Accessible UI building blocks (dialogs, menus, popovers, tabs) | shadcn/ui on Radix UI: components are copied into `src/components/ui/` and restyled with Tailwind, not installed as a styled kit |
| Toast messages | Sonner |
| Drag-to-reorder (capture media strip) | dnd-kit |
| Mobile bottom sheets (capture on phones) | Vaul |
| Date picker | react-day-picker (shadcn's Calendar) |
| Shrink photos before upload | browser-image-compression (target ~2–3 MB, keeps uploads fast and R2 usage low) |
| Full-screen photo viewer | yet-another-react-lightbox |
| Isolating render errors | react-error-boundary |

**Added when the task that needs them starts** (each still needs approval at that point):

| Library | Use | When |
|---|---|---|
| TanStack Virtual | Only render on-screen cards in a long feed | When the feed gets slow (Sprint 4 or later) |
| wavesurfer.js | Waveform display for voice notes | Voice-note polish, any time after 4.7 |
| Storybook | Build and preview components in isolation | Optional, once `docs/design/DESIGN.md` exists |
| exifr | Read a photo's own date before it's shrunk (photo dump, D-34) | Task 4.13 (already approved, 2026-10-28) |
| vite-plugin-pwa | Installable app (D-31) | Phase 2 |
| react-force-graph or visx | Friendship map | Phase 4 |
| Dexie | Offline capture | Phase 5 |
| @react-pdf/renderer | Week PDF export (if Q4 picks it) and zine export | Phase 2 (week PDFs), Phase 5 (zine) |

**Not used:** all-in-one styled kits (MUI, Chakra, Mantine) fight Tailwind and make a distinctive look harder; Next.js adds server rendering a private, logged-in app doesn't need; Redux duplicates TanStack Query plus Zustand.

These were chosen during setup as sensible defaults. Change them freely, but record the change here. Pin exact versions when scaffolding (task 4.1) and check current docs, since several have had recent major releases.

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
- At most 25 MB per file and 20 media items per entry, so a busy day fits in one post.
- No video in Phases 1–4.
- HEIC is not accepted. iOS converts photos to JPEG when the file picker's `accept` list excludes HEIC; verify this in Sprint 4.

## D-18 AI providers are chosen at Phase 4
The chat model, the embedding provider and therefore the `vector(N)` dimension are decided when Phase 4 starts (Q6). Nothing in Phases 1–3 depends on them. Model names in the original drafts are outdated.

## D-19 Groups are contribution-based; entries stay personal
Phase 3 adds private groups so friends can build shared memories, for example a trip. A group never owns an entry; it holds links to entries that members choose to add.
- A **group** has members (an owner plus the people they invite) and, optionally, trip dates and a shared unlock date.
- A **contribution** is a row in `group_entries` linking a group to one of the contributor's own entries. Nothing is copied. The entry stays in the author's journal and can be withdrawn at any time.
- **Starting from an entry:** "share with friends" on an entry starts a group from it. `POST /groups` with an `entryId` prefills the title and date from the entry and makes it the first contribution, as the README puts it: "Share one with friends and let them add their side".
- **Personal endpoints don't change.** `GET /entries/{id}` for another member's entry still returns `404`, even when it sits in a shared group. Members read contributions only through `/groups/**`, which checks membership first. A non-member gets `404`. A member who lacks the role for an action (such as an owner-only edit) gets `403`, because the group's existence is no secret from them.
- **Redaction is unchanged.** Every contribution becomes a DTO through `EntryMapper`, so a sealed one is redacted exactly as in D-10. Media is presigned only after the membership check.
- A **group Loot Drop** is a group with an `unlock_date`. Contributing to it seals the entry until at least that date, using the existing seal rules: the contributor confirms the date first, and the group's date, like an entry's, can only move later.
- **Leaving, removal and account deletion** unlink the member's contributions. Their entries, and any Era they created from the trip, stay with them.
- **Deleted entries:** an entry in Recently deleted (D-30) is left out of its groups. Restoring it brings the contribution back; erasing it removes the contribution.
- **AI (Phase 4):** the shared story, and each member's opt-in to it, come with the AI biographer (D-31). Group content reaches an AI provider only from members who opted in, and never while sealed.
- **Not a social network:** no followers, likes, public groups or discovery. Groups are small and invite-only.
- **Not planned:** linking a person in the Cast of Characters to a real group member. It only added a label, and it tied private notes about someone to their real account.

**Why:**
- The private journal stays private. A person shares only what they choose to add.
- The Phase 1 rules (D-10, D-11, CLAUDE.md rules 3 and 4) keep working, because the personal endpoints never widen.
- Leaving or deleting is a simple unlink, and the seal and redaction code is reused instead of copied.

**Consequences:**
- Phase 1 needs no schema or API change for groups: one Entry→DTO path, object keys with presigned URLs, and user-scoped finders keep the door open.
- Groups need real accounts (Phase 2). The shared story needs the AI work, so it comes in Phase 4 (D-31).
- Task 6.1 extends `.claude/rules/security.md` with the membership rule. Open questions Q8, Q11 and Q26 are settled before Sprint 6, and Q9 before Sprint 7.

## D-20 Paper diaries: pages are marked media; reading them is on tap
People who write by hand bring paper diaries in by photographing the pages.
- A **page scan** is an ordinary `media` image with `purpose = PAGE_SCAN`, chosen when it's added. `purpose` is in V1, so pages are marked from Phase 1 and lists can label them *Diary page* (D-24). The original photo is always kept and is the record. Text read from it is a derived suggestion that may be wrong.
- Pages go into ordinary entries. The page's date becomes the entry's memory date (backdating, D-09) and the entry is filed into its week as usual. Several pages can share one entry (20 media at most, D-17), and are read in `display_order`.
- **Transcription** (reading the handwriting, Phase 4) is optional and asynchronous, and runs only when the user taps "Read this page"; one tap reads every unread page of the entry. The result is stored in `media_transcriptions` for the user to review. Nothing reaches the caption until the user chooses "Use text"; it is then saved with the normal `PUT /entries/{id}` and validated like any other content (D-05). There is no special write path into entry content. A date found on the page is offered as a one-tap suggestion.
- It runs behind a `TranscriptionProvider` interface, so the approach (a multimodal model, a dedicated handwriting service or on-device recognition) can change without touching the schema or the API. Each result records its provider and model, so a page can be read again later with a better one.
- **Privacy:** a page image leaves the system only when the user taps "Read this page", never for a sealed entry, and never with location metadata. Transcripts are visible only to the entry's owner, including when the entry is in a group (D-19).
- **Seal last (D-10):** read the pages, then seal. A sealed entry can't be read.
- **Phase placement:** photos of pages, marked as pages, work from Phase 1. Reading them is Phase 4 (Sprint 7c), because it needs the AI provider decision. A camera "scan mode" (crop, straighten, several pages, offline) is Phase 5.

**Why:**
- Many people write by hand, and a photographed page is the lowest-friction way in.
- Keeping the photo means a wrong transcript never loses anything, and the handwriting itself is part of the nostalgia.
- Reading only on a tap keeps page images private by default, and ties the cost to pages people care about.

**Consequences:**
- The Phase 4 migration adds `media_transcriptions`. Drafts are in DATABASE_SCHEMA.md and API_CONTRACT.md.
- A transcript the user has used lives in `content_text`, so weekly summaries, the Cast of Characters and word search (D-29) include paper diaries with no extra work.
- Q12 (recognition approach and consent) is settled before Sprint 7c, and Q13 (bulk notebook import) before Sprint 8.

## D-21 One AI layer: ask, generate and surface, never chat
The AI features share one layer instead of each building their own: Help me think, tag suggestions, weekly and Era summaries, friend profiles and the friendship map (Phase 4), and proactive insights and the writing coach (Phase 6).
- **Three modes.** *Ask*: short questions and tag suggestions answered with one tap ("Help me think"). *Generate*: stored narratives such as weekly summaries, Era summaries and friend profiles. *Surface*: cards drawn from the user's history, such as patterns, growth reminders and highlight reels. The AI never opens a chat. Everything it offers is a card the user can ignore.
- **Suggestions.** Questions, tag suggestions, writing insights and insight cards are rows in `ai_suggestions`, told apart by `kind` (a string checked in the service, so a new kind needs no migration). A question or tag suggestion always has exactly two options. The server stores what each option would do; the client sends only the option the user chose, and the server then applies a whitelisted effect (add a tag, record a fact). Nothing the AI says changes data on its own.
- **Drafts.** Help me think and the writing coach can work on text that isn't saved yet. The draft is processed in memory and never stored; only the resulting cards are, and they expire. A card made from a draft is answered once the entry is saved, by naming its id.
- **One reader.** Every AI feature gets entry text, tags and AI facts from a single `AiContentSource`, which leaves out sealed entries, applies group consent (D-19) and returns plain text with ids. No other code loads entries for AI. This puts the sealed rule (D-10, D-18, D-20) in one place.
- **Sources and invalidation.** Every AI output records its provider, model and the sources it was built from. Editing, sealing, unlocking, deleting or restoring (D-30) an entry deletes the cards that cite it and marks the summaries and profiles built from it stale, so they are rebuilt without it. A stale narrative is never used as input to another. This also covers an old entry that is sealed after the AI has already read it.
- **Consent and switches.** One AI consent (with its wording version) is stored per user, and every AI endpoint needs it (`403 consent-required`), including reading pages (D-20). Each feature also has its own switch, as the README promises. Whether a feature is available at all goes through a `FeatureGate`, which reads configuration today and can read a plan later, so a feature can become paid without changing the API (Q15).
- **Providers stay swappable.** Chat, embedding and transcription are separate interfaces (D-18, D-20). Entry text is treated as data, never as instructions: model output is parsed against a schema, and unknown fields and effects are dropped.
- **Phase placement:** Help me think, tag suggestions, weekly and Era summaries, friend profiles and the friendship map are Phase 4. Proactive insights and the writing coach are Phase 6 (Sprint 9), because they need built-up history, entry-level retrieval and an answer on paid features.
- **Hard topics:** every AI feature follows D-39: a gentle tone, and never patterns or cards about self-harm, abuse or grief.
- **The positioning stays:** "a biographer, not a therapist". Insight cards and the writing coach are opt-in, capped and dismissible, and never conversational.

**Why:**
- These features share the same risks: leaking sealed content, sending data without consent, and derived text outliving its source. Solving each once is safer than solving it five times.
- One suggestions model gives questions, tags, coach nudges and insight cards the same lifecycle, feedback and expiry. A new kind of card is a new string and a new renderer, not a new table and endpoint.
- Two-option questions and whitelisted effects keep the AI a helper, not an editor.

**Consequences:**
- Phase 1 needs no schema or API change for the AI layer.
- Summaries are written when a week or an Era is closed (D-28).
- New tables are drafted in DATABASE_SCHEMA.md (`user_settings`, `ai_suggestions`, `era_summaries`, and `entry_embeddings` in Phase 6), with the endpoints in API_CONTRACT.md. `ai_summaries` carries `is_stale`, `provider` and `model`.
- Open questions Q14, Q17 and Q23 are settled before Sprint 7, and Q15 and Q16 before Sprint 9.

## D-22 Eras can overlap and are defined by their dates
Chapters of life overlap: a trip happens inside a year at university, and a relationship runs across several jobs. So a week can belong to more than one Era, and an Era is simply its dates.
- **An Era is its date range.** A week is in every Era whose dates overlap it: the Era starts on or before the week's Sunday, and has no end date or ends on or after its Monday. Nothing links them in the database; the service works it out from the dates. A new Era, or a change to an Era's dates, therefore applies at once to every week, old ones and backdated entries included.
- **No changes by hand.** Users don't add single weeks to an Era or take them out. To change what an Era holds, they change its dates. Tags (D-26) cover themes that don't follow dates.
- **Only the entries inside its dates:** an Era's page and its counts show only the entries whose memory date falls inside the Era's dates. A weekend trip shows just that weekend, although its week also belongs to the Era.
- Deleting an Era deletes only the Era. Its weeks, their entries and their other Eras stay.
- The API shows a week's Eras as a list (`eras`): pinned Eras first, then latest start first. `PUT /weekly-logs/{id}` sets only the title.
- **The feed lists each week once (answers Q18):**
  - **Chapters inside chapters.** A week goes under the first Era in its list. If that Era sits wholly inside another of the week's Eras (it starts on or after it, and ends on or before it; an Era with no end date runs on), the week is shown as a section nested inside that bigger Era, one level deep. A trip shows as a section inside "Uni Days".
  - **Partial overlaps.** When neither Era fits inside the other (a job from March to December and a relationship from June onwards), the week goes under the Era that started most recently, with the others as chips on the week.
  - **Pin to choose.** The user can pin an Era ("show first in the feed", `eras.pinned_in_feed`). A pinned Era comes first in a week's list, so it wins over unpinned ones; among several pinned Eras, the latest start wins.
- Trip Eras (D-19) are ordinary Eras linked to a group, so they cover the trip's dates. An entry added to the trip but dated outside them stays in the group, not in the trip Era.
- Anything counted per Era counts only the entries inside its dates, such as the friendship map and a person's mentions per Era (D-21). Era summaries are built from weekly summaries (D-28); how they treat a week the Era only partly covers is open question Q28.

**Why:**
- "Trips become chapters" (Phase 3) needs a short Era inside a long one.
- The README promises "Weeks and Eras, filed automatically": an Era such as "Uni Days" gathers its weeks with no sorting.
- One rule ("an Era is its dates") is easy to predict and leaves nothing to keep in step: no link table, no exceptions, no cap on Eras per week. Showing only the entries inside an Era's dates keeps a weekend trip to its weekend.
- Changes by hand covered rare cases that an Era's dates, or a tag, already handle. The user chose dates only on 2026-10-27, after the feature review (R-01, R-02).

**Consequences:** this answers the old open questions Q1, Q10 and Q18. V1 has no week–Era table; `eras` has a `pinned_in_feed` flag. Q28 is settled before Sprint 7. The user chose nested chapters with a pin on 2026-10-28.

## D-23 Capture and sealing behaviour
Answers to the gaps G-01 to G-07 found while drafting [USER_FLOWS.md](USER_FLOWS.md).
- **Unsaved text (G-01):** closing capture with unsaved changes asks *keep editing* or *discard*. One draft is also kept in the browser's local storage, so a reload or crash doesn't lose it. It is cleared on save or discard.
- **Empty entries (G-02):** the user can't save an entry with no title, no caption and no media; the client keeps *save* disabled. The server still allows an empty entry *at create time*, because media are uploaded after the entry exists (D-10, F-03). If every upload fails and nothing else was entered, the client deletes the empty entry, which is erased at once instead of waiting in Recently deleted (D-30). Removing the last photo from an entry with no text offers to delete the entry instead.
- **Partial uploads (G-03):** the entry is saved with the media that uploaded. Failed items stay in capture with *retry* and *remove* until capture is closed.
- **Backdated entries (G-04):** after saving an entry that lands outside the week on screen, a toast names its week with a *go there* action.
- **Unlock time (G-05):** the user picks a day; the entry unlocks at **09:00 in their timezone** on that day. The client turns that into the `unlockDate` instant; the API is unchanged.
- **Two devices (G-06):** entries use optimistic locking. `entries.version` goes up on every change; `PUT /entries/{id}` sends the `version` it read, and a stale one returns `409 entry-stale` instead of overwriting. The editor keeps the user's unsaved text and offers to load the latest version; there's no merge screen. Silent overwrites lose writing, the worst failure for a journal. The check is one column and Hibernate's `@Version`, it's in V1 so no later migration is needed, and it also guards Phase 5 offline sync. Decided 2026-10-29 (the optimistic-locking part of Q21).
- **Choosing a template after typing (G-07):** its headings are inserted above the existing text.

## D-24 Entries have an optional title; how lists name an entry
- `entries.title` is an optional short title (up to 150 characters).
- Wherever an entry is listed by name (the all-entries timeline, week summary bars, search results), its **display title** is the title if set, otherwise its preview line, otherwise a label for its first media item (*Photo*, *Diary page*, *Voice note*).
- The server returns the title, a `previewLine` and `firstMediaKind`; the client picks the label. `previewLine` is the first non-empty line of the caption that isn't a heading (up to 120 characters), or the first heading if there's nothing else. Skipping headings stops every entry started from a template being listed under its first prompt, such as "What triggered the spiral?".
- A title is private entry content, so a sealed entry's `title`, `previewLine` and `firstMediaKind` are hidden like its caption (D-10).

**Why:** the all-entries timeline (D-25) needs something to show for each entry, and a generated first line isn't always a good name.

## D-25 Week summary view and an all-entries timeline
- **Week summary (G-08):** the feed stays the main screen. Tapping a week opens its summary: title, dates, Eras, counts of entries, photos and voice notes, a few thumbnails, its tags, and then one **bar per entry** (display title, day, small icons for photos or voice notes, and a lock for sealed entries). Tapping a bar opens the entry. It uses `GET /weekly-logs/{id}`, so it needs no new endpoint. From Phase 4 the AI-written weekly summary appears at the top of the same view.
- **All-entries timeline:** a second view lists every entry in the journal, newest first, one row per entry with its display title, date and icons, and no summaries. It can be filtered by tag. It is backed by `GET /entries` with paging.

**Why:** the week summary gives Phase 4's AI summary a ready-made home, and the timeline is the quickest way to scan everything.

## D-26 Manual tags in Phase 1; AI tag suggestions in Phase 4
- Users create their own tags (for example *Work*, *Family*, *Going out*), add them while writing or editing, and filter the all-entries timeline by tag.
- `POST` and `PUT /entries` accept `tagIds`. Tags are scoped to the user and unique ignoring case; at most 20 per entry.
- A sealed entry's tags are hidden, can't be changed, and don't count in tag filters or counts (D-10).
- Phase 4 adds only the AI part: `TAG` suggestions (D-21) that add a tag after the user taps. `entry_tags.source` records `USER` or `AI`.

**Why:** tags are useful without AI, and having the user's own tags in place before Phase 4 gives the AI a vocabulary to suggest from, rather than inventing one.

## D-27 The README wins on what and when; this file wins on how
- The README says what users get and in which phase it ships. When another doc disagrees with it about that, the README wins and the other doc is updated.
- This file says how things work: technical choices and detailed product rules. When another doc disagrees with this file, this file wins.
- If the README and this file disagree, stop and ask the user. Record the answer here, and change the README only with their approval.

**Why:** the README is the product's source of truth, but it stays short and doesn't spell out technical detail.
**Consequences:** CLAUDE.md, DEVELOPMENT.md and the Claude tooling (`/sync-readme`, `/record-decision`, `/sprint-ready`, spec-reviewer and the doc-sync hook) follow this rule.

## D-28 Closing a week or an Era writes its summary; closing an Era is a ritual
- **Weeks (Phase 4):** closing a week (`POST /weekly-logs/{id}/close`) writes its AI summary. There is no automatic closing and no "close all weeks" action. A week the user never closes has no summary, and that trade-off is accepted.
- **Wrap up card:** at the top of the feed, it offers the most recent finished week (its Sunday has passed in the user's timezone) that has entries and is still open. That is usually last week; after a break, it's the last week the user wrote in. One tap closes it and shows its summary. The card also offers Eras whose end date has passed but that aren't closed.
- **Gentle reminder:** when three or more finished weeks are still open, a small note appears under the card, at most once a week. It can be dismissed, and has its own switch.
- **Eras (Phase 4):** closing an Era (`POST /eras/{id}/close`) is a short ritual.
  1. It shows how many of the Era's weeks are still open, and closes them too.
  2. It offers a *last word*: an ordinary entry dated the Era's last day.
  3. It offers an optional letter to future you, sealed as a Loot Drop for 5 years by default (D-10).
  4. The Era summary is written in the background from the weeks' summaries, within the daily AI limit.

  Closing sets the Era's end date to the end of its latest week if it has none, so no new weeks join it (D-22). An entry backdated into its dates later still belongs to it and makes the Era summary stale. `POST /eras/{id}/reopen` reopens it. The Era's cover (D-36) can be set at any time from Phase 1.
- Era summaries, search by feeling (D-29) and Phase 6 insights use closed weeks only.

**Why:** closing a week is a small, satisfying ritual, and the AI cost follows what users choose to do. The card makes it one tap at a natural moment, without streaks or guilt. Closing an Era together with its weeks saves closing 150 weeks one by one. The last word and the letter turn the end of a chapter into a moment, using entries and Loot Drops that already exist; the user put the ritual with the summary on 2026-10-28.
**Consequences:** Phase 1 keeps close and reopen for weeks as they are. Task 7.13 builds Era closing and its ritual (`eras.is_closed`), and task 7.16 the card and the reminder. Automatic and bulk closing are listed in PRD §6 as not planned.

## D-29 Search by word and by feeling
- **Word search (Phase 4):** Postgres full-text search over each entry's title and `content_text` (which includes page text the user has used). Sealed entries never match (D-10). `POST /search/words` returns `PageResponse<EntrySummary>`.
- **Search by feeling (Phase 4):** "when did I last feel like this?" over the summaries of closed weeks (D-28), with `POST /search/feeling`.
- Search terms go in the request body, never in the URL, and are never logged, because they are as private as entries.

**Why:** the README promises page text "you can search". "Insights" is the Phase 6 name for cards, so the Phase 4 search is called search by feeling.

## D-30 Deleted entries wait 30 days in Recently deleted
- **Deleting an entry moves it to Recently deleted** (`entries.deleted_at`). It disappears from everywhere else at once: the feed, its week, the all-entries timeline, the Opened strip and unlock emails, tag counts and filters, search, share links, groups and AI input. A week whose only entries are deleted is hidden like an empty one (D-08).
- **For 30 days** the user can restore it or erase it now (`GET /entries/deleted`, `POST /entries/{id}/restore` and `POST /entries/{id}/erase`). Restoring puts it back in its week with its media, tags and seal as they were.
- **After 30 days** a daily sweep erases it with its media files. Erasing can't be undone.
- **A sealed entry stays sealed** in Recently deleted: the list shows only its memory date and unlock date (D-10). Deleting a sealed entry is still always allowed.
- **Empty entries are erased at once:** an entry with no title, caption or media, such as one whose uploads all failed (D-23), skips Recently deleted.
- **Only whole entries:** removing a photo or voice note from an entry is an edit and takes effect at once.
- Every other request about a deleted entry returns `404`, as if it were gone. A create that reuses its `id` returns `409 conflict` (D-12).
- Deleting the account erases everything at once, Recently deleted included (Q3).

**Why:** losing an entry by accident is the worst failure for a memory app, and other journals and photo apps let people undo it. Building it into V1 costs a column and a filter; adding it later would change what "delete" means across the API. The user chose it for Phase 1 on 2026-10-27, after the feature review (R-03).

**Consequences:** V1 gains `entries.deleted_at`. Entry queries leave deleted rows out, and stored files are deleted when an entry is erased, not when it's deleted. A restore counts as an edit for AI results (D-21). Task 3.11 builds the API and task 4.12 the screen.

## D-31 Build order: groups before the AI biographer
The phases run in this order. The README Timeline is the source of truth for it (D-27).

| Phase | What | Sprint |
|---|---|---|
| 1 | The core journal | 1–4 |
| 2 | Online, with accounts, now including installing the app | 5 |
| 3 | Memories with friends | 6 |
| 4 | The AI biographer | 7 (7a, 7b, 7c) |
| 5 | On your phone | 8 |
| 6 | Looking back with you | 9 |

- **Groups come third.** They need real accounts (Phase 2) but no AI, and they're how new people hear about memoirist. The parts that need the AI move to Phase 4: the shared trip story and each member's opt-in to it (D-19).
- **Installing comes at launch.** Phase 2 adds the home-screen app (the installable PWA shell). Offline writing, scan mode, dictation, inline photos, print and push notifications stay in Phase 5.
- **The weekly email** (D-32) builds on week summaries, so it comes in Phase 4 too.
- Phases 4 and 5 can still swap places: neither needs the other.

**Why:** groups are the main way memoirist grows and need no AI, so they come right after launch, and launch users get an app on their home screen. The user chose this order on 2026-10-28, after the feature review (R-07, R-31).

**Consequences:** sprints and tasks were renumbered to follow the phases (groups 6.x, the AI 7.x, phones 8.x), which is allowed before a sprint starts.

## D-32 Bringing people back: On this day, the yearly letter and the weekly email
- **On this day (Phase 1):** a strip at the top of the feed, beside the Opened strip, shows entries from the same day and month in earlier years (`GET /entries/on-this-day`). Sealed entries never appear (D-10), nor do deleted ones (D-30). In a year that isn't a leap year, entries from 29 February show on 28 February.
- **The yearly letter (Phase 2):** the user picks a yearly date, their birthday by default (`users.birthday`, D-33). At 09:00 on that day, in their timezone, an email and a card in the app invite them to write to next year's self. The letter is sealed as a Loot Drop until 09:00 on the same date next year.
- **The weekly email (Phase 4):** at 09:00 on Monday, after a week that has entries, an email points to that week: *read its story* if it's closed, or *wrap it up* if it's still open (D-28). It's on by default, has its own switch, and never follows an empty week.
- **No email contains entry content.** Every memoirist email (unlock, yearly letter, weekly) carries dates, counts and a link, never titles, captions, photos or summary text, because inboxes are often shared or owned by an employer.
- No streaks, ever: nothing counts days or weeks in a row.

**Why:** the review found that nothing brought people back (R-06, R-08, R-51). Emails without content keep the privacy promise, because email isn't private. Chosen by the user on 2026-10-28.

## D-33 Loot Drop extras: suggested dates, a reply to past you and a first letter
All three are Phase 1.
- **Suggested unlock dates:** the seal dialog offers *in a month*, *in a year* and *on my next birthday* as one-tap choices beside the date picker. Each still opens at 09:00 in the user's timezone (D-23) and still asks for confirmation (D-10). The birthday choice appears once `users.birthday` is set in Settings.
- **Reply to past you:** an opened Loot Drop offers *reply*. A reply is an ordinary new entry whose `reply_to_entry_id` points at the Loot Drop. Only the user's own Loot Drops that have opened can be replied to. The Loot Drop links to its replies, and each reply shows what it answers. Deleting either one leaves the other alone.
- **A first letter:** the first-run screen (F-01) ends with an optional, skippable letter to future you, sealed for a month, so every new user has a reason to come back.

**Why:** they make sealing quicker and opening a moment (R-15, R-21). The user chose all three on 2026-10-28.

## D-34 Photo dump: photos filed by the day they were taken
- **Photo dump (Phase 1):** the user picks many photos at once. The app reads each photo's own date (EXIF, in the user's timezone) before shrinking it (D-13), groups the photos by day, and creates one backdated entry per day with no caption, to fill in later. A day with more photos than the per-entry limit (20, D-17) becomes several entries. Photos with no date go into one entry dated today and marked *date unknown* (below).
- **Photo dates for single photos:** when a photo is added in capture, its own date is offered as the memory date with one tap, so a photo taken last Tuesday is filed on last Tuesday.
- **Date unknown:** `entries.date_unknown` marks an entry whose date is a guess. The photo dump sets it with `dateUnknown: true` on `POST /entries`; the server clears it when a `PUT` changes `entryDate`, and clients never send it on `PUT`. `Entry` and `EntrySummary` include `dateUnknown`, and it stays visible while sealed, because the memory date isn't private (D-10). The card shows a *date unknown, fix it* badge. Decided 2026-10-29: the mark has to survive reloads and other devices so the user can find what to fix, and a tag would mix a system marker into the user's own tags and counts.
- It uses the existing endpoints (`POST /entries` with `entryDate` and, for undated photos, `dateUnknown`, then the media uploads), and client-generated ids make retries safe (D-12).
- Only the date is read from a photo in the app. What the server does with a photo's GPS data is open question Q19.

**Why:** it's the memory hoarder's exact problem, and it closes the review's photo-date gap (R-16, R-44). Chosen by the user on 2026-10-28.

## D-35 Soundtracks: Spotify links, and search through our server
- **Where (Phase 1):** an entry, a week and an Era can each have one optional soundtrack: a Spotify track, album or playlist.
- **What's stored:** only its Spotify URI, such as `spotify:track:` followed by a 22-character id, in `soundtrack` on `entries`, `weekly_logs` and `eras`. The server checks the format. No URL is stored.
- **Adding one:** paste a Spotify share link (the app turns it into a URI), or search.
- **Search:** `POST /music/search` sends the words in the request body to our server, which asks Spotify's Web API with app-only credentials (the client credentials flow; `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` in `.env`). It returns up to 10 results, each with a URI, a title, an artist or owner and a cover image. The secret never reaches the browser, and search terms are never logged (as D-29).
- **When Spotify says no:** if search isn't set up, or Spotify refuses (new apps get a small quota and `429`s), the endpoint returns `503 music-search-unavailable` and the app offers paste-a-link instead.
- **Playing:** Spotify's own embedded player, loaded only when the user taps it, so Spotify sees nothing until then.
- **Privacy:** a sealed entry's soundtrack is hidden like the rest of its content (D-10). Share links and groups show it like the rest of the entry.

**Why:** music is a strong memory trigger, and a link costs almost nothing to store (R-46). Spotify still lets new apps search with an app-only token, but with a small quota and at most 10 results, and it gives more only to large businesses, so paste-a-link must always work. The Spotify developer account must keep Premium for the app to keep working. Chosen by the user on 2026-10-28.

## D-36 Era covers
- **Phase 1:** an Era can have a cover photo (`eras.cover_media_id`): one of the user's own photos, usually from that Era. The feed shows it as the Era's title page above its weeks, and so does the Era's page.
- A cover shows only while its entry is neither sealed (D-10) nor in Recently deleted (D-30); otherwise the Era has no cover. Removing the photo clears the cover.
- Choosing a photo from a sealed entry as the cover is `400`; one from an entry in Recently deleted is `404`, like every other request about that entry (D-30).
- The cover can be chosen whenever the Era is edited. From Phase 4, closing an Era offers it too (D-28).

**Why:** chapters you can see make the journal feel like a book from day one (R-20, R-48). The user chose covers now and the closing ritual with the AI on 2026-10-28.

## D-37 App lock
- **Phase 2:** an optional lock in Settings. When it's on, memoirist locks after a few minutes in the background and asks to be unlocked before showing anything.
- It unlocks with the device's own fingerprint, face or passcode, through a passkey-style check (WebAuthn), or with a memoirist PIN where the device has none.
- It guards the screen on a shared or borrowed device; it doesn't replace signing in. How it pairs with sign-in, and where a PIN is checked, is part of Q2.

**Why:** people expect a lock on a private journal, and Day One and Apple Journal have one (R-09). Chosen by the user on 2026-10-28.

## D-38 Product metrics: counts only, never content
- **From Phase 2:** the server records product events in `product_events`: a user, a kind and a time, nothing else. The kinds are `SIGNED_UP`, `ENTRY_CREATED`, `LOOT_DROP_SEALED`, `WEEK_CLOSED` and `INVITE_ACCEPTED`.
- Five numbers come from it by query: time to first entry; how many new users write again in their second week; weekly active writers; Loot Drops sealed; and invites accepted per group (with group membership).
- An event never holds content, titles, tags, search terms or URLs. There are no third-party analytics, cookies or tracking scripts.
- Deleting an account deletes its events.

**Why:** the README expects the plan to shift "as we learn what people use", and this is how to learn without reading anyone's journal (R-14). Chosen by the user on 2026-10-28.

## D-39 Hard topics and the AI
This applies to every AI feature from Phase 4 on: summaries, profiles, Help me think, insights and the writing coach.
- **A gentle tone.** The biographer writes about hard times kindly and briefly, and never diagnoses or advises.
- **Never patterns or cards about self-harm, abuse or grief.** Insight cards, *last time you felt like this* and highlight reels (Phase 6) leave these topics out.
- **Summaries** mention them softly or not at all, and never quote them.
- **Help me think** never asks probing questions about them.
- **A helpline where it fits:** when an entry suggests someone may be at risk, the app may show a short note they can dismiss, with a local helpline (Lifeline on 13 11 14 in Australia). It never tells anyone else.
- How these topics are recognised is settled with the AI provider (Q6), and each rule gets a test with the fake provider.

**Why:** templates like *Spiralling* invite people to write at their lowest, and an AI that reads entries must handle that with care (R-24). The user chose to settle this now, before the AI phase, on 2026-10-28.

---

## Open questions

Decide each one before the sprint that needs it, then record it with `/record-decision`: as a new entry, or as an update to the entry it changes.

| # | Question | Needed by | Default if nobody decides |
|---|---|---|---|
| Q2 | Auth details: JWT in an httpOnly cookie or an `Authorization` header; expiry and refresh; Argon2id or BCrypt; email verification; password reset; login rate limiting; which email service sends verification, reset and unlock emails; how the app lock (D-37) pairs with sign-in and where a PIN is checked | Sprint 5 | — |
| Q3 | Account deletion and data export (privacy obligations) | Sprint 5 | Export includes sealed entries only as locked placeholders (memory date and unlock date). Deleting an account first says how many sealed entries will be lost unseen |
| Q4 | PDF export of a week (Phase 2): which library? | Sprint 5 | — |
| Q5 | Render's free tier sleeps and takes about a minute to wake. Is that acceptable against the "loads in 2 seconds" goal? While it sleeps, scheduled jobs (unlock emails, sweeps) don't run, and `@Async` work is lost on a restart | Sprint 5 | Database rows are the queue (due, pending and stale rows). A sweep resumes them, started by an outside scheduler such as a GitHub Actions cron, and every job is safe to repeat |
| Q6 | AI chat model, embedding provider and dimension; consent and retention wording for sending entries to an AI provider | Sprint 7 | — |
| Q7 | Voice-to-text: Chrome's Web Speech API sends audio to Google. Acceptable, or transcribe on the server? | Sprint 8 | — |
| Q8 | Group shape: one group per occasion (a trip), or one long-lived friend group that holds several trips? | Sprint 6 | One group per occasion |
| Q9 | Shared story and consent: what each member agrees to before their contributions reach an AI provider, and what happens to a stored story when someone withdraws or leaves (see also Q6) | Sprint 7 | Opt-in per member per group, on top of each member's own AI consent. The stored story is deleted when anyone withdraws, opts out or leaves, and can be rebuilt |
| Q11 | Invites and roles: expiring link only, or also email or username invites? Can any member invite, or only the owner? How big can a group be, and who owns it when its owner deletes their account? | Sprint 6 | The owner creates expiring links only; at most 12 members; ownership passes to the longest-standing member |
| Q12 | Handwriting recognition: which approach (a multimodal AI model, a dedicated handwriting service or on-device), which languages, the cost per page and any daily limit, and the consent and retention wording for sending page images to a provider (see Q6). The consent record itself is covered by D-21 | Sprint 7 (7c) | No provider chosen: pages stay photos and "Read this page" is hidden |
| Q13 | Notebook import: should Phase 5 add bulk import of a whole notebook (many pages at once, one entry per page or per day, a review queue), or only single and multi-page entries? | Sprint 8 | Single and multi-page entries only |
| Q14 | Help me think timing: how long a pause before the button appears, and whether the pause prompt is on by default | Sprint 7 | 10 seconds, on by default. The user can change the delay or turn the prompt off, and the AI is never called until the button is tapped |
| Q15 | Paid features: which are paid (the writing coach? proactive insights?), how we learn a user's plan, and whether any have a free allowance | Sprint 9 | Everything is free and sits behind switches; `FeatureGate` reads configuration |
| Q16 | Proactive insights: how often they appear, where (inbox only, or banners and notifications), and which kinds are on by default | Sprint 9 | Opt-in, inbox only, at most 3 a week |
| Q17 | Withdrawing AI consent: does it also delete the summaries, profiles and cards already made? | Sprint 7 | Withdrawing stops all AI use at once. Existing results stay until the user chooses "delete AI-generated content", which removes everything derived |
| Q19 | Share-link privacy: shared pages are built by `EntryMapper`, which includes private tags; a week link also shows entries added to that week later; and photos can carry GPS data | Sprint 5 | Shared and group views leave out tags and template; a week link shows only the entries it had when it was made; the server strips GPS data from photos on upload, keeping the capture date |
| Q20 | Storage and backups: R2's free 10 GB holds about 4,000 phone photos across all users, and nothing restores data after a mistake or an outage | Sprint 5 | A per-user storage quota with a clear message; Neon point-in-time restore plus a scheduled copy of the bucket, with a restore tested before launch |
| Q21 | Devices and sessions: drafts left in local storage on a shared device, and CSRF if the token is in a cookie (Q2). Two devices editing one entry is settled in D-23 | Sprint 5 | Drafts kept per user and cleared on logout; a CSRF token if cookies are used |
| Q22 | Encryption: entries aren't end-to-end encrypted, because the AI and share links need the server to read them. How do we say so? | Sprint 5 | Encrypted at rest by the providers, and said plainly in the privacy policy |
| Q23 | AI summary lifecycle: adding an entry to, or moving one into, a week that already has a summary doesn't mark it stale (D-21 covers edit, seal, unlock and delete). Summaries and profiles also have no status for "not yet", "failed" or "nothing to say" | Sprint 7 | Adding or moving an entry marks that week's summary and its Eras' summaries stale. Each summary and profile has a `status`: `PENDING`, `READY`, `FAILED` or `SKIPPED` |
| Q24 | Voice notes: the biographer and search can't read them. Transcribe them? | Sprint 8 (with Q7) | Optional and on tap, through the same `TranscriptionProvider` as pages |
| Q25 | Writing time and mood: patterns such as "flat on Sunday evenings" need when an entry was really written (`created_at` is wrong for backdated and offline entries) and a mood for each entry | Sprint 8 (mood: Sprint 9) | Offline entries send a client `capturedAt`; Phase 6 adds a per-entry mood as derived AI data, with the usual seal and staleness rules |
| Q26 | Group Loot Drop dates: if the owner moves the date later, entries added earlier still unlock on the old date; an entry already sealed for longer opens later; and withdrawing doesn't unseal | Sprint 6 | Group views hide content until the later of the entry's and the group's dates; the withdraw dialog says the entry stays sealed |
| Q28 | Era summaries and partly covered weeks: an Era shows only the entries inside its dates (D-22), but a weekly summary covers the whole week. For a short Era, such as a weekend trip, should the Era summary read the entries inside its dates instead of whole-week summaries? | Sprint 7 | A week the Era covers only partly is read from its entries inside the Era's dates; a week it covers fully uses its weekly summary |
