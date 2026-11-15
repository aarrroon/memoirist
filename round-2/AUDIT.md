# Round 2: design and front-end audit

> Audited 2026-11-14. Every design in `round-2/directions/` (7 Afterglow, 8 Starlight, 9 Nightstand, 10 Folio, 11 Keepsake) was checked for layout, spacing, fonts, colour, contrast, the phone view and load speed, to answer one question: **what makes these look cheap?**
>
> The mockups were not changed. The fixes are rules for the real frontend: [`.claude/rules/design.md`](../../../../.claude/rules/design.md), recorded as [D-40](../../../DECISIONS.md). [SENIOR-REVIEW.md](SENIOR-REVIEW.md) compares the five designs with each other; this audit measures what all five get wrong.

## 1. The short version

The five designs differ in paint and share their faults. None has a system underneath: every screen was tuned by hand, so nothing quite lines up, and that is what reads as cheap.

The bones are sound. No screen scrolls sideways at 375 px, all text passes contrast, focus and reduced motion are handled, and there are almost no shadows.

| # | What makes it look cheap | Where | Fixed by (section of the rule) |
|---|---|---|---|
| 1 | Nothing sits on a spacing scale, so edges and gaps drift | All five | Spacing and alignment |
| 2 | Too many text sizes, weights and line heights | All five | Type |
| 3 | The first phone screen is mostly chrome; the entry starts half-way down | All five (worst: 8 and 11) | First screen |
| 4 | Boxes inside boxes, each with its own corner | 7, 8, 9 and 10 | Shape |
| 5 | The Loot Drop drawings look like clip-art | All five (worst: 11 and 7) | Loot Drop object |
| 6 | Slow and heavy to load, and two designs jump as they load | All five | Loading |
| 7 | More than one accent colour | 8 and 9; every dark mode | Colour |
| 8 | Everything is a pale pill, so you can't tell what can be tapped | All five (worst: 7) | Surfaces and controls |
| 9 | A crowded tab bar, small tap targets and mixed control styles | 8, 9, 10 and 11 | Navigation and controls |
| 10 | Laptop screens are a phone column in an empty canvas | All five (best: 10) | Laptop layout |

The order is by how much each one hurts the look. Number 6 can't be seen in a screenshot, but on a real phone it would hurt the most.

## 2. How it was measured

| Step | What |
|---|---|
| Code | Counted what each design's CSS declares: spacing values, text sizes, radii, colours, breakpoints, fonts |
| Rendered pages | Loaded all five screens (feed, entry, Era, Loot Drop, Write) of each design in headless Chrome at 375×812 (phone, touch) and 1440×900 (laptop): 150 loads across both themes and both modes for contrast and colour, then 50 loads with [`tools/audit.mjs`](../tools/audit.mjs) for the numbers below (theme a, light) |
| Load speed | Loaded each feed and entry once on a slow phone: slow 4G (1.6 Mbps, 150 ms delay), a processor slowed four times, an empty cache. This is Lighthouse's phone profile |
| By eye | The overview board and about 40 screenshots at both widths, in light and dark |

Limits of the method:
- The checker can't judge taste or drawings, and it can't measure text that sits on a drawing or a photo. It lists those for a person to look at.
- Colours are counted as hue families 30° wide, leaving out greys and photos.
- Load times are for the mockups, served from this machine with the network and processor slowed. Fonts came from Google over the real network. Times vary by several seconds between runs: Afterglow's entry measured 7.1 s and 10.8 s, and Folio's feed 11.1 s and 16.3 s. Every run was far over the 4 s limit, so the finding holds; the exact seconds don't.

## 3. The ten problems

### 1. Nothing sits on a spacing scale
- **In the code:** 23 to 35 different spacing values per design. 35 to 46% of them are off a 4 px grid: 6, 10, 14, 18 and 22 px are everywhere.
- **On the page:** 23 to 27 different paddings and gaps per design, and 15 to 20 of them fit no scale.
- **Rhythm:** a phone feed has 15 to 25 different vertical gaps between neighbours.
- **Edges drift:** Afterglow starts its headings 16 px from the edge and its cards 12 px. Nightstand's photo starts at 16 px and the text under it at 18 px. Starlight has text at 16 and 20 px; Keepsake at 10, 12 and 16 px. On laptops, Afterglow has columns at 388 and 390 px, and Starlight at 356 and 360 px.
- **Neighbours don't match:** Afterglow's date card and template card on Write have different heights and different tops.
- **Why it looks cheap:** the eye reads a 2 px miss as a mistake, even when it can't name it.

