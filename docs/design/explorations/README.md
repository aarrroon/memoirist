# Design explorations

Six ways memoirist could look, built before any real frontend so people can react to them (2026-10-28). Nothing here is decided: when a direction wins, record it with `/record-decision`, then write `docs/design/DESIGN.md`. The rough ideas these grew from are in [DIRECTIONS.md](../DIRECTIONS.md).

## What's here

| Path | What |
|---|---|
| `index.html` | All six side by side: an overview, then one board per direction |
| `directions/<name>.html` | Each direction as a clickable mockup of four screens: `#feed`, `#entry`, `#era`, `#loot` |
| `screenshots/boards/` | One image per direction, plus `0-overview.jpg`: the ones to send people |
| `screenshots/<name>/` | Every screen at phone (375px, 2x) and laptop (1440px) width |
| `shared/content.js` | The invented journal every direction renders, shaped like the API (`docs/API_CONTRACT.md`) |
| `shared/helpers.js` | Dates in Sam's timezone, counts that leave sealed entries out (D-10), display titles (D-24) |
| `shared/photos/` | Real photos from Wikimedia Commons, credited in [CREDITS.md](shared/photos/CREDITS.md) |

Open any HTML file straight from disk; nothing needs building. Fonts come from Google Fonts, so the first load needs a connection.

## The directions

| # | Direction | From | The idea |
|---|---|---|---|
| 1 | Exercise Book | DIRECTIONS.md: Scrapbook | Your paper diary is the app; a sealed Loot Drop is a taped envelope |
| 2 | Memoir | DIRECTIONS.md: Quiet editorial | Typeset like a printed book; a sealed Loot Drop is banded pages |
| 3 | Observatory | DIRECTIONS.md: Night sky | Real moon phases; a sealed Loot Drop is starlight still on its way |
| 4 | Golden Hour | DIRECTIONS.md: Instagram-modern | The familiar feed, with weeks as rings of seven days |
| 5 | Colour Chapters | New | Each Era is a colour field; your life as coloured blocks |
| 6 | Contact Sheet | New | Weeks as film strips; a sealed Loot Drop is an undeveloped roll |

Every direction shows the same journal: the persona Sam in Melbourne, "now" fixed at Wednesday 28 October 2026, 7:42 pm. To change the content, edit `shared/content.js` and every direction follows.

## Practice piece

`describeUnlock` in `shared/helpers.js` has a working answer between `ANSWER` markers, under a `TODO(human)`. Cover the answer and write your own; any `#loot` screen shows the result.
