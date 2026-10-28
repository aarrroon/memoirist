# Product Requirements: memoirist

> Product intent and scope, in more detail than the [README](../README.md). The README wins on what users get and in which phase; [DECISIONS.md](DECISIONS.md) wins on how it works (D-27). For build order see [SPRINT_PLAN.md](SPRINT_PLAN.md).

## 1. Vision

### 1.1 The problem
Traditional journaling apps feel like homework. They demand daily habits, blank pages and calendar grids. Users feel guilty when they miss days and get decision fatigue from tagging and categorising. People want to remember their lives without working for it.

### 1.2 The solution
memoirist is a private Progressive Web App that acts as an automated biographer.
- Users dump unstructured "word vomit" (rich text), photos and voice notes whenever they like.
- Each entry looks like a private Instagram post: a swipeable media carousel above a rich-text caption written in TipTap.
- The app files entries into weeks automatically, and weeks into flexible life **Eras** by date (D-22). There's no calendar grid.
- Entries can be sealed as **Loot Drops** until a future date.
- People who write on paper can photograph their diary pages and bring them into the same timeline. In Phase 4 the app can also read the handwriting, when asked.
- In Phase 3 friends can add entries from their own journals to a private group, for example for a trip, and relive it together.
- In Phase 4 an AI biographer writes up the weeks and Eras the user closes, keeps profiles and a friendship map of the people in their story, weaves a shared story of a trip, and helps when they're stuck.

### 1.3 Goals
- **Zero-friction capture.** Open the app, write or add a photo, done. No formatting, tagging or filing required.
- **A biographer, not a therapist.** The AI only offers 1–3 quick two-option questions per entry (such as "stress or excitement?"), summaries of the weeks and Eras the user closes and, only if the user opts in, occasional insight cards. There's never a chat loop.
- **A nostalgia engine.** A visual, chronological feed grouped into Eras replaces the filing cabinet, and *On this day* brings old memories back without streaks or guilt (D-32).
- **Private first, shared by choice.** Nothing leaves a user's journal unless they add it to a group or create a share link. Groups are small and invite-only, never a social network.

## 2. Personas
- **The overwhelmed professional** (for example, a software engineer): analytical but short on time and energy. Wants to offload mental RAM (stress, career thoughts) and have patterns surfaced over time.
- **The memory hoarder:** takes hundreds of photos and writes messy notes about trips, friends and funny stories, but never turns them into a narrative.
- **The paper diarist:** has written by hand for years and has notebooks on a shelf. Doesn't want to retype them or give up the pen, but wants those pages in the same timeline as everything else.
- **The friend group (Phase 3):** four people back from the same trip, each with their own camera roll and their own version of what happened. They want one shared story without giving up their private journals.

## 3. Glossary

