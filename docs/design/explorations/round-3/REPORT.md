# Round 3: report

Built 2026-11-24 in one session from [PLAN.md](PLAN.md) and FEEDBACK.md's "Round 2 feedback". One design, number 12, in `directions/starlight.html`, with 17 screens.

## Checks

| Check | How | Result |
|---|---|---|
| D-40 limits | `node tools/audit.mjs --mockup` on all 17 screens, light and dark, at 375×812 and 1440×900 (68 loads) | "No check failed." in both modes. Two allowed warnings: the sample photos are full size, and the fonts come from Google |
| First phone screen | The audit's first-screen line on `#feed` | "first photo starts 223px down (27%), after 5 rows of text" (limit 284 px, five rows) |
| No sideways scroll | `shoot.mjs` probe `scrollWidth` on every phone shot | 375 on all 30 |
| Sealed card leaks nothing | Probe of `[data-sealed]` text | Feed: "Opens Mon 25 Oct 2027 in 362 days". Week: "Sun 25 Loot Drop · Opens 25 Oct 2027". Timeline: "25 Oct Sun Loot Drop Opens 25 Oct 2027". No title, text, tags or photos |
| Countdown | Loot Drop page and sealed card | "in 362 days" |
| Page problems | `shoot.mjs` on 43 shots, and the browser console | None. The two practice-block `console.assert`s and `helpers.js`'s asserts all pass |
| Contrast | Audit contrast check | Every text run passes in both modes |

## Fixed while building
- On this day's closed list still took up space, because the list's `display: flex` overrode `hidden`. That pushed the first photo to 343 px. Fixed with a `[hidden]` rule.
- The star's lit trail stopped short: a dash pattern can't measure along a curve once the stroke keeps one width at every size. It is now drawn as its own line up to the light front.
- On a laptop, Write's Seal box was hidden by a later rule. It now shows in the dialog's side column with its quick dates.
- The Spotify Play button on the Era band was ink on ink. Radios and the appearance switch were under 44 px. A 60 px indent was off the spacing scale. The weekly line cut long Era names. All fixed.

## Boards
In `screenshots/boards/`: `0-overview.jpg`, `1-journal.jpg`, `2-writing.jpg`, `3-dark.jpg`, `4-laptop.jpg`. The board page is `index.html`.

## Practice blocks
- `directions/starlight.html:396`, `feedRatio`: the photo shape rule as a clamp.
- `directions/starlight.html:408`, `slideIndex`: which photo of a swipe is showing, from the scroll position.
