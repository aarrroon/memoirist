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
- People who write on paper can photograph their diary pages and bring them into the same timeline. In Phase 3 the app can also read the handwriting.
- In Phase 3 an AI biographer quietly organises themes, people and weekly summaries.
- In Phase 5 friends can add entries from their own journals to a private group, for example for a trip, and relive it together.

### 1.3 Goals
- **Zero-friction capture.** Open the app, write or add a photo, done. No formatting, tagging or filing required.
- **A biographer, not a therapist.** The AI only asks for quick 1–3 tap yes/no confirmations per entry, plus summaries of weeks and Eras and, only if the user opts in, occasional insight cards. There's never a chat loop.
- **A nostalgia engine.** A visual, chronological feed grouped into Eras replaces the filing cabinet.
- **Private first, shared by choice.** Nothing leaves a user's journal unless they add it to a group or create a share link. Groups are small and invite-only, never a social network.

## 2. Personas
- **The overwhelmed professional** (for example, a software engineer): analytical but short on time and energy. Wants to offload mental RAM (stress, career thoughts) and have patterns surfaced over time.
- **The memory hoarder:** takes hundreds of photos and writes messy notes about trips, friends and funny stories, but never turns them into a narrative.
- **The paper diarist:** has written by hand for years and has notebooks on a shelf. Doesn't want to retype them or give up the pen, but wants those pages in the same timeline as everything else.
- **The friend group (Phase 5):** four people back from the same trip, each with their own camera roll and their own version of what happened. They want one shared story without giving up their private journals.

## 3. Glossary

| Term | Meaning |
|---|---|
| **Entry** | One post: an optional title, an optional rich-text caption, optional tags, plus 0–10 photos or voice notes. It belongs to one memory date. |
| **Memory date** | The day an entry is *about*. Defaults to today and can be backdated, never set in the future. |
| **Week** | Monday–Sunday in the user's timezone. Created automatically for the first entry dated in it. Users can title, close and reopen it. Stored as `weekly_logs`. |
| **Era** | A user-named chapter of life with a start date and an optional end date (for example, "Monash Double Degree Era"). Holds many weeks. A week can belong to several Eras, so chapters can overlap (D-22). |
| **Template** | A reusable list of prompts (for example, "Spiralling Check-in") inserted as headings when writing an entry. |
| **Loot Drop** | An entry sealed until a future unlock date. Nobody, including its author, can read or change it until then. |
| **Closing a week** | Marks a week as done. In Phase 3 this triggers the AI weekly summary. A closed week can still be edited and reopened. |
| **Page scan** | A photo of a paper diary page, stored as media on an entry. The original photo is always kept. |
| **Transcript** (Phase 3) | Text the app reads from a page scan. It is a suggestion for the user to review, and becomes part of the entry only when they choose to use it. |
| **Help me think** (Phase 3) | A button that appears after a pause in writing. It offers a few two-option questions and tag suggestions, each answered with one tap. |
| **Tag** | A category the user puts on an entry, such as Work or Family (Phase 1, D-26). From Phase 3 the AI can suggest tags, but one is added only when the user taps. |
| **Week summary** | The overview that opens when a week is tapped: its facts, photos and tags, then one bar per entry. The AI-written summary joins it in Phase 3 (D-25). |
| **Timeline** | A plain list of every entry, newest first, one row each, filterable by tag (D-25). |
| **Era summary** (Phase 3) | An AI-written summary of a whole Era, built from its weekly summaries. |
| **Friend profile** (Phase 3) | An auto-generated overview of a person in the Cast of Characters: who they are to the user, the moments they share and which Eras they belong to. |
| **Group** (Phase 5) | A private, invite-only space for a small set of people, usually built around one occasion such as a trip. Members add their own entries to it. |
| **Contribution** (Phase 5) | An entry a member has added to a group. It is linked, not copied: it stays in its author's journal and can be withdrawn at any time. |
| **Group Loot Drop** (Phase 5) | A group with one shared unlock date. Every contribution is sealed until then, so everyone's memories open together. |
| **Trip Era** (Phase 5) | An Era created in a member's own timeline from a group's title and dates. |
| **Insight** (Phase 6) | A card in the user's inbox drawn from their own history, such as a pattern, a growth reminder or a highlight reel. Always opt-in, always dismissible, and always listing the entries it is based on. |

## 4. Core user loop
1. **Capture.** The user opens the app and writes a messy caption.
   - Optionally they pick a template (its prompts appear as headings), change the memory date, and add photos or record a voice note.
   - The entry is filed into the right week automatically.
   - Prefer paper? They photograph a diary page and set its date (backdating is allowed). It is filed like any other entry. From Phase 3 they can tap "Read this page" to turn the handwriting into text.
