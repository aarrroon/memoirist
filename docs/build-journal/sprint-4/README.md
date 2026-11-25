# Sprint 4: The React UI

> **Tasks:** 4.1 to 4.17 ([SPRINT_PLAN](../../SPRINT_PLAN.md#sprint-4-react-ui)) · **Branch:** `phase-1-sprint-4`, stacked on `phase-1-sprint-3` · **Built:** 2026-11-24 22:17 to 2026-11-25 19:40 (Melbourne time, by the commit timestamps), in one unattended overnight run that paused three times at the usage limit · **Status:** not finished. Tasks 4.1 to 4.8 are built, reviewed and ticked. Task 4.9 is built and committed but **needs attention** (a design deviation made to pass the audit, and a full `npm test` run that times out under load), so it is not ticked. The run stopped when the weekly usage reached 90%. **The next task is 4.10 (Week summary view).** Tasks 4.10 to 4.17 are left. The last CI run on the branch (4.9's push) was green; CI builds only the backend until task 4.17 adds the frontend job.
> **Built so far:** the `frontend/` app: generated API types and a typed client, the routes and the phone-first shell, the Journal feed with weeks filed under Eras, the entry card (swipe, caption, voice note), capture (write sheet, uploads, draft), voice recording, Loot Drops (seal, travelling star, Opened row, the Loot Drop page), and the Eras, Templates and You screens. Two backend changes came with it: photo sizes (`V2__media_dimensions.sql`, D-17) and `GET /eras/{id}` leaving out empty weeks (D-22).

## The goal
Sprint 3 finished the Phase 1 API: 37 endpoints, documented in `/v3/api-docs`. Sprint 4 builds the screens on top of it, so Phase 1 becomes something a person can use. The look is chosen (D-41, *Starlight refined*, [DESIGN.md](../../design/DESIGN.md)) and every screen must pass the design check (`audit.mjs`) at 375 px and 1440 px, in light and dark. It has seventeen tasks:
- **4.1** scaffolds `frontend/` with the design tokens and fonts; **4.2** adds the typed API client and the data layer; **4.3** adds the routes, the shell and the time utilities. These three are the foundation every screen uses.
- **4.4** builds the feed and **4.5** the entry card inside it (with the photo sizes it needs from the backend).
- **4.6** builds capture, **4.7** adds voice notes to it, **4.8** adds Loot Drops.
- **4.9** builds the Eras, Templates and You screens.
- **Still to do:** 4.10 week summary, 4.11 timeline and tags, 4.12 Recently deleted, 4.13 photo dump and photo dates, 4.14 On this day and Era covers, 4.15 soundtracks, 4.16 Loot Drop extras and the first letter, 4.17 the end-to-end smoke test and the frontend CI job.

What a user gets so far, against the README's Phase 1 list: entries with text, photos, voice notes and diary-page photos; optional titles, tags and templates with a starter gallery; weeks filed automatically under their Eras; Loot Drops sealed until a date; a feed. Not yet: soundtracks in the UI, the photo dump, Era covers in lists, quick unlock dates, replies and the first letter, On this day, the week summary, the timeline and Recently deleted.

CLAUDE.md's frontend rules get their enforcement here. Generated types only: `npm run gen:api` writes `src/api/schema.d.ts`, and every call goes through the `openapi-fetch` client. No `fetch` in a component and no `dangerouslySetInnerHTML`: 4.1's ESLint rules fail the build on both. Sealed Loot Drops never leak (non-negotiable 4): the sealed card reads only `unlockDate` and the memory date, and 4.8's review found and fixed a cache path that would have shown a sealed entry's content (see [Lessons](#lessons)). All dates go through `src/lib/time.ts`, which never uses the browser's zone.

## How the tasks fit together, in build order
| Task | What it needed from the task before | What it gave the tasks after it |
|---|---|---|
| **4.1** Scaffold the frontend | Sprint 3's API on :8080; DESIGN.md's tokens | Vite 8, React 19, TypeScript, Tailwind v4 with only the `@theme` tokens (D-40), the served fonts, ESLint guards for `fetch` and HTML injection, Vitest + Testing Library + MSW, the `/api` proxy |
| **4.2** API client and data layer | 4.1's project; `/v3/api-docs` from Sprint 3 (fixed in 3.17) | `schema.d.ts`, the `api` client (dev header only in dev), `ApiProblem` and `problemMessage()`, `queryKeys`, the `QueryClient` (30 min `staleTime`, retry only network and 5xx), `useMe()`, shared MSW handlers and `renderWithQuery` |
| **4.3** Routing, shell and time | 4.2's `useMe()` (the user's zone) | `router.tsx` with every Phase 1 route, `AppShell` (top bar with the moon line, tab bar with Write, side nav), `useUiStore`, `lib/time.ts` (TZDate in the user's zone), `cn()` |
| **4.4** Feed | 4.2's client and keys; 4.3's shell and time | `useFeed` (infinite query over `GET /feed`), `grouping.ts` (D-22 filing and nesting, `isInside`), `WeekSection`, a first `FeedEntry`, a text-only `SealedCard`, `Button`, the loading, empty and error states |
| **4.5** Entry card | 4.4's `FeedEntry`; the backend's media upload (3.6) | Backend: `V2__media_dimensions.sql`, `ImageSize` (PNG, JPEG, WebP headers), `width` and `height` on `MediaResponse`. Frontend: `Swipe` (Embla, framed by `frameRatio`), `RichText` (TipTap JSON as React, `safeHref`), the first `VoiceNote` row |
| **4.6** Capture modal | 4.3's Write menu and `useUiStore.capture`; 4.5's `RichText` and media types | `CaptureHost` (lazy Vaul sheet or dialog), `CaptureForm`, the TipTap editor, template, date and tag chips, `MediaTray`, the zod-checked draft, `saveEntry` (create with a client id → upload → order, with partial failure), `?write=entry` for the audit |
| **4.7** Voice notes | 4.6's tray and `saveEntry`; 4.5's `VoiceNote` | `useRecorder` (a reducer state machine over `MediaRecorder`), live and saved 32-bar levels, `picturesFirst` (D-24 order), a keyboard-operable player, `audioDuration` for Chrome's WebM |
| **4.8** Loot Drops in the UI | 4.4's `SealedCard`; 4.6's capture; 4.3's time | The travelling star SVG, `starPath`, the Loot Drop helpers in `time.ts` (`unlockInstant` at 09:00 in the user's zone), `api/lootDrops.ts` (`useEntry`, `useOpened`, `useSeal`, `useMarkSeen`, `useDeleteEntry`, `afterSeal`), `SealPanel`, the `/entries/:entryId` page, the Opened row, `useUnlockWatch` |
| **4.9** Eras, templates and settings | 4.4's `isInside`; 4.8's dialog patterns | Backend: `GET /eras/{id}` leaves out weeks with no readable entry (D-22). Frontend: `api/eras.ts`, template mutations, `useUpdateMe`, `/eras`, `/eras/:eraId`, `FormDialog`, `/templates` with the starter gallery, `/settings` with Appearance (`useSyncExternalStore`) |

## Task entries
| Task | Commit | Journal entry | Spec review |
|---|---|---|---|
| 4.1 Scaffold the frontend | [`2127e2c`](https://github.com/aarrroon/memoirist/commit/2127e2ceff97acc2297a63d345c8eabf7f299ebf), [`9950b38`](https://github.com/aarrroon/memoirist/commit/9950b380c799d2d87965e56e06681d7c8f91ba3f) (fonts) | [4.1 Scaffold the frontend](4.1-scaffold-frontend.md) | Changes needed: 1 major (no task owned the fonts; fixed by serving them) and 9 minor, all fixed |
| 4.2 API client and data layer | [`79e0976`](https://github.com/aarrroon/memoirist/commit/79e0976b4da19c43c3eff124cd0e17ff68cb687a) | [4.2 API client and data layer](4.2-api-client-data-layer.md) | Pass with notes: 2 minor (two error sentences named one cause), fixed |
| 4.3 Routing, shell and time utilities | [`d35d893`](https://github.com/aarrroon/memoirist/commit/d35d893fe80f80ed23676c4368ff9335cccaca09) | [4.3 Routing, shell and time utilities](4.3-routing-shell-time.md) | Pass with notes: 5 minor (top bar off the column at tablet width), fixed |
| 4.4 Feed | [`11bc81d`](https://github.com/aarrroon/memoirist/commit/11bc81de6f805d6b30362dcca89842c94501c0ba) | [4.4 Feed](4.4-feed.md) | Pass with notes: 5 minor, 4 fixed; the entry title link waits for an entry route |
| 4.5 Entry card | [`801b8ef`](https://github.com/aarrroon/memoirist/commit/801b8ef3fbc24ac4eaadda40fae280be29f92f7f) | [4.5 Entry card](4.5-entry-card.md) | Changes needed, then pass with notes: 8 findings; 2 major (lint failing, a repeated *Voice note* label) fixed |
| 4.6 Capture modal | [`224b600`](https://github.com/aarrroon/memoirist/commit/224b600640db23bff9f68f61ff6e53dd12320922) | [4.6 Capture modal](4.6-capture-modal.md) | Changes needed, then pass with notes: 11 findings; 2 major (a restored draft lost its photos from the order; stale TECH_STACK prose) fixed |
| 4.7 Voice notes | [`0358647`](https://github.com/aarrroon/memoirist/commit/0358647c8857e1a50ceeb1c5b0f3a81f40d76827) | [4.7 Voice notes](4.7-voice-notes.md) | Changes needed, then pass with notes: 6 findings; 1 major (voice notes not kept after pictures, D-24) fixed |
| 4.8 Loot Drops in the UI | [`31939b1`](https://github.com/aarrroon/memoirist/commit/31939b190ec055f0157ed80ffeda1961aab35ec6) | [4.8 Loot Drops in the UI](4.8-loot-drops-ui.md) | Changes needed twice: 12 findings; 2 blockers (a sealed entry's full copy left in the cache, D-10; a flaky full test run) fixed |
| 4.9 Eras, templates and settings pages | [`af2aa96`](https://github.com/aarrroon/memoirist/commit/af2aa960af61a7b699776422790e31623e6fe960) | [4.9 Eras, templates and settings pages](4.9-eras-templates-settings.md) | Changes needed, then pass with notes: 9 findings; 1 blocker (the audit's image check failed at 1440 px) worked around by stacking the cover, which **waits for your decision** |

The sprint's other commits are docs and setup: `6e32149` (the overnight prompt), `25f3c62` (the Sprint 4 decisions D-17, D-22, D-26, D-32, D-36, D-42), `85206fd` (the Sprint 4 libraries, D-13) and `7343a9b` (the overnight report).

## The frontend at the end of 4.9
Drawn from `src/router.tsx`, `src/api/*.ts`, `src/stores/ui.ts` and `src/features/capture/saveEntry.ts`. Solid lines are calls; dotted lines are cache invalidations.

```mermaid
flowchart LR
  subgraph Routes["router.tsx (AppShell layout)"]
    R1["/ FeedPage"]
    R2["/entries/:entryId EntryPage"]
    R3["/eras ErasPage"]
    R4["/eras/:eraId EraPage"]
    R5["/templates TemplatesPage"]
    R6["/settings SettingsPage"]
    R7["/weeks/:weekId, /entries, /tags,<br/>/recently-deleted (placeholders, 4.10 to 4.12)"]
  end

  subgraph Shell["components/shell"]
    WM["WriteMenu"]
    CH["CaptureHost (lazy CaptureView:<br/>Vaul sheet or dialog)"]
  end

  subgraph Zustand["Zustand (UI state only)"]
    UI["useUiStore.capture<br/>'entry' | 'dump' | 'page' | null"]
  end

  subgraph Local["Browser storage (per device)"]
    DR["capture draft (zod-checked)"]
    AP["appearance (useSyncExternalStore)"]
  end

  subgraph Query["TanStack Query hooks and their keys"]
    Q1["useMe / useUpdateMe<br/>[memoirist, me]"]
    Q2["useFeed (infinite)<br/>[memoirist, feed]"]
    Q3["useEntry / useSeal / useDeleteEntry<br/>[memoirist, entries, id]"]
    Q4["useOpened / useMarkSeen (optimistic)<br/>[memoirist, opened]"]
    Q5["useEras, useEra, create/update/delete<br/>[memoirist, eras] and [memoirist, eras, id]"]
    Q6["useTemplates + mutations<br/>[memoirist, templates]"]
    Q7["useTags / useCreateTag<br/>[memoirist, tags]"]
    SE["saveEntry (plain async, one mutation)"]
  end

  subgraph Client["api/client.ts (openapi-fetch + schema.d.ts)"]
    C["api.GET/POST/PUT/PATCH/DELETE<br/>middleware: X-Dev-User-Id in dev only;<br/>no response → ApiProblem 'network'"]
    P["problem.ts: unwrap() → ApiProblem"]
  end

  API[("Spring Boot API :8080<br/>/api/v1 (Vite proxies /api)")]

  WM -->|openCapture| UI
  UI --> CH
  CH --> DR
  CH --> SE
  CH --> Q6
  CH --> Q7
  R6 --> AP

  R1 --> Q2
  R1 --> Q4
  R2 --> Q3
  R3 --> Q5
  R4 --> Q5
  R5 --> Q6
  R6 --> Q1
  Shell --> Q1

  Q1 --> C
  Q2 --> C
  Q3 --> C
  Q4 --> C
  Q5 --> C
  Q6 --> C
  Q7 --> C
  SE --> C
  C --> P
  C --> API

  SE -.->|feed, tags| Q2
  Q3 -.->|afterSeal: feed, opened| Q2
  Q5 -.->|eras, feed| Q2
  Q1 -.->|timezone change: all| Q2
```

The endpoints behind each hook:

| Hook or function | Endpoint | Key |
|---|---|---|
| `useMe`, `useUpdateMe` | `GET`, `PATCH /users/me` | `me`; a change invalidates `all`, because the zone changes every date |
| `useFeed` | `GET /feed?page&size=10` | `feed` (all pages under one key) |
| `useEntry`, `useDeleteEntry` | `GET`, `DELETE /entries/{id}` | `entries, id` |
| `useSeal` | `POST /entries/{id}/seal` | writes the redacted answer into `entries, id` (`afterSeal`), invalidates `feed` and `opened` |
| `useOpened`, `useMarkSeen` | `GET /entries/opened`, `POST /entries/{id}/mark-seen` | `opened`, optimistic with rollback |
| `useEras`, `useEra`, `useCreateEra`, `useUpdateEra`, `useDeleteEra` | `GET`, `POST /eras`, `GET`, `PUT`, `DELETE /eras/{id}` | `eras`, `eras, id`; writes invalidate `eras` and `feed` |
| `useTemplates` and its mutations | `GET`, `POST /templates`, `PUT`, `DELETE /templates/{id}` | `templates` |
| `useTags`, `useCreateTag` | `GET`, `POST /tags` | `tags` |
| `saveEntry` (capture) | `POST` or `PUT /entries/{id}`, `POST /entries/{id}/media`, `DELETE /media/{id}`, `PUT /entries/{id}/media/order` | invalidates `feed` and `tags` on success |

Three rules hold the picture together. **Server data lives only in the Query cache**: `useUiStore` holds which capture view is open and nothing else (D-13). **Every key comes from `queryKeys.ts`**, so one prefix invalidates a family. **Every failure is an `ApiProblem`**: `unwrap()` turns a problem+json into a typed error, and `problemMessage()` gives the one sentence a screen shows; the server's `detail` never reaches the screen.

## Screens
Each pair is 375 px, light then dark, against the local backend with its sample content (`--memoirist.sample-content=true`). The screenshots are in [`screens/`](screens/).

**4.2 Starter page:** the 4.1 starter greets the current user through `useMe()`.

<img src="screens/4.2-starter-light.png" width="240" alt="Starter page, light"> <img src="screens/4.2-starter-dark.png" width="240" alt="Starter page, dark">

**4.3 Journal with the shell:** the moon date line, the tab bar with Write in its centre, and a placeholder body.

<img src="screens/4.3-journal-light.png" width="240" alt="Journal shell, light"> <img src="screens/4.3-journal-dark.png" width="240" alt="Journal shell, dark">

**4.3 Week placeholder:** an inner screen, with a back link to Journal in the top bar.

<img src="screens/4.3-week-light.png" width="240" alt="Week placeholder, light"> <img src="screens/4.3-week-dark.png" width="240" alt="Week placeholder, dark">

**4.3 You:** the first You screen, with rows to Tags, Templates and Recently deleted.

<img src="screens/4.3-you-light.png" width="240" alt="You placeholder, light"> <img src="screens/4.3-you-dark.png" width="240" alt="You placeholder, dark">

**4.4 Feed:** weeks under their Era, the sticky Era and week line, entries with the first photo in a 4:5 frame.

<img src="screens/4.4-feed-light.png" width="240" alt="Feed, light"> <img src="screens/4.4-feed-dark.png" width="240" alt="Feed, dark">

**4.4 Feed states (light only):** a text-only sealed card, a nested Era week (*Tasmania in Uni Days*), the empty state, and the error state.

<img src="screens/4.4-feed-sealed-light.png" width="180" alt="Sealed card"> <img src="screens/4.4-feed-nested-light.png" width="180" alt="Nested week"> <img src="screens/4.4-feed-empty-light.png" width="180" alt="Empty feed"> <img src="screens/4.4-feed-error-light.png" width="180" alt="Feed error">

**4.5 Entry card:** the swipe framed by the first photo's shape, dots, the caption from TipTap JSON, and the voice note row.

<img src="screens/4.5-feed-light.png" width="240" alt="Entry card, light"> <img src="screens/4.5-feed-dark.png" width="240" alt="Entry card, dark">

**4.6 Write:** the capture sheet: title, editor, template and date chips, tags, and the add bar.

<img src="screens/4.6-write-light.png" width="240" alt="Capture sheet, light"> <img src="screens/4.6-write-dark.png" width="240" alt="Capture sheet, dark">

**4.7 Capture with a voice note:** a recorded note in the tray, with its saved bars.

<img src="screens/4.7-capture-light.png" width="240" alt="Capture with voice note, light"> <img src="screens/4.7-capture-dark.png" width="240" alt="Capture with voice note, dark">

**4.7 Recording:** live loudness bars and the outlined Stop button.

<img src="screens/4.7-recording-light.png" width="240" alt="Recording, light"> <img src="screens/4.7-recording-dark.png" width="240" alt="Recording, dark">

**4.7 Feed with a voice note:** the keyboard-operable player in an entry.

<img src="screens/4.7-feed-light.png" width="240" alt="Feed voice note, light"> <img src="screens/4.7-feed-dark.png" width="240" alt="Feed voice note, dark">

**4.8 Loot Drop page:** the travelling star, the opening date said once, the countdown, Extend and Delete.

<img src="screens/4.8-loot-drop-light.png" width="240" alt="Loot Drop page, light"> <img src="screens/4.8-loot-drop-dark.png" width="240" alt="Loot Drop page, dark">

**4.8 Extend panel:** the phone's bottom panel to move the opening date later.

<img src="screens/4.8-extend-panel-light.png" width="240" alt="Extend panel, light"> <img src="screens/4.8-extend-panel-dark.png" width="240" alt="Extend panel, dark">

**4.8 Opened row:** a Loot Drop that has opened, at the top of the feed, with Dismiss.

<img src="screens/4.8-feed-opened-light.png" width="240" alt="Opened row, light"> <img src="screens/4.8-feed-opened-dark.png" width="240" alt="Opened row, dark">

**4.8 Sealed card:** the 112 px card in the feed, with the star column, opening date and live countdown.

<img src="screens/4.8-sealed-card-light.png" width="240" alt="Sealed card, light"> <img src="screens/4.8-sealed-card-dark.png" width="240" alt="Sealed card, dark">

**4.9 Eras:** the Eras list, an Era inside another indented under it, each with its initial.

<img src="screens/4.9-eras-light.png" width="240" alt="Eras list, light"> <img src="screens/4.9-eras-dark.png" width="240" alt="Eras list, dark">

**4.9 Era page:** the title band with name, dates, length and counts.

<img src="screens/4.9-era-light.png" width="240" alt="Era page, light"> <img src="screens/4.9-era-dark.png" width="240" alt="Era page, dark">

**4.9 Era weeks:** the weeks as a contents list, and *Eras inside this one*.

<img src="screens/4.9-era-weeks-light.png" width="240" alt="Era weeks, light"> <img src="screens/4.9-era-weeks-dark.png" width="240" alt="Era weeks, dark">

**4.9 Era form:** the one form for New and Edit, with *Show first in the feed* and Delete.

<img src="screens/4.9-era-form-light.png" width="240" alt="Era form, light"> <img src="screens/4.9-era-form-dark.png" width="240" alt="Era form, dark">

**4.9 Era cover on a laptop (1440 px):** the cover stacked above the band, made unattended and approved by the user on 2026-11-25 (D-36, DESIGN.md §4; see [Lessons](#what-to-look-at-first-in-review)).

<img src="screens/4.9-era-cover-laptop-light.jpg" width="480" alt="Era cover on a laptop, light"> <img src="screens/4.9-era-cover-laptop-dark.jpg" width="480" alt="Era cover on a laptop, dark">

**4.9 Templates:** your templates and the starter gallery with Add.

<img src="screens/4.9-templates-light.png" width="240" alt="Templates, light"> <img src="screens/4.9-templates-dark.png" width="240" alt="Templates, dark">

**4.9 You:** display name, timezone, birthday, Appearance, and the rows with counts.

<img src="screens/4.9-you-light.png" width="240" alt="You, light"> <img src="screens/4.9-you-dark.png" width="240" alt="You, dark">

## Test strategy
### The layers
| Layer | Tool | What it proves | Where |
|---|---|---|---|
| Pure logic | Vitest, fixed instants and zones | Time in the user's zone (`time.test.ts`: both Melbourne daylight-saving changes, New York, Kolkata, London, UTC, and a `vi.stubEnv('TZ')` test proving the machine's zone is never read), D-22 filing (`grouping.test.ts`), `frameRatio`, `safeHref`, `starPath`, `eraLength`, the recorder reducer, the draft schema | `src/lib/*.test.ts`, `src/features/*/*.test.ts` |
| Components | Vitest + React Testing Library + MSW | Each screen against fake API answers: loading, empty, error, paging, saving, focus after a dialog closes, keyboard use. MSW answers the real paths, so the generated client is exercised end to end in Node | `src/**/*.test.tsx`, handlers in `src/test/msw/` |
| Sealed entries (D-10) | The same, with a sealed entry in the data | `SealedCard.test.tsx` (the card reads only the allowed fields), `FeedPage.test.tsx` (a sealed entry is never counted), `LootDrop.test.tsx` (seal, extend, the page shows nothing sealed), and `CaptureView.test.tsx`'s *sealing a restored saved entry leaves no whole copy in the cache* (fails with `afterSeal` disabled) | listed |
| Backend | JUnit + Testcontainers (Sprint 3's set) | 4.5's `ImageSize` and V2 constraint; 4.9's `EraWeeksTest.onlyWeeksWithAnEntryToShowAreListed` with a sealed, a deleted, an out-of-range and another user's entry | `backend/src/test/java/` |
| Real browser | Playwright scripts against the local backend | Keyboard order and focus rings, reduced motion (no running animations), infinite scroll, a real save (`POST 201`, uploads `201`, order `200`), a fake microphone recording played back from SeaweedFS | scratch scripts, kept out of the repo; the committed suite is task 4.17 |
| Design | `audit.mjs` | Tokens only, tap targets, type sizes, contrast, layout shift, images no more than 2x their drawn size, the slow-phone first paint, at 375 and 1440 px in light and dark | `docs/design/explorations/tools/audit.mjs` |

Each task broke its main rule on purpose and watched the test go red: the dev header forced on and the retry rule flipped (4.2), `todayIn` in the browser's zone (4.3), `safeHref` and the frame clamp (4.5), the client id dropped from `POST` (4.6), `picturesFirst` disabled (4.7), `afterSeal` and the focus return removed (4.8), the Era `PUT`'s soundtrack dropped and the empty-week rule removed on the backend (4.9).

### How it grew
| After | Frontend tests | Feed JS (gzip) |
|---|---|---|
| 4.1 | 40 | 67 kB |
| 4.2 | 73 | 82.5 kB |
| 4.3 | 131 | 163.8 kB |
| 4.4 | 165 | 168.5 kB |
| 4.5 | 195 (backend 1621) | 178.3 kB |
| 4.6 | 243 | 180.3 kB (capture is a lazy 220 kB chunk) |
| 4.7 | 273 | under 200 kB |
| 4.8 | 311 | 187.3 kB |
| 4.9 | 340 | under 200 kB |

The budget is 200 kB for the feed's first JavaScript (design.md §6); React Router, tailwind-merge, date-fns and Radix took most of it in 4.3, and capture and the seal panel are lazy-loaded to stay under it.

### Not covered yet
- **No committed Playwright suite.** The browser checks were scratch scripts; task 4.17 adds the end-to-end smoke test (USER_FLOWS `F-xx` names) and the frontend CI job.
- **Safari and iOS** were not tested (the `audio/mp4` recording path, `<use href>` with `?no-inline`).
- **The full `npm test` run is not reliable under load** on this machine (see [Lessons](#what-recurred)).

## How to run everything
Git Bash, from the repo root. The full list, with ports and troubleshooting, is [DEVELOPMENT.md](../../DEVELOPMENT.md). Gradle needs JDK 25 in `JAVA_HOME`; set it in the same command if the session's value is stale.

```bash
docker compose up -d                                                      # Postgres and SeaweedFS
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'   # API on :8080 (reads ../.env, so start it in backend/)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local --memoirist.sample-content=true'   # the same, filling an empty dev journal
cd backend && ./gradlew spotlessApply && ./gradlew build                  # what CI runs (2 to 9 minutes)
cd frontend && npm run dev                                                # :5173, proxies /api to :8080
cd frontend && npm test && npm run lint && npm run typecheck && npm run build   # every frontend gate
cd frontend && npm run gen:api                                            # regenerate src/api/schema.d.ts (backend running)
cd frontend && npm run build && npm run preview                           # the production build on :4173, for audit.mjs --slow
node docs/design/explorations/tools/audit.mjs http://localhost:5173/      # the design check; add --dark, --slow, --out <folder>
node docs/design/explorations/tools/audit.mjs http://localhost:5173/?write=entry   # capture opens from a link (?write=page for Diary page)
gh run list --workflow ci.yml --branch phase-1-sprint-4 --limit 3         # CI (backend job only until 4.17)
```

New in this sprint:
- **The frontend acts as the dev user.** In `npm run dev` the client sends `X-Dev-User-Id` with the seeded user's id; the production bundle contains neither the id nor the header name (4.2).
- **Measure speed on the production build**, not the dev server: the dev server sends about 16 MB of unbundled modules, so `--slow` against :5173 measures the tool (4.4, 4.5).
- **The first time capture opens on the dev server**, Vite may re-optimise TipTap and show *Failed to fetch dynamically imported module* once; reload (4.6).
- **Run the full test suite with the servers stopped.** With `bootRun`, Vite and Chromium running, `findBy` lookups time out at random.

## Lessons
### What went wrong
- **A sealed entry could have leaked through the cache (4.8, blocker).** Sealing an entry from capture left the whole entry, title and text, under `queryKeys.entry(id)`; the Loot Drop page would have read it. `EntryMapper` makes the server safe, but a client cache is a second copy. Fixed with `afterSeal`, which writes the server's redacted answer over the cached one, and a test that fails without it.
- **A restored draft lost its photos (4.6, major).** After a reload, a saved entry's draft started with an empty tray, so `PUT /media/order` listed too few ids (400) and an entry with no text could be erased with its photos. The order now comes from the server's media list, and the restore reads the entry back before Save is enabled.
- **Voice notes broke the entry's name (4.7, major).** Record, then add a photo, and the voice note was first in the order, so D-24 named a photo entry "Voice note". `picturesFirst` fixes the order in state and again before the save.
- **Real browsers found bugs that component tests could not (4.6, 4.8).** Photos lost when the window crossed `lg`, focus left behind the sheet, no focus ring on the editor, a disabled Save that looked enabled, focus falling to `<body>` after a Radix dialog with no trigger closed. jsdom has no layout and no real focus management; the Playwright scripts caught all of them.
- **The usage limit stopped three tasks mid-run (4.4, 4.6, 4.8)**, and Docker Desktop crashed after a restart (4.7: a broken socket folder was renamed, nothing deleted). Each resumed from the working tree; the journal entries say what the interrupted attempt had already done.
- **A doc conflict (4.5):** SPRINT_PLAN put an audio player in the swipe; D-41 and DESIGN.md put the voice note in a row under the text. D-27 says DECISIONS wins on how, so the swipe holds pictures only.

### What recurred
- **Full-suite test timeouts (4.6, 4.7, 4.8, 4.9).** Lazy-loaded modules (`CaptureView`, `SealPanel`) and the 1 s `findBy` default don't fit when the machine is busy. 4.8 made the run reliable by importing lazy modules in `beforeAll` and holding fake answers on promises; 4.9's new tests brought it back (`6 failed | 334 passed`, then `1 failed | 339 passed` with `--maxWorkers=1`). The likely fix is a longer `asyncUtilTimeout` in `test/setup.ts` or fewer Vitest workers, a config change that needs your approval.
- **Q29: the API serves original photos.** 4.4 measured the first photo at 2.7 s on a throttled phone with 34 KB samples; 4.5 measured a real 3.3 MB photo at 19.2 s; 4.9's Era cover was 3x its drawn width at 1440 px and failed the audit. Every screen that shows a photo small will hit this until there are smaller copies.
- **The first screen (D-25) moves with the sample data.** The first photo's position was 651 px (4.4), 809 px (4.7, after a test entry with no photo) and 905 px (4.8, with the Opened row). The rule itself holds for a photo-first week (253 px, limit 284 px); task 4.14 must measure it again.
- **The generated request types don't allow `null`** (4.6, 4.9). Fields are left out instead, which the server reads as none. Sending explicit nulls needs `@Schema(nullable = true)` on the request records and a `gen:api` run.
- **Missing copy and missing layouts.** No doc gave error sentences, placeholder text, form layouts or laptop versions of some panels; each task wrote its own and listed it as an assumption (70 in all, [OVERNIGHT_REPORT S4 Assumptions](../../OVERNIGHT_REPORT.md#s4-assumptions)).
- **Skipped dependency:** wavesurfer.js isn't installed (D-13, "added when needed"), so 4.5 and 4.7 built a plain player and drew bars only for notes recorded in this capture.

### What to look at first in review
1. `frontend/src/api/lootDrops.ts` (`afterSeal`, `useMarkSeen`) and `features/feed/SealedCard.tsx`: the client side of D-10.
2. `frontend/src/features/capture/saveEntry.ts`: the multi-step save, what fails per item and what escapes, and the order from the server's media list.
3. `frontend/src/api/client.ts` and `problem.ts`: the dev header only in dev, and the one place an error body is parsed.
4. `frontend/src/components/RichText.tsx` and `lib/links.ts`: TipTap JSON rendered as React elements, links checked again.
5. `frontend/src/lib/time.ts`: every date in the user's zone, `unlockInstant` at 09:00.
6. Backend: `V2__media_dimensions.sql` and `media/ImageSize.java` (4.5); `EraService.get` and `EraWeeksTest` (4.9).
7. `frontend/src/features/eras/EraPage.tsx`: the cover stacked above the band on a laptop, a deviation from DESIGN.md §4 made unattended. The user kept it on 2026-11-25, and DESIGN.md §4 and D-36 now say so.
8. `frontend/src/test/setup.ts` and the `beforeAll` lazy imports: why the full run is slow and how it was made to pass.

## Open items carried into 4.10
- **4.9 still needs attention:** the stacked Era cover was approved on 2026-11-25 (D-36), but the full `npm test` run still times out on 1 to 6 tests under load. Once that is fixed, tick 4.9 in SPRINT_PLAN and move the Current focus line in CLAUDE.md to 4.10.
- **4.9 needs approval:** a test config change to make the full `npm test` run reliable.
- **Follow-ups named for later tasks:** `SealedCard`'s `showMemoryDate` for 4.10 and 4.11; the delete offer when the last photo is removed (D-23) and Recently deleted's count for 4.12; a gentler shrink for diary pages and reading EXIF before shrinking for 4.13; Era covers in lists and an initial in place of a missing cover for 4.14; whole (`contain`) photos in the entry view.
- **Approvals from earlier tasks:** the computed moon (4.3, design.md §5), `src/features/deleted/` in `.claude/rules/frontend.md` (4.3), voice notes fixed after the pictures vs F-03's drag-everything wording (4.7).
- The full list is [OVERNIGHT_REPORT S4 Needs attention](../../OVERNIGHT_REPORT.md#s4-needs-attention).
