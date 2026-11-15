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