2. **Seal (optional).** The user turns the entry into a Loot Drop by choosing an unlock date and confirming it. Sealing happens after the media is attached.
3. **Relive.** The feed shows weeks newest first under sticky Era headers. Each week's entries are shown as cards with a carousel and caption. Sealed entries show a vault card with a countdown.
4. **Help me think (Phase 3).** While writing, the user pauses and a "Help me think" button appears. They tap it, and within 5 seconds the AI offers 1–3 quick two-option questions (for example "Stress or excitement?") and suggested tags (for example "Add the tag Work?"). Each takes one tap. Nothing is sent to the AI until the tap, and it works on text that isn't saved yet.
5. **Weekly synthesis (Phase 3).** The user closes a week. A background job writes a narrative summary, stores an embedding for "similar memories", and updates the Cast of Characters.
   - When an Era ends, or on request, the AI also writes an Era summary from its weekly summaries.
   - Each person who keeps turning up in the user's entries gets a profile, built automatically and rebuilt when it goes stale. The user can edit notes, rename, merge duplicates and hide anyone.
6. **Share a trip (Phase 5).** The user starts a group for a trip and invites friends with a link.
   - Each person adds entries from their own journal. They choose which, and can take any back.
   - The group feed shows everyone's contributions in date order, with who wrote each. Sealed ones show a vault card.
   - The trip lands in everyone's own timeline as an Era.
   - Afterwards, the AI weaves the contributions into one shared story.
7. **Look back (Phase 6).** Occasionally an insight card appears in the user's inbox, such as a pattern or a reminder of how a past worry turned out. The user can mark it useful or not useful, or dismiss it.

## 5. Roadmap

### Phase 1: Local MVP (Sprints 1–4)
Everything runs locally with Docker Compose: Postgres plus SeaweedFS for object storage.
- **Entries:** create, edit and delete, with an optional title, an optional TipTap caption, a memory date (backdating allowed), an optional template and optional tags. Drafts survive a reload (D-23, D-24).
- **Tags:** the user's own tags, added by hand, with a page to rename or delete them (D-26).
- **Week summary and timeline:** tap a week for its summary with one bar per entry; or open a plain timeline of every entry, filterable by tag (D-25).
- **Media carousel:** images and voice notes stored privately and served through short-lived presigned URLs. Upload, reorder, delete.
- **Automatic weeks:** title, assign to one or more Eras, close and reopen.
- **Eras:** create, edit and delete chapters; assign weeks to them. Chapters can overlap, so a trip can sit inside a longer chapter (D-22).
- **Templates:** create, edit and delete prompt lists.
- **Loot Drops:** strict seal with server-side redaction everywhere.
- **Feed:** paged, newest weeks first, grouped by Era. A week in several Eras is listed once, under the one that started most recently, with the others as chips (Q18).
- **Settings:** display name and timezone (default Australia/Melbourne).
- **Dev user:** a seeded local user, with no login yet.
- **API docs:** Swagger UI at `/swagger-ui.html`.
- **Paper diaries:** nothing extra to build. A photo of a handwritten page is an ordinary photo on a backdated entry (D-20).

### Phase 2: Cloud, auth and sharing (Sprint 5)
- **Deploy:** frontend on Vercel, Spring Boot on Render, Postgres on Neon, media on Cloudflare R2. Configured through Spring profiles and environment variables.
- **Auth:** email and password with JWT (details are open question Q2). Controllers don't change.
- **Share links:** read-only links to an entry or a week that expire and can be revoked. Loot Drop rules still apply.
- **Export:** PDF export of a week (library is open question Q4); account export and deletion (Q3).

### Phase 3: The AI biographer (Sprint 6, likely split)
- **Help me think:** after a pause in writing, a button appears. Tapping it gives 1–3 quick two-option questions (for example stress or excitement, yes or no) and suggested tags, each answered with one tap. It works on text that isn't saved yet, and nothing is sent to the AI until the button is tapped.
- **Tag suggestions:** the AI suggests tags, drawn from the user's own tags first (manual tags already exist from Phase 1). One tap adds it.
- **Weekly summaries:** narrative summary plus embedding when a week is closed (asynchronous), shown at the top of the week summary view.
- **Era summaries:** an AI summary of a whole Era, built from its weekly summaries, written on request and when an Era ends.
- **Cast of Characters and friend profiles:** people mentioned across entries, a friendship map across Eras, and an auto-generated profile for each person who keeps turning up (who they are to the user, the moments they share, which Eras they belong to). The user can edit notes, rename, merge duplicates and hide anyone. The AI never overwrites the user's own notes.
- **Insights:** semantic search ("when did I last feel like this?") over weekly summaries.
- **Paper diaries, read (Sprint 6c):** on a page photo the user taps "Read this page" and gets a transcript to review. They can use the text as the caption and accept a date found on the page. A page is read only when asked, and never while sealed (D-20).
- **Rule:** sealed entries are never sent to an AI provider. Editing, sealing or deleting an entry updates everything the AI built from it (D-21).
- **Consent and switches:** AI features need a one-time consent, and each has its own switch in settings, including whether the pause nudge appears.

