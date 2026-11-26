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
**Phase 1, Sprint 4. Next task: 4.10** (Week summary view). (needs attention: see docs/OVERNIGHT_REPORT.md)
- Sprints 1–3 are built: the whole Phase 1 API (37 endpoints, about 1,600 tests). Task 4.1 scaffolded `frontend/`: the tokens and fonts from DESIGN.md, ESLint, Prettier, Vitest and MSW, and a starter page. Task 4.2 added `src/api/`: generated types, the `openapi-fetch` client, `ApiProblem` errors, the query-key factory, the `QueryClient` and `useMe()`. Task 4.3 added the eight Phase 1 routes (`router.tsx`), the app shell (top bar, tab bar with Write, side nav), the Zustand UI store and `src/lib/time.ts`. Task 4.4 made the Journal at `/` a real feed: an infinite query over `GET /feed`, weeks filed under their Eras (D-22), a text-only sealed card, and empty, loading and error states. Task 4.5 stored photo sizes (`V2__media_dimensions.sql`, D-17) and gave each feed entry an Embla swipe framed from the first photo, a caption drawn from TipTap JSON (`RichText`) and a voice note row. Task 4.6 built capture (`src/features/capture/`): Write opens a Vaul sheet on phones and a dialog on laptops, lazy-loaded, with a title, the TipTap editor, templates, a memory-date picker, tags made inline, photos that upload, reorder and can be marked as diary pages, one draft in local storage, and the create → upload → order save (`saveEntry.ts`). Task 4.7 added voice notes: a `MediaRecorder` hook (`useRecorder.ts`, Opus in WebM or AAC in MP4) behind Voice in capture, voice notes kept after the pictures in the media order (D-24), and a keyboard-operable player (`components/VoiceNote.tsx`) that finds the length Chrome's WebM leaves out. Task 4.8 built Loot Drops: the travelling star (`src/assets/travelling-star.svg`, `lib/starPath.ts`), the seal panel and confirmation (react-day-picker, opening at 09:00 in the user's zone), a fixed-size sealed card with a live countdown, the Loot Drop page at `/entries/:entryId` with Extend and Delete, and the Opened row at the top of the feed (`src/api/lootDrops.ts`, `src/features/lootdrop/`). Task 4.9: `GET /eras/{id}` lists only weeks with an entry to show, plus the Eras list and Era page with one Era form (`src/features/eras/`), Templates with the starter gallery, and You with timezone, birthday and Appearance (`lib/appearance.ts`); the Era cover sits above its title band at every width (D-36). Task 4.10 is built but not ticked (its thumbnails fail the audit's image-size check until Q29 is decided): the week summary at `/weeks/:weekId` (`src/features/week/`, `src/api/weeks.ts`) with counts, thumbnails, tags, one bar per entry, a star-only sealed bar, and Rename, Close and Reopen week; the display title is computed in `lib/displayTitle.ts`. Task 4.11 made `/entries` the Timeline (`src/features/timeline/`, `src/api/timeline.ts`): every entry newest first under sticky month headings, 50 a page from `GET /entries`, tag chips that filter through `?tag=<id>`, and a star-only sealed row; `/tags` (`src/features/tags/`) renames and deletes tags and refreshes every query that shows a tag name. Task 4.12 made every screen but the Journal load on first visit (route `lazy` with `createRoutes()` in `router.tsx`) and built `/recently-deleted` (`src/features/deleted/`, `src/api/deleted.ts`): the list with each entry's erase day (`erasureDay` in `lib/time.ts`), Restore and Erase now, one shared delete question (`DeleteEntryDialog`) used by a feed entry's more-actions menu (`EntryMenu`), an open entry and the Loot Drop page, the count on You and a row at the foot of the Timeline. Task 4.13 built the photo dump (`PhotoDump.tsx`, `dumpPlan.ts`): each photo's EXIF date is read with exifr before it is shrunk (`lib/photoDate.ts`), one backdated entry is filed per day (several for more than 20 photos) with a client id that a retry reuses (D-12), undated photos go into one entry marked `dateUnknown` with a *Date unknown, fix it* badge on its feed card (`DateUnknownBadge`, a full `PUT` through `useChangeEntryDate`), capture offers a photo's own date as the memory date, and photos are now shrunk at upload, a diary page more gently (D-34). Task 4.14 is built but not ticked (the feed's 32 px Era cover fails the audit's image-size check until Q29 is decided): `GET /entries/on-this-day` leaves out `dateUnknown` entries and a `PUT /eras/{id}` keeps a hidden cover; the feed's *On this day* row (`OnThisDay.tsx`, `src/api/onThisDay.ts`) expands in place, at the top or below the first entry while the Opened row shows; each Era line shows its cover and links to its Era page; Edit Era has a cover picker (`CoverPicker.tsx`, `useEraPhotos` in `src/api/eras.ts`). Task 4.15 added soundtracks (`src/features/music/`, `lib/spotify.ts`, `src/api/music.ts`): one field in capture, Edit week (was Rename week) and Edit Era takes a pasted Spotify link or searches through `POST /music/search`, paste only after a `503`; every soundtrack row shows the Spotify mark, the kind and a Play button that adds Spotify's embed only on the tap (D-35). Task 4.16 added the Loot Drop extras (D-33): the seal panel's quick dates (In a month, In a year, On my birthday once one is set; `quickDays` in `lib/time.ts`), Reply to past you on an opened Loot Drop with links both ways (`useReplies` in `src/api/lootDrops.ts`, `replyTo` in the UI store), and a skippable letter to future you at the end of the first-run screen, sealed for a month (`lib/firstLetter.ts`, capture kind `letter`). Most other screens are placeholders.
- Progress is tracked by the checkboxes in `docs/SPRINT_PLAN.md`. Update this line when a task is ticked; a Stop hook checks it.
- Build only what the current phase needs. Don't add tables, endpoints or libraries from later phases.

## How it's built
- **Backend:** Spring Boot 4.1 on Java 25, one package per feature. A request goes `XController` (HTTP only) → `XService` (transaction, ownership, rules, mapping to DTO records) → `XRepository` (user-scoped finders only).
- **Data:** Postgres 17, changed only by Flyway migrations (`ddl-auto=validate`). Files go to S3-style storage (SeaweedFS locally, R2 in Phase 2) through `StorageService` only; the database holds object keys.
- **Errors:** services throw the domain exceptions in `common/`; one `GlobalExceptionHandler` turns them into RFC 9457 problems (`urn:memoirist:problem:<suffix>`).
- **Frontend (Sprint 4):** React 19, Vite, TypeScript, Tailwind v4. API types are generated from `/v3/api-docs`; TanStack Query owns server data; Zustand holds UI state only. The library list is D-13.
- **Tests:** mostly integration tests against real Postgres (Testcontainers); CI runs `./gradlew build` on every push to `main` or `phase-*`.

## Repo layout
| Path | What |
|---|---|
| `backend/src/main/java/com/memoirist/<feature>/` | `common`, `user`, `era`, `template`, `tag`, `music`, `week`, `entry`, `media`, `storage`, `dev`; DTO records in each `dto/` |
| `backend/src/main/resources/` | `application.yml`, `application-local.yml`, `db/migration/V{n}__*.sql`, `db/seed-local/` (dev user; `local` and `test` only) |
| `backend/src/test/java/com/memoirist/` | Tests beside the code they test. Shared helpers at the top: `@IntegrationTest`, `Fixtures`, `MutableClockConfiguration`, `SeaweedFsTestConfiguration` |
| `frontend/` | From task 4.1: `src/api`, `src/features/<feature>`, `src/components/ui`, `src/lib`, `src/stores` |
| `docker-compose.yml` | Postgres 17 (pgvector image) on :5432; SeaweedFS S3 on :8333 (filer UI :8888) |
| `docs/` | Specs (doc map below); `build-journal/` records how each task was built; `design/explorations/` holds the design rounds |
| `.claude/` | `settings.json`, path-scoped `rules/`, `skills/`, `agents/`, `hooks/`, `output-styles/` |
| `.github/workflows/ci.yml`, `.env.example` | CI (backend job); the names of every environment variable |

## Commands
Run these from the repo root in Git Bash on Windows. The full list, ports and troubleshooting are in `docs/DEVELOPMENT.md`; when a command changes, update both.

```bash
docker compose up -d                                                      # data services; needed by bootRun, not by the tests
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'   # API on :8080, /swagger-ui.html (reads ../.env, so start it in backend/)
cd backend && ./gradlew test                                              # Testcontainers needs Docker running
cd backend && ./gradlew test --tests '*EntryEndpointTest'                 # one class
cd backend && ./gradlew spotlessApply                                     # format Java; run it before build
cd backend && ./gradlew build                                             # compile, format check, every test: what CI runs (2 to 9 minutes)
cd frontend && npm run dev                                                # 4.1: :5173, proxies /api to :8080
cd frontend && npm test && npm run lint && npm run typecheck              # 4.1
cd frontend && npm run gen:api                                            # 4.2: regenerate API types (backend must be running)
```
Add `--memoirist.sample-content=true` to the `bootRun` arguments to fill an empty dev journal (task 3.16).

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
| Building a screen, or writing an end-to-end test | `docs/design/DESIGN.md` (the look, D-41) and `docs/USER_FLOWS.md` (draft; flow IDs `F-xx` name the Playwright tests) |
| Wondering how an earlier task solved something | `docs/build-journal/sprint-N/` (each sprint's README ends with its lessons) |

`docs/product/` is market research and `docs/marketing/` is a pitch. Don't load them for coding.

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

## Coding rules (the short version of `.claude/rules/`)
- **Contract first.** Change `docs/API_CONTRACT.md` or `docs/DATABASE_SCHEMA.md` before or with the code. Paths are `/api/v1/<plural>`; `201` create, `200` read or update, `204` delete; `PUT` is a full replace.
- **Java:** DTOs are records with Jakarta validation. No Lombok, no MapStruct, no scattered `@Value` (use `@ConfigurationProperties` records). Format is palantir-java-format, 4 spaces, 120 columns.
- **Services** are `@Transactional` (`readOnly = true` for reads) and map to DTOs inside the transaction, because open-in-view is off. Controllers never touch a repository.
- **Persistence:** every association is `LAZY`; never fetch-join two collections; for a page, load the IDs, then the children with `IN (…)`. Read a row a request refers to with a lock (`FOR SHARE`), so a delete at the same moment gives `404`, not `500`.
- **Responses:** `PageResponse`, never a Spring `Page`; an out-of-range `size` or `page` is `400`, not a silent clamp. Nothing the client sent comes back in `detail` or `fieldErrors`. Free text the user types goes in request bodies, never in URLs or logs.
- **Tests:** every endpoint gets the happy path, `400`, `404` for another user's resource, and `409 entry-sealed` where sealing applies. Pure logic gets plain unit tests with a fixed `Clock`.
- **Frontend:** generated types only (`npm run gen:api`); components use Query hooks, never `fetch`; `staleTime` is 30 minutes or less (presigned URLs last 60); all dates go through `src/lib/time.ts`; TipTap uses StarterKit and Link only.
- **Comments** say why, and name the decision (D-xx) or task they come from.

## Design rules
**One design system (D-40): one spacing scale, two typefaces at most, one accent colour.** The checkable rules and the check to run after every fix are in `.claude/rules/design.md`, which loads when you touch `frontend/`. The reasons are in `docs/design/explorations/round-2/AUDIT.md`: the ten things that made the mockups look cheap.

**The look is chosen (D-41):** design 12, *Starlight refined*. Every value and component is in `docs/design/DESIGN.md`; build screens from it. The mockup (`docs/design/explorations/round-3/`) is a picture to compare against: never copy its code into `frontend/`. In short:
- **Phone first:** designed at 375 px, then the laptop. Photos run edge to edge and swipe; a hairline and space separate entries.
- **Spacing:** 4, 8, 12, 16, 24, 32, 48 or 64 px for every padding, margin and gap. One 16 px gutter on phones. Tap targets are at least 44×44 px, and nothing scrolls sideways at 375 px.
- **Type:** Instrument Sans for text and the interface, Newsreader for titles only. Seven text sizes, nothing under 12 px, no emoji.
- **Colour:** black, white and gold, with gold the one accent, on actions, the selected item, focus and Loot Drops. No second accent (the Spotify mark is black or white), no page filled with saturated colour.
- **Light and dark:** each mode is designed on its own, never inverted; the app follows the phone, with a switch in settings. Dark is charcoal, not black, with off-white text and a softer gold.
- **Shape:** two corner radii plus full. Boxes nest two deep at most.
- **First screen:** on a phone the first entry's photo starts no lower than 35% down the feed, so On this day, the Era and the week stay compact (D-25).
- **Components:** dialogs, menus, popovers and tabs are shadcn/ui copied into `src/components/ui/` and rewritten to the tokens; icons are `lucide-react` only; capture is a Vaul sheet on phones and a dialog on laptops. One Write button, in the centre of the phone tab bar: a plus or a word, never a pencil, never floating over text. It opens New entry, Photo dump and Diary page.
- **Loot Drops:** a sealed card has a fixed size and shows only its memory date, opening date and countdown, and under a day heading only the last two (D-10). The page says the opening date once. Its waiting object is the approved travelling star (DESIGN.md §5): never improvised, never a padlock, pill or capsule (D-39).
- **Never:** streaks, calendar grids or anything that shows the days with no entry (D-32); film, tape or school-paper props; a fact repeated on one screen; labels that aren't the PRD §3 words (Eras, weeks, entries, Opens, Written).
- **Also:** contrast is WCAG AA (text 4.5:1; control borders, meaningful graphics and large text 3:1); focus is visible; `prefers-reduced-motion` stops motion; fonts are served by the app; photos load lazily with their space reserved, and are shown whole in the entry view.
- **Tokens only:** every colour, font, text size and radius is an `@theme` token in `src/index.css`. Never write a hex value, a font name or a one-off size in a component. If a task needs a look DESIGN.md doesn't give, ask.

## Mistakes to avoid
Each one happened, or nearly did, in Sprints 1–3.

| Mistake | Do this instead |
|---|---|
| Copying a Spring Boot 3, Jackson 2 or JUnit 5 example | Boot 4 names: `spring-boot-starter-webmvc`, one `-test` starter per starter, `spring.web.error.*`, `RestTestClient`. Import Jackson 3 from `tools.jackson.databind` (annotations stay in `com.fasterxml.jackson.annotation`). Check context7 first |
| A new entries query, count or filter that forgets the seal | Write `(e.unlockDate is null or e.unlockDate <= :now)` in the query itself (`unlock_date` in native SQL), and add a test that holds a sealed entry. In Java, `EntrySeal.isSealed` is the one definition |
| Building an entry response by copying every field, then blanking some | Only `EntryMapper` builds one, from an allow-list of what a sealed entry may show. `EntryMapperIsTheOnlyPathTest` fails otherwise |
| Forgetting that `Entry` has `@SQLRestriction("deleted_at IS NULL")` | JPQL never sees Recently deleted rows, even through a join. A native query on `entries` adds `deleted_at IS NULL` itself. Erase only through `EntryErasure`, or the files leak |
| Adding `findById`, answering `403`, or taking a `userId` parameter | Repositories extend the bare `Repository<X, UUID>` and declare only user-scoped finders. Not owned is `404`. Every create calls `UserService.currentUser()` first |
| "Today" from `Instant.now()`, the JVM's zone or the browser's zone | `LocalDate.now(clock.withZone(userZone))` on the server; `src/lib/time.ts` in the browser |
| Treating `PUT` as a patch | An omitted field is cleared. Send every field every time: `version`, `tagIds`, `templateId`, `soundtrack`, `replyToEntryId`, and an Era's `coverMediaId` |
| Fixing a schema error by editing `V1` or changing `ddl-auto` | A new migration (`/new-migration`), with the entity, `DATABASE_SCHEMA.md` and tests in the same change |
| A response record not named `…Response`, or a nullable field without `@Schema(nullable = true)` | The generated TypeScript types would lie. Add the null case to `ApiDocsContractTest` |
| H2 or mocks for a database rule, or trusting a test that passed first time | `@IntegrationTest` (real Postgres). Then break the rule on purpose (drop the seal clause, the lock, the user ID) and watch the test go red |
| Reasoning from memory about a library, a query count or a query plan | Run it, or read the current docs through context7, and quote the output |
| Logging an exception message, search words, entry text or a presigned URL | Log IDs and exception classes only (`ScrubbedException`). A Postgres error message can contain the whole entry |
| Editing files with heredocs, `sed`, `perl` or `python3` | Use the Edit and Write tools. Working-tree files are CRLF, so a scripted edit can match nothing without saying so; read the file back |
| Hand-written API types, `fetch` in a component, or server data in Zustand | `npm run gen:api`, the `openapi-fetch` client and TanStack Query hooks |
| A one-off size, colour or corner in a component (`p-[13px]`, `text-[15px]`, a hex value), or a drawing improvised in JSX | Tokens only (D-40). The limits, and the check to run after each fix, are in `.claude/rules/design.md` |
| Choosing silently when docs disagree, a Q-x is open, or a library isn't in D-13 | Stop and ask. Unattended: take the documented default and list it as an assumption |

## How to work here
- Start each sprint task with `/implement-task <id>` in a fresh session (or after `/clear`). One task per session. It plans first and waits for approval.
- Read narrowly: the task's block, the doc sections it names, and the D-entries those mention. Then read the code you will change before you change it.
- Write tests with the change and run them before saying it's done. Report failures plainly, with output.
- Ask before adding a dependency, deviating from the docs, or resolving a conflict between docs.
- Ask the user to decide things as a short list of interactive questions (AskUserQuestion): each option with its trade-offs, and a recommended default based on stated reasons, not guesses. Record the answer with `/record-decision`.
- When an endpoint, table or decision changes, update its doc in the same change (`/sync-docs` checks the code against the API and schema docs). After the README changes, run `/sync-readme`.
- Commit only when asked. Use one commit per task, with the message `Sprint <id>: <summary>` and no attribution lines. Never push to `main` and never force-push.
- Spring Boot 4, Hibernate 7, Jackson 3, React 19, Tailwind v4 and TipTap 3 are newer than many examples. Check current docs (the context7 MCP) before using an API you're unsure of.

## Before saying a task is done
1. Every **Done when** item in the task's block was checked by running something, and the decisive output is quoted.
2. Backend changed: `./gradlew spotlessApply`, then `./gradlew build` ends in `BUILD SUCCESSFUL` (Docker running). Frontend changed: `npm test && npm run lint && npm run typecheck && npm run build` all pass.
3. Any new list, count, filter or lookup of entries has a test with a sealed entry, a Recently deleted entry and another user's entry in the data.
4. The test for the main rule was seen to fail when the rule was removed.
5. Docs changed with the code: `API_CONTRACT.md`, `DATABASE_SCHEMA.md`, `DECISIONS.md`, `TECH_STACK.md`, and `DEVELOPMENT.md` for a command. `/sync-docs` was run after endpoint or schema work.
6. A screen passes `node docs/design/explorations/tools/audit.mjs <url>` (it loads the page at 375 px and 1440 px), and was looked at in light and dark, used by keyboard, and tried with reduced motion.
7. No new dependency without approval; new environment variables are in `.env.example`; `git status` shows only files this task meant to change.
8. The `spec-reviewer` subagent reviewed the change, and its blocker and major findings are fixed.
9. The task is ticked in `docs/SPRINT_PLAN.md` and the Current focus line above names the next task.
10. The summary says what changed, what was verified and how, what could not be run, and each practice block (file and line).

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
- `/ste [question | rewrite text | 90 question]`: answer or rewrite in "STE-80" (80% of ASD-STE100) with a learning frame: why, syntax to recall, design and review views, self-test.
- `/log-learning [notes]`: add an entry to `docs/LEARNING_LOG.md` and update its skills tracker.
- `spec-reviewer` subagent: read-only review of a change against the README, specs and rules. Use it after each task.
- Hooks: a PreToolUse hook blocks edits to committed migrations; a Stop hook flags docs that lag behind the code, the README or the sprint plan; a SessionStart hook prints the next task and anything blocking it.
- context7 MCP (`.mcp.json`): current library docs.
