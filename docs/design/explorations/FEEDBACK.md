# Design feedback

What people said about each round of explorations, and the choices Aaron made from it. The overnight prompt [design-round.md](../../prompts/design-round.md) reads the newest section and plans the next round from it. After each round, add a section here (or paste the chat into Claude and ask it to add one).

## Round 1 feedback (2026-11-10)

Round 1 is the six directions in `directions/`, numbered 1–6 on the boards in `screenshots/boards/`.

### Aaron

| # | Direction | Overall | What Aaron said |
|---|---|---|---|
| 1 | Exercise Book | Out | Doesn't like it at all |
| 2 | Memoir | 8/10 | Likes the design. Colour about 7.5/10. Slightly simplistic, and not as modern as 4 |
| 3 | Observatory | 8/10 | Design 9.5/10. Unsure about the very dark colours: change the colour theme, then offer light and dark mode |
| 4 | Golden Hour | 9/10 | Design 9.5/10. It looks like Instagram, which isn't bad because people already know how to use it, but it could be a bit more its own. Definitely not the orange |
| 5 | Colour Chapters | Out | Colours 0/10, fonts 0/10. Hates it |
| 6 | Contact Sheet | Out as a theme | Doesn't like the film-tape parts or the photography theme, which looks childish. The other parts are good. The font could be better. Colours 8/10 |

### Thai (a friend)
Aaron asked Thai whether 4 is the best, and whether to borrow from 6, 3 or 2, which Aaron rates as alright too.
- Likes Observatory (3) the most, because dark suits a diary.
- The sealed Loot Drop in 6 (the film canister) has more personality than the others. Wants Loot Drops that feel that special.

### What it means for round 2

| Keep | From |
|---|---|
| The familiar social feed and its layout, with one signature of its own | 4 |
| The structure: Eras as constellations, a Loot Drop as light still on its way, and night as a mode | 3 |
| Book-like calm and typography, made more modern | 2 |
| A Loot Drop as a waiting object with personality | 6 (the canister) |
| Neutral greys with one strong accent | 6's colours |

| Avoid | Because of |
|---|---|
| Orange accents | 4 |
| Whole pages of saturated colour, and condensed poster type | 5 |
| A school exercise book or paper look | 1 |
| Film strips, tape and other photography props | 6 (childish) |
| Very dark as the only mode | 3 |

### Answers (2026-11-10)

| Question | Answer |
|---|---|
| Light and dark | Every design in both modes |
| Screens | Round 1's four (feed, entry, Era and the sealed Loot Drop) plus Write, the capture view, and the feed scrolled to the sealed card |
| How far to move | Four designs refined from 4, 3 and 2, with 6's Loot Drop personality, plus one wildcard |
| Colours | Each design in two colour themes, drawn from the four palettes below. Up to two more palettes may be proposed |

### Colour palettes (agreed 2026-11-10)