### Phase 4: Mobile PWA, offline and polish (Sprint 7)
- **Installable PWA** (`vite-plugin-pwa`).
- **Offline capture:** IndexedDB (Dexie) with a sync queue. Client-generated IDs make this possible. Includes a "try before you trust" local trial before creating an account.
- **Voice-to-text dictation** (approach is open question Q7).
- **Inline photos:** between caption paragraphs, as a TipTap node that references a `mediaId`.
- **Zine export:** printable scrapbook layouts.
- **Scan mode:** point the camera at a diary page and the app straightens, crops and brightens it. Several pages can be added in a row, and it works offline (D-20).

### Phase 5: Groups and shared memories (Sprint 8, split into 8a and 8b)
Friends build shared memories from their own journals, for example after a trip (D-19). A group never owns an entry; it holds links to entries that members choose to add. It needs real accounts (Phase 2), and 8b also needs the AI work (Phase 3). It can be built before or after Phase 4.

**8a: no AI needed**
- **Groups:** create a private group with a title and optional trip dates, invite friends with an expiring link, and leave at any time (your contributions leave with you). The owner can remove members or delete the group.
- **Contributions:** add any of your own entries to a group and withdraw them whenever you like. The group feed shows everyone's contributions in date order, with who wrote each.
- **Group Loot Drops:** give a group one shared unlock date. Everything added to it is sealed until then and opens for everyone at once. Until then, members can see who has added something, but not what.
- **Trips as Eras:** a group with trip dates appears in each member's own timeline as an Era, created automatically when they join. Weeks in which they add to the trip are added to it, and stay in their other Eras too (see Q10).

**8b: needs the AI biographer**
- **One trip, several perspectives:** the AI weaves everyone's contributions into one shared story. It uses only contributions from members who said yes, and never sealed entries.
- **A real friendship map:** people in the Cast of Characters can be linked to actual group members. Linking shows nothing of that member's journal.

### Phase 6: Insights and coaching (Sprint 9, after Phase 3)
The app starts to look back with the user. Everything here is opt-in, appears as a card the user can ignore, and never becomes a chat (D-21). It uses only the user's own entries, never other members' contributions, and never anything sealed.
- **Proactive insights:** cards in an inbox, each listing the entries it is based on.
  - **Pattern spotters:** for example "you've written about feeling flat on most Sunday evenings this month".
  - **Growth reminders:** what the user wrote when they were worried about something a year ago, and how it turned out.
  - **Last time you felt like this:** when an entry sounds stressed, a card sets it beside similar past moments and what got the user through.
- **Highlight reels:** the best moments of an Era, a year or a friendship, gathered on request and when an Era ends.
- **Writing coach (optional, off by default):** while writing, a single sharp question or a better way to say it, shown only when it is clearly worth it. It sits behind a switch and may become a paid feature (Q15).
- **Switches and limits:** each feature can be turned on or off in settings, and insight frequency is capped (Q16).

## 6. Out of scope for now
- Video uploads (Phases 1–3)
- Sending a time-locked message to *another* person who isn't in a group with you (several competitors do this)
- Social-network features: followers, likes, comments, public profiles, public or searchable groups. Private groups are planned for Phase 5 (D-19)
- Native iOS or Android apps (the PWA covers mobile)
- Open-ended chat with the AI. It offers taps and cards, never a conversation (D-21)
- Importing from other journaling apps
- Bulk import of a whole notebook (many pages at once, with a review queue) until open question Q13 is settled. Single pages and multi-page entries are supported

## 7. Non-functional requirements
- **Privacy:**
  - Media buckets are private; media is reached only through presigned URLs that expire after 60 minutes.
  - Sealed entries never leak content, media or URLs over the network.
  - No entry content in logs.
  - AI features (Phase 3 onwards): need a one-time consent, can be switched off, and run only on request or after opt-in. Text from an unsaved draft is processed in memory and never stored; only the resulting questions and cards are, and they expire. Editing, sealing or deleting an entry updates everything the AI built from it (D-21).
  - Page images (Phase 3): sent to a recognition provider only when the user taps "Read this page", never for sealed entries and never with location metadata. Transcripts are visible only to the entry's owner.
  - Group contributions (Phase 5): withdrawing one removes it from the group at once. A sealed contribution stays sealed for every member, including its author and the group owner, until it unlocks.
- **Ownership:** a user can only ever see or change their own data (Phase 5 adds one exception: entries other members have added to a group they belong to, read only through the group endpoints). Other users' IDs return 404.
- **Time correctness:** timestamps are stored in UTC. Days, weeks and displayed times follow the user's timezone, including across daylight-saving changes.
- **Performance:**
  - The app shell loads in under 2 seconds on a warm server (Render's free-tier cold start is open question Q5).
  - Help me think responds in under 5 seconds.
  - Weekly AI work runs in the background.
  - Reading a page runs in the background and never blocks capture.
- **Accessibility:**
  - Keyboard-operable carousel and dialogs, visible focus, labelled controls.
  - Respects `prefers-reduced-motion`.
  - Text contrast meets WCAG AA.
  - Every camera feature has a file-picker alternative.
- **Data ownership (Phase 2):** users can export everything and delete their account.
