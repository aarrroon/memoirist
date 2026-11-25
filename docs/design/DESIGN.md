# memoirist design

The look of memoirist: design 12, *Starlight refined* (D-41), inside the limits of D-40. The rules that are checked on every screen are in [`.claude/rules/design.md`](../../.claude/rules/design.md); this file holds the values and the components they apply to.

- **Picture it:** the mockup is [`explorations/round-3/directions/starlight.html`](explorations/round-3/directions/starlight.html). Open it with `#feed`, `#entry`, `#loot` and so on, and add `?mode=dark` for dark mode. Its screenshots are in `explorations/round-3/screenshots/`.
- **Build from this file, not from the mockup's code.** The mockup is plain HTML with sample data. The app uses Tailwind tokens, shadcn/ui and lucide-react (D-13).
- **Phone first.** Every screen is designed at 375 × 812 first. The laptop layout follows the phone and never drives it.

## 1. Colour tokens

Black, white and gold. Gold is the one accent: the primary action, links, focus, the selected item and Loot Drops. It never colours headings, body text or decoration. Photos carry the colour; the interface stays neutral. Dark mode is designed on its own: charcoal, not black, off-white text and a softer gold. The Spotify mark takes `--ink`, so it is black in light mode and white in dark.

| Token | Light | Dark | Used for |
|---|---|---|---|
| `--page` | `#FFFFFF` | `#161616` | The page, the top bar, the tab bar |
| `--surface` | `#F4F4F3` | `#1F1F1F` | The sealed card, the first letter, the seal panel, the selected nav item |
| `--raised` | `#EBEBE9` | `#292929` | Hover on menu rows, an Era's initial when it has no cover |
| `--ink` | `#111111` | `#EDEDED` | Text, icons, the Spotify mark |
| `--ink-2` | `#5B5B5B` | `#A3A3A3` | Secondary text, unselected tabs, chevrons |
| `--line` | `#E2E2E0` | `#2E2E2E` | Hairlines between entries and rows (decorative) |
| `--control` | `#8A8A88` | `#6B6B6B` | Borders of buttons, chips, fields and radios; the star's resting trail; swipe dots |
| `--accent` | `#8F6614` | `#E3C06A` | Primary buttons, Write, focus rings, the selected chip and segment, the active dot, Loot Drops |
| `--on-accent` | `#FFFFFF` | `#161616` | Text and icons on the accent |
| `--danger` | `#B42318` | `#F97066` | An error under a field, and the button of a destructive confirmation. Nothing else |
| `--scrim` | `rgba(17,17,17,.48)` | `rgba(0,0,0,.6)` | Behind a dialog or a sheet, over everything including the nav |
| `--band` | `#111111` | `#292929` | An Era's title band |
| `--on-band` | `#FFFFFF` | `#EDEDED` | Text on the band |
| `--on-band-2` | `#C8C8C6` | `#A3A3A3` | Secondary text on the band |
| `--moon-lit` | `#D6D6D3` | `#EDEDED` | The lit part of the moon in the date line |
| `--moon-dark` | `#6E6E6C` | `#3A3A3A` | The dark part of the moon |

Measured contrast (WCAG 2), light / dark:

| Pair | Light | Dark | Needs |
|---|---|---|---|
| ink on page | 18.88 | 15.46 | 4.5 |
| ink-2 on page, surface, raised | 6.79, 6.17, 5.69 | 7.17, 6.53, 5.77 | 4.5 |
| accent on page, surface | 5.15, 4.68 | 10.35, 9.43 | 4.5 (it is also used as text) |
| on-accent on accent | 5.15 | 10.35 | 4.5 |
| control on page, surface | 3.46, 3.14 | 3.40, 3.09 | 3 (WCAG 1.4.11) |
| danger on page | 6.57 | 6.50 | 4.5 |
| on-band-2 on band | 11.27 | 5.77 | 4.5 |

Don't put accent text on `--raised` in light mode (4.26:1), and don't put a `--control` border on `--raised` in dark mode (2.7:1).

## 2. Type

Two typefaces, served by the app as `woff2` (D-40): **Instrument Sans** 400 and 600 for text and the interface, entry text included, and **Newsreader** 500 for titles only. Both are SIL Open Font License fonts. Task 4.1 added them as Latin woff2 files from Google Fonts, the source the user chose on 2026-11-24, in `frontend/public/fonts/` with each font's licence. That is two files, 89 KB together: one variable Instrument Sans holds 400 and 600, and Newsreader 500 keeps its optical-size axis, so titles get the display cuts. Instrument Sans is preloaded.