### 2. Too many text sizes, weights and line heights
- **Sizes:** 14 to 19 rendered sizes per design (Afterglow and Folio 19, Nightstand 14), with half pixels in Afterglow: 12.5, 13.5 and 15.5 px.
- **Weights:** 4 to 6. Afterglow uses 400, 500, 600, 650, 700 and 800; Starlight adds 450.
- **Line heights:** 9 to 12. Letter-spacings: 3 to 7.
- **One job, several sizes:** a caption is 15.5 px in one Afterglow card and 16 px in the next; 16 and 17 px in Nightstand and Keepsake. A real title is 19 px and a fallback title 16 px in the same list.
- **A third typeface:** Georgia, for one italic *I* on the Write toolbar (7, 9 and 11).
- **Sizes are in px,** so the reader's own text-size setting is ignored.
- **Why it looks cheap:** with 19 sizes there is no hierarchy, only noise.

### 3. The first phone screen is mostly chrome
- **The first photo starts 347 to 464 px down** a 375×812 screen: 43% (Folio and Nightstand), 46% (Afterglow), 55% (Keepsake), 57% (Starlight).
- **8 to 13 rows of small text come first:** the wordmark, On this day, the Era, the week, the date, the title.
- **7 to 9 rounded boxes** sit on that first screen in four designs. The tab bar takes another 64 to 68 px.
- **One skeleton:** all five stack the same rows in the same order, as the senior review said.
- **On this day is cramped:** its text runs to three lines beside a 40 px thumbnail (Afterglow), a title is cut to "First night in the big r…" and the label is squeezed onto two lines (Folio).
- **Why it looks cheap:** a journal is your photo and your words. A first screen of small widgets reads like a dashboard template.

