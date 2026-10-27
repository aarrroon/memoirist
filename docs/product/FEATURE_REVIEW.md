# Feature Review

> A product review of every planned feature, written on 2026-10-27 before any code existed. It scores each feature, names what's holding the product back, and proposes changes and new ideas. **Everything here is a proposal until it's accepted.** Accept one with `/record-decision`, then set its status in [§6](#6-recommendations-and-status). **Not needed for coding tasks.**
>
> Competitor facts and their sources live in [COMPETITOR_NOTES.md](COMPETITOR_NOTES.md). This file links to them instead of repeating them.
>
> **Phase numbers below are the order at the time of writing.** On 2026-10-28, D-31 moved groups to Phase 3, the AI biographer to Phase 4 and the phone features to Phase 5. The README Timeline has the current order.

## 1. Summary
- **The engineering spec is excellent; the product spec isn't finished.** Phase 1 is specified down to its error codes, and the privacy rules (sealing, ownership, AI consent) are more careful than in many apps that have shipped. What's missing is what decides whether people stay and pay: the visual design, onboarding, notifications, search, pricing and success metrics.
- **The distinctive assets** are Loot Drops, Eras, the private Instagram-style feed, and sealing a group trip until its anniversary. No competitor combines them.
- **Retention is the biggest risk.** The README opens by saying people abandon journals. Yet nothing in the plan brings anyone back, and the weekly AI summary only runs if the user closes each week by hand.
- **The build order works against the product.** It's a phone-first app, but phones arrive in Phase 4. Groups, the main way new people would hear about it, come in Phase 5. Print, the clearest way to earn money, is a Phase 4 polish item.
- **The market has moved.** Apple Journal is free on iPhone, iPad and Mac, with multiple journals. Day One has shared journals and a paid AI plan that includes a chat. Retro covers weekly photos with close friends.

## 2. Scorecard
How to read the columns:
- **Spec:** how fleshed out the feature is. 5 means a decision, a full user flow, a final API and schema, and a sprint task with "Done when" checks. 1 means a line in the README or PRD.
- **Fit:** whether the design solves the problem for its persona.
- **Market:** how much the feature helps sell the app.
- **Need:** Must, Should, Could or Rethink.

| Feature | Spec | Fit | Market | Need | Verdict |
|---|---|---|---|---|---|
| **Phase 1** | | | | | |
| Capture (text, title, photos, voice) | 5 | Good | Med | Must | Solid. Ignores the photo's own date; 10 items is tight for trips |
| Templates | 5 | Good | Low | Should | Ship starter templates, or most people never see this |
| Tags (AI suggestions in Phase 3) | 5 | Good | Low | Should | Fine, and expected |
| Paper pages as photos | 5 | Partly | Med | Should | Fine for one page; the real need is a shelf of notebooks |
| Automatic weeks, close and reopen | 5 | Partly | Low | Must | "Close week" is a chore that later gates all the AI |
| Eras | 4 | Partly | High | Must | Adding weeks by hand (Q1 default) undercuts "we do the organising" |
| Feed grouped by Era | 4 | Good | High | Must | The core differentiator. No way to jump to a date |
| Week summary and timeline | 5 | Good | Low | Should | Good home for Phase 3 summaries. No keyword search anywhere |
| Loot Drops | 5 | Good | High | Must | Best feature in the plan. The unlock is silent and undesigned |
| Settings | 5 | Partly | Low | Must | Travellers get the wrong "today"; the Melbourne default won't suit other countries |
| Visual design | 1 | — | High | Must | The pitch rests on look and feel, and DESIGN.md doesn't exist. Needed by Sprint 4 |
| **Phase 2** | | | | | |
| Accounts | 2 | Good | Low | Must | Q2 is wide open; passkeys or email sign-in links beat passwords |
| Share links | 3 | Partly | Med | Should | A week link shares every entry in it; photos may carry home GPS |
| Export and account deletion | 1 | Good | Med | Must | A legal duty and a trust signal; make the export readable |
| **Phase 3** | | | | | |
| AI consent and switches | 4 | Good | Med | Must | Excellent design; market it |
| Help me think | 4 | Partly | Med | Should | Sold as help when you're stuck, but a two-option answer doesn't help you write |
| Weekly summaries | 3 | Poor | High | Must | The hero AI feature, but it only runs for weeks closed by hand |
| Era summaries | 4 | Partly | High | Should | Same dependency. Could become a chapter of a printed book |
| Cast of Characters and profiles | 4 | Partly | High | Should | Distinctive but sensitive. There's no "split", and the graph is a demo |
| Search by feeling | 2 | Partly | Med | Could | Returns weeks, not entries; plain search must come first |
| Read handwriting | 4 | Good | Med | Should | Reading is becoming common; filing and dating the page is the edge |
| **Phase 4** | | | | | |
| Installable app (PWA) | 2 | Good | Low | Must | Far too late for a phone-first app |
| Offline capture and local trial | 2 | Good | Med | Should | The trial isn't in any sprint task |
| Voice-to-text dictation | 1 | Poor | Low | Rethink | Phone keyboards already dictate; transcribe voice notes instead |
| Inline photos | 2 | Good | Low | Could | Nice for long writers; Apple Journal has it |
| Zine export (print) | 1 | Good | High | Should | Underrated: the clearest way to earn money |
| Scan mode | 3 | Poor | Low | Rethink | Phones' own scanners do it better; accept what they produce |
| **Phase 5** | | | | | |
| Groups and contributions | 4 | Good | High | Should | The main way new people would join. Invited friends need a 30-second path |
| Group Loot Drops | 4 | Good | High | Should | The most distinctive idea in the plan; needs notifications |
| Trips as Eras | 3 | Partly | Med | Should | Adding whole weeks (Q10) means a weekend trip swallows Monday's notes |
| Shared story | 3 | Good | High | Could | Strong demo. Deleted whenever anyone withdraws (Q9) |
| **Phase 6** | | | | | |
| Patterns and growth reminders | 3 | Partly | Med | Could | Growth reminders are great; patterns drift toward therapy |
| Highlight reels | 3 | Partly | Med | Could | Photos apps make free video reels; make ours printable and giftable |
| Writing coach | 3 | Poor | Low | Rethink | Contradicts "messy is fine"; fold it into Help me think |

Low spec scores in later phases are expected, because each phase is specified just in time. The worrying ones are needed soon: design by Sprint 4, and accounts and export by Sprint 5. Phases 5 and 6 already have drafted APIs, so spec effort has gone to the distant future before what launch needs.

## 3. What's holding it back

### 3.1 Nothing brings people back
- Until Phase 6, nothing contacts a user outside the app, and even there it's an open question (Q16). `entries.unlock_notified` is a "hook for a future unlock-notification job", but no phase builds a way to notify anyone. A Loot Drop opens silently, a group's unlock date passes unnoticed, and nobody is reminded about an invite.
- Nothing resurfaces old memories until Phase 6, and then only as opt-in AI cards. Day One, Apple Journal and Retro all do this already.
- **Fix:**
  - email notifications from Phase 2 (R-06)
  - web push once the app is installable (R-07)
  - an "On this day" card that needs no AI (R-08)
  - onboarding that ends with sealing a letter to yourself for next month (R-15)
- Keep "no streaks, ever". It's a good marketing line.

### 3.2 The AI biographer depends on a button nobody will press
- Weekly summaries, embeddings and Cast of Characters updates run when the user *closes a week* ([PRD.md §4](../PRD.md#4-core-user-loop), step 5).
- Era summaries are built from weekly summaries (task 6.13 returns `not-enough-content` without them), and "search by feeling" searches those same summaries.
- If people don't close weeks, and most won't, Phase 3 produces almost nothing.
- **Fix:** summarise each week automatically after it ends, and make "close week" an optional "I'm done early" (R-23). D-21's stale-and-rebuild rules already handle later edits.

### 3.3 A phone-first app that reaches phones in Phase 4
- The pitch is "snap a photo, talk into your phone", but Phase 1 runs on localhost. Phase 2 is the first time a phone can reach the app, and installing it waits until Phase 4.
- On iPhones, web push only works for an installed web app, so notifications wait too.
- **Fix:** move the installable basics (task 7.1) into Phase 2, and leave offline sync in Phase 4 (R-07).

### 3.4 Growth and money come last, and nothing is measured
- Groups and share links are the only ways a new person hears about memoirist, and groups come in Phase 5 (R-31).
- There's no pricing model; Q15 only asks which AI features might be paid (R-39).
- There are no success metrics (R-14). The README expects the plan to change "as we learn what people use", but nothing is set up to measure it. Start with these, as counts only and never entry content:
  - time to first entry
  - how many new users write again in their second week
  - weekly active writers
  - Loot Drops sealed
  - invites accepted per group
- Render's free tier takes about a minute to wake up (Q5). That can't work for "open the app, write, done" (R-11).

### 3.5 Basics every competitor has are missing
- **Keyword search.** Phase 1 has only a tag filter, and Phase 3's semantic search covers weekly summaries only (R-04). Postgres full-text search over `title` and `content_text` needs no new infrastructure.
- **App lock** with a passcode or Face ID (R-09).
- **Recently deleted.** Deleting an entry erases it and its photos at once. In a memory app, accidental loss is the worst failure, and Apple Journal added recovery in 2025 (R-03).
- **Import** from Day One and others is out of scope ([PRD.md §6](../PRD.md#6-out-of-scope-for-now)), but it's the main thing stopping people from switching (R-43).
- **Photo dates.** A photo taken last Tuesday should suggest last Tuesday as the memory date (R-16).

### 3.6 The look and feel is unspecified
The README sells a feeling ("as easy as posting on Instagram", "a nostalgia engine"), but DESIGN.md doesn't exist and [DIRECTIONS.md](../design/DIRECTIONS.md) lists four untested options. The unlock, the most emotional moment in the app, is left as "a design choice for DESIGN.md" (F-07). Give design the same rigour as the API (R-22).

### 3.7 No plan for sensitive content
Templates like *Spiralling* invite people to write at their lowest. Phase 3 sends entries to an AI, and Phase 6 looks for patterns in them. Before Phase 3, decide how summaries and insights handle self-harm, abuse and grief (R-24):
- use a gentle tone
- never generate "patterns" about these topics
- show local helplines where it fits, such as Lifeline (13 11 14) in Australia

## 4. Feature notes

### Capture
- Read each photo's own date (EXIF) on upload, before compression drops it, and suggest it as the memory date (R-16). This makes backdating and photo dumps effortless.
- 10 items per entry (D-17) is tight for trips; Instagram allows 20 (R-18).
- An Instagram-style app with no video at all looks dated. Plan short clips for Phase 4 (R-41).
- **Travel:**
  - The home timezone is kept by design (F-13), so a Tuesday-night entry in Paris defaults to Wednesday for a Melbourne user. Offer "Use local time for this entry?" when the device's timezone differs (R-19).
  - At sign-up, take the timezone from the browser instead of defaulting to Melbourne (R-10).

### Templates and tags
- Ship four or five starter templates and let a template carry default tags (R-17): *Trip day*, *Spiralling check-in*, *Night out*, *Letter to future me* and *My week*. Most people never create a template from scratch.
- Tags are fine as planned. An optional one-tap mood chip would give "search by feeling" and Phase 6 real data instead of AI guesses (R-42).

### Paper diaries
- The paper diarist's real need is a shelf of old notebooks, and bulk import is out of scope (Q13 default). One page at a time, each dated by hand, is too slow for 200 pages.
- Make *Import a notebook* the main flow for this persona (R-29):
  1. Add many pages at once.
  2. Read them in the background.
  3. Date each page from its transcript (`detectedDate` already exists).
  4. Review the results in a queue.
- Accept multi-page PDFs from the phone's own scanner instead of building scan mode (R-30, R-35). D-17 rejects PDFs today.
- Use the Cast of Characters to correct names in transcripts. Names are the weak spot for rival scanners.

### Weeks, Eras and the feed
- Fill Eras by date (R-01).
  - Adding weeks by hand means about 200 ticks for a four-year Era.
  - Filed by hand, an Era is a folder with dates, which is what Apple Journal's multiple journals already are.
  - Chapters that fill themselves are what make Eras different.
- Show only the entries inside an Era's dates, so a weekend trip stays a weekend (R-02).
- Give each Era a cover photo and a colour, so its sticky header reads like a chapter title page. Add a date scrubber for jumping through time without a calendar grid (R-20).

### Loot Drops
- This is the best-designed feature in the docs:
  - a strict seal
  - redaction on the server
  - unlocks at 9 am
  - a confirmation that reads the date back in words
- Make the unlock an event (R-21):
  1. A notification.
  2. An opening animation.
  3. A *Reply to past you* option, which creates a new entry linked to the drop.
- Suggest unlock dates (one month, one year, next birthday) so sealing takes ten seconds (R-21).
- "Loot Drop" is memorable, but some personas will read it as gaming slang. Test the name, and pair it with plain words in the app ("Loot Drop: a letter to future you").

### Accounts, sharing and export (Phase 2)
- Passkeys or email sign-in links add less friction than passwords, need no reset flow, and fit a privacy brand (R-10).
- **Share links (R-13):**
  - A week's share link shares every unsealed entry in it, so a *Spiralling* note could sit beside the trip photos. Show a preview where entries can be unticked.
  - Strip GPS from photos before they appear on a public page or in a group.
- **Export:**
  - Make it readable without the app (R-12). "Your journal outlives us" answers the fear of being locked in.
  - A week PDF adds little on its own. Build it as the first page type of print (R-33).

### The AI biographer (Phase 3)
- The design is excellent, and you can market it as it stands: one gate for all AI reading, consent first, rebuilds when entries change, and sources on everything. In plain words: "AI is off until you turn it on, never reads sealed entries, and shows its sources."
- **Weekly summaries (R-23):**
  - Present each one as a visual recap: a cover photo, a short story of the week, and its people and tags.
  - Deliver it on Monday morning. That brings people back, and it's also the screenshot for your marketing.
- **Help me think (R-26):**
  - It's sold as help when you're lost for words, but a two-option answer records a fact; it doesn't help anyone write.
  - Make each answer insert a follow-up heading at the cursor ("What made it exciting?"), reusing template headings.
  - A 10-second pause (Q14) is shorter than normal thinking time.
- **Cast of Characters (R-27):**
  - This is the most distinctive AI feature, and the riskiest. An AI-written profile of a real person who never agreed to it can be wrong or hurtful.
  - Lead with facts, and make written profiles opt-in per person.
  - Add *split* for two people merged by mistake; only merge is specified.
  - Leave the friendship graph until profiles prove popular (R-37).
- **Voice notes (R-28):** the AI reads entry text only, so someone who talks instead of typing gets nothing from the biographer. The D-20 pipeline already works per media item, so audio is a small extension.

### Mobile (Phase 4)
- Phone keyboards already have a dictation key, so in-app dictation adds little (R-34).
- The "try before you trust" trial is in the PRD but in no sprint task (R-38).
- People find journaling apps in app stores. A web app can be listed on Google Play fairly cheaply; iOS needs a wrapper (R-40).

### Groups (Phase 5)
- The design is right:
  - entries stay personal
  - contributions are links, not copies
  - one seal rule covers everything
- The space isn't empty: Day One's Shared Journals and Retro already exist. Memoirist's edge is the combination: sealed until the anniversary, every friend's version, and the trip as an Era in each person's own timeline.
- An invited friend's first five minutes decide whether groups spread the app (R-32). Let them join and add photos in one step. Behind the scenes it can still create an entry in their own journal, so D-19 holds.
- Show "3 of 4 have added their side" with a gentle *nudge* button. That social pull is the feature.

### Insights and coaching (Phase 6)
- Growth reminders ("last year you worried about this, and here's how it turned out") are the most moving idea in the roadmap. Keep them.
- Pattern spotters are the closest the plan gets to therapy. Ship them last and gently, under the sensitive-content policy (R-24).
- Photos apps already make free video reels. Make memoirist's about words and people, and make them printable and giftable.
- The writing coach contradicts "no formatting, no pressure", and phones' own writing tools already rewrite text (R-36).

## 5. Competitive position
Facts and sources are in [COMPETITOR_NOTES.md](COMPETITOR_NOTES.md):
- §1: Day One
- §3: AI journals
- §8: Apple Journal
- §9: Retro
- §10: printed memories

What they mean for memoirist:
- AI summaries alone no longer stand out, because Day One's AI plan has them. Day One also added a chat, so "never a chat" is a sharper contrast than before.
- Apple Journal is free and good, but Apple-only. Its multiple journals are close to Eras filed by hand.
- Memoirist can't offer end-to-end encryption while its AI runs on the server. Describe privacy precisely, and never claim end-to-end encryption.
- People pay for print: Storyworth and Polarsteps both sell books.

**Where memoirist wins:**
- life in chapters (Eras)
- letters to future you (Loot Drops)
- trips sealed until their anniversary
- a biographer that writes instead of chatting
- it works on any device and could be printed as a book

No competitor combines these, so lead with them.

## 6. Recommendations and status
Status is **Proposed** until it's decided. Then it becomes **Accepted (D-xx)**, **Accepted in part** (naming what was accepted), **Rejected**, or **Deferred** (naming the question or phase it waits for).

### Decide before the V1 migration (task 2.3) or Sprint 3
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-01 | Eras fill themselves by date: a week is in every Era whose dates it overlaps | Q1, D-22, F-10, tasks 3.3, 3.7, 4.9 | Accepted (D-22) |
| R-02 | An Era shows only the entries inside its dates | Q10, F-10, tasks 3.3, 8.5 | Accepted (D-22) |
| R-03 | Recently deleted: a deleted entry can be restored for 30 days | D-10, F-09, V1, tasks 3.5, 3.6 | Accepted (D-30) |
| R-04 | Keyword search over titles and captions | F-16, tasks 3.9, 4.11 | Deferred to Phase 3 (D-29) |
| R-05 | Decide whether users can start their week on Sunday | D-08, task 3.5 | Deferred (Q27) |

### Launch (Phase 2)
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-06 | Email notifications: Loot Drop unlocks, a weekly recap, invites and group activity | `unlock_notified`, Sprint 5 | Accepted in part: unlock emails (D-10) and the weekly email (D-32); group-activity emails stay Proposed |
| R-07 | Move the installable-app basics (task 7.1) into Phase 2; keep offline sync in Phase 4 | Tasks 7.1, 7.2 | Accepted (D-31) |
| R-08 | An "On this day" card for memories from past years, with no AI | Phase 1 or 2 | Accepted (D-32) |
| R-09 | App lock with a passcode or Face ID | Phase 2 | Accepted (D-37) |
| R-10 | Passkeys or email sign-in links instead of passwords; sign-up takes the browser's timezone | Q2, D-07 | Deferred (Q2) |
| R-11 | An always-on server at launch | Q5 | Deferred (Q5) |
| R-12 | A readable export: HTML or Markdown with the photos, plus JSON | Q3 | Deferred (Q3) |
| R-13 | Share previews where entries can be unticked; strip GPS from photos on public pages and in groups | Task 5.3, D-19 | Deferred (Q19) |
| R-14 | Privacy-safe product metrics: counts only, never content | Sprint 5 | Accepted (D-38) |
| R-15 | Onboarding that ends with sealing a letter to yourself for next month | F-01 | Accepted (D-33) |

### Phase 1 polish
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-16 | Suggest the memory date from the photo's own date | Task 4.6 | Accepted (D-34) |
| R-17 | Starter templates, and templates that carry default tags | Tasks 3.4, 4.9 | Accepted in part: starter templates (D-06); default tags stay Proposed |
| R-18 | Raise the limit from 10 to 20 media items per entry | D-17 | Accepted (D-17) |
| R-19 | Offer the device's timezone for an entry's date when it differs from the profile's | F-13, task 4.6 | Proposed |
| R-20 | Era covers (a photo and a colour) and a date scrubber in the feed | Tasks 4.4, 4.9 | Accepted in part: Era cover photos (D-36); the colour and the date scrubber stay Proposed |
| R-21 | Make an unlock an event (notification, animation, *Reply to past you*) and suggest unlock dates | F-06, F-07, task 4.8 | Accepted (D-10, D-33); the opening animation is a design choice for DESIGN.md |
| R-22 | Write DESIGN.md before Sprint 4, mobile first, including the unlock moment | DIRECTIONS.md | Proposed |

### The AI biographer (Phase 3)
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-23 | Summarise each week automatically after it ends; "close week" becomes optional | PRD §4, task 6.3 | Rejected (D-28: closing stays manual, with a Wrap up card and a reminder) |
| R-24 | A sensitive-content policy before any AI reads entries | Phases 3 and 6 | Accepted (D-39) |
| R-25 | Build Phase 3 in this order: 6.9, 6.10, 6.3, then the rest | Sprint 6 | Proposed |
| R-26 | Help me think answers insert a follow-up heading; a longer pause or a toolbar button | Q14, tasks 6.2, 6.12 | Proposed |
| R-27 | Cast of Characters: facts first, written profiles opt-in per person, and a *split* action | Task 6.14 | Proposed |
| R-28 | Transcribe voice notes with the D-20 pipeline | D-20, tasks 6.6–6.8 | Deferred (Q24) |

### Paper diaries
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-29 | Notebook import: many pages, dated from their transcripts, with a review queue | Q13 | Deferred (Q13) |
| R-30 | Accept multi-page PDFs from the phone's own scanner | D-17 | Proposed |

### Move up or promote
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-31 | Build groups and group Loot Drops (8a) right after the first part of Phase 3, or before it | Sprint 8 | Accepted (D-31): groups now come before the AI |
| R-32 | Invited friends can join a group and add photos in one step | Tasks 8.2, 8.6 | Proposed |
| R-33 | Make print ("Era books") a paid product instead of a Phase 4 polish item | Task 7.5, Q15 | Proposed |

### Cut or postpone
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-34 | Cut voice-to-text dictation | Task 7.3, Q7 | Proposed |
| R-35 | Cut or postpone scan mode in favour of R-30 | Task 7.6 | Proposed |
| R-36 | Cut the writing coach; fold its one sharp question into Help me think | Task 9.4 | Proposed |
| R-37 | Postpone the friendship graph until profiles prove popular | Tasks 6.4, 8.8 | Rejected (the README and D-19 keep the friendship map in Phase 3; linking people to group members, task 8.8, was dropped) |
| R-38 | Schedule the "try before you trust" trial, or drop it from the PRD | PRD Phase 4, task 7.2 | Proposed |

### Business and reach
| ID | Recommendation | Touches | Status |
|---|---|---|---|
| R-39 | Pricing: a free journal, Loot Drops, groups and sharing; a paid biographer, transcription and storage at about $40–60 a year (between Retro and Day One Gold); printed books per order | Q15 | Deferred (Q15) |
| R-40 | A Google Play listing, and an iOS wrapper, before any marketing spend | PRD §6 | Proposed |
| R-41 | Short video clips (30 seconds or less) | D-17, Phase 4 | Proposed |
| R-42 | An optional one-tap mood chip | Phase 1 or 3 | Deferred (Q25) |
| R-43 | Import from Day One | PRD §6 | Parked (PRD §6, ideas for much later) |

## 7. New feature ideas
Status is **Idea** until someone picks one up. Sizes run from S (small) to L (large).

| ID | Idea | What it is | Why it's worth it | Size, when | Status |
|---|---|---|---|---|---|
| R-44 | Photo dump | Pick 60 photos and they're filed into backdated entries, one per day taken, to caption later | The memory hoarder's exact problem; nobody else files a camera roll into chapters | S–M, Phase 1–2 | Accepted (D-34) |
| R-45 | Email-in capture | Email or forward anything to a private address and it becomes an entry | COMPETITOR_NOTES §5 claims capture as easy as the bot apps, but nothing in the plan delivers it | M, Phase 2 | Idea |
| R-46 | Soundtrack | Attach the song you had on repeat; each Era gets a playlist | Music is a strong memory trigger, and a link is cheap to store | S, any phase | Accepted (D-35) |
| R-47 | Life in chapters | A zoomed-out view of all your Eras as overlapping bars across the years | Makes Eras visual and worth a screenshot, without showing private content | M, Phase 2 | Idea |
| R-48 | Closing an Era | Ending a chapter prompts for a cover, a last word, and an optional letter "to open in 5 years". From Phase 3 it also adds the chapter's summary | Turns a settings change into a ritual that links three features | S–M | Accepted: covers in Phase 1 (D-36), the ritual in Phase 4 (D-28) |
| R-49 | Era suggestions | "New names and places since March. Start a new chapter?" | Delivers "we handle the organising" for Eras | M, Phase 3 | Idea |
| R-50 | Predictions | A sealed yes-or-no bet with yourself ("Still at this job in a year?"). On unlock you answer again, and the app keeps score | Uses the two-option card style; fun and repeatable | S | Accepted (Phase 6, task 9.6) |
| R-51 | Yearly letter | A birthday reminder to seal a letter to next year's you | A reason to come back every year, with no streaks | S, needs R-06 | Accepted (D-32) |
| R-52 | A gift of memories | "Mo's birthday is Friday": your moments with Mo, gathered into a page to share or print | A gift brings memoirist to someone new | M, Phase 3+ | Accepted (Phase 6, task 9.7) |
| R-53 | AI receipts | A page listing what the AI read, when, and for which feature | Makes "private AI" visible; D-21 already records sources, so it's mostly UI | S, Phase 3 | Idea |
| R-54 | Discreet entries | Chosen entries stay blurred in the feed until tapped | Privacy on the train or at work; cheap to build | S | Idea |
| R-55 | Trip prompt | During a group's trip, one daily prompt to each member: "Today's highlight?" (a photo and a line) | Builds the shared story during the trip, not weeks later | M, Phase 5 | Idea |
| R-56 | Reveal night | When a sealed trip opens, a full-screen slideshow of everyone's entries that you can cast to a TV | The opening is the moment people will talk about | M, Phase 5 | Idea |
| R-57 | Year in review | Each December: your chapters, people, places and best photos, plus a card with no private content to post publicly | A yearly moment that spreads the app, like Spotify Wrapped | M, Phase 3 | Accepted (Phase 6, task 9.8) |
| R-58 | Write my memoir | Turn an Era, or a whole life, into an editable first-person book draft, then print it | It's what the name promises, and Storyworth charges $59–$199 for less | L, after Phase 3 and R-33 | Idea |
| R-59 | Legacy | Choose someone to receive selected Eras or Loot Drops later, such as your kids at 18 | Deep emotional value that fits "memoir"; needs careful design | L, later | Idea |

## Changelog
- 2026-10-27 Review written.
- 2026-10-28 Statuses set from the user's answers to the seven open problems and the new features (D-31 to D-39, and updates to D-06, D-17 and D-28). Day One import parked.
- 2026-10-27 Statuses set from the README sync (D-10, D-19, D-28, D-29 and Q2–Q26) and the user's answers: Eras by dates only (D-22), Recently deleted (D-30), word search staying in Phase 3, and week start as Q27.