Aaron isn't sure about colour, so these came from his feedback plus research:
- Journaling apps keep colour quiet:
  - [Day One](https://dayoneapp.com/guides/tips-and-tutorials/journal-themes/) uses blue, plus a colour per journal.
  - [Stoic](https://medium.com/technology-hits/apples-journal-app-vs-day-one-vs-stoic-comparing-journaling-apps-89b9df8ab76c) is black and white.
  - [Rosebud](https://brandfetch.com/rosebud.app.md) uses one pink.
- [2026 app guidance](https://updivision.com/blog/post/ui-color-trends-to-watch-in-2026) favours a neutral base with one accent.
- [Dark-mode guidance](https://uxcel.com/blog/12-principles-of-dark-mode-design-627) says to design dark mode as its own theme: dark grey rather than black, off-white text and softer accents.

Every pair passes WCAG AA: text 4.5:1 or more, and accent marks 3:1 or more on their surfaces. They are starting points: a design may tune them if it keeps the family and the contrast.

| Palette | Mode | Page | Surface | Ink | Ink 2 | Accent | On accent | Loot Drop |
|---|---|---|---|---|---|---|---|---|
| Dusk blue | Light | #F6F7F9 | #FFFFFF | #141A26 | #4A5468 | #2F57C8 | #FFFFFF | #2F57C8 |
| | Dark | #11151D | #1A202B | #E7EAF0 | #9BA5B5 | #93ACFF | #10141C | #93ACFF |
| Bookcloth green | Light | #F3F5F1 | #FFFFFF | #1A1F1C | #53605A | #2E5A44 | #FFFFFF | #2E5A44 |
| | Dark | #101614 | #18211D | #E3EAE5 | #9CAAA2 | #93C6A9 | #101614 | #93C6A9 |
| Ink and red | Light | #F2F2EF | #FFFFFF | #151515 | #57574F | #C21F2B | #FFFFFF | #C21F2B |
| | Dark | #161616 | #202020 | #ECECE8 | #A4A49D | #F2737A | #161616 | #F2737A |
| Black, white and gold | Light | #FFFFFF | #F4F4F3 | #111111 | #5B5B5B | #8F6614 | #FFFFFF | #8F6614 |
| | Dark | #121212 | #1C1C1C | #EDEDED | #A3A3A3 | #E3C06A | #121212 | #E3C06A |

- **Dusk blue:** Observatory's night feel, softened, with a light mode.
- **Bookcloth green:** Memoir's green, made more modern.
- **Ink and red:** Contact Sheet's colours, without the film.
- **Black, white and gold:** a neutral base for the familiar feed, with gold as its one accent: on actions, focus, the selected item and the Loot Drop.

### One accent (D-40, changed 2026-11-15)

Aaron set one accent colour for each theme on 2026-11-14 (D-40): a Loot Drop uses the accent, not a colour of its own. Two palettes broke that, and Aaron chose these answers on 2026-11-15. The table above now holds the new values:

| Palette | What it was | What it is now |
|---|---|---|
| Dusk blue, dark | A blue accent with a gold Loot Drop (#EFD9A0) | The Loot Drop takes the blue accent, #93ACFF, as it already did in light mode |
| Black, white and gold, light | An ink accent (#111111) with a gold Loot Drop (#A87A1E, only 3.5:1 on white) | Gold is the accent, darkened to #8F6614 so text passes: 5.15:1 on white, 4.68:1 on the surface, and white text on it 5.15:1 |
| Black, white and gold, dark | An off-white accent (#EDEDED) with a gold Loot Drop (#E3C06A) | Gold is the accent, #E3C06A: 10.72:1 on the page, with #121212 text on it |

- Bookcloth green and Ink and red already used one colour, and so does round 2's Sunset rose.
- The Spotify mark is black or white in every palette.
- `shared/content.js` still holds the old values, because round 2's pages must render as they did. The next round adds its own palettes from the table above.

## Round 2 feedback (2026-11-24)

Round 2 is the five designs in `round-2/directions/`, numbered 7–11 on the boards in `round-2/screenshots/boards/`. The senior review, with ratings, is [round-2/SENIOR-REVIEW.md](round-2/SENIOR-REVIEW.md).

### Aaron

| Rank | # | Design | What Aaron said |
|---|---|---|---|
| 1 | 8 | Starlight | The winner, as the phone version. It is the main theme |
| 2 | 10 | Folio | Still likes its modern, sleek parts |
| 3 | 9 | Nightstand | |
| 4 | 7 | Afterglow | Its Loot Drop is good. A bit too round, and not as modern as Starlight |
| Out | 11 | Keepsake | Bad |

- **Phone first.** About 90% of use will be on phones, so the design is planned for the phone. The laptop follows it.
- **Too compact.** Entries need a clear break between them.
- **Photos are screen-sized:** edge to edge, swiped left and right, as on Instagram.
- **Loot Drop:** Starlight's and Afterglow's are both good.
- **Colour:** Black, white and gold is good. Sunset rose and Dusk blue should be options.

### Answers (2026-11-24)

**Feed on the phone**

| Question | Answer |
|---|---|
| Break between entries | A full-width hairline with 24 px of space above and below it. It was option A of four drawn: hairline and space, tinted band, space only, day header as divider |
| Photo shape in the feed | Full width, by Instagram's rule: the first photo keeps its own shape, between 4:5 tall and 1.91:1 wide. The other photos in the swipe are cropped to match. The entry view still shows every photo whole |
| Several photos | Swiped left and right, with dots below and a "1/4" count on the photo. Diary pages are slides too. A voice note is a play row under the swipe |
| Order of an entry | A short line (time · place), the photo edge to edge, then the title, the first lines of text, the tags and the soundtrack |
| Top of the feed | The Era and the week merge into one slim sticky line ("The Brunswick Years · This week · 3 entries"). On this day is one row you tap. The first photo still starts within 284 px (D-25) |

**Look**

| Question | Answer |
|---|---|
| Palette | Black, white and gold is the default. Phase 1 ships only this palette, in light and dark, and the tokens keep the others a swap away |
| Choosing a palette | A picker with Dusk blue and Sunset rose comes later, with settings in Phase 2. That is a README change, made when it is planned |
| Light or dark | Follows the phone's setting, with a switch in settings |
| Typefaces | Folio's pair replaces Fraunces and Hanken Grotesk: Newsreader for titles only, as D-40 says, and Instrument Sans for everything else, entry text included |
| Dark mode | Neutral charcoal, as in Black, white and gold. The dusk blue-grey belongs to the Dusk blue palette |
| Shape | Not as round as Afterglow. Small corners, within D-40's two radii |

**From Starlight and Folio**

| Part | Answer |
|---|---|
| Moon date line | Kept, but shrunk to one line in the top bar in place of the "memoirist" wordmark. "94% lit" goes |
| Constellation | Kept, as the picture at the top of an Era's week list. The list does the finding, because 98 stars can't each be a 44 px tap target |
| On this day | Labelled "On this day", the PRD word. "The same night, other years" goes |
| Era page | Folio's title page in neutral colours: an ink band in light mode and a raised grey in dark, never a gold or green band. The stats sit in one row split by hairlines, not in a box |
| Week list | Folio's dot-leader list: "This week ······ 3 entries" |
| Laptop | Three columns, as in Starlight and Folio: the side nav, the feed in the centre with Folio's margin dates, and On this day on the right. Write opens as a dialog. Every screen is drawn at 1440 px, but after the phone, never driving it |
| Running head | Dropped. The slim sticky line does its job |

**Loot Drop**

| Question | Answer |
|---|---|
| Waiting object | Starlight's travelling star, redrawn so that it also works in light mode (round 2's light version read like a diagram) |
| Progress | From Nightstand's hourglass: the trail is lit for the part of the wait already travelled, with a clear "you are here" point |
| Sealed card in the feed | Under a day header it shows only the opening date and the countdown, because the header already is its memory date. Everywhere else it keeps the memory date. This changes D-10's list of what a sealed card shows: record it with `/record-decision` |

**Process**

| Question | Answer |
|---|---|
| Next pass | One refined Starlight, not another round of several designs. Phone first, in Black, white and gold, light and dark, within D-40, and every screen checked with `tools/audit.mjs` |
| Screens it adds | The ones no round has drawn: the week summary, the timeline, the Eras list, templates and the starter gallery, settings, Recently deleted, photo dump, an opened Loot Drop with its reply, and empty and error states |
| After it | Aaron approves it, the choice is recorded with `/record-decision`, and `docs/design/DESIGN.md` is written from it |
| Friends | Aaron's feedback is recorded first. Friends' notes go in this section if they arrive before the next pass starts |

### What it means for the next pass

| Keep | From |
|---|---|
| The structure: the moon date line, an Era as a constellation, the travelling-star Loot Drop, Write in the centre of the tab bar, and Write as a laptop dialog with Seal on its first screen | 8 Starlight |
| The type, the Era title page, the week list and the laptop margin dates | 10 Folio |
| A waiting object that shows how much of the wait has passed | 9 Nightstand |

| Change | Why |
|---|---|
| Photos run edge to edge and swipe | Aaron: the phone is the main device, and photos should fill it |
| A hairline and space between entries | Aaron: round 2's phone feeds were too compact |
| Three stacked rows at the top become one slim line | Bigger photos must still start within 284 px (D-25) |
| The star works in light mode as well as dark | Round 2's light star read like a diagram |

| Avoid | Because of |
|---|---|
| Large radii and soft, bubbly shapes | 7 Afterglow (too round, not modern) |
| Medicine-like shapes | 11 Keepsake's capsule read as a pill (D-39) |
| A floating button over text | 8 Starlight's first version covered the caption |
| A filled box for every row at the top of the feed | Round 2's feeds stacked three |
| A page-wide band of colour | One accent, on actions and Loot Drops only (D-40) |