| Term | Meaning |
|---|---|
| **Entry** | One post: an optional title, an optional rich-text caption, optional tags and an optional soundtrack, plus 0–20 photos or voice notes. It belongs to one memory date. |
| **Memory date** | The day an entry is *about*. Defaults to today and can be backdated, never set in the future. |
| **Week** | Monday–Sunday in the user's timezone. Created automatically for the first entry dated in it. Users can title, close and reopen it. Stored as `weekly_logs`. |
| **Era** | A user-named chapter of life with a start date and an optional end date (for example, "Monash Double Degree Era"). Defined by its dates: it holds every week they overlap and shows the entries inside them (D-22). A week can belong to several Eras, so chapters can overlap. An Era can have a cover photo (D-36). |
| **Template** | A reusable list of prompts (for example, "Spiralling Check-in") inserted as headings when writing an entry. Prompts left empty are removed on save. A gallery of starter templates adds an editable copy with one tap (D-06). |
| **Photo dump** | Many photos picked at once and filed into entries, one per day they were taken (D-34). |
| **Soundtrack** | An optional Spotify song, album or playlist on an entry, a week or an Era (D-35). |
| **Loot Drop** | An entry sealed until a future unlock date. Nobody, including its author, can read or change it until then. |
| **Opened strip** | A strip at the top of the feed listing Loot Drops that have just opened, until the user opens or dismisses them (D-10). |
| **Reply to past you** | An entry written in answer to an opened Loot Drop, and linked to it (D-33). |
| **On this day** | A strip at the top of the feed showing entries from the same day in earlier years (D-32). |
| **Recently deleted** | Where a deleted entry waits for 30 days, so the user can restore it or erase it for good (D-30). |
| **Yearly letter** (Phase 2) | A letter to next year's self, prompted on a yearly date the user picks (their birthday by default) and sealed until that date next year (D-32). |
| **App lock** (Phase 2) | An optional lock screen that guards the journal on a shared or borrowed device (D-37). |
| **Closing a week** | Marks a week as done. From Phase 4 it is what writes the week's AI summary (D-28). A closed week can still be edited and reopened. |
| **Closing an Era** (Phase 4) | Wraps up a chapter: closes its open weeks, sets an end date if it has none, offers a last word and a letter to open years later, and writes the Era summary (D-28). |
| **Wrap up card** (Phase 4) | A card at the top of the feed that offers the most recent finished week that's still open, or an Era that has ended, and closes it with one tap (D-28). |
| **Weekly email** (Phase 4) | An email on Monday, after a week with entries, that points to its story or to Wrap up. It never contains entry content (D-32). |
| **Page scan** | A photo of a paper diary page, marked as a page when it's added and stored as media on an entry (D-20). The original photo is always kept. |
| **Transcript** (Phase 4) | Text the app reads from a page scan. It is a suggestion for the user to review, and becomes part of the entry only when they choose to use it. |
| **Help me think** (Phase 4) | A button that appears after a pause in writing. It offers a few two-option questions and tag suggestions, each answered with one tap. |
| **Tag** | A category the user puts on an entry, such as Work or Family (Phase 1, D-26). From Phase 4 the AI can suggest tags, but one is added only when the user taps. |
| **Week summary** | The overview that opens when a week is tapped: its facts, photos and tags, then one bar per entry. The AI-written summary joins it in Phase 4 (D-25). |
| **Timeline** | A plain list of every entry, newest first, one row each, filterable by tag (D-25). In user-facing copy, "timeline" can also mean the whole journal. |
| **Era summary** (Phase 4) | An AI-written summary of a whole Era, built from its weekly summaries when the user closes the Era (D-28). |
| **Cast of Characters** (Phase 4) | The people the biographer finds in the user's entries. Each one gets a friend profile. |
| **Friend profile** (Phase 4) | An auto-generated overview of a person in the Cast of Characters: who they are to the user, the moments they share and which Eras they belong to. |
| **Friendship map** (Phase 4) | A picture of the Cast of Characters as a web: people who appear in the same entries are joined, and it can be filtered by Era (D-21). |
| **Search** (Phase 4) | Word search over titles, captions and used page text, and *search by feeling* ("when did I last feel like this?") over the summaries of closed weeks (D-29). |
| **Group** (Phase 3) | A private, invite-only space for a small set of people, usually built around one occasion such as a trip. Members add their own entries to it. |
| **Contribution** (Phase 3) | An entry a member has added to a group. It is linked, not copied: it stays in its author's journal and can be withdrawn at any time. |
| **Group Loot Drop** (Phase 3) | A group with one shared unlock date. Every contribution is sealed until then, so everyone's memories open together. |
| **Trip Era** (Phase 3) | An Era created in a member's own timeline from a group's title and dates. |
| **Shared story** (Phase 4) | One story of a trip, woven by the AI from the contributions of members who opted in (D-19). |
| **Insight** (Phase 6) | A card in the user's inbox drawn from their own history, such as a pattern, a growth reminder or a highlight reel. Always opt-in, always dismissible, and always listing the entries it is based on. |
| **Prediction** (Phase 6) | A sealed yes-or-no question to future you, answered again when it opens and scored. |
| **Year in review** (Phase 6) | A look back each December at the year's chapters, people and best photos, with a card to share that shows nothing private. |
| **Gift of memories** (Phase 6) | A page of the user's moments with one person, shared with an expiring link. |
| **Writing coach** (Phase 6) | Shown to users as "a nudge while you write": an optional single question or better wording, offered only when it's clearly worth it (D-21). |

## 4. Core user loop
1. **Capture.** The user opens the app and writes a messy caption.
   - Optionally they pick a template (its prompts appear as headings), change the memory date, add photos or record a voice note, and add a soundtrack (D-35).
   - The entry is filed into the right week automatically. A photo's own date is offered as the memory date (D-34).
   - A camera roll full of a trip? A *photo dump* files many photos at once, one entry per day they were taken (D-34).
   - Prefer paper? They photograph a diary page, mark it as a page and set its date (backdating is allowed). It is filed like any other entry. From Phase 4 they can tap "Read this page" to turn the handwriting into text.