| Token | Size | Line height | Face and weight | Used for |
|---|---|---|---|---|
| `meta` | 12 px | 1.5 | Instrument 400 | Tab labels, "One year ago", counts under stats, "Draft saved 7:41 pm" |
| `small` | 14 px | 1.5 | Instrument 400 or 600 | Rows' second lines, the day line, the week line, tags, chips, the opening date |
| `body` | 16 px | 1.5 | Instrument 400 or 600 | Feed captions, buttons, list titles, settings rows |
| `read` | 18 px | 1.6 | Instrument 400 | An entry's own text in the entry view |
| `title` | 20 px | 1.2 | Newsreader 500 | Entry titles, the moon date line, section titles, the countdown on a sealed card, prompts in an entry |
| `heading` | 28 px | 1.2, −0.01em | Newsreader 500 | Screen titles (Timeline, Eras, a week's name, an entry's title in the entry view) |
| `display` | 40 px | 1.2, −0.02em | Newsreader 500 | Once a screen at most: an Era's name on its band, the countdown on a Loot Drop page |

Bold is 600. No italics in the interface; italic in an entry's own text is Instrument Sans italic, drawn by the browser. A title wraps to a second line before it is cut, and a feed caption is cut after three lines.

## 3. Space, shape and layout

- **Spacing:** 4, 8, 12, 16, 24, 32, 48, 64 px only. 8 inside a group, 16 between blocks, 32 between sections.
- **Corners:** `sm` 8 px (thumbnails, inputs, menu rows), `lg` 16 px (cards, the sheet's top, photos on tablet and laptop), and full (buttons, chips, the Write pill, dots, avatars). Feed photos on a phone have no corners: they run edge to edge.
- **Shadow:** one, `--shadow-float` (`0 16px 48px` at 18% black in light, 50% in dark), only on the Write menu, the seal panel, a dialog and the laptop swipe arrows.
- **Boxes** nest two deep at most. A row that is only a link (the Era and week line, a settings row) is not a box: no fill, no border, a hairline between rows.

| Width | Layout |
|---|---|
| Phone (below 48rem) | Top bar 56 px, tab bar 64 px, 16 px gutter. Photos edge to edge |
| Tablet (`md`, 48rem) | The phone layout in one column 640 px wide, centred, 24 px gutter. Photos get `lg` corners |
| Laptop (`lg`, 64rem) | A 264 px side nav with the moon line, Write and the places; the feed column (a 96 px date margin, a 32 px gap, then the entries) up to 696 px; On this day in a 320 px right rail; 48 px between columns; 32 px gutter. No top bar or tab bar. Reading text 680 px at most |

## 4. Components

### Shell
- **Top bar** (phone, 56 px, sticky, `--page`): on the feed, Timeline, Eras and You it holds the **moon date line**, a 24 px moon in the phase of the user's "today" (lit on the right when waning, as seen from Melbourne) and "Wednesday at dusk" in `title`. The part of the day is *morning* to 12:00, *afternoon* to 17:00, *at dusk* to 21:00, then *at night*. It replaces the wordmark. Inner screens show a back link naming where it goes (`body` 600, with an arrow) and at most one quiet action on the right (Edit).
- **Tab bar** (phone, 64 px, fixed, `--page`, a hairline on top): five equal columns, Journal, Timeline, **Write**, Eras, You. Each is a 20 px icon over a `meta` label in `--ink-2`; the current one is `--ink` and 600. Write is a 48 × 28 accent pill with a plus, over the label "Write" in `--ink` 400. It opens a menu above it: New entry, Photo dump, Diary page.
- **Side nav** (laptop): the moon line; Write as a full-width accent pill with the same menu; Journal, Timeline, Eras, Templates; at the foot Recently deleted and You. The current item has a `--surface` fill and 600.

### Feed
- **On this day row**: 64 px, a 40 px thumbnail (`sm`), "On this day" (`small` 600) over "First night in the big room, and 1 more" (`small`, `--ink-2`), and a chevron. Tapping opens the list in place; the chevron turns down.
- **Opened row** (D-10): while a Loot Drop has just opened it takes On this day's place: the star mark in `--accent`, "A Loot Drop opened" over its display title, and a 44 px dismiss button. On this day moves below the first entry until the row is dismissed.
- **Era and week line**: 48 px, sticky under the top bar (at the top of the column on a laptop), `--page` with a hairline below. On the left the Era's 32 px cover (or its initial) and name (`small` 600) open the Era; on the right "This week · 3 entries" (`small`, `--ink-2`) with a chevron opens the week summary. Either side wraps to two lines before it is cut. A week in several Eras shows `eras[0]` here (D-22).
- **Entry in the feed**: the day line ("Monday 26 October", or "Today"; `small` 600) only on the first entry of a day; the **swipe**; then, 12 px below, the title (`title`, a link), the caption (`body`, three lines), the voice note row, the soundtrack row and the tags (`small`, `--ink-2`, each a link to the timeline filtered by it). An entry with no title and no caption shows its display title (D-24) in `small` `--ink-2`.
- **Between entries**: 24 px, a full-width `--line` hairline, 24 px.
- **On a laptop** the day moves into the margin: the weekday (`meta`) over "26 Oct" (`title`), right-aligned beside the photo.

### Swipe (photos)
- One row of photos that scrolls sideways and snaps to each photo, full width on a phone. Its height is reserved from the first photo's shape (`width` and `height`, D-17): the shape is kept between 4:5 tall and 1.91:1 wide, and the rest of the photos are cropped to it. A photo with no size gets 4:5.
- With more than one photo, 6 px dots sit 8 px below it, the current one in `--accent` and the rest in `--control`. The dots are decoration; the swipe is a group labelled "Photos, 4 photos", and each photo has its alt text.
- On a laptop, 44 px round arrows (`--page`, `--shadow-float`) sit inside the left and right edges. The arrow keys move it when it has focus.
- In the **entry view** every photo is shown whole (`contain`, on `--page`), in a frame with the first photo's shape.
- Only the first photo on a screen loads eagerly (`fetchpriority="high"`); every other image is lazy.

### Voice note and soundtrack
- **Voice note row**: a 44 px round play button with a `--control` border, a waveform of 3 px bars in `--ink-2` (32 bars, at most 240 px wide), and the length in `meta`.
- **Soundtrack row**: the Spotify mark (20 px, `--ink`), "**Holocene**, Bon Iver" in `small`, and a quiet *Play* button. The player loads only when Play is tapped (D-35).

### Loot Drops
- **The drawing**, the one approved Loot Drop object (D-39, D-41): the *travelling star*. See §5 for its geometry.
- **Sealed card**: one fixed size, 112 px tall, full width inside the gutter, `--surface`, `lg` corners. A 128 px column holds the drawing at the card's progress; beside it, the opening date ("Opens Mon 25 Oct 2027", `small`, `--ink-2`) over the countdown ("in 362 days", `title`, `--accent`). When it is not under a day line, the memory date ("Sunday 25 October", `small` 600) comes first (D-10). It is one link to the Loot Drop page, labelled with everything it shows.
- **Sealed row** (week summary, timeline, Recently deleted): a 16 px star mark in `--accent` and "Loot Drop", with the memory date and "Opens 25 Oct 2027". Never a title, tags, media or a padlock.
- **Loot Drop page**: the drawing at the column's width with the past part of the trail lit, "Written 25 Oct 2026" under the star and "You" under the end (`meta`); then the countdown in `display` and `--accent`, the opening moment once in `title` ("Opens Monday 25 October 2027 at 9:00 am", with "9:00 am" kept together), a line saying nobody can open or change it before then, and *Extend the seal* (secondary) and *Delete* (quiet). On a laptop it is one block, at most 640 px wide, centred.
- **Opened Loot Drop**: the same drawing with the whole trail lit and "You" filled in `--accent`; "Written 27 Oct 2025" and "Opened yesterday"; the title in `heading`; the text in `read`; and *Reply to past you* as the screen's one primary button (D-33).

### Controls
- **Buttons** (44 px tall, full corners, `body` 600, 16 px sides): **primary** is `--accent` with `--on-accent` text, one on a screen; **secondary** has a 1 px `--control` border; **quiet** is text only, 12 px sides.
- **Chips** (32 px tall with a 44 px hit area, `small`, full corners, `--control` border): tags, filters and the date and template picks in Write. A selected chip is filled with `--accent`.
- **Rows**: 56 px at least, a hairline between them, a chevron when they open something. Variants: settings rows (label, value in `--ink-2`, chevron); the **contents list** on an Era page (the week's name, a dotted leader of 2 px `--control` dots, "3 entries"); week bars (day "Mon 19", display title, media counts); timeline rows (the date in `title` over the weekday in `meta`, then the display title and a second line of media counts and tags).
- **Stats row**: up to four figures in `title` over their labels in `meta`, split by hairlines, never in a box.
- **Radios** in a list: the whole row is the label; the radio is a 44 px target drawn as a 20 px ring in `--control`, with an `--accent` dot when chosen.
- **Segmented control** (Appearance): three 44 px segments inside a full-corner `--control` border; the chosen one is filled with `--accent`.

### Write (F-02 to F-06)
- **Phone**: a full-screen sheet. A 56 px bar: *Cancel* (quiet), "New entry" over "Draft saved 7:41 pm" (`meta`), and *Save* (primary). Then the memory date and template chips, the title (`title`, no border), a toolbar of 44 px icon buttons (bold, italic, heading, list) between hairlines, and the editor (`body`, prompts as `body` 600). Below, photo thumbnails (72 px, `sm`) with a dashed *Add* tile, the voice note, tag chips and the soundtrack.
- **The add bar**: fixed at the bottom of the sheet, 64 px, five columns of 20 px icons over `meta` labels: Photo, Voice, Tag, Song and **Seal**, in `--accent` with the star mark, so sealing is on the first screen.
- Icons everywhere are lucide-react at 16 or 20 px (design.md §9). The moon and the star are our own drawings, not icons.
- **Seal panel**: opens from the bottom over a scrim (`--surface`, `lg` top corners): "Seal as Loot Drop" (`title`), a line saying nobody can open it until the day picked and that the wait can be made longer but never shorter, the quick dates as radio rows (In a month, In a year, On my birthday, each with its date; Pick a date), the moment it opens ("Thursday 28 October 2027 at 9:00 am"), and *Seal it* as the primary button (D-23, D-33).
- **Laptop**: a dialog up to 960 px wide over the scrim, two columns: the writing on the left; photos, voice note, tags, soundtrack and a *Seal as Loot Drop* box with the quick dates on the right (320 px).

### Title band (Era page)
The Era's cover photo (3:2, edge to edge on a phone), then a band in `--band`: the Era's name in `display`, the dates and length (`small`, `--on-band-2`), its description (`body`), its soundtrack, and a stats row (weeks, entries, photos, voice notes). On a laptop the cover sits above the band too, both with `lg` corners, not side by side: beside the band the cover is drawn about 320 px wide, while the API serves the original photo, so it would be over twice its drawn size (D-36, Q29; chosen 2026-11-25).

### Constellation
Every week of an Era drawn as a dot, sized by its entries (2 to 6 px, more for busier weeks), in `--ink-2`; the listed weeks are brighter (`--ink`) and joined by 1 px `--control` lines; an Era inside it (a trip) makes a small figure of its own. It is a picture only, with a text label for screen readers. The contents list below it is how a week is found.

## 5. The travelling star

One SVG file in `frontend/src/assets/` (design.md §5), drawn on a 240 × 96 grid with one stroke width, 2 px at every size (`vector-effect: non-scaling-stroke`), in token colours only, with no text and nothing rotated. The dates are HTML text beside it.

| Part | Geometry | Colour |
|---|---|---|
| Star | A four-point star 28 units across, centred on (24, 24) (the path in the mockup's `STAR`, scaled 1.1667 from a 24 grid and moved by 10, 10), with two 2-unit rays through it, 36 long upright and 32 across | `--accent` |
| Trail | A cubic curve from (44, 30) through (104, 30) and (150, 74) to (212, 74), dashed 2 on, 6 off | `--control` |
| Travelled part | The same curve from its start to the light front, drawn as its own line (a dash pattern can't measure along the curve once the stroke doesn't scale) | `--accent` |
| Light front | A 3.5-unit dot inside a 7-unit ring, at the point on the curve that is *travelled ÷ total* of its length | `--accent` |
| You | A 6-unit circle at (220, 78), a 2 px ring on `--page`; filled with `--accent` once the Loot Drop has opened (no light front then) | `--ink` |
| Specks (page only) | 14 one-unit dots placed by a seeded random, the same every time | `--ink-2` at 50% |

*Travelled* is the days from the memory date to today; *total* is the days from the memory date to the opening day, both in the user's timezone. Early in a long wait the lit part is short, and the countdown does the talking. The star scales from 100% to 86% and back every 3.2 seconds; with reduced motion it is still. The 16 to 24 px **star mark** is the star alone, filled with `currentColor`.

## 6. Screens

| Screen | Route (task 4.3 lists the first ones) | Mockup | Notes |
|---|---|---|---|
| Feed | `/` | `#feed`, `#feed-opened` | First photo no lower than 284 px on a phone (it is at 223 px) |
| Entry | `/entries/:id` (or a sheet over the feed) | `#entry` | Photos whole; *Edit* top right; "In The Brunswick Years and Grampians Long Weekend. Written from the Trip day template." at the foot |
| Week summary | `/weeks/:weekId` | `#week` | Title, dates and Eras; stats row; equal thumbnails, five across; tag chips with counts; one bar per entry; *Rename week* and *Close week* |
| Timeline | `/entries` | `#timeline` | Tag filter chips that scroll sideways (*All entries* chosen); sticky month headings; one row per entry |
| Eras | `/eras` | `#eras` | 64 px covers (or the initial), Eras inside another indented 24 px; *New Era* |
| Era | `/eras/:id` | `#era` | Title band, constellation, contents list, *Show 91 earlier weeks*, Eras inside this one |
| Loot Drop | `/entries/:id` while sealed | `#loot` | §4 Loot Drops |
| Opened Loot Drop | `/entries/:id` once opened | `#opened` | *Reply to past you* |
| Write | a sheet or dialog | `#write`, `#write-seal` | §4 Write |
| Photo dump | a sheet | `#dump` | Grouped by day, three across; *Date unknown* with *Change date*; *File them* (F-19) |
| Templates | `/templates` | `#templates` | Yours; the starter gallery with *Add* on each |
| You (settings) | `/settings` | `#you` | Display name, timezone, birthday; Appearance; Tags, Templates, Recently deleted |
| Recently deleted | `/recently-deleted` | `#deleted` | "Erased on 26 Nov"; *Restore* and *Erase now*; a sealed one shows only its dates and *Restore, still sealed* |
| First run | `/` with no entries | `#empty` | "Your journal starts here", *Write your first entry* (primary), *Photo dump*, and the first letter, sealed for a month (D-33) |
| Can't connect | any read | `#error` | "Can't reach memoirist", "Anything you were writing is still here", *Try again* (F-14) |

Tags (`/tags`) follow the Templates screen: rows with a rename and a delete.

## 7. States and motion

- **Loading:** every photo's space is reserved (its stored size), so nothing jumps. Lists show their rows' shapes in `--surface` only after 300 ms; a slow request shows progress after a short delay, and its button can't be pressed twice (F-14).
- **Empty:** an invitation, not an apology, with the action that fills it (the first-run screen; "No entries with this tag yet" and *Clear the filter* in the timeline).
- **Errors:** next to the field in `--danger`; a whole screen uses the *Can't reach memoirist* block. Never technical detail.
- **Motion:** only the star's twinkle, the menu and panels opening (200 ms, ease out) and the swipe's own scrolling. `prefers-reduced-motion` stops the twinkle and makes the laptop arrows jump instead of glide.

## 8. Accessibility

- Contrast as in §1. Focus is a 2 px `--accent` outline 2 px outside the element (inside a radio's ring).
- Every tap target is 44 × 44 px or more; chips get a 44 px hit area.
- The moon has a label ("Waning gibbous moon"); the drawing on a Loot Drop page is an image with a sentence ("A star whose light set out on Sunday 25 October 2026. It has travelled 3 days of 365."); the sealed card's link says everything the card shows.
- Dialogs and sheets trap focus and close with Escape; the Write menu is a menu button.
- Check every screen with `node docs/design/explorations/tools/audit.mjs <url>` at 375 and 1440 px, in light and dark (`--dark`), by keyboard, and with reduced motion.

## 9. Words

PRD §3 words only: Journal, Timeline, Eras, You, Write, entries, weeks, *On this day*, Loot Drop, Opens, Written, *Reply to past you*, *Photo dump*, *Recently deleted*, "Eras inside this one". "Wednesday at dusk" is a greeting, not a label. No emoji.

## 10. Not settled here

- **Smaller copies of photos** (Q29): until it is decided, lists load originals, which is slow on a phone.
- **A palette picker:** Dusk blue and Sunset rose (FEEDBACK.md) stay candidates for a later phase; nothing promises one.
