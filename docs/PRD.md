# Product Requirements: memoirist

> Product intent and scope. For settled technical choices see [DECISIONS.md](DECISIONS.md). For build order see [SPRINT_PLAN.md](SPRINT_PLAN.md).

## 1. Vision

### 1.1 The problem
Traditional journaling apps feel like homework. They demand daily habits, blank pages and calendar grids. Users feel guilty when they miss days and get decision fatigue from tagging and categorising. People want to remember their lives without working for it.

### 1.2 The solution
memoirist is a private Progressive Web App that acts as an automated biographer.
- Users dump unstructured "word vomit" (rich text), photos and voice notes whenever they like.
- Each entry looks like a private Instagram post: a swipeable media carousel above a rich-text caption written in TipTap.
- The app files entries into weeks automatically and groups weeks into flexible life **Eras**. There's no calendar grid.
- Entries can be sealed as **Loot Drops** until a future date.
- In Phase 3 an AI biographer quietly organises themes, people and weekly summaries.

### 1.3 Goals
- **Zero-friction capture.** Open the app, write or add a photo, done. No formatting, tagging or filing required.
- **A biographer, not a therapist.** The AI only asks for quick 1–3 tap yes/no confirmations per entry, plus a weekly summary when the user closes a week. There's never a chat loop.
- **A nostalgia engine.** A visual, chronological feed grouped into Eras replaces the filing cabinet.

## 2. Personas
- **The overwhelmed professional** (for example, a software engineer): analytical but short on time and energy. Wants to offload mental RAM (stress, career thoughts) and have patterns surfaced over time.
- **The memory hoarder:** takes hundreds of photos and writes messy notes about trips, friends and funny stories, but never turns them into a narrative.

## 3. Glossary

| Term | Meaning |
|---|---|
| **Entry** | One post: an optional rich-text caption plus 0–10 photos or voice notes. It belongs to one memory date. |
| **Memory date** | The day an entry is *about*. Defaults to today and can be backdated, never set in the future. |
| **Week** | Monday–Sunday in the user's timezone. Created automatically for the first entry dated in it. Users can title, close and reopen it. Stored as `weekly_logs`. |
| **Era** | A user-named chapter of life with a start date and an optional end date (for example, "Monash Double Degree Era"). Holds many weeks. |
| **Template** | A reusable list of prompts (for example, "Spiralling Check-in") inserted as headings when writing an entry. |
| **Loot Drop** | An entry sealed until a future unlock date. Nobody, including its author, can read or change it until then. |
| **Closing a week** | Marks a week as done. In Phase 3 this triggers the AI weekly summary. A closed week can still be edited and reopened. |

## 4. Core user loop
1. **Capture.** The user opens the app and writes a messy caption.
   - Optionally they pick a template (its prompts appear as headings), change the memory date, and add photos or record a voice note.
   - The entry is filed into the right week automatically.
2. **Seal (optional).** The user turns the entry into a Loot Drop by choosing an unlock date and confirming it. Sealing happens after the media is attached.
3. **Relive.** The feed shows weeks newest first under sticky Era headers. Each week's entries are shown as cards with a carousel and caption. Sealed entries show a vault card with a countdown.
4. **Per-entry polish (Phase 3).** The user taps "Analyse". Within 5 seconds the AI offers 1–3 one-tap chips, such as "Core theme: Career Growth?" or "Tag friend: Amogh?".
5. **Weekly synthesis (Phase 3).** The user closes a week. A background job writes a narrative summary, stores an embedding for "similar memories", and updates the Cast of Characters.

## 5. Roadmap

### Phase 1: Local MVP (Sprints 1–4)
Everything runs locally with Docker Compose: Postgres plus SeaweedFS for object storage.
- **Entries:** create, edit and delete, with an optional TipTap caption, a memory date (backdating allowed) and an optional template.
- **Media carousel:** images and voice notes stored privately and served through short-lived presigned URLs. Upload, reorder, delete.
- **Automatic weeks:** title, assign to an era, close and reopen.
- **Eras:** create, edit and delete chapters; assign weeks to them.
- **Templates:** create, edit and delete prompt lists.
- **Loot Drops:** strict seal with server-side redaction everywhere.
- **Feed:** paged, newest weeks first, grouped by era.
- **Settings:** display name and timezone (default Australia/Melbourne).
- **Dev user:** a seeded local user, with no login yet.
- **API docs:** Swagger UI at `/swagger-ui.html`.

### Phase 2: Cloud, auth and sharing (Sprint 5)
- **Deploy:** frontend on Vercel, Spring Boot on Render, Postgres on Neon, media on Cloudflare R2. Configured through Spring profiles and environment variables.
- **Auth:** email and password with JWT (details are open question Q2). Controllers don't change.
- **Share links:** read-only links to an entry or a week that expire and can be revoked. Loot Drop rules still apply.
- **Export:** PDF export of a week (library is open question Q4); account export and deletion (Q3).

### Phase 3: The AI biographer (Sprint 6, likely split)
- **Per-entry analysis:** extract themes, mood and people, then offer one-tap confirmations.
- **Weekly summaries:** narrative summary plus embedding when a week is closed (asynchronous).
- **Cast of Characters:** people mentioned across entries, and a friendship map across Eras.
- **Insights:** semantic search ("when did I last feel like this?") over weekly summaries.
- **Rule:** sealed entries are never sent to an AI provider.

### Phase 4: Mobile PWA, offline and polish (Sprint 7)
- **Installable PWA** (`vite-plugin-pwa`).
- **Offline capture:** IndexedDB (Dexie) with a sync queue. Client-generated IDs make this possible. Includes a "try before you trust" local trial before creating an account.
- **Voice-to-text dictation** (approach is open question Q7).
- **Inline photos:** between caption paragraphs, as a TipTap node that references a `mediaId`.
- **Zine export:** printable scrapbook layouts.

## 6. Out of scope for now
- Video uploads (Phases 1–3)
- Sending a capsule to *another* person, or shared group capsules (several competitors do this; reconsider after Phase 3)
- Social features: followers, likes, public profiles
- Native iOS or Android apps (the PWA covers mobile)
- Importing from other journaling apps

## 7. Non-functional requirements
- **Privacy:**
  - Media buckets are private; media is reached only through presigned URLs that expire after 60 minutes.
  - Sealed entries never leak content, media or URLs over the network.
  - No entry content in logs.
- **Ownership:** a user can only ever see or change their own data. Other users' IDs return 404.
- **Time correctness:** timestamps are stored in UTC. Days, weeks and displayed times follow the user's timezone, including across daylight-saving changes.
- **Performance:**
  - The app shell loads in under 2 seconds on a warm server (Render's free-tier cold start is open question Q5).
  - Per-entry AI analysis responds in under 5 seconds.
  - Weekly AI work runs in the background.
- **Accessibility:**
  - Keyboard-operable carousel and dialogs, visible focus, labelled controls.
  - Respects `prefers-reduced-motion`.
  - Text contrast meets WCAG AA.
- **Data ownership (Phase 2):** users can export everything and delete their account.