2. **Seal (optional).** The user turns the entry into a Loot Drop by choosing an unlock date (one tap for a month, a year or their next birthday, D-33) and confirming it. Sealing happens after the media is attached.
3. **Relive.** The feed shows weeks newest first under sticky Era headers, each Era with its cover (D-36). Each week's entries are shown as cards with a carousel and caption. Sealed entries show a vault card with a countdown. When a Loot Drop opens, it appears in an *Opened* strip at the top of the feed until the user opens it, and they can reply to past them (D-10, D-33). An *On this day* strip shows the same day in earlier years (D-32).
4. **Help me think (Phase 4).** While writing, the user pauses and a "Help me think" button appears. They tap it, and within 5 seconds the AI offers 1–3 quick two-option questions (for example "Stress or excitement?") and suggested tags (for example "Add the tag Work?"). Each takes one tap. Nothing is sent to the AI until the tap, and it works on text that isn't saved yet.
5. **Weekly synthesis (Phase 4).** The user closes a week, often with one tap on the *Wrap up* card at the top of the feed or from the Monday email (D-28, D-32). A background job writes a narrative summary, stores an embedding for search by feeling, and updates the Cast of Characters.
   - When the user closes an Era, its open weeks are closed too. They can add a last word and seal a letter to open years later, and the AI writes an Era summary from the weekly summaries.
   - Each person who keeps turning up in the user's entries gets a profile, built automatically and rebuilt when it goes stale. The user can edit notes, rename, merge duplicates and hide anyone.
6. **Share a trip (Phase 3).** The user starts a group for a trip, or from one of their entries with *share with friends* (D-19), and invites friends with a link.
   - Each person adds entries from their own journal. They choose which, and can take any back.
   - The group feed shows everyone's contributions in date order, with who wrote each. Sealed ones show a vault card.
   - The trip lands in everyone's own timeline as an Era.
   - From Phase 4, the AI weaves the contributions into one shared story.
7. **Look back (Phase 6).** Occasionally an insight card appears in the user's inbox, such as a pattern or a reminder of how a past worry turned out. The user can mark it useful or not useful, or dismiss it. Predictions open and are scored, and each December brings a year in review.

## 5. Roadmap
The phase order follows D-31: groups come before the AI biographer, and the phone extras come after it.

### Phase 1: The core journal (local MVP, Sprints 1–4)
Everything runs locally with Docker Compose: Postgres plus SeaweedFS for object storage.
- **Entries:** create, edit and delete, with an optional title, an optional TipTap caption, a memory date (backdating allowed, back to 1900, D-09), an optional template and optional tags. Drafts survive a reload (D-23, D-24).
- **Photo dump:** pick many photos and they're filed into backdated entries, one per day they were taken. A photo's own date is offered for single photos too (D-34).
- **Recently deleted:** a deleted entry waits 30 days, then is erased with its media. Until then the user can restore it or erase it now (D-30).
- **Tags:** the user's own tags, added by hand, with a page to rename or delete them (D-26).
- **Week summary and timeline:** tap a week for its summary with one bar per entry; or open a plain timeline of every entry, filterable by tag (D-25).
- **Media carousel:** images and voice notes stored privately and served through short-lived presigned URLs. Upload, reorder, delete; up to 20 per entry (D-17). A photo can be marked as a diary page (D-20).
- **Soundtracks:** an optional Spotify song, album or playlist on an entry, a week or an Era, pasted as a link or found with search through our server. The player loads only when tapped (D-35).
- **Automatic weeks:** title, close and reopen. Each week belongs to the Eras whose dates overlap it (D-22).
- **Eras:** create, edit and delete chapters. An Era gathers the weeks inside its dates by itself and shows the entries inside them; to change what it holds, change its dates (D-22). Chapters can overlap, so a trip can sit inside a longer chapter. Each Era can have a cover photo, shown as its title page in the feed (D-36).
- **Templates:** create, edit and delete prompt lists, or add one from a gallery of starter templates (D-06).
- **Loot Drops:** strict seal with server-side redaction everywhere, including counts and filters, and an *Opened* strip when one opens (D-10). Suggested unlock dates, a reply to past you, and a first letter at the end of the first-run screen (D-33).
- **On this day:** a strip at the top of the feed with entries from the same day in earlier years (D-32).
- **Feed:** paged, newest weeks first, grouped by Era. A week in several Eras is listed once: a chapter that sits inside a bigger one (a trip inside "Uni Days") shows as a section nested inside it; when Eras only partly overlap, the most recently started one wins, with the others as chips. The user can pin an Era to make it win (D-22).
- **Settings:** display name, timezone (default Australia/Melbourne) and birthday (D-33).
- **Dev user:** a seeded local user, with no login yet, and optional sample content for building the UI (task 3.16).
- **API docs:** Swagger UI at `/swagger-ui.html`.
- **Paper diaries:** a photo of a handwritten page is an ordinary photo on a backdated entry, marked as a page (D-20).

