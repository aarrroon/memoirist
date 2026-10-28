# Design review: one last iteration before friends see it

> Reviewed on 2026-10-29: the 48 screenshots in `screenshots/` (6 directions × feed, entry, Era and Loot Drop × 1440 and 375) and the 7 boards in `screenshots/boards/`, checked against `README.md`, `docs/PRD.md` and `docs/DECISIONS.md`.

All six directions are real, distinct ideas with care in the details. Most of what's below is the same few problems repeated across the directions, often coming from the shared `content.js` and `helpers.js`. **P1** means fix before friends see it, **P2** means fix if there's time, and **P3** is polish. When you're done, re-take every screenshot and rebuild the boards.

## A. Fix in every direction

**A1. P1: The first phone screen should show the newest entry.** In all six, the first 812px is On this day, the Era cover and a week summary, and the newest entry starts below the fold (in Memoir, about 1,160px down). D-25 says the week summary is what opens when you *tap* a week, so the feed only needs a week header. The budget:
- On this day as one compact, horizontally scrolling row of about 96px.
- The Era header at no more than about 40% of the viewport on the feed. Contact sheet's compact black Era bar is the model. The Era's own page keeps the full spread.
- The week as a one-line header, such as "This week · 3 entries · Work, Walks", which opens the summary when tapped.
- Check at 375×812 that the newest entry's title and the top of its photo show on load.

**A2. P1: Week summaries must not track days.** Golden hour's seven-arc rings, Observatory's M–S dots, Colour chapters' M–S boxes and Exercise book's M–S checkboxes all show which days you *didn't* write. PRD §1.1 names "users feel guilty when they miss days" and "calendar grids" as the problem, and D-32 says no streaks, ever. Show what happened instead: counts, thumbnails and tags (D-25). Memoir's "Three entries, two photos and one voice note" and Contact sheet's film strip (a frame only for days with an entry) are the models.

**A3. P1: Sealed entries never appear in counts (D-10, CLAUDE.md rule 4).** Remove these:
- Memoir: "One more is sealed." in `weekFacts()` and "one sealed" in the contents rows.
- Golden hour: the "1 sealed" stat.
- Observatory: ", one sealed".
- Exercise book: ", and one sealed".
- Contact sheet: ", one roll undeveloped".

The locked row in the list already shows that the entry exists. Era totals and Colour chapters' weekly bars must leave sealed entries out too. If you think the count helps, raise it with Aaron instead, because D-10 would have to change.

**A4. P1: Give Write one clear button within thumb reach, and keep it apart from Edit.** On phones, Write is a small icon in a top corner in every direction. On Entry and Era pages the same pencil reads as "edit this". Golden hour's bottom bar with a central + is the model, but its own Home screen is missing it. Use a word where the direction has one: Exercise book's "New page" is the best label of all six. Put Photo dump behind Write, in a small sheet with New entry, Photo dump and Diary page. The two-squares photo-dump icon (Golden hour, Contact sheet) reads as "copy". Touch targets must be at least 44×44px.

**A5. P1: Show the sealed card in the feed.** Every direction has a sealed card in the feed, but no feed screenshot reaches it, and DIRECTIONS.md names it as one of the screens to compare. Add a second phone feed screenshot scrolled to the Grampians week, or put that card on the board.

