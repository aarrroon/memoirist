# Design explorations

Six ways memoirist could look, built before any real frontend so people can react to them (2026-10-28), then revised once after [REVIEW.md](REVIEW.md) (2026-10-29). Nothing here is decided: when a direction wins, record it with `/record-decision`, then write `docs/design/DESIGN.md`. The rough ideas these grew from are in [DIRECTIONS.md](../DIRECTIONS.md).

## What's here

| Path | What |
|---|---|
| `index.html` | All six side by side: an overview with four questions for friends, then one board per direction |
| `directions/<name>.html` | Each direction as a clickable mockup of four screens: `#feed`, `#entry`, `#era`, `#loot` |
| `screenshots/boards/` | `0-overview.jpg` plus one image per direction: the ones to send people |
| `screenshots/<name>/` | Every screen at phone (375px, 2x) and laptop (1440px) width, plus `feed-sealed-375.jpg`: the phone feed scrolled to the sealed Loot Drop |
| `REVIEW.md` | The design review the last iteration worked through |
| `FEEDBACK.md` | What people said about each round, the choices made from it, and the agreed colour palettes |
| `round-<n>/` | Later rounds, each planned from FEEDBACK.md and built overnight with [design-round.md](../../prompts/design-round.md) |
| `round-2/` | Five designs, 7–11 (Afterglow, Starlight, Nightstand, Folio, Keepsake), refined from 4, 3 and 2 plus a wildcard, each in two of five palettes, light and dark, with a Write screen and a waiting-object Loot Drop. [PLAN](round-2/PLAN.md), [REPORT](round-2/REPORT.md), boards in `round-2/screenshots/boards/` |
| `round-3/` | Design 12, Starlight refined: the round 2 winner drawn phone first in Black, white and gold, light and dark, with Folio's type and Era page, inside D-40, and the 17 screens Phase 1 needs. [PLAN](round-3/PLAN.md), [REPORT](round-3/REPORT.md), boards in `round-3/screenshots/boards/` |
| `round-2/AUDIT.md` | The design and front-end audit of round 2 (2026-11-14): the ten things that make the mockups look cheap, with measurements. Its fixes are rules for the real frontend (`.claude/rules/design.md`, D-40) |
| `tools/shoot.mjs` | Takes the screenshots and exports the boards through Chrome; needs Node 22 or later and nothing installed |
| `tools/audit.mjs` | Checks a page against the design rule at phone and laptop width: spacing, text sizes, colours, contrast, tap targets, images and load speed. `--self-test` proves each check can fail. Needs Node 22 or later and Chrome |
| `shared/content.js` | The invented journal every direction renders, shaped like the API (`docs/API_CONTRACT.md`) |
| `shared/helpers.js` | Dates in Sam's timezone, counts that leave sealed entries out (D-10), display titles (D-24), the Write menu |
| `shared/photos/` | Real photos from Wikimedia Commons, credited in [CREDITS.md](shared/photos/CREDITS.md) |

Open any HTML file straight from disk; nothing needs building. Fonts come from Google Fonts, so the first load needs a connection.

## The directions

| # | Direction | From | The idea |
|---|---|---|---|
| 1 | Exercise Book | DIRECTIONS.md: Scrapbook | Your paper diary is the app; a sealed Loot Drop is an envelope with a wax seal |
| 2 | Memoir | DIRECTIONS.md: Quiet editorial | Typeset like a printed book; a sealed Loot Drop is pages in a band printed with the day it opens |
| 3 | Observatory | DIRECTIONS.md: Night sky | Real moon phases; a sealed Loot Drop is starlight still on its way |
| 4 | Golden Hour | DIRECTIONS.md: Instagram-modern | The familiar feed, made private; gold rings mark memories new to see |
| 5 | Colour Chapters | New | Each Era is a colour field; your life as coloured blocks |
| 6 | Contact Sheet | New | Weeks as film strips; a sealed Loot Drop is a roll still in its canister |

Every direction shows the same journal: the persona Sam in Melbourne, "now" fixed at Wednesday 28 October 2026, 7:42 pm. To change the content, edit `shared/content.js` and every direction follows.

## What the review changed (2026-10-29)

All six now follow the same rules:
- **First screen:** the phone feed's first screen shows the newest entry. On this day is one short row, the Era is a compact header, and each week is a one-line header that opens its summary (D-25).
- **No day tracking:** nothing shows which days you didn't write (D-32).
- **Sealed entries:** they are never counted (D-10).
- **Write:** one Write button within thumb reach opens New entry, Photo dump and Diary page.
- **Words:** labels use the PRD's words ("Eras inside this one", Opens, Written).

Left for later:
- C5, a paper-reading variant of Colour Chapters.
- An opened Spotify embed (A8).
- E4, Exercise Book's long-title check.
- The P3s.

## Practice pieces

Each has a working answer between `ANSWER` markers, under a `TODO(human) PRACTICE`. Cover the answer, write your own, then compare.

| Where | What it practises | How to check |
|---|---|---|
| `describeUnlock` in `shared/helpers.js` | Formatting an instant in the user's timezone, and counting calendar days rather than 24-hour periods | Any `#loot` screen should say "in 362 days" |
| `bandColours` in `directions/colour-chapters.html` | WCAG contrast, and adjusting a colour's lightness until text on it passes 4.5:1 | The `console.assert` lines under it, in the browser console |
| `backdrop` in `tools/audit.mjs` | Finding the colour behind text when its parents are see-through: walking up the page and blending the layers | `node tools/audit.mjs --self-test` still passes |
| The wait loop in `load` in `tools/audit.mjs` | Waiting until a page has really finished loading: polling with a deadline, and counting the requests still in flight | `node tools/audit.mjs --serve . --slow "round-2/directions/folio.html#feed"` still reports about 5,600 KB, not a few hundred |