### Phase 2: Online, with accounts (cloud, auth and sharing; Sprint 5)
- **Deploy:** frontend on Vercel, Spring Boot on Render, Postgres on Neon, media on Cloudflare R2. Configured through Spring profiles and environment variables.
- **Auth:** email and password with JWT (details are open question Q2). Controllers don't change.
- **Installable app:** the home-screen app (`vite-plugin-pwa`). Offline writing comes in Phase 5 (D-31).
- **App lock:** an optional lock that guards the journal on a shared device (D-37).
- **Share links:** read-only links to an entry or a week that expire and can be revoked. Loot Drop rules still apply.
- **Export:** PDF export of a week (library is open question Q4); account export and deletion (Q3).
- **Unlock emails:** an email when a Loot Drop opens (D-10).
- **Yearly letter:** on a yearly date the user picks, their birthday by default, an email and a card invite a letter to next year's self, sealed until then (D-32).
- **Product metrics:** counts of a few product events, never content (D-38).

### Phase 3: Memories with friends (groups; Sprint 6)
Friends build shared memories from their own journals, for example after a trip (D-19). A group never owns an entry; it holds links to entries that members choose to add. It needs real accounts (Phase 2) but no AI; the shared story comes with the AI in Phase 4 (D-31).
- **Groups:** create a private group with a title and optional trip dates, or start one from an entry with *share with friends* (D-19). Invite friends with an expiring link, and leave at any time (your contributions leave with you). The owner can remove members or delete the group.
- **Contributions:** add any of your own entries to a group and withdraw them whenever you like. The group feed shows everyone's contributions in date order, with who wrote each.
- **Group Loot Drops:** give a group one shared unlock date. Everything added to it is sealed until then and opens for everyone at once. Until then, members can see who has added something, but not what.
- **Trips as Eras:** a group with trip dates appears in each member's own timeline as an Era, created automatically when they join. Like any Era, it holds the weeks inside the trip dates and shows only the entries inside them, so a weekend trip stays a weekend (D-22). Every week stays in its other Eras.

### Phase 4: The AI biographer (Sprint 7, likely split)
- **Help me think:** after a pause in writing, a button appears. Tapping it gives 1–3 quick two-option questions (for example stress or excitement, yes or no) and suggested tags, each answered with one tap. It works on text that isn't saved yet, and nothing is sent to the AI until the button is tapped.
- **Tag suggestions:** the AI suggests tags, drawn from the user's own tags first (manual tags already exist from Phase 1). One tap adds it.
- **Weekly summaries:** narrative summary plus embedding when the user closes a week (asynchronous), shown at the top of the week summary view. A *Wrap up* card offers the latest finished week with one tap, and a gentle reminder appears when several finished weeks are still open (D-28).
- **Weekly email:** on Monday, after a week with entries, an email points to its story or to Wrap up. It never contains entry content (D-32).
- **Era summaries and closing an Era:** closing an Era closes its open weeks, offers a last word and a letter to open years later, and writes an AI summary built from its weekly summaries (D-28).
- **Cast of Characters and friend profiles:** people mentioned across entries, a friendship map across Eras, and an auto-generated profile for each person who keeps turning up (who they are to the user, the moments they share, which Eras they belong to). The user can edit notes, rename, merge duplicates and hide anyone. The AI never overwrites the user's own notes.
- **Search:** word search over titles, captions and used page text, and search by feeling ("when did I last feel like this?") over the summaries of closed weeks (D-29).
- **Paper diaries, read (Sprint 7c):** on a page photo the user taps "Read this page" and gets a transcript to review. They can use the text as the caption and accept a date found on the page. A page is read only when asked, and never while sealed (D-20).
- **Shared story:** the AI weaves everyone's contributions to a group into one shared story. It uses only contributions from members who said yes, and never sealed entries (D-19).
- **Rule:** sealed entries are never sent to an AI provider. Editing, sealing or deleting an entry updates everything the AI built from it (D-21). Hard topics are handled gently and never turned into patterns (D-39).
- **Consent and switches:** AI features need a one-time consent, and each has its own switch in settings, including whether the Help me think pause prompt appears.