**A6. P2: Loot Drop page.**
- Show the opening date once as the headline and once in the timeline at most. Every direction repeats it two or three times. For example, Exercise book has it on the envelope, the subtitle and the checklist, and Colour chapters on the poster, a line and the table.
- A padlock reads as "forbidden" (Memoir's band, Golden hour's disc, Colour chapters' label). A waiting object reads as "something is coming for you": the envelope, the canister, a star whose light is still on its way. Prefer the object.

**A7. P2: One word per idea.** The nav says "Eras", but pages say "Chapters inside this one" (Memoir, Observatory, Colour chapters), "Smaller books tucked inside" (Exercise book), and "Shorter rolls inside this one" and "rolls (weeks)" (Contact sheet). Let the visuals carry the metaphor, and keep labels in PRD §3's words: "Eras inside this one", weeks, entries, Opens, Written. When a label needs "(weeks)" to be understood, the metaphor is costing clarity.

**A8. P2: Spotify.** Spotify's brand rules allow its icon only in green, black or white, and want it next to Spotify content. Memoir and Observatory use grey, and Exercise book shows a music note with no Spotify icon. Golden hour (green), Colour chapters and Contact sheet (white or black) are right. Under D-35, Play loads Spotify's own embed when tapped. One screenshot of that opened state, in any direction, would show friends what to expect.

**A9. P2: Don't repeat a fact on one screen.**
- Desktop feeds show "This week" both in the column and in the rail (Memoir, Observatory, Golden hour).
- Golden hour's stories row repeats On this day and This week.
- Golden hour's entry shows its date three times.
- Observatory's entry shows the moon three times.
- Memoir's Loot Drop says "Dated Sunday 25 October 2026" and "Written Sunday 25 October 2026". Show the date once when they match.

## B. Per direction ("What works" is there so you protect it)

### Memoir
**What works:** It's the most coherent concept. Dates sit in the margin, photos are plates, each Era gets a title page, and entry pages have a running head. The Era page's contents list with dot leaders is one of the best screens in the set. One accent colour and surfaces separated by tone make it calm and private. The copy is warm and exact. It has good basics: focus outlines, 44px targets, reduced motion, and secondary text at about 5.6:1. The new "in 362 days" countdown fixed the Loot Drop's biggest gap.
- **M1 (P1, A1):** Fix the phone feed order and sizes.
- **M2 (P2, A6):** Replace the padlock on the band with the opening date printed on it, like the paper band around a new book. On the Written → Sealed → Opens line, fill in the part that has passed.
- **M3 (P2):** Italic does too many jobs: margin dates, meta, prompt labels, "Sam's journal", "1 of 4", the top-bar title and "Filed under". When everything secondary is italic, italic means nothing. Keep it for prompt labels and quotes. Set dates and meta in roman `--ink-2` with tabular figures, or in small letter-spaced capitals like a book's running head.
- **M4 (P2):** PRD §4 asks for sticky Era headers, but the feed's top bar says "Journal". Once the cover scrolls away, show the current Era there as the running head, as the entry page already does.
- **M5 (P2):** On the desktop entry page, the photo fills the first screen. Size the plate to at most about 60% of the viewport height so the first lines of writing show.
- **M6 (P3):** There's only one breakpoint (1100px), so tablets get the phone layout stretched wide. Cap the column width and check one 1024px screenshot.
- **M7 (P3):** "Written from the Trips template" adds little for the reader. Move it into an info menu.

### Golden hour
**What works:** It's the easiest to learn. The bottom tab bar with a central + works well. The Era as a profile page (a stats row, highlights, and Weeks/Photos/Tags tabs) is understood at a glance. Its Loot Drop countdown is the clearest of the six. The Spotify icon is correctly green, and "Your journal, private to you" is a good privacy cue.
- **G1 (P1, A2):** The story rings fill on the days you wrote. A ring should mean "something new to see", such as an opened Loot Drop or an On this day memory.
- **G2 (P1, A4):** The phone Home screen has no bottom bar, but Entry, Era and Loot do. Give Home the bar, and remove the top-right + and the copy-style icon.
- **G3 (P2):** Entry photos are cropped to 4:5, so the sunrise loses its sky. Keep each photo's own shape in the entry view, and crop only in grids.
- **G4 (P2):** The grey number discs (4, 2, 6, J) sit off-centre inside their rings. Check the disc and ring sizes.
- **G5 (P2):** The ring orange `#F2701D` is 2.95:1 on white, but graphics that carry meaning need 3:1 (WCAG 1.4.11). About `#E5640F` gives 3.4:1.
- **G6 (P2):** An Era without a cover is an empty grey square in the desktop rail, but a "J" disc on the Era page. Use the disc everywhere.
- **G7 (P2):** The week tiles put small white text straight onto photos. Add a gradient scrim. The Grampians Trip Week tile also shows a latte, so give it a trip photo.
- **G8 (P3):** A full-screen orange Loot Drop is glaring on a phone at night, which is when people journal. Keep it a card with margins.

### Observatory
**What works:** It's the only dark-first direction, which suits writing at night. It has the best Loot Drop idea in the set: "Its light arrives on Monday 25 October 2027 at 9:00 am", plus the moon that morning. "The same night, other years" and "beginning under a full moon" are lovely. The constellation of weeks is the most original Era visual.
- **O1 (P1, A2):** The week card has M–S dots that light up on the days you wrote.
- **O2 (P2):** The moon is beside nearly every heading and appears three times on the entry page, so it stops meaning anything. Keep it in the date line and on the Loot Drop. On the phone, the moon disc in the top bar looks like a button with no clear job.
- **O3 (P2):** In the constellation, height reads as a trend line, so people will read it as mood or activity rising. Either say what height means, or scatter the stars so it isn't a chart. Make each star open its week.
- **O4 (P2, A6):** The sealed page has no object, just text on a starfield and a lone sparkle. Make the sparkle the Loot Drop, with a faint trail showing how far its light has travelled.
- **O5 (P2):** On the entry page, four large thumbnails push the writing below the fold. Use small ones. Fold the In, Week and Template cards (a heavy box for one line each) into one quiet line.
- **O6 (P3):** There are three typefaces, and the script does a lot. Keep the script for the countdown and one or two headings.

### Colour chapters
**What works:** It's the boldest and most memorable. Colour as the Era is a real system: you always know which chapter you're in, and On this day cards carry their Era's colour. The left rail, "Your chapters, 2019 to now", is a map of your life. Archivo Extra Condensed is striking, and the Loot Drop's "25.10.27" reads like a poster.
- **C1 (P1):** The colour rule has to survive any cover, because colours come from cover photos. White on the Japan red `#C9503A` is 4.48:1, just under AA for small text, and white on the Grampians yellow would be about 1.7:1. Take the colour, adjust its lightness until text passes 4.5:1, then pick ink or white automatically. Test it with a pale pink and a near-white cover.
- **C2 (P1, A2):** The week panel has M–S boxes.
- **C3 (P1):** The year labels on the left rail are cut off ("019", "020"…).
- **C4 (P2):** On the yellow entry page, the voice note's play button looks like a plain black square, as if the triangle is missing.
- **C5 (P2):** Whole pages of saturated colour are tiring to read, and the yellow fights the sunrise photo. Keep the direction, but make one variant with the colour on the title page, header and margins and the reading column on paper. Let friends compare the two.
- **C6 (P2):** On a phone, the Loot Drop has about 600px of empty black between "25.10.27" and the details. Keep the date and the countdown together.
- **C7 (P2):** "Every week, by how much you wrote" scores the writing. Call it "Every week of this Era", and leave sealed entries out of the bars (A3).

### Exercise book
**What works:** It's the warmest and most personal, and it ties in with photographing paper diary pages (D-20). Text sits on the feint rules, dates are handwritten in the red margin, photos are taped prints, and index tabs on the page edge work as navigation. "New page" is the best Write label. Its Loot Drop is the most human: a taped envelope saying "Don't open until…!" and a sticky note saying "362 days to go". Atkinson Hyperlegible for body text is an excellent, accessible choice.
- **E1 (P1, A2):** The M–S checkboxes with red ticks are a habit tracker, the "homework" feeling PRD §1.1 wants gone. An exercise book already suggests school, so don't add ticks.
- **E2 (P1):** The red seal on the envelope reads as a + (an Add button or a first-aid cross). Draw a wax seal or a sticker instead.
- **E3 (P2):** The Loot Drop's checklist (Written ✓, Sealed ✓, Opens ☐, Write back ☐) turns replying into a task, but D-33 makes it optional. Keep the pen marks and drop the boxes.
- **E4 (P2):** Kalam struggles with long titles: "The Brunswick Years" breaks onto three lines on the phone. Test "Walked home the long way through Princes Park" at phone width. Or set titles in the print face and keep the hand for dates, captions and labels.
- **E5 (P2):** "Book 3" beside the logo is cryptic. Write "Book 3 · The Brunswick Years", or drop it.
- **E6 (P2):** Check the contrast of the white index-tab labels. The pink Journal tab and the mustard Templates tab look under 4.5:1.
- **E7 (P3):** Text on the rules needs every block to be a multiple of 32px tall. Taped prints that break the rules on purpose are the way out. Note it for the build.

### Contact sheet
**What works:** It's photo-first and crafted. The feed's compact black Era bar is the best Era header on any feed (the A1 model). A week as a film strip, with frames only for the days you wrote, shows the week without showing gaps (the A2 model). The Loot Drop metaphor is natural: a canister labelled "Do not develop till Oct 2027", and in the week's roll, "The last frame stays dark until it develops". It's sealed by design and leaks nothing. On this day as slides in card mounts, and one red accent used with discipline, are both good.
- **K1 (P1):** The red circles mean "frames you kept", but memoirist has no favourites (PRD §3), so friends will ask how to circle one. Tie the circle to something real, such as the photo chosen as a cover, or drop it.
- **K2 (P1):** A dark frame means two things. It's the sealed entry on the Loot Drop's roll, but text-only entries are dark frames too ("Post-trip blues" on the feed, and the Era page's rolls). Give text-only entries a light frame with their title, and keep the dark frame for sealed entries.
- **K3 (P2, A7):** Film words replace product words: rolls (weeks), frames, Exposed, Develops and Prints. Keep the film in the pictures and the product words in the labels.
- **K4 (P2):** "No. 26" in lists reads as an entry number, and two entries on one day both become "No. 24". Use the weekday in lists ("Sat 24"), and keep frame numbers on the strip's edge.
- **K5 (P2):** The red crop marks cover the corners of the user's photo. Put marks on the mount, never on the photo. The loupe repeats the same photo, so drop it or make it a real tap-to-zoom.
- **K6 (P2):** Entry titles are in the grease-pencil font, which breaks the direction's own rule ("only for marks you would make by hand"). Set titles in Plex.

## C. The boards friends will see
- **C1. The overview compares the wrong screen.** Each phone shows On this day, the Era cover and the week summary. All six look like the same three widgets in different paint, and no entry shows. After A1, show two phones per direction: the feed with an entry in view, and the sealed Loot Drop, which is each direction's most distinctive screen.
- **C2. Keep the names and "Suits" lines on the overview** (Aaron's choice). Make sure each "Suits" line describes the design honestly once A1–A5 change it.
- **C3. Ask questions people can answer on instinct:** "Which would you open at 11pm to write about your day?", "Whose sealed Loot Drop makes you most want to seal something?", "Which feels most private?" and "What would you change?" Ask for a top two, not a ranking of six.
- **C4.** "Open the clickable version" can't be clicked in a JPG. Share the boards as one page (HTML or PDF) with working links, or remove the line. Also drop internal notes such as "From DIRECTIONS.md: Quiet editorial".
- **C5. Keep** the photo credits in the footer (CC BY-SA requires them), the realistic sample journal, and the same content in all six.

## D. Scope of this iteration
- **Don't add** a Write (capture) screen or dark mode this round. Aaron will have both built for the two directions friends pick, as DIRECTIONS.md step 2 describes.
- Spend the time on the P1s (A1–A5 and the P1s in section B), then the P2s. Leave the P3s for later.
- Don't add new screens, features or copy that the PRD doesn't describe.
