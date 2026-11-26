# Sprint 4: The React UI

> **Tasks:** 4.1 to 4.17 ([SPRINT_PLAN](../../SPRINT_PLAN.md#sprint-4-react-ui)) · **Branch:** `phase-1-sprint-4`, stacked on `phase-1-sprint-3` · **Built:** 2026-11-24 22:17 to 2026-11-26 17:45 (Melbourne time, by the commit timestamps), in one unattended run over two nights that paused at the usage limit several times · **Status:** every task is built and committed. Fifteen of seventeen are ticked. **4.10 and 4.14 need attention:** both pass every check except the design audit's `images` line, because the week thumbnails and the feed's 32 px Era cover are the original photos (Q29, open). They wait on your Q29 decision. CI on the last push ([run 45770403437](https://github.com/aarrroon/memoirist/actions/runs/45770403437)) was green on both the backend and frontend jobs.
> **Built:** the whole Phase 1 frontend in `frontend/`: generated API types and a typed client, the routes and the phone-first shell, the Journal feed with weeks filed under Eras, On this day and the Opened row, the entry card, capture (write sheet, uploads, draft, voice notes, photo dump, diary pages, soundtracks), Loot Drops (seal with quick dates, the travelling star, replies, the first letter), the Eras, Era, Templates, You, Week, Timeline, Tags and Recently deleted screens, two Playwright smoke tests and a frontend CI job. Backend changes came with it: photo sizes (`V2__media_dimensions.sql`, D-17), `GET /eras/{id}` leaving out empty weeks (D-22), On this day leaving out `dateUnknown` entries (D-32) and a `PUT /eras/{id}` keeping a hidden cover (D-36).

## The goal
Sprint 3 finished the Phase 1 API: 37 endpoints, documented in `/v3/api-docs`. Sprint 4 builds the screens on top of it, so Phase 1 becomes something a person can use. The look is chosen (D-41, *Starlight refined*, [DESIGN.md](../../design/DESIGN.md)) and every screen must pass the design check (`audit.mjs`) at 375 px and 1440 px, in light and dark. It has seventeen tasks:
- **4.1** scaffolds `frontend/` with the design tokens and fonts; **4.2** adds the typed API client and the data layer; **4.3** adds the routes, the shell and the time utilities. These three are the foundation every screen uses.
- **4.4** builds the feed and **4.5** the entry card inside it (with the photo sizes it needs from the backend).
- **4.6** builds capture, **4.7** adds voice notes to it, **4.8** adds Loot Drops.
- **4.9** builds the Eras, Templates and You screens; **4.10** the week summary; **4.11** the Timeline and Tags; **4.12** Recently deleted (and lazy-loads every inner screen).
- **4.13** adds the photo dump and photo dates; **4.14** On this day and Era covers; **4.15** soundtracks; **4.16** quick unlock dates, replies and the first letter.
- **4.17** adds the end-to-end smoke tests and the frontend CI job.

What a user gets, against the README's Phase 1 list: entries with text, photos, voice notes and diary-page photos; optional titles, tags, templates (with a starter gallery) and Spotify soundtracks; the photo dump; weeks filed automatically and joining Eras by date, with Era covers; Loot Drops sealed until a future date, with quick unlock dates, replies and a first letter; a feed with *On this day*, a week summary and an all-entries timeline; Recently deleted for 30 days. Local only, as the seeded dev user. That is the whole Phase 1 list; the two open items are how photos are drawn small (Q29), not missing features.

CLAUDE.md's frontend rules get their enforcement here. Generated types only: `npm run gen:api` writes `src/api/schema.d.ts`, and every call goes through the `openapi-fetch` client. No `fetch` in a component and no `dangerouslySetInnerHTML`: 4.1's ESLint rules fail the build on both. Sealed Loot Drops never leak (non-negotiable 4): every sealed card, bar and row reads only the dates, every count leaves sealed entries out in the code that makes it, and every entry write refreshes the cached screens that could still show the entry whole (see [Lessons](#lessons)). All dates go through `src/lib/time.ts`, which never uses the browser's zone.

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
| **4.8** Loot Drops in the UI | 4.4's `SealedCard`; 4.6's capture; 4.3's time | The travelling star SVG, `starPath`, `unlockInstant` (09:00 in the user's zone), `api/lootDrops.ts` (`useEntry`, `useOpened`, `useSeal`, `useMarkSeen`, `useDeleteEntry`, `afterSeal`, `dropAndRefetch`), `SealPanel`, the `/entries/:entryId` page, the Opened row, `useUnlockWatch` |
| **4.9** Eras, templates and settings | 4.4's `isInside`; 4.8's dialog patterns | Backend: `GET /eras/{id}` leaves out weeks with no readable entry (D-22). Frontend: `api/eras.ts`, template mutations, `useUpdateMe`, `/eras`, `/eras/:eraId`, `FormDialog`, `/templates` with the starter gallery, `/settings` with Appearance (`useSyncExternalStore`); the 5 s `findBy` wait that made the full test run reliable |
| **4.10** Week summary | 4.8's star and `dropAndRefetch`; 4.9's Era links | `/weeks/:weekId` over `GET /weekly-logs/{id}`, `api/weeks.ts`, `summary.ts` (counts with sealed entries filtered out first), `lib/displayTitle.ts` (the backend's display-title rule on the client), the `weeks()` key family that every entry write invalidates |
| **4.11** Timeline and tags | 4.10's display title; 4.8's sealed row rules | `/entries` (infinite `GET /entries`, 50 a page, sticky months, `?tag=` chips), `months.ts`, `/tags` with rename and delete, the `timelines()` and `entries()` families |
| **4.12** Recently deleted | 4.11's Timeline (its foot row); every invalidation so far | Route `lazy` for every screen but the Journal (`createRoutes()`; the feed's first JS 199.4 → 190.5 kB gzip), `/recently-deleted` with Restore and Erase now, `erasureDay`, one `DeleteEntryDialog` (feed menu, entry page, Loot Drop page), the `deleted()` family |
| **4.13** Photo dump and photo dates | 4.6's `saveEntry` and client ids; 4.12's lazy chunks | `PhotoDump.tsx` and `dumpPlan.ts` (one backdated entry per day, 20 photos at most, the retry reuses the client id, D-12), `lib/photoDate.ts` (exifr, EXIF date in the user's zone), the *Date unknown, fix it* badge and `useChangeEntryDate`, shrinking at upload (a diary page more gently) |
| **4.14** On this day and Era covers | 4.8's Opened row; 4.9's Era form; 4.10's weeks | Backend: On this day leaves out `dateUnknown` (D-32), a `PUT` keeps a hidden cover (`EraCovers.isHidden`, D-36). Frontend: `useOnThisDay`, the expanding `OnThisDay` row, the Era line's cover and link, `CoverPicker` with `useEraPhotos` (`useQueries` with `combine`) |
| **4.15** Soundtracks | 4.6's capture; 4.9's Era form; 4.10's week | `lib/spotify.ts` (pasted links to URIs), `useMusicSearch` (`skipToken`, no retry, paste only after `503`), `SoundtrackField` and the tap-to-load `SoundtrackPlayer` (0 Spotify requests before Play), *Edit week* in place of *Rename week* |
| **4.16** Loot Drop extras | 4.8's seal panel and Loot Drop page; 4.6's capture | Quick dates (`quickDays`, `nextBirthday`, `addMonthsClamped` in `lib/time.ts`), Reply to past you (`useUiStore.replyTo`, `useReplies`), the first letter on the empty first-run screen (capture kind `letter`, `lib/firstLetter.ts`) |
| **4.17** E2E and CI | Every screen above; the real stack | `frontend/e2e/` (F-03 and F-06 at 375 and 1440 px, a clean-up fixture, a global setup that checks the backend) and the CI `frontend` job (test, lint, typecheck, build) |

Each task leaned on something the one before it proved:
- 4.8's `afterSeal` (write the server's redacted answer over the cached entry) became the rule for every later cache family: 4.10 found the week page missing from it (blocker), 4.11 added the Timelines, 4.12 Recently deleted, 4.14 On this day.
- 4.6's client id for creates is what let 4.13's photo dump retry a failed day without making a second entry.
- 4.10's `entryDisplayTitle` names rows in 4.11's Timeline, 4.12's Recently deleted and 4.14's On this day.
- 4.11 left the feed's first JS 0.58 kB under its 200 kB budget, which is why 4.12 lazy-loaded the routes before building its screen.
- 4.9's longer `findBy` wait kept the full test run clean from 4.10 to 4.17 locally; CI found one more case (see [What recurred](#what-recurred)).

## Task entries
| Task | Commit | Journal entry | Spec review |
|---|---|---|---|
| 4.1 Scaffold the frontend | [`2127e2c`](https://github.com/aarrroon/memoirist/commit/2127e2ceff97acc2297a63d345c8eabf7f299ebf), [`9950b38`](https://github.com/aarrroon/memoirist/commit/9950b380c799d2d87965e56e06681d7c8f91ba3f) (fonts) | [4.1 Scaffold the frontend](4.1-scaffold-frontend.md) | Changes needed: 1 major (no task owned the fonts; fixed by serving them) and 9 minor, all fixed |
| 4.2 API client and data layer | [`79e0976`](https://github.com/aarrroon/memoirist/commit/79e0976b4da19c43c3eff124cd0e17ff68cb687a) | [4.2 API client and data layer](4.2-api-client-data-layer.md) | Pass with notes: 2 minor (two error sentences named one cause), fixed |
| 4.3 Routing, shell and time utilities | [`d35d893`](https://github.com/aarrroon/memoirist/commit/d35d893fe80f80ed23676c4368ff9335cccaca09) | [4.3 Routing, shell and time utilities](4.3-routing-shell-time.md) | Pass with notes: 5 minor (top bar off the column at tablet width), fixed |
| 4.4 Feed | [`11bc81d`](https://github.com/aarrroon/memoirist/commit/11bc81de6f805d6b30362dcca89842c94501c0ba) | [4.4 Feed](4.4-feed.md) | Pass with notes: 5 minor, 4 fixed; the entry title link waited for an entry route |
| 4.5 Entry card | [`801b8ef`](https://github.com/aarrroon/memoirist/commit/801b8ef3fbc24ac4eaadda40fae280be29f92f7f) | [4.5 Entry card](4.5-entry-card.md) | Changes needed, then pass with notes: 8 findings; 2 major (lint failing, a repeated *Voice note* label) fixed |
| 4.6 Capture modal | [`224b600`](https://github.com/aarrroon/memoirist/commit/224b600640db23bff9f68f61ff6e53dd12320922) | [4.6 Capture modal](4.6-capture-modal.md) | Changes needed, then pass with notes: 11 findings; 2 major (a restored draft lost its photos from the order; stale TECH_STACK prose) fixed |
| 4.7 Voice notes | [`0358647`](https://github.com/aarrroon/memoirist/commit/0358647c8857e1a50ceeb1c5b0f3a81f40d76827) | [4.7 Voice notes](4.7-voice-notes.md) | Changes needed, then pass with notes: 6 findings; 1 major (voice notes not kept after pictures, D-24) fixed |
| 4.8 Loot Drops in the UI | [`31939b1`](https://github.com/aarrroon/memoirist/commit/31939b190ec055f0157ed80ffeda1961aab35ec6) | [4.8 Loot Drops in the UI](4.8-loot-drops-ui.md) | Changes needed twice: 12 findings; 2 blockers (a sealed entry's full copy left in the cache, D-10; a flaky full test run) fixed |
| 4.9 Eras, templates and settings pages | [`af2aa96`](https://github.com/aarrroon/memoirist/commit/af2aa960af61a7b699776422790e31623e6fe960), [`c20c542`](https://github.com/aarrroon/memoirist/commit/c20c54225c18f107bc49b3df161d7ca6a1f6dbf6) (cover kept, D-36), [`a6f379a`](https://github.com/aarrroon/memoirist/commit/a6f379ac75603f5e8baa9a8aa020b24018da9f14) (test fix, ticked) | [4.9 Eras, templates and settings pages](4.9-eras-templates-settings.md) | Changes needed, then pass with notes: 9 findings; 1 blocker (the audit's image check at 1440 px) worked around by stacking the cover, which you approved on 2026-11-25 |
| 4.10 Week summary view | [`ba484ab`](https://github.com/aarrroon/memoirist/commit/ba484aba87d649394d47548eb298bb4362a16a8b) | [4.10 Week summary view](4.10-week-summary.md) | Changes needed twice: 13 findings; 1 blocker (cached week pages never refreshed after a seal or delete, D-10) fixed; the `images` audit line (Q29) **waits for your decision** |
| 4.11 All-entries timeline and tags UI | [`a77e86b`](https://github.com/aarrroon/memoirist/commit/a77e86b86737576f7e6ae06d18a1e816d50f461b) | [4.11 All-entries timeline and tags UI](4.11-timeline-tags.md) | Pass with notes: 3 minor (F-17 wording, journal links), fixed in docs |
| 4.12 Recently deleted screen | [`e2f3924`](https://github.com/aarrroon/memoirist/commit/e2f39243773956119d01ba7a1bfb8574967b7633) | [4.12 Recently deleted screen](4.12-recently-deleted.md) | Pass with notes: 5 minor; a stale card after a `404` delete and the dialog copy fixed; the sealed row's look left for a design decision |
| 4.13 Photo dump and photo dates | [`1bec4f2`](https://github.com/aarrroon/memoirist/commit/1bec4f2423ae2f1450e6a0cda1185d29514aa8c7) | [4.13 Photo dump and photo dates](4.13-photo-dump.md) | Pass with notes: 4 minor; the date offer with an undated photo fixed; D-34's sealed badge wording left for you |
| 4.14 On this day strip and Era covers | [`aea6114`](https://github.com/aarrroon/memoirist/commit/aea611437d89613ea659b890ce66ebea593daa8e) | [4.14 On this day strip and Era covers](4.14-on-this-day-era-covers.md) | Changes needed twice: 9 findings; 1 blocker (the feed's 32 px Era cover fails the `images` line, Q29) **waits for your decision**; the minors fixed or listed |
| 4.15 Soundtrack picker and player | [`b5d97cc`](https://github.com/aarrroon/memoirist/commit/b5d97cc08d6038c14f69da0293593bbad1bca701) | [4.15 Soundtrack picker and player](4.15-soundtrack.md) | Pass with notes: 10 minor; the doubled *Spotify* name and stale comments fixed; the stored-title question left for you |
| 4.16 Loot Drop extras and the first letter | [`cb967ef`](https://github.com/aarrroon/memoirist/commit/cb967ef646ad9d1b0a0da574b50e54b74ad190d1) | [4.16 Loot Drop extras and the first letter](4.16-loot-drop-extras.md) | Pass with notes: 3 minor; a removed reply link coming back from a draft fixed; F-01's wording left for you |
| 4.17 End-to-end smoke test and CI: frontend | [`011911e`](https://github.com/aarrroon/memoirist/commit/011911e0301c2e3b35af10325127026d8d94d635), [`0963651`](https://github.com/aarrroon/memoirist/commit/096365104b1150c762abd366a24bd825baeee8c9) (green CI, ticked) | [4.17 End-to-end smoke test and CI: frontend](4.17-e2e-ci-frontend.md) | Pass with notes twice: 7 findings; 1 major ("CI is green" claimed before any push) fixed by pushing and quoting run 45770081824 |

The sprint's other commits are docs and setup: `6e32149` (the overnight prompt), `25f3c62` (the Sprint 4 decisions D-17, D-22, D-26, D-32, D-36, D-42), `85206fd` (the Sprint 4 libraries, D-13), `7343a9b` (the overnight report), and `9084ce0` and `c54089d` (the first wrap-up after 4.9).

## The frontend at the end of the sprint
Drawn from `src/router.tsx`, `src/api/*.ts`, `src/api/queryKeys.ts`, `src/stores/ui.ts` and `src/features/capture/saveEntry.ts`. Solid lines are calls; dotted lines are cache invalidations. Every key starts with `memoirist`.

```mermaid
flowchart LR
  subgraph Routes["router.tsx: createRoutes() inside AppShell; all but / are route lazy (4.12)"]
    R1["/ FeedPage"]
    R2["/entries TimelinePage (?tag=)"]
    R3["/entries/:entryId EntryPage<br/>(LootDropPage when sealed)"]
    R4["/weeks/:weekId WeekSummaryPage"]
    R5["/eras ErasPage<br/>/eras/:eraId EraPage"]
    R6["/templates TemplatesPage"]
    R7["/tags TagsPage"]
    R8["/settings SettingsPage (You)"]
    R9["/recently-deleted RecentlyDeletedPage"]
  end

  subgraph Shell["components/shell"]
    WM["WriteMenu: New entry, Photo dump, Diary page"]
    CH["CaptureHost (lazy): CaptureForm or PhotoDump,<br/>Vaul sheet on phones, dialog on laptops"]
  end

  subgraph Zustand["Zustand (UI state only)"]
    UI["useUiStore<br/>capture: entry | dump | page | letter | null<br/>replyTo: Loot Drop id | null"]
  end

  subgraph Local["Browser storage (per device)"]
    DR["capture draft (zod-checked)"]
    LS["appearance, first letter skipped"]
  end

  subgraph Query["TanStack Query hooks and their keys"]
    Q1["useMe / useUpdateMe<br/>[me]"]
    Q2["useFeed (infinite)<br/>[feed]"]
    Q3["useOpened / useMarkSeen (optimistic)<br/>[opened]"]
    Q4["useOnThisDay<br/>[on-this-day]"]
    Q5["useEntry, useReplies, useSeal,<br/>useDeleteEntry, useChangeEntryDate<br/>[entries, id]"]
    Q6["useWeek, useEditWeek, useSetWeekClosed<br/>[weeks, id]"]
    Q7["useTimeline (infinite)<br/>[timeline, {tagId}]"]
    Q8["useEras, useEra, mutations, useEraPhotos<br/>[eras], [eras, id]"]
    Q9["useTemplates + mutations<br/>[templates]"]
    Q10["useTags, create, rename, delete<br/>[tags]"]
    Q11["useRecentlyDeleted, useDeletedCount,<br/>useRestoreEntry, useEraseEntry<br/>[deleted, list], [deleted, count]"]
    Q12["useMusicSearch (skipToken)<br/>[music-search, words]"]
    SE["saveEntry (plain async):<br/>create or PUT, upload, order"]
  end

  subgraph Client["api/client.ts (openapi-fetch + schema.d.ts)"]
    C["api.GET/POST/PUT/PATCH/DELETE<br/>X-Dev-User-Id in dev only;<br/>no response → ApiProblem 'network'"]
    P["problem.ts: unwrap() → ApiProblem,<br/>problemMessage()"]
  end

  API[("Spring Boot API :8080<br/>/api/v1 (Vite proxies /api)")]

  WM -->|openCapture| UI
  R3 -->|Reply to past you| UI
  R1 -->|first letter| UI
  UI --> CH
  CH --> DR
  CH --> SE
  CH --> Q9
  CH --> Q10
  CH --> Q12
  R8 --> LS

  R1 --> Q2
  R1 --> Q3
  R1 --> Q4
  R1 --> Q8
  R2 --> Q7
  R3 --> Q5
  R4 --> Q6
  R5 --> Q8
  R6 --> Q9
  R7 --> Q10
  R8 --> Q1
  R8 --> Q11
  R9 --> Q11
  Shell --> Q1

  Query --> C
  C --> P
  C --> API

  SE -.->|feed, weeks, timelines, on-this-day, tags| Q2
  Q5 -.->|afterSeal / dropAndRefetch: feed, weeks,<br/>timelines, opened, on-this-day, deleted| Q2
  Q11 -.->|restore: feed, weeks, timelines, opened, tags, eras| Q2
  Q10 -.->|rename or delete: entries, feed, weeks, timelines| Q2
  Q8 -.->|eras, feed| Q2
  Q1 -.->|timezone change: all| Q2
```

The endpoints behind each hook:

| Hook or function | Endpoint | Key |
|---|---|---|
| `useMe`, `useUpdateMe` | `GET`, `PATCH /users/me` | `me`; a change invalidates `all`, because the zone changes every date |
| `useFeed` | `GET /feed?page&size=10` | `feed` (all pages under one key) |
| `useOpened`, `useMarkSeen` | `GET /entries/opened`, `POST /entries/{id}/mark-seen` | `opened`, optimistic with rollback |
| `useOnThisDay` | `GET /entries/on-this-day` | `on-this-day` |
| `useEntry`, `useReplies`, `useDeleteEntry`, `useChangeEntryDate` | `GET`, `DELETE`, `PUT /entries/{id}` | `entries, id`; `useReplies` is `useQueries` over the reply ids |
| `useSeal` | `POST /entries/{id}/seal` | writes the redacted answer into `entries, id` (`afterSeal`), then drops and refetches the feed, weeks, Timelines, Opened and On this day |
| `useWeek`, `useEditWeek`, `useSetWeekClosed` | `GET`, `PUT /weekly-logs/{id}`, `POST …/close`, `POST …/reopen` | `weeks, id`; writes invalidate the week, the feed and `eras` |
| `useTimeline` | `GET /entries?tagId&page&size=50` | `timeline, {tagId}` under `timelines` |
| `useEras`, `useEra`, `useCreateEra`, `useUpdateEra`, `useDeleteEra`, `useEraPhotos` | `GET`, `POST /eras`, `GET`, `PUT`, `DELETE /eras/{id}`; the photos from `GET /weekly-logs/{id}` | `eras`, `eras, id`; writes invalidate `eras` and `feed` |
| `useTemplates` and its mutations | `GET`, `POST /templates`, `PUT`, `DELETE /templates/{id}` | `templates` |
| `useTags`, `useCreateTag`, `useRenameTag`, `useDeleteTag` | `GET`, `POST /tags`, `PUT`, `DELETE /tags/{id}` | `tags`; a rename or delete also refreshes `entries`, the feed, weeks and Timelines |
| `useRecentlyDeleted`, `useDeletedCount`, `useRestoreEntry`, `useEraseEntry` | `GET /entries/deleted`, `POST /entries/{id}/restore`, `POST /entries/{id}/erase` | `deleted, list`, `deleted, count` under `deleted` |
| `useMusicSearch` | `POST /music/search` (the words in the body, never a URL) | `music-search, words`; `retry: false` |
| `saveEntry` (capture, photo dump) | `POST` or `PUT /entries/{id}`, `POST /entries/{id}/media`, `DELETE /media/{id}`, `PUT /entries/{id}/media/order` | invalidates the feed, weeks, Timelines, On this day and `tags` on success |

Three rules hold the picture together. **Server data lives only in the Query cache**: `useUiStore` holds which capture view is open and which Loot Drop a reply answers, and nothing else (D-13). **Every key comes from `queryKeys.ts`**, so one prefix invalidates a family, and lists that must not refetch with one entry (`opened`, `on-this-day`, `timeline`, `deleted`) sit outside `entries`. **Every failure is an `ApiProblem`**: `unwrap()` turns a problem+json into a typed error, and `problemMessage()` gives the one sentence a screen shows; the server's `detail` never reaches the screen.

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

**4.9 Era cover on a laptop (1440 px):** the cover stacked above the band, made unattended and approved by you on 2026-11-25 (D-36, DESIGN.md §4).

<img src="screens/4.9-era-cover-laptop-light.jpg" width="480" alt="Era cover on a laptop, light"> <img src="screens/4.9-era-cover-laptop-dark.jpg" width="480" alt="Era cover on a laptop, dark">

**4.9 Templates:** your templates and the starter gallery with Add.

<img src="screens/4.9-templates-light.png" width="240" alt="Templates, light"> <img src="screens/4.9-templates-dark.png" width="240" alt="Templates, dark">

**4.9 You:** display name, timezone, birthday, Appearance, and the rows with counts.

<img src="screens/4.9-you-light.png" width="240" alt="You, light"> <img src="screens/4.9-you-dark.png" width="240" alt="You, dark">

**4.10 Week summary:** name, dates and Eras, the stats row, the thumbnails (originals: the Q29 audit failure), tags with counts and one bar per entry, a sealed one star-only.

<img src="screens/4.10-week-light.png" width="240" alt="Week summary, light"> <img src="screens/4.10-week-dark.png" width="240" alt="Week summary, dark">

**4.11 Timeline:** every entry newest first under sticky month headings, with the tag chips that filter.

<img src="screens/4.11-timeline-light.png" width="240" alt="Timeline, light"> <img src="screens/4.11-timeline-dark.png" width="240" alt="Timeline, dark">

**4.11 Tags:** each tag with its count, and Rename and Delete.

<img src="screens/4.11-tags-light.png" width="240" alt="Tags, light"> <img src="screens/4.11-tags-dark.png" width="240" alt="Tags, dark">

**4.12 Recently deleted:** each row's erase day, Restore (or *Restore, still sealed*) and Erase now.

<img src="screens/4.12-recently-deleted-light.png" width="240" alt="Recently deleted, light"> <img src="screens/4.12-recently-deleted-dark.png" width="240" alt="Recently deleted, dark">

**4.12 Delete from the feed:** the more-actions menu's one delete question.

<img src="screens/4.12-feed-delete-light.png" width="240" alt="Delete question, light"> <img src="screens/4.12-feed-delete-dark.png" width="240" alt="Delete question, dark">

**4.12 You with the count:** the Recently deleted row shows how many entries wait there.

<img src="screens/4.12-you-light.png" width="240" alt="You with count, light"> <img src="screens/4.12-you-dark.png" width="240" alt="You with count, dark">

**4.13 Photo dump:** picked photos grouped by their own day, oldest first, ready to file.

<img src="screens/4.13-dump-light.png" width="240" alt="Photo dump, light"> <img src="screens/4.13-dump-dark.png" width="240" alt="Photo dump, dark">

**4.13 Date unknown:** undated photos last, under *Date unknown*, with Change date.

<img src="screens/4.13-dump-unknown-light.png" width="240" alt="Date unknown group, light"> <img src="screens/4.13-dump-unknown-dark.png" width="240" alt="Date unknown group, dark">

**4.13 Feed badge:** *Date unknown, fix it* on an undated entry's card.

<img src="screens/4.13-feed-badge-light.png" width="240" alt="Date unknown badge, light"> <img src="screens/4.13-feed-badge-dark.png" width="240" alt="Date unknown badge, dark">

**4.13 Photo date offer in capture:** *Taken Tue 17 Nov*, with Use this date and Not now.

<img src="screens/4.13-write-date-light.png" width="240" alt="Photo date offer, light"> <img src="screens/4.13-write-date-dark.png" width="240" alt="Photo date offer, dark">

**4.14 Feed with On this day:** the 64 px row at the top and the Era line with its 32 px cover (the original: the Q29 audit failure).

<img src="screens/4.14-feed-light.png" width="240" alt="Feed with On this day, light"> <img src="screens/4.14-feed-dark.png" width="240" alt="Feed with On this day, dark">

**4.14 On this day open:** the row expanded in place, each entry with *N years ago*.

<img src="screens/4.14-on-this-day-open-light.png" width="240" alt="On this day open, light"> <img src="screens/4.14-on-this-day-open-dark.png" width="240" alt="On this day open, dark">

**4.14 Cover picker:** Edit Era's radio tiles, No cover first, then the Era's photos.

<img src="screens/4.14-era-cover-picker-light.png" width="240" alt="Cover picker, light"> <img src="screens/4.14-era-cover-picker-dark.png" width="240" alt="Cover picker, dark">

**4.15 Soundtrack in capture:** the field under the tags, taking a pasted link or search words.

<img src="screens/4.15-capture-light.png" width="240" alt="Soundtrack field, light"> <img src="screens/4.15-capture-dark.png" width="240" alt="Soundtrack field, dark">

**4.15 Entry with a soundtrack:** the row with the Spotify mark, *Song on Spotify* and a quiet Play.

<img src="screens/4.15-entry-light.png" width="240" alt="Entry soundtrack row, light"> <img src="screens/4.15-entry-dark.png" width="240" alt="Entry soundtrack row, dark">

**4.15 Era band with a soundtrack:** the mark in the band's own colour.

<img src="screens/4.15-era-light.png" width="240" alt="Era soundtrack, light"> <img src="screens/4.15-era-dark.png" width="240" alt="Era soundtrack, dark">

**4.15 Edit week (dark only):** one dialog for the name and the soundtrack.

<img src="screens/4.15-week-dialog-dark.png" width="240" alt="Edit week, dark">

**4.16 Seal panel:** the quick dates as radio rows, each showing its day.

<img src="screens/4.16-seal-panel-light.png" width="240" alt="Seal panel, light"> <img src="screens/4.16-seal-panel-dark.png" width="240" alt="Seal panel, dark">

**4.16 Opened Loot Drop:** Reply to past you as the one primary button, and its replies.

<img src="screens/4.16-opened-light.png" width="240" alt="Opened Loot Drop, light"> <img src="screens/4.16-opened-dark.png" width="240" alt="Opened Loot Drop, dark">

**4.16 Reply capture:** headed *Reply to past you*, with *In reply to* and Remove.

<img src="screens/4.16-reply-capture-light.png" width="240" alt="Reply capture, light"> <img src="screens/4.16-reply-capture-dark.png" width="240" alt="Reply capture, dark">

**4.16 A reply's page:** *In reply to* linking back to the Loot Drop.

<img src="screens/4.16-reply-light.png" width="240" alt="Reply page, light"> <img src="screens/4.16-reply-dark.png" width="240" alt="Reply page, dark">

**4.16 First run:** the empty journal ending with *A letter to open in a month*, Write the letter and Skip.

<img src="screens/4.16-first-run-light.png" width="240" alt="First run, light"> <img src="screens/4.16-first-run-dark.png" width="240" alt="First run, dark">

**4.16 The letter:** capture as *Letter to future you*, where Save becomes Seal it.

<img src="screens/4.16-letter-light.png" width="240" alt="First letter, light"> <img src="screens/4.16-letter-dark.png" width="240" alt="First letter, dark">

4.17 built no screen.

## Test strategy
### The layers
| Layer | Tool | What it proves | Where |
|---|---|---|---|
| Pure logic | Vitest, fixed instants and zones | Time in the user's zone (`time.test.ts`: both Melbourne daylight-saving changes, other zones, and a `vi.stubEnv('TZ')` test proving the machine's zone is never read; `unlockInstant`, `erasureDay`, `nextBirthday`, `quickDays`, `yearsAgo`), D-22 filing (`grouping.test.ts`), `frameRatio`, `safeHref`, `spotifyUri`, `starPath`, `photoDay`, `planDump`, `groupByMonth`, `summarise`, `entryDisplayTitle`, the recorder reducer, the draft schema | `src/lib/*.test.ts`, `src/features/*/*.test.ts` |
| Components | Vitest + React Testing Library + MSW | Each screen against fake API answers: loading, empty, error, paging, saving, focus after a dialog closes, keyboard use. MSW answers the real paths, so the generated client is exercised end to end in Node. Uploads use `fakeBackend()` from `test/msw/capture.ts` (jsdom can't stream a multipart `File`) | `src/**/*.test.tsx`, handlers in `src/test/msw/` |
| Sealed entries (D-10) | The same, with a sealed entry in the data | `SealedCard.test.tsx` (only the allowed fields), `FeedPage.test.tsx` (never counted), `LootDrop.test.tsx`, `CaptureView.test.tsx` (*sealing a restored saved entry leaves no whole copy in the cache*), `WeekSummary.test.tsx` (*entry writes refresh a cached week*), `summary.test.ts`, `Timeline.test.tsx`, `RecentlyDeleted.test.tsx` (the redacted row), `OnThisDay.test.tsx`, `FeedEntry.test.tsx` (no badge or soundtrack on a sealed card), `LootDropExtras.test.tsx` (a reply whose Loot Drop was sealed again) | listed |
| Backend | JUnit + Testcontainers (Sprint 3's set) | 4.5's `ImageSize` and V2 constraint; 4.9's `EraWeeksTest`; 4.14's `OnThisDayEndpointTest` and `EraCoverTest`, each with a sealed, a deleted and another user's entry | `backend/src/test/java/` |
| End to end | Playwright against the real stack | F-03 (an entry with a photo: the `201` upload, the photo loads, the API stored its width, the picker leaves HEIC out) and F-06 (seal from capture: a sealed card, the caption nowhere, the API answers `isLocked=true` with no content), at 375×812 and 1440×900; the `journal` fixture erases what each test made | `frontend/e2e/` |
| Real browser, per task | Playwright scratch scripts | Keyboard order and focus rings, reduced motion, infinite scroll, a real save, a fake microphone, 0 Spotify requests before Play | kept out of the repo |
| Design | `audit.mjs` | Tokens only, tap targets, type sizes, contrast, layout shift, images no more than 2x their drawn size, the slow-phone first paint, at 375 and 1440 px in light and dark | `docs/design/explorations/tools/audit.mjs` |

Each task broke its main rule on purpose and watched the test go red: the dev header forced on and the retry rule flipped (4.2), `todayIn` in the browser's zone (4.3), `safeHref` and the frame clamp (4.5), the client id dropped from `POST` (4.6), `picturesFirst` disabled (4.7), `afterSeal` and the focus return removed (4.8), the empty-week rule removed on the backend (4.9), the sealed filter in `summarise` and the week invalidations (4.10), `erasureDay`'s boundary and the restore's refresh (4.12), a fresh client id on each dump retry (4.13), the `dateUnknown` clause and the hidden-cover rule on the backend (4.14), Play starting on (4.15), the 09:00 unlock and the birthday fallback (4.16), HEIC allowed in the picker (4.17).

### How it grew
| After | Frontend tests | Feed's first JS (gzip) |
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
| 4.10 | 370 | 197.2 kB |
| 4.11 | 389 | 199.4 kB |
| 4.12 | 404 | 190.5 kB (routes lazy; Vite's entry chunk alone is 162 kB) |
| 4.13 | 433 | 190.5 kB |
| 4.14 | 448 | not measured |
| 4.15 | 487 | 169.0 kB as the task measured it |
| 4.16 | 513 | entry chunk 169.7 kB |
| 4.17 | 513 (+ 2 Playwright tests × 2 sizes) | unchanged |

The budget is 200 kB for the feed's first JavaScript (design.md §6). 4.12's numbers count the entry chunk plus the shared chunks it imports and preloads; later tasks quoted Vite's entry chunk, so compare like with like.

### Not covered
- **The Playwright smoke tests run locally only.** CI runs the frontend's test, lint, typecheck and build; Playwright needs the backend and both services, which fits the deploy check (5.6).
- **Safari and iOS** were not tested: the `audio/mp4` recording path, iOS's HEIC-to-JPEG conversion (D-17), `<use href>` with `?no-inline`, and the sandboxed Spotify embed.
- **Real Spotify search:** without `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` the server answers `503`, so only the paste-only path was used.
- **The filled photo dump and the phone seal panel** can't be reached by URL, so `audit.mjs` didn't check them; Playwright measured and photographed them.

## How to run everything
Git Bash, from the repo root. The full list, with ports and troubleshooting, is [DEVELOPMENT.md](../../DEVELOPMENT.md). Gradle needs JDK 25 in `JAVA_HOME`; set it in the same command if the session's value is stale.

```bash
docker compose up -d                                                      # Postgres and SeaweedFS
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'   # API on :8080 (reads ../.env, so start it in backend/)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local --memoirist.sample-content=true'   # the same, filling an empty dev journal
cd backend && ./gradlew spotlessApply && ./gradlew build                  # what CI's backend job runs (2 to 9 minutes)
cd frontend && npm run dev                                                # :5173, proxies /api to :8080
cd frontend && npm test && npm run lint && npm run typecheck && npm run build   # what CI's frontend job runs
cd frontend && npm run gen:api                                            # regenerate src/api/schema.d.ts (backend running)
cd frontend && npx playwright test                                        # smoke tests F-03 and F-06 (Compose and bootRun up; starts Vite)
cd frontend && npx playwright test f06 --project=phone                    # one file, one size
cd frontend && npm run build && npm run preview                           # the production build on :4173, for audit.mjs --slow
node docs/design/explorations/tools/audit.mjs http://localhost:5173/      # the design check; add --dark, --slow, --out <folder>
node docs/design/explorations/tools/audit.mjs http://localhost:5173/?write=entry   # capture opens from a link (page, letter too)
gh run list --workflow ci.yml --branch phase-1-sprint-4 --limit 3         # CI (backend and frontend jobs)
```

New in this sprint:
- **The frontend acts as the dev user.** In `npm run dev` the client sends `X-Dev-User-Id` with the seeded user's id; the production bundle contains neither the id nor the header name (4.2).
- **Measure speed on the production build**, not the dev server: the dev server sends about 16 MB of unbundled modules, so `--slow` against :5173 measures the tool (4.4, 4.5).
- **The first time capture opens on the dev server**, Vite may re-optimise TipTap and show *Failed to fetch dynamically imported module* once; reload (4.6).
- **The smoke tests write to the dev journal** and erase what they made, so the sample content can be there or not (4.17).
- **Run the full test suite with the servers stopped** if it is slow: with `bootRun`, Vite and Chromium running, the machine is busy enough to stretch `findBy` waits.

## Lessons
### What went wrong
- **A sealed entry could have leaked through the cache, twice (4.8 and 4.10, blockers).** 4.8: sealing from capture left the whole entry under `queryKeys.entry(id)`. 4.10: a cached week page kept a just-sealed entry's title, and a deleted entry, for up to 30 minutes, because entry writes never invalidated `weeks`. `EntryMapper` makes the server safe, but every client cache is another copy. Both fixed with tests that fail without the fix; 4.11, 4.12 and 4.14 added their own families to the same list.
- **A restored draft lost its photos (4.6, major),** and later a removed reply link came back from one (4.16, minor). A draft and the server both hold a value; each task had to say which wins.
- **Voice notes broke the entry's name (4.7, major).** `picturesFirst` fixes the order before the save (D-24).
- **Real browsers found bugs that component tests could not (4.6, 4.8, 4.12, 4.16).** Photos lost when the window crossed `lg`, focus left behind a sheet or falling to `<body>` after a Radix dialog closed, a disabled Save that looked enabled. jsdom has no layout and no real focus management; the Playwright scripts caught all of them.
- **"Done" claimed on local evidence (4.17, major).** The first close-out marked "CI is green" before anything was pushed. The fix was to push and quote the run.
- **The usage limit stopped tasks mid-run (4.4, 4.6, 4.8, 4.11, 4.14, 4.17).** Each resumed from the working tree, and the journal entries say what the interrupted attempt had already checked. 4.11's audit and keyboard results come from the first attempt only.

### What recurred
- **Q29: the API serves original photos.** 4.4 measured 2.7 s to the first photo on a throttled phone; 4.5 19.2 s for a real 3.3 MB photo; 4.9's Era cover was 3x its width at 1440 px (worked around by stacking it); 4.10's week thumbnails are 7.3x and 7.8x; 4.14's 32 px Era cover 15x and 30x. **This is why 4.10 and 4.14 are not ticked.** One decision (a small copy made at upload) fixes all of them, plus the cover picker and the slow-phone first paint.
- **Test timeouts under load (4.6 to 4.9, then CI).** Lazy modules and the 1 s `findBy` default didn't fit a busy machine; 4.9 settled it locally with a 5 s `asyncUtilTimeout`, a 20 s test limit and a warm-up import. On GitHub, the pull-request run [45770411217](https://github.com/aarrroon/memoirist/actions/runs/45770411217) for the same commit as a green push run failed one test: `LootDropExtras.test.tsx:181` uses `getByRole('link', { name: /Dear past me/ })` right after `findByRole('region', …)`, while the replies list was still its loading row. A `findByRole` there would wait for it.
- **The bundle budget.** The feed's first JS climbed to 199.4 of 200 kB by 4.11; 4.12's route `lazy` gave back 9 kB. Each new screen should stay lazy.
- **Docs that disagree, left for you.** No unattended run may reword a D-entry, so each task followed DECISIONS (D-27) and listed the doc to fix: D-25's "lock", D-34's sealed badge, D-36's null cover and title page, D-17's Sprint 4 HEIC check, F-01's first letter, F-15's *Rename week*, DESIGN.md's soundtrack title and the sealed Recently deleted row.
- **The generated request types don't allow `null`** (4.6, 4.9, 4.10). Fields are left out instead, which the server reads as none; explicit nulls need `@Schema(nullable = true)` on the request records and a `gen:api` run.
- **Missing copy and layouts.** No doc gave error sentences, placeholder text or some laptop panels; each task wrote its own and listed it (138 in all, [OVERNIGHT_REPORT S4 Assumptions](../../OVERNIGHT_REPORT.md#s4-assumptions)).
- **The first screen (D-25) moves with the sample data:** 651 px (4.4), 809 px (4.7), 905 px (4.8), 1094 px (4.15). The rule holds for a photo-first week (253 px, limit 284 px); the extra test entries in the local journal push it down.

### What to look at first in review
1. `frontend/src/api/queryKeys.ts` and `api/lootDrops.ts` (`afterSeal`, `dropAndRefetch`, `useMarkSeen`), then every `invalidateQueries` in `api/` and `features/capture/`: the client side of D-10 and D-30.
2. `frontend/src/features/capture/saveEntry.ts` and `PhotoDump.tsx`: the multi-step save, what fails per item, and the client id a retry reuses.
3. `frontend/src/api/client.ts` and `problem.ts`: the dev header only in dev, and the one place an error body is parsed.
4. `frontend/src/components/RichText.tsx`, `lib/links.ts` and `lib/spotify.ts`: untrusted JSON and pasted links turned into React and allow-listed URLs.
5. `frontend/src/lib/time.ts`: every date in the user's zone (`unlockInstant`, `erasureDay`, `nextBirthday`, `yearsAgo`).
6. `frontend/src/router.tsx`: `createRoutes()` and route `lazy` with `hydrateFallbackElement`.
7. Backend: `V2__media_dimensions.sql` and `media/ImageSize.java` (4.5); `EraService.get` (4.9); the On this day query and `EraCovers.isHidden` (4.14).
8. `frontend/e2e/fixtures.ts`: the clean-up that runs after a failed test.

## Open items
- **Q29 (blocks 4.10 and 4.14):** decide whether the server makes small copies at upload, accept the `images` failure until it does, or show the initial in place of photos drawn small. Then tick both tasks and move CLAUDE.md's Current focus on.
- **Docs to confirm and reword** (each with `/record-decision`): D-25, D-34, D-36 (twice), D-17 and F-04, F-01, F-15 and DESIGN.md §6 (*Edit week*), DESIGN.md §4 (soundtrack title, the Spotify mark on the band), DESIGN.md §6 and §4 (the sealed Recently deleted row).
- **Approvals from earlier tasks:** the computed moon (4.3), `src/features/deleted/` in `.claude/rules/frontend.md` (4.3), voice notes fixed after the pictures vs F-03's wording (4.7).
- **Setup on real devices:** an iPhone check that capture uploads a JPEG (D-17), Safari recording, and Spotify app credentials for real search.
- **Follow-ups for later tasks:** the CI flake above; explicit nulls in the Era and week `PUT`s; the D-23 delete offer and the empty-entry delete wording with the edit view; whole photos in the entry view; Playwright in CI with the deploy check (5.6).
- The full list is [OVERNIGHT_REPORT S4 Needs attention](../../OVERNIGHT_REPORT.md#s4-needs-attention).
