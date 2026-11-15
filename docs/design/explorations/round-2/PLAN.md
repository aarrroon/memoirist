# Round 2 plan

Planned 2026-11-10 from [FEEDBACK.md](../FEEDBACK.md) "Round 1 feedback", [REVIEW.md](../REVIEW.md) and round 1's boards. Five designs, numbered 7–11 so friends never mix them up with round 1's 1–6. Each design has two colour themes (a and b), each in its own light and dark mode, and six screens.

## 1. What the feedback means

| Keep | Change | Avoid |
|---|---|---|
| 4's familiar feed: bottom tab bar on phones, full-width photo cards, the Era as a profile-like page | Give the familiar feed one signature of its own per design, so it stops reading as Instagram | Orange anywhere (4) |
| 3's structure: Eras as constellations, a Loot Drop as light still on its way | 3's very dark night becomes a lighter night (dark grey-blue, not black) with a real light mode | Very dark as the only mode (3) |
| 2's calm, its type and its dates in the margin | Make 2 more modern: a sans for the interface, fewer italics, bigger photos | Whole pages of saturated colour, condensed poster type (5) |
| 6's Loot Drop as a waiting object with personality (the canister) | Every Loot Drop becomes an object that is clearly waiting, with no film | Film strips, tape, sticky notes and other photo props (6); school and paper looks (1); padlocks |
| 6's neutral greys with one strong accent | Every palette is neutral with one accent; colour sits on actions, Loot Drops and data | Two accents fighting; colour floods |
| Both modes for every design (Aaron's answer) | Design each mode on its own: dark grey rather than black, off-white text, softer accents | Inverting light into dark |

Round 1 rules every design keeps are in the round prompt (first phone screen shows an entry, no day tracking, sealed entries never counted or leaked, one Write button, PRD words, Spotify in green, black or white, AA contrast, visible focus, reduced motion, no emoji, nothing the PRD doesn't describe).

## 2. Colour

Four palettes from FEEDBACK.md and **one new one**, so 10 theme slots hold each palette exactly twice, on two different designs. Friends can then judge a palette apart from a layout.

| Palette | Designs | Why there |
|---|---|---|
| Dusk blue | 8 Starlight (a), 9 Nightstand (a) | Observatory's night, softened; the natural home of both night-feeling designs |
| Bookcloth green | 10 Folio (a), 11 Keepsake (b) | Memoir's green, made modern; also tests it on a photo-first layout |
| Ink and red | 11 Keepsake (a), 10 Folio (b) | Contact Sheet's colours without the film; on Folio it reads like a book's red rubric |
| Black, white and gold | 7 Afterglow (b), 8 Starlight (b) | A neutral base for the familiar feed; gold only on Loot Drops, which suits starlight |
| **Sunset rose (new)** | 7 Afterglow (a), 9 Nightstand (b) | See below |

**Sunset rose, proposed.** Golden Hour (9/10 for design) lost points only for its orange. Its idea, the warm light at the end of a day, survives in the afterglow: the pink that follows the gold. All four agreed palettes are cool or neutral; this gives friends one warm choice that is not orange, and it echoes journaling apps that use a single pink (Rosebud, FEEDBACK.md research). The tints in the neutrals are kept faint so the dark mode reads as warm grey, not plum. Checked with WCAG formulas (`M.contrast`, shared/helpers.js):

| Mode | Page | Surface | Ink | Ink 2 | Accent | On accent | Loot Drop |
|---|---|---|---|---|---|---|---|
| Light | #F7F4F5 | #FFFFFF | #1C1619 | #5C5157 | #A8325A | #FFFFFF | #A8325A |
| Dark | #171315 | #221C1F | #F1E9EC | #B4A7AD | #F09DB8 | #1A1216 | #F09DB8 |

Light: ink 16.3:1, ink 2 6.9:1 on page, accent 5.9:1 on page, white on accent 6.4:1. Dark: ink 15.4:1, ink 2 7.9:1, accent 9.0:1, on accent 9.0:1. A second new palette was not needed: five palettes fill ten slots evenly.

Designs may tune a palette's values if they keep the family and the contrast, and must note it in REPORT.md.

## 3. Shared conventions (every design)

- **File:** `round-2/directions/<id>.html`, loading `../../shared/content.js` then `../../shared/helpers.js`.
- **URL:** `?theme=<a|b>&mode=<light|dark>#<feed|entry|era|loot|write>`. No parameters: theme a, mode from `prefers-color-scheme`. `M.applyTheme()` sets `data-theme` and `data-mode` on `<html>`; `M.mountApp(render)` adds the `write` screen to round 1's four.
- **Tokens:** CSS variables in `:root`, overridden in `[data-theme="a"][data-mode="light"]`, `[data-theme="a"][data-mode="dark"]`, `[data-theme="b"][data-mode="light"]` and `[data-theme="b"][data-mode="dark"]`. Each block is designed on its own, not inverted.
- **Screens:**
  - `#feed`: On this day as one short row; a compact Era header; each week a one-line header that opens its summary (D-25); entries in day order (F-08). The Grampians week holds the sealed card (`data-sealed` on it).
  - `#entry`: Sunrise at the Pinnacle (`M.featured.entry()`), photos in their own shape, text visible on the first laptop screen.
  - `#era`: The Brunswick Years (`M.eraPage`), with "Eras inside this one".
  - `#loot`: the sealed Loot Drop page, the opening date once, a waiting object.
  - `#write`: the capture view, mid-entry (`M.data.draft`, F-02/03/04/06/11). Full-screen sheet on phones; a panel or dialog over the dimmed feed on laptops. Shows the memory date, an optional title, formatted text (template headings, bold, a list), photos in a staging strip, a voice note, tags, the template it started from, a soundtrack, and *Seal as Loot Drop* with its quick dates (`M.sealSuggestions()`: in a month, in a year, on my birthday). Save is the one primary action; it never looks like Edit.
- **Sealed card:** a fixed size. Shows only the memory date, the opening date and the countdown, from `M.sealedView(entry)`, which returns nothing else.
- **Write:** one button within thumb reach, at least 44×44 px, opening New entry, Photo dump and Diary page (`M.writeMenu`, or the design's own menu with the same three items). A plus or a word, never a pencil.
- **Spotify:** the mark is green (#1DB954 on light only where it passes as a graphic), black or white. Tapping Play would load Spotify's own embed (D-35); not shown this round.

## 4. The designs

### 7 Afterglow: Golden Hour refined
**Idea:** the familiar feed, lit by the pink that follows sunset; your entries arrive like posts from yourself.
- **Keeps from 4:** the bottom tab bar with a central Write, full-width photo cards, the Era as a profile page with its stats row and tabs, and the clearest countdown. People already know how to use it (Aaron: 9/10).
- **Its own signature:** the **day stamp**. Each card's header is a tall date block (weekday over a large day number) set in the display face, aligned on the left edge of the feed so the column of stamps reads as the week's spine. Only days with an entry get one, so it never shows a gap. This replaces Instagram's avatar-and-name row, the most Instagram part of round 1.
- **Layout 375:** top bar (wordmark, search-free), On this day as one row of two small rounded thumbnails with "One year ago" captions, the Era as a 56 px bar (cover disc, name, dates), "This week · 3 entries" one-liner, then cards. Bottom bar: Home, Timeline, Write (centre, filled), Eras, You.
- **Layout 1440:** left nav rail (240 px) with the Write button at its top, centre feed column 560 px, right rail with On this day and the week summary card (no repeat of the week line in the column: the column shows only the week header).
- **Fonts:** Bricolage Grotesque (stamps, titles), Figtree (text).
- **Loot Drop object: a wrapped parcel.** A small square parcel tied with string, with a luggage tag. The tag carries the opening date in the feed card; on the page the tag carries "Opens" and the parcel sits on its own shadow. Personality: the tag tilts a few degrees and swings once on load (not with reduced motion). The countdown "in 362 days" sits large beside it. The sealed feed card is a fixed-height card: parcel, memory date, "Opens Mon 25 Oct 2027", "in 362 days".
- **Themes:** a = Sunset rose, b = Black, white and gold (gold on the parcel's string and tag only).

### 8 Starlight: Observatory softened
**Idea:** a journal under Melbourne's sky, with dusk instead of midnight; an Era is a constellation and a Loot Drop is a star whose light is still travelling.
- **Keeps from 3:** the structure Aaron rated 9.5/10: the date line with the real moon phase, the constellation of weeks on the Era page (O3 fixed: stars scattered, not a chart; each opens its week), "The same night, other years", and the starlight Loot Drop (Thai's favourite direction).
- **Changes:** the night is dusk blue (#11151D), not black; a real light mode as a pale morning sky; IM Fell English (hard to read small) replaced; the moon only in the date line and on the Loot Drop (O2); small thumbnails on the entry (O5).
- **Layout 375:** top bar with wordmark; date line "Wednesday night · waning gibbous"; On this day row ("The same night, other years"); compact Era header with cover; one-line week header; cards. Write is a bottom-right round button with the word.
- **Layout 1440:** left nav, centre column 620 px, right rail with On this day.
- **Fonts:** Fraunces (titles, the countdown in italic), Hanken Grotesk (text).
- **Loot Drop object: the travelling star.** On the page, a bright star at the top of the screen with a faint trail curving down to "you" (a small dot at the foot), the trail lit for the part of the journey already travelled (1 day of 363). "Its light arrives on Monday 25 October 2027 at 9:00 am" said once. Personality: the star twinkles slowly (reduced motion: static). Sealed card: a small star with its trail, memory date, opens, countdown.
- **Themes:** a = Dusk blue (Loot Drop pale gold #EFD9A0 in dark), b = Black, white and gold.

### 9 Nightstand: the familiar feed at night
**Idea:** 4's layout with 3's mood: the journal you reach for on the bedside table at 11 pm.
- **Keeps from 4:** the familiar card feed and bottom bar. **From 3:** the night mood and calm, low-contrast chrome. Dark-first, with a light mode designed as a lamp-lit morning, not an inversion.
- **Its own signature:** the **week fan**. Each week's one-line header carries a small fan of up to three overlapping thumbnails from its open entries (never from sealed ones) and opens the summary. The feed reads as stacks of nights rather than posts.
- **Layout 375:** top bar with wordmark and the time of day ("Wednesday, 7:42 pm") as quiet text; On this day row; compact Era header; week fan headers; cards with a rounded photo and title under it. Bottom bar: Home, Timeline, Write, Eras, You.
- **Layout 1440:** two-column: feed 600 px plus a right column with On this day and the Era card, left icon-and-label nav.
- **Fonts:** Outfit (titles), Work Sans (text).
- **Loot Drop object: an hourglass.** Sand mostly still in the top bulb, a thin stream falling, the bottom just covered: the part of the wait already passed (1 of 363 days). Personality: grains fall slowly (reduced motion: still). The sealed card shows a small hourglass, memory date, opens and countdown.
- **Themes:** a = Dusk blue, b = Sunset rose.

### 10 Folio: Memoir made modern
**Idea:** your life typeset as a modern book: plates, a running head and dates in the margin, with an app's ease.
- **Keeps from 2:** the calm, the bookcloth title page for each Era, the contents list with dot leaders on the Era page (one of round 1's best screens), dates in the margin (Aaron: 8/10, likes the design).
- **Changes (modern, and REVIEW M2–M5):** a sans for the interface and meta, the serif only for titles and entry text; italics only for prompts and quotes; the current Era as the sticky running head (M4); bigger plates, edge to edge on phones; a bottom bar like 4's so it is as easy as 4.
- **Layout 375:** running head (Era name), On this day row, compact Era band, week line, entries with margin dates folded above the title on phones. Bottom bar: Journal, Timeline, Write, Eras, You.
- **Layout 1440:** left nav, a 680 px text column with a 120 px date margin, right rail for On this day.
- **Fonts:** Newsreader (titles and entry text), Instrument Sans (interface).
- **Loot Drop object: a sealed volume.** A slim closed book standing upright, its ribbon marker hanging out of the bottom, and a paper band around it printed with the opening date (the band M2 asked for, no padlock). Personality: the ribbon sways when the page opens. The sealed card: a small book spine, memory date, opens, countdown.
- **Themes:** a = Bookcloth green, b = Ink and red.

### 11 Keepsake: the wildcard
**Idea:** a modern, photo-first box of kept things in neutral grey with one strong accent, where a Loot Drop is a time capsule you can almost pick up.
- **Built from the feedback:** 6's colours (8/10) and its object-like Loot Drop without the film; 4's ease; none of 6's photography props.
- **Its own signature:** **big day numbers**. Each entry starts with its day number set very large in the accent-free ink ("24", with "Sat" beside it), and the week line is a row of square thumbnails, one per open entry (text-only entries as a typographic tile with their title). It reads like a modern Swiss catalogue.
- **Layout 375:** top bar with wordmark; On this day row; compact black-or-ink Era bar (6's best feed Era header); week line; entries. Write is a bottom-right "New entry" button.
- **Layout 1440:** left nav, centre 600 px feed, right rail with On this day and the week's thumbnails.
- **Fonts:** Archivo (normal width only) for everything; tabular figures for the day numbers.
- **Loot Drop object: a time capsule.** A metal capsule (a cylinder with a screw cap and a printed label band in the accent colour carrying the opening date), lying at a slight angle on its own shadow. Personality: the cap shows its thread; on load it settles with a small roll (reduced motion: still). The sealed card: a small capsule, memory date, opens, countdown.
- **Themes:** a = Ink and red, b = Bookcloth green.

## 5. Screenshots and boards

**Each build saves** (`round-2/screenshots/<id>/<theme>-<mode>/`):
- Theme a, light and dark: `feed`, `entry`, `era`, `loot`, `write` at `-375.jpg` (375×812, 2x, mobile) and `-1440.jpg` (1440×900, 1x), plus `feed-sealed-375.jpg` (`scrollTo: "[data-sealed]"`, `scrollOffset: 300`).
- Theme b, light and dark: `feed-375.jpg`, `entry-375.jpg`, `loot-375.jpg`.

**Boards** (`round-2/index.html`, exported to `round-2/screenshots/boards/` at 1600 px wide, 1.5x):
| File | Contents |
|---|---|
| `0-overview.jpg` | For each design: phone feed light and dark (theme a), its sealed Loot Drop (theme a light), number, name, who it suits. The four questions; a top two, not a ranking. Credits |
| `1-colours.jpg` | One row per palette: its swatches, then the phone feed in light and dark on both designs that use it. Credits |
| `7a-afterglow.jpg` … `11a-keepsake.jpg` | Theme a light: feed, feed scrolled to the sealed card, entry, Era, Loot Drop, Write (6 phones); the laptop feed and entry; idea, fonts, swatches. Credits |
| `7b-afterglow.jpg` … `11b-keepsake.jpg` | Theme a dark: feed, entry, Loot Drop; theme b light and dark: feed, entry, Loot Drop (9 phones); the laptop Write; Good and Watch out. Credits |

No file names, round numbers or internal notes on any board.

## 6. Practice blocks
- `applyTheme` in `shared/helpers.js`: reading URL parameters and the device's colour-scheme preference, with a fallback.
- `contrast` in `shared/helpers.js`: the WCAG relative-luminance formula, checked by `console.assert` lines on the round's palettes.

## 7. Allowances
The taste skill's app-screen rules were written for dashboards. Allowances recorded for this round, with reasons (builds append theirs below):
- **Photos with rounded corners** where a design calls for them (7, 9): the familiar social feed is the point of those designs.
- **A second typeface** per design: the display face carries each design's identity (stamps, titles), as round 1's feedback asked for "a signature of its own".
- **A filled accent Write button:** the product's one primary action, which REVIEW A4 requires to be findable within thumb reach.
- **Drawn Loot Drop objects** (SVG): no honest photo of a sealed future exists; they are the point of the feedback.
- **Shadows on the Loot Drop object** only: it is an object resting on a surface, by design.
- **11 Keepsake, day numbers at 56 px (phone) and 64 px (laptop)** (inspector `kpi-size`): the big day number is this design's signature (§4); it labels a day of entries, it is not a dashboard figure.
- **11 Keepsake, Era title at 32 px on phones and laptops** (inspector `app-title-size`): the Era page is the cover of a life chapter, and its title is that page's one headline under the cover photo.
- **11 Keepsake, the Loot Drop stage at 1440 holds open space** (inspector `hero-void`, `panel-void`): the capsule rests alone on its own surface with its shadow; the space around the waiting object is the point, and the page has nothing else true to put there.
- **11 Keepsake, a shadow on the Write menu** (lint `shadow`): it is a real overlay that floats above the feed.
- **7 Afterglow, `below-fold` on the laptop feed and Write (WARN):** a feed is a scrolling list of entries, so only the newest card starts on the first screen; Write scrolls inside its dialog, and its first screen holds the date, title and text that F-02 starts with.
- **7 Afterglow, `hero-void` on the laptop entry and Era (WARN):** the whole entry fits on one screen, so the space below it is the end of a short page; on the Era page the "void" is the inside of the week rows (text left, thumbnails right) beside the short "Eras inside this one" column.
- **7 Afterglow, `icon-chips` on the dark Write (WARN):** the three 44 px circles are the voice notes' Play buttons (real controls), not decoration.
- **7 Afterglow, feed h1:** the wordmark is the feed's one h1, rendered in whichever of the phone top bar or the laptop side nav is showing (chosen by `matchMedia`, re-rendered when the width crosses 900 px), so the screen never has a hidden first h1.
- **10 Folio, covered text under the phone tab bar** (`covered-text`, feed at 375): the inspector samples fixed scroll steps, and at one of them an entry title ("Mackenzie Falls on the way out") sits behind the fixed bottom bar (Journal, Timeline, Write, Eras, You) that the brief asks for. Content scrolling under an app's tab bar is normal; nothing is pinned over it at rest.
- **10 Folio, the journal under the Write dialog** (`covered-text`, Write at 1440): the brief puts Write on laptops in a dialog over the dimmed feed, so the feed's titles lie under the scrim by design. The feed behind is `inert` and `aria-hidden`, and its running head is not an h1, so the dialog's "New entry" is the screen's only h1.
- **10 Folio, the Era title page at 44 px** (`app-title-size`, Era at 1440 and 38 px at 375): the bookcloth title page is the design's keepsake from round 1's Memoir (Aaron 8/10); a book's title page is display-sized. Entry titles were brought down to 28 px (phone) and 34 px (laptop) instead.
- **10 Folio, Loot Drop page centred** (`hero-void`, `unused-canvas`, Loot at 1440): the page is one object and its words, centred as a pair in the canvas; the empty band below is the screen's one rest, not a missing column.
- **9 Nightstand, covered text on #write at laptop width:** the inspector reports "Wednesday, 7:42 pm" hidden under the Write dialog. The brief asks for a dialog over the dimmed feed, so the feed's time line sits under the scrim and the sheet by design; the background is `inert`, and its line is a `<p>`, not a second `h1`.
- **9 Nightstand, overlay shadows:** the Write menu and the Write dialog keep a shadow (marked `taste-skill-ignore`); both are real overlays. The dialog's shadow is under 8% black, with a 1px edge doing the separating.
- **9 Nightstand, sealed card date beside a day header:** on Sunday 25 October the sealed card's memory date sits under the same day header as the open entry before it. The sealed-card rule requires the memory date on the card, so it stays.
- **8 Starlight, `covered-text` on #write at 1440:** the brief puts the Write panel over the dimmed feed, so the feed's "Wednesday at dusk" and the sealed card's "in 362 days" lie under the dialog by design; the feed underneath is `inert` and `aria-hidden`.
- **8 Starlight, `icon-chips` (WARN) on #write at 1440:** the three tinted circles are voice-note play buttons (the draft's and the dimmed feed's), real controls in the one accent colour, not decorative icon tiles.
- **8 Starlight, `unused-canvas` (WARN) on #write at 1440:** the canvas measured is the Write dialog, which is centred in the window with equal margins over the dimmed feed; the inspector measures it from the sidebar behind it.
- **8 Starlight, the On this day row at 375:** changed from a side-scrolling row (which bled past the edge) to two half-width items, so both memories show in one short row with no text at the screen edge; the year moves to the laptop rail only.
- **10 Folio, the sealed card's "Written Sun 25 Oct" under Sunday's margin date** (feed, scrolled to the sealed card): the Loot Drop shares Sunday with Mackenzie Falls, so the day appears in the margin and again on the card. The sealed-card rule requires the memory date on the card, so it stays (as in 9 Nightstand).
