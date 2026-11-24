# Round 3: Starlight, refined

One design, not a round of several: Aaron chose Starlight (8) on 2026-11-24, with parts of Folio (10). The answers are in [FEEDBACK.md](../FEEDBACK.md), "Round 2 feedback". This pass draws it phone first, adds the screens no round has drawn, and keeps every limit of D-40 so it can be built as drawn. Number 12 on the boards.

File: `round-3/directions/starlight.html`. URLs: `?mode=light|dark#<screen>`; with no `mode`, it follows the device.

## Settled for this pass

| Area | Choice |
|---|---|
| Palette | Black, white and gold only (FEEDBACK.md, "One accent"). Gold is the one accent: Write, primary buttons, focus, the selected item and the Loot Drop |
| Light | page `#FFFFFF`, surface `#F4F4F3`, raised `#EBEBE9`, ink `#111111`, ink-2 `#5B5B5B`, line `#E2E2E0`, control `#8A8A88`, accent `#8F6614`, on-accent `#FFFFFF` |
| Dark | page `#161616`, surface `#1F1F1F`, raised `#292929`, ink `#EDEDED`, ink-2 `#A3A3A3`, line `#2E2E2E`, control `#6B6B6B`, accent `#E3C06A`, on-accent `#161616`. Charcoal, not black, and not blue (the dusk tone belongs to Dusk blue) |
| Type | Newsreader 500 for titles only (D-40); Instrument Sans 400 and 600 for everything else, entry text included. Sizes 12, 14, 16, 18, 20, 28, 40; line heights 1.2, 1.5, 1.6 |
| Spacing and shape | 4, 8, 12, 16, 24, 32, 48, 64. Radii 8 and 16, plus full. 16 px gutter on phones |
| Feed photos | Edge to edge on phones. The first photo keeps its shape between 4:5 and 1.91:1; the rest of the swipe are cropped to it. Dots under the photo |
| Between entries | 24 px, a full-width hairline, 24 px |
| Order of an entry | The day (first entry of a day only), the photo, the title, three lines of text, then voice note, soundtrack and tags |
| Top of the feed | Top bar with the moon line in place of the wordmark ("Wednesday at dusk"); On this day as one row; one sticky line for the Era and the week |
| Sealed card | Fixed size. Under a day line it shows only Opens and the countdown; on a day of its own it adds the memory date |
| Loot Drop object | The travelling star, redrawn on one 24 px grid with one stroke width, in token colours, with no text and nothing rotated. The trail is lit for the part of the wait already travelled, with a clear point where the light has got to. No sky panel in light mode |
| Laptop | Side nav, a 600 px feed with Folio's margin dates, On this day in the right rail. Write is a dialog |

## Screens

| Hash | Screen | Source |
|---|---|---|
| `feed` | The feed | F-08 |
| `feed-opened` | The feed with the Opened strip in On this day's place | F-07, D-10 |
| `entry` | One entry, photos whole | F-08 |
| `era` | An Era: Folio's title page in neutral colours, the constellation, the dot-leader week list, Eras inside | F-10, D-22, D-36 |
| `eras` | The Eras list, nested | F-10 |
| `week` | A week summary: facts, photos, tags, one bar per entry | F-15, D-25 |
| `timeline` | Every entry, one row each, filterable by tag | F-16 |
| `loot` | A sealed Loot Drop | F-07 |
| `opened` | An opened Loot Drop, with Reply to past you | F-07, D-33 |
| `write` | New entry, with Seal as one row on the first phone screen | F-02–F-06, F-11 |
| `write-seal` | The seal panel open, with the quick dates | F-06, D-33 |
| `dump` | Photo dump, grouped by day | F-19, D-34 |
| `templates` | Your templates and the starter gallery | F-11, D-06 |
| `you` | Settings: name, timezone, birthday, appearance; links to Tags, Templates and Recently deleted | F-13 |
| `deleted` | Recently deleted | F-18, D-30 |
| `empty` | First run: no entries yet, with the first letter | D-33 |
| `error` | Can't reach memoirist | F-14 |

## Assumptions
- **No place line.** The feedback's "time · place" line needs a location, which entries don't have. The line above a photo shows the day instead, once per day.
- **A star, not a lock, on sealed rows.** F-15 and F-16 say a sealed bar shows a lock; D-39 and the design rules say never a padlock. Rows use the star mark.
- **Light, dark or follow the phone** sits in settings. Aaron chose it on 2026-11-24; the PRD lists only name, timezone and birthday.
- **The Opened strip takes On this day's row** while it shows, so the first photo still starts within 284 px; On this day moves below the first entry until the strip is dismissed.
- **Mock data** for the new screens (an opened Loot Drop, deleted entries, a photo dump) lives in the design file, not in `shared/`, so earlier rounds are untouched.

## Practice blocks
Two, in the design file's script: the photo shape rule (clamp a ratio), and which slide of a swipe is showing.