### Phase 5: On your phone (offline and polish; Sprint 8)
- **Offline capture:** IndexedDB (Dexie) with a sync queue. Client-generated IDs make this possible. Includes a "try before you trust" local trial before creating an account.
- **Voice-to-text dictation** (approach is open question Q7).
- **Inline photos:** between caption paragraphs, as a TipTap node that references a `mediaId`.
- **Zine export:** printable scrapbook layouts.
- **Scan mode:** point the camera at a diary page and the app straightens, crops and brightens it. Several pages can be added in a row, and it works offline (D-20).
- **Unlock notifications:** a push notification from the installed app when a Loot Drop opens (D-10).

### Phase 6: Looking back with you (insights and coaching; Sprint 9, after Phase 4)
The app starts to look back with the user. Everything here is opt-in, appears as a card the user can ignore, and never becomes a chat (D-21). It uses only the user's own entries, never other members' contributions, and never anything sealed.
- **Proactive insights:** cards in an inbox, each listing the entries it is based on.
  - **Pattern spotters:** for example "you've written about feeling flat on most Sunday evenings this month".
  - **Growth reminders:** what the user wrote when they were worried about something a year ago, and how it turned out.
  - **Last time you felt like this:** when an entry sounds stressed, a card sets it beside similar past moments and what got the user through.
- **Highlight reels:** the best moments of an Era, a year or a friendship, gathered on request and when an Era is closed.
- **Predictions:** a sealed yes-or-no question to future you. When it opens, the user answers again and sees how they did, with a running score.
- **Year in review:** each December, the year's chapters, people and best photos, with a card to share that shows nothing private.
- **A gift of memories:** the user's moments with one person, gathered into a page to share with an expiring link, for example on their birthday.
- **Writing coach** (shown to users as "a nudge while you write"; optional, off by default): while writing, a single sharp question or a better way to say it, shown only when it is clearly worth it. It sits behind a switch and may become a paid feature (Q15).
- **Switches and limits:** each feature can be turned on or off in settings, and insight frequency is capped (Q16).

## 6. Out of scope for now

### Not planned
- Video uploads (Phases 1–4)
- Sending a time-locked message to *another* person who isn't in a group with you (several competitors do this)
- Social-network features: followers, likes, comments, public profiles, public or searchable groups. Private groups are planned for Phase 3 (D-19)
- Native iOS or Android apps (the PWA covers mobile)
- Open-ended chat with the AI. It offers taps and cards, never a conversation (D-21)
- Bulk import of a whole notebook (many pages at once, with a review queue) until open question Q13 is settled. Single pages and multi-page entries are supported
- Summaries of weeks the user hasn't closed: there is no automatic closing and no "close all weeks" action (D-28)
- Linking people in the Cast of Characters to real group members (D-19)
- Adding or removing single weeks from an Era by hand: an Era is its dates (D-22)
- Choosing a different first day of the week: every week runs Monday to Sunday (D-08)
- Third-party analytics, cookies or tracking scripts (D-38)

### Parked: ideas for much later
Ideas worth keeping that aren't being considered yet. None is planned; each needs a decision before it joins a phase.
- **Import from other journaling apps,** starting with Day One (its JSON export plus photos). It would remove the main reason not to switch (feature review R-43).

## 7. Non-functional requirements
- **Privacy:**
  - Media buckets are private; media is reached only through presigned URLs that expire after 60 minutes.
  - Sealed entries never leak content, media or URLs over the network, and never show up in counts, filters or search results (D-10).
  - Search terms, including music searches, are never logged or put in URLs (D-29, D-35).
  - No entry content in logs.
  - No entry content in emails: they carry dates, counts and a link only (D-32).
  - Product metrics are counts only, never content, with no third-party trackers (D-38).
  - Spotify's player loads only when tapped, and music search goes through our server, so the browser doesn't contact Spotify until then (D-35).
  - AI features (Phase 4 onwards): need a one-time consent, can be switched off, and run only on request or after opt-in. Text from an unsaved draft is processed in memory and never stored; only the resulting questions and cards are, and they expire. Editing, sealing or deleting an entry updates everything the AI built from it (D-21). Hard topics are handled gently (D-39).
  - Page images (Phase 4): sent to a recognition provider only when the user taps "Read this page", never for sealed entries and never with location metadata. Transcripts are visible only to the entry's owner.
  - Group contributions (Phase 3): withdrawing one removes it from the group at once. A sealed contribution stays sealed for every member, including its author and the group owner, until it unlocks.
- **Ownership:** a user can only ever see or change their own data (Phase 3 adds one exception: entries other members have added to a group they belong to, read only through the group endpoints). Other users' IDs return 404.
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