### 4. Boxes inside boxes, each with its own corner
- **Count:** 18 to 38 rounded boxes on a feed. In four designs they nest three deep: a card, a stamp or chip in it, a control in that.
- **Corners:** 12 to 15 radii declared per design, and 6 to 8 rendered in four of them. Afterglow renders 8, 10, 12, 14, 16, 18, 20 and 24 px, and shows four of them on its first phone screen.
- **Keepsake has three radii** and looks the calmest for it.
- **Pills of three heights in one column:** tags 36 px, the Spotify pill 44 px, the voice note 56 px (Afterglow's entry).
- **Why it looks cheap:** rounded-rectangle soup is the stock look of a generated app.

### 5. The Loot Drop drawings look like clip-art
- **Five improvised drawings:** a parcel, a star chart, an hourglass, a book and a canister, each hand-written SVG in its own style.
- **Craft:** flat fills, uneven outlines, a parcel with the wrong perspective, text rotated on a tag, and a canister that reads as a jerry can. The first capsule read as a pill (senior review).
- **They sit beside real photographs,** so the gap in craft shows, on the product's signature screen.
- **They bring colours of their own:** gold and mustard that nothing else uses.
- **Text on the drawing:** the dates on Keepsake's label sit on the canister, where a checker can't measure contrast.
- **Why it looks cheap:** one amateur illustration lowers how everything around it is judged.

### 6. Slow and heavy to load, and it jumps
- **On a slow phone** the feed's first photo appears after 10.0 to 11.5 s, and the entry's after 6.8 to 10.8 s. Good is 2.5 s; over 4 s is poor.
- **Weight:** the feed is 2.6 MB (Afterglow, the only one that loads photos lazily) to 5.7 MB, in 13 to 22 requests. Four designs fetch 9 to 19 photos that are below the first screen.
- **Photos are far too big:** 1,280 to 1,920 px wide for a 327 px slot, and the same files fill 40 px thumbnails, up to 27 times the width needed. One 40 px Era disc downloads a 735 KB photo.
- **Write loads the whole feed behind it:** 3.1 to 5.4 MB before you type a word.
- **Fonts come from Google:** a request to another company's server that blocks the first paint on every load, and 34 KB (Keepsake, one typeface) to 298 KB (Starlight).
- **Two entry screens jump** while they load, because the photo's space isn't reserved: a layout shift of 0.23 (Nightstand) and 0.26 (Folio). Good is 0.1 or less.
- **The real app would be slower.** These sample photos are 0.2 to 0.7 MB. The app keeps uploads at about 2 to 3 MB (D-13) and serves that one file everywhere. See Q29 and Q30 in section 7.
- **Why it looks cheap:** a page that fills in slowly and shifts under your thumb feels broken, however it looks once loaded.

### 7. More than one accent colour
- **Two accents on one screen:** Starlight and Nightstand use blue for actions, and gold or mustard for the Loot Drop, the countdown and the moon.
- **Every dark mode adds Spotify green.**
- **Near-duplicates:** Afterglow has three roses: #A8325A (accent), #9A2D52 (links) and #8E2A4C (the date stamp).
- **Muddy or faint:** Nightstand's light-mode mustard (#7D6512) looks muddy beside the blue. Starlight's light-mode gold is 4.1 to 4.6:1, so it passes only as large text.
- **The accent is spent on things that aren't actions:** template headings (Afterglow), a filled Play button (Starlight), and a filled *Extend the seal* (Nightstand) where the others use an outline.
- **Why it looks cheap:** when two colours compete, neither means anything.

### 8. Everything is a pale pill
- **Five pale tones within 1.2:1 of each other** on one screen (Afterglow: page, surface, raised, stamp, line).
- **A tone step is the only sign that something can be tapped.** A card differs from the page by 1.07 to 1.15:1, and a chip by 1.07 to 1.33:1. That is normal for a card, but not enough for a control.
- **Control outlines are faint:** outline buttons, *Add tag* and the quick dates are 1.4 to 2.7:1 against their background. WCAG asks 3:1 for the edge of a control (1.4.11).
- **Tappable and static things share one shape.** On this day cards, the Era row, the week bar, tags and the Spotify pill can be tapped. The date stamp and the stats card can't. All are pale rounded rectangles.
- **Why it looks cheap:** washed-out screens with no clear buttons feel unfinished, and people tap the wrong things.

### 9. A crowded tab bar, small targets, mixed controls
- **The Write pill is wider than its fifth of the bar:** *Timeline* ends 2 px from it in Starlight, 8 px in Nightstand and 9 px in Folio. Its label is 15 px beside 12 px tab labels.
- **Afterglow shows two bold labels** (the current tab and Write). Keepsake has three tabs and a 104 px pill, with no *You*.
- **The laptop nav is a different list** in a different order (Starlight: Journal, Eras, Timeline, Templates, Tags; on the phone: Journal, Timeline, Write, Eras, You).
- **Tap targets under 44 px:** 3 to 18 per design on the phone. Tag chips are 32 to 36 px tall, *remove tag* 28 to 36 px, Play 36 px, formatting buttons 40 px, *Edit* 37 to 43 px wide.
- **Mixed styles:** the formatting bar mixes letters (B, H), a Georgia italic *I* and line icons. Links come underlined, coloured and plain grey.
- **Why it looks cheap:** uneven controls look assembled from parts.

### 10. Laptop screens are a phone column in an empty canvas
- **The entry ends in an empty band** 144 to 240 px tall on the first screen in four designs. Folio fills it.
- **The Loot Drop page** leaves 240 to 448 px empty across and 192 to 376 px down. Keepsake's canister sits in a grey slab with a third of the screen empty below.
- **Columns don't line up:** Afterglow's thumbnails are 165, 196 and 196 px wide and stop 27 px short of the photo above them.
- **Rails repeat the column:** the week's thumbnails are the photos right beside them (Afterglow and Keepsake).
- **Loose ends:** an Era with no cover is an empty grey box (Keepsake). Afterglow's Write dialog half-covers the nav's Write button. Folio's running head "Journal" floats alone at the top.
- **Why it looks cheap:** a stretched phone layout says nobody designed the big screen.

## 4. Scorecard

Theme a, light mode, all five screens at both widths. The limit is what `.claude/rules/design.md` now allows.

| Measure | Limit | 7 Afterglow | 8 Starlight | 9 Nightstand | 10 Folio | 11 Keepsake |
|---|---|---|---|---|---|---|
| Text sizes | 7 | 19 | 17 | 14 | 19 | 17 |
| Weights | 3 | 6 | 5 | 4 | 5 | 5 |
| Line heights | 3 | 11 | 12 | 9 | 11 | 10 |
| Typefaces | 2 | 2 and Georgia | 2 | 2 and Georgia | 2 | 1 and Georgia |
| Paddings and gaps (off the scale) | 8 (0) | 27 (20) | 24 (17) | 23 (16) | 27 (20) | 23 (15) |
| Corner radii, besides pills and circles | 2 | 8 | 8 | 7 | 6 | 3 |
| Hues besides photos, light and dark | 1 | 1 and 2 | 3 and 3 | 2 and 3 | 1 and 1 | 1 and 2 |
| Boxes nested | 2 deep | 3 | 3 | 3 | 3 | 2 |
| Boxes on the first phone feed screen | 3 | 9 | 7 | 8 | 7 | 3 |
| First feed photo starts (phone) | 35% down | 46% | 57% | 43% | 43% | 55% |
| Rows of text before it | 5 | 11 | 13 | 8 | 9 | 12 |
| Tab bar: closest neighbours | 8 px | 25 px | 2 px | 8 px | 9 px | 35 px |
| Tab bar label sizes | 1 | 1 | 2 | 2 | 2 | 2 |
| Tap targets under 44 px (phone) | 0 | 7 of 74 | 6 of 89 | 3 of 66 | 18 of 88 | 12 of 70 |
| Text contrast failures | 0 | 0 | 0 | 0 | 0 | 0 |
| Sideways scroll at 375 px | none | none | none | none | none | none |
| Feed photos fetched below the first screen | 0 | 1 | 19 | 9 | 11 | 11 |
| Widest photo compared with its slot | 2 times | 24 | 18 | 28 | 15 | 24 |

Load on a slow phone:

| Measure | Target | 7 Afterglow | 8 Starlight | 9 Nightstand | 10 Folio | 11 Keepsake |
|---|---|---|---|---|---|---|
| Feed: first photo appears | 2.5 s | 10.0 s | 11.5 s | 10.3 s | 11.1 s | 10.2 s |
| Feed: weight and requests | — | 2.6 MB, 13 | 5.7 MB, 22 | 3.9 MB, 16 | 5.6 MB, 22 | 5.4 MB, 20 |
| Entry: first photo appears | 2.5 s | 7.1 to 10.8 s | 7.3 s | 6.9 s | 7.7 s | 6.8 s |
| Entry: layout shift | 0.1 | 0.05 | 0 | 0.23 | 0.26 | 0.02 |
| Fonts | 150 KB | 95 KB | 298 KB | 66 KB | 220 KB | 34 KB |
| Calls to other hosts | none | Google Fonts | Google Fonts | Google Fonts | Google Fonts | Google Fonts |

## 5. Each design in a line

| Design | Best thing to keep | Biggest fault |
|---|---|---|
| 7 Afterglow | Warm and easy to use; the only one that loads photos lazily, so the lightest feed | The most boxes: 9 on the first screen, 8 radii, 19 text sizes |
| 8 Starlight | The best dark mode and the best Write screen | Two accents, the tightest tab bar (2 px), the heaviest fonts, and the first photo 57% down |
| 9 Nightstand | The best first screen (43%) and the fewest text sizes (14) | Two accents with a muddy mustard; the entry jumps as it loads |
| 10 Folio | The most refined type and the only laptop layout that fills its screen | 19 text sizes, 18 small tap targets, a clipped On this day row, 220 KB of fonts |
| 11 Keepsake | The calmest shapes (3 radii, 3 boxes) and one typeface (34 KB) | The canister drawing, the first photo 55% down, a three-tab bar with no *You* |

## 6. What is already good

Keep these when the frontend is built:
- No screen scrolls sideways at 375 px (25 screens).
- All text passes contrast. Secondary text is 5.2:1 or more on every surface, in both themes and both modes.
- Focus outlines are visible, motion stops under reduced motion, and there are almost no shadows.
- Reading text is a good size: 17 to 19 px with a line height of 1.55 to 1.6, in lines of 35 to 42 characters on a phone.
- Dark modes are designed on their own: dark grey, not black.
- Sealed cards leak nothing (checked in SENIOR-REVIEW §5).

## 7. What this means for the frontend

**Recorded as D-40:** one design system, with one spacing scale, two typefaces at most and one accent colour. The checkable rules are in `.claude/rules/design.md`. It loads whenever a file in `frontend/` is touched, and the `spec-reviewer` subagent checks it.

**Two palettes were reworked.** Dusk blue's dark mode puts a gold Loot Drop beside a blue accent. Black, white and gold keeps actions in ink and puts its one colour, gold, only on the Loot Drop. Under one accent, actions and the Loot Drop share one colour. Aaron chose the answers on 2026-11-15, and [FEEDBACK.md](../FEEDBACK.md) holds the new values: Dusk blue's Loot Drop turns blue, and gold becomes the accent of Black, white and gold.

**Two questions are open.** They are in `docs/DECISIONS.md`:

| # | Question | Why the audit raises it |
|---|---|---|
| Q29 | Should the app make smaller copies of each photo? | The API serves the uploaded file everywhere. A 40 px thumbnail would download 2 to 3 MB |
| Q30 | Should `media` store each photo's width and height? | Without them a screen can't hold a photo's own shape before it loads, so the page jumps |

**Design rounds keep the same limits** (agreed 2026-11-15). [`docs/prompts/design-round.md`](../../../prompts/design-round.md) holds every new mockup to D-40 and runs `tools/audit.mjs --mockup` on it, so the direction that wins can be built as drawn.

## 8. Run it again

From `docs/design/explorations/`, with Node 22 or later and Chrome:

```bash
node tools/audit.mjs --self-test
```

```bash
node tools/audit.mjs --serve . --slow "round-2/directions/folio.html?theme=a&mode=light#feed"
```

- `--self-test` loads a page with one fault for every check and a clean page. It passes only if every fault is caught and the clean page raises none.
- `--serve .` serves this folder for the run. Any other target is a URL, such as `http://localhost:5173/` once the frontend exists.
- `--slow` adds the slow-phone load. `--dark` asks for the dark colour scheme. `--out <folder>` keeps the numbers and a screenshot of each width.
- `--mockup` is for design rounds. A mockup can't shrink its sample photos or host its own fonts, so photo size and calls to other hosts only warn.
- The exit code is 1 when any check fails.
