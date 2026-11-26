---
paths:
  - "frontend/**"
  - "docs/design/DESIGN.md"
---

# Design system rules (D-40)

One design system for the whole frontend: **one spacing scale, two typefaces at most, one accent colour.** Each numbered section fixes one of the ten problems that made the round 2 mockups look cheap; the evidence is in `docs/design/explorations/round-2/AUDIT.md`. `docs/design/DESIGN.md` holds the chosen look (D-41): the typefaces, the accent, the final values and every component, inside these limits. Build screens from it, never from the mockups' code. If a screen needs something these rules and DESIGN.md don't give, ask; don't invent it.

## Tokens (`frontend/src/index.css`)
- Every colour, font, text size, weight, radius and shadow is a token. A component never holds a hex value, a font name or an arbitrary value (`p-[13px]`, `text-[15px]`, `rounded-[10px]`, `bg-[#fff]`).
- Remove Tailwind's defaults for those namespaces, so `bg-red-500`, `text-3xl` or `rounded-xl` can't be written by accident:

```css
@import "tailwindcss";
@custom-variant dark (&:where([data-theme=dark], [data-theme=dark] *));

:root { --page: …; --surface: …; --raised: …; --ink: …; --ink-2: …; --line: …; --control: …; --accent: …; --on-accent: …; --danger: …; --scrim: …;
        --band: …; --on-band: …; --on-band-2: …; --moon-lit: …; --moon-dark: …; --float: …; } /* values: DESIGN.md §1 and §3 */
[data-theme="dark"] { /* the same names, designed on their own, never inverted */ }

@theme {
  --color-*: initial; --font-*: initial; --text-*: initial; --font-weight-*: initial; --radius-*: initial; --shadow-*: initial;
  --leading-*: initial; --tracking-*: initial; --inset-shadow-*: initial; --drop-shadow-*: initial; --text-shadow-*: initial; --breakpoint-*: initial;
  --animate-*: initial; --default-transition-duration: 200ms; --default-transition-timing-function: ease-out; /* DESIGN.md §7 */
  --font-text: …; --font-display: …; --default-font-family: var(--font-text); --default-mono-font-family: var(--font-text);
  --text-meta: 0.75rem;    --text-meta--line-height: 1.5;
  --text-small: 0.875rem;  --text-small--line-height: 1.5;
  --text-body: 1rem;       --text-body--line-height: 1.5;
  --text-read: 1.125rem;   --text-read--line-height: 1.6;
  --text-title: 1.25rem;   --text-title--line-height: 1.2;   --text-title--font-weight: 500;
  --text-heading: 1.75rem; --text-heading--line-height: 1.2; --text-heading--font-weight: 500; --text-heading--letter-spacing: -0.01em;
  --text-display: 2.5rem;  --text-display--line-height: 1.2; --text-display--font-weight: 500; --text-display--letter-spacing: -0.02em;
  --font-weight-normal: 400; --font-weight-strong: 600; /* the title sizes carry Newsreader's 500 themselves */
  --radius-sm: 0.5rem; --radius-lg: 1rem; /* rounded-full is built in */
  --breakpoint-md: 48rem; --breakpoint-lg: 64rem;
}
/* One line for each name in :root. The shadow differs by mode, so :root and dark hold --float. */
@theme inline { --color-page: var(--page); /* … */ --shadow-float: var(--float); }
```
- This syntax was checked against the Tailwind v4 docs on 2026-11-14, and again with context7 and a build of Tailwind 4.3.5 in task 4.1 (2026-11-24). The resets leave no named utilities (`bg-red-500`, `text-3xl`, `rounded-xl`, `shadow-lg`, `leading-tight`, `animate-spin`, `xl:` all compile to nothing). Three things still compile because they come from the spacing step or are built in: paddings off the scale (`p-5`), spacing line heights (`leading-6`) and `leading-none`. The checker catches all three.
- `src/tokens.test.ts` fails when dark mode misses a token, a token has no utility, or a text pair is under 4.5:1 (3:1 for a `control` border). Add a pair there when a screen puts text on a new background.
- shadcn/ui components arrive with Tailwind's default class names (`text-sm`, `rounded-md`, `bg-background`). Rewrite every class to these tokens when you copy one in (D-13).

| Kind | Limit | Value (DESIGN.md, D-41) |
|---|---|---|
| Spacing | One scale | 4, 8, 12, 16, 24, 32, 48, 64 px: the steps `1 2 3 4 6 8 12 16` |
| Typefaces | Two | Instrument Sans for text and the interface, entry text included; Newsreader for titles only |
| Text sizes | Seven, none under 12 px, in rem | meta 12, small 14, body 16, read 18, title 20, heading 28, display 40 px |
| Weights | Three | Instrument Sans 400 and 600, Newsreader 500 |
| Line heights | Three | 1.2 for titles, 1.5 for the interface, 1.6 for reading |
| Colours | One accent hue in each theme | Black, white and gold: page, surface, raised, ink, ink-2, line, control, accent (gold), on-accent, danger, scrim, plus band, on-band and on-band-2 for an Era's title band and moon-lit and moon-dark for the moon. Values in DESIGN.md §1 |
| Corners | Two radii, plus full | sm 8 px (controls, thumbnails), lg 16 px (cards, photos, sheets), full (pills, avatars) |
| Shadow | One | Only on things that float: a menu, a dialog, a sheet |
| Breakpoints | Two | Layout changes at `md` (48rem) and `lg` (64rem) only |

## 1. Spacing and alignment
- Every padding, margin and gap is a step of the scale. No other step and no half step. Widths and heights are free.
- One page gutter: 16 px on a phone, 24 px from `md`, 32 px from `lg`. Every block starts on the gutter or runs to the screen edge. Nothing sits 2 or 4 px off it.
- Rhythm: 8 px inside a group, 16 px between blocks, 32 px between sections. Space children with `gap` on the parent, not with margins.
- Things side by side share a top and a height.

## 2. Type
- One job, one size: every caption is `body`, every list title `title`, an entry's own writing `read`, a screen title `heading`. `display` appears once on a screen at most (a countdown, an Era title page).
- Bold is weight 600. No other weight on the text typeface, no third typeface for italics, and no letter-spacing except on `heading` and `display`.
- Clamp long text by lines. A title wraps to a second line before it is cut.

## 3. First screen (phone feed)
- At 375×812 the first entry's photo starts no lower than 35% down (284 px), after at most five rows of text and three filled boxes. The checker prints both numbers.
- The budget is fixed. DESIGN.md meets it with the moon line in the top bar, On this day as one row, and the Era and the week on one line (the mockup's first photo starts at 223 px). Never add a row above the first entry without removing one: the Opened row takes On this day's place.
- The top bar and the tab bar together take at most 15% of the height.

## 4. Shape
- Boxes nest two deep at most: a card, then a control. A rounded thing inside a rounded box uses `sm` or no radius.
- A row that is only a link (an Era line, a week line) is not a box: no fill and no border. Separate rows with space or one `line` rule.
- Controls in one row or column share a height: 44 px for buttons and fields, 32 px for chips.

## 5. Loot Drop object
- Never improvise a drawing in JSX. The object is one SVG file in `src/assets/`, drawn on one grid with one stroke width, in token colours only, with no text in it and nothing rotated.
- The approved object is the **travelling star** (D-41); its geometry is in DESIGN.md §5. Any other drawing needs the user's approval first.
- Dates and the countdown are always HTML text. No padlock, and no pill or capsule shape (D-39).

## 6. Loading
- Fonts are served by the app as `woff2`: at most four files and 150 KB, with `font-display: swap` and the text typeface preloaded. No page load calls another company's server (Spotify's player loads only on a tap, D-35). A font package is a new dependency: ask first.
- Every `<img>` has `width` and `height`, or sits in a box with a fixed `aspect-ratio`, and has `decoding="async"`. The first photo on a screen is eager with `fetchpriority="high"`; every other one has `loading="lazy"`. A list loads only the first photo of each entry until the user swipes.
- Anything that arrives late (photos, the Opened strip, On this day) has its space reserved, so nothing jumps: layout shift stays at 0.1 or less.
- The feed doesn't ship the editor: load capture (TipTap, dnd-kit, Vaul), the lightbox and the date picker with `React.lazy`. The feed's first JavaScript is 200 KB gzipped or less.
- Target on the slow-phone profile: the largest thing on screen appears within 2.5 s. Lists and the feed use the photo's smaller copies (D-43, from task 4.18): the thumbnail for anything small, the screen copy for the swipe, the original only in the entry view. When a copy is missing, fall back to the next size up. A photo's shape is reserved from its stored `width` and `height` (D-17, from task 4.5), with a 4:5 frame when they are `null`.

## 7. Colour
- One accent hue. It marks the primary action, links, the selected item, focus and the Loot Drop. It never colours headings, body text or decoration.
- One accent value in each mode: no second shade for links or stamps. The Loot Drop uses the accent, not gold.
- The Spotify mark is black in light mode and white in dark mode, never green.
- `danger` is for an error message or a destructive confirmation, and nothing else.
- Photos carry the colour; the interface stays neutral.

## 8. Surfaces and controls
- Three surface tones at most: page, surface, raised. A tone step alone never marks something as tappable.
- Three button styles: primary (filled accent, one on a screen), secondary (a 1 px `control` border), quiet (text). Everything tappable is a button, a link, a nav item, a chip or a row with a chevron.
- A `control` border contrasts 3:1 or more with its background (WCAG 1.4.11). Text contrasts 4.5:1, or 3:1 from 24 px.
- Static things (a date stamp, a row of counts) never look like a button or a pill.

## 9. Navigation and controls
- Phone tab bar: five equal columns, Write in the centre and no wider than its column, every label `meta` size, exactly one item marked current.
- The laptop nav lists the same places in the same order.
- Everything tappable is at least 44×44 px on a phone. A 32 px chip gets a 44 px hit area.
- Icons are `lucide-react` only, at 16 or 20 px with one stroke width. Toolbar buttons are icons, not letters. Links have one style.

## 10. Laptop layout
- Reading text is 680 px wide at most (about 70 characters). A feed column is 560 to 640 px.
- Content starts at the top of its area, and columns share that top. Only a screen with one short block (a sealed Loot Drop) centres it, as one block no wider than 640 px.
- A rail never repeats what the column shows. A missing cover shows the Era's initial at the cover's size, never an empty box.
- A dialog is centred on the screen, over a scrim that covers everything, the nav included.
- A strip of thumbnails has equal tiles and ends on the edge of the photo above it.

## After every fix
Fix one problem at a time. After each one:
1. With the dev server up, run the checker on the screen you changed: `node docs/design/explorations/tools/audit.mjs http://localhost:5173/<path>`. Add `--dark` for dark mode, `--slow` when the fix touches loading, and `--out <folder>` to keep both screenshots. It loads the page at 375×812 and at 1440×900 and prints PASS or FAIL against these limits.
2. Look at the page at both widths, in light and dark: the two screenshots, or the browser pane at phone and desktop size.
3. Tell the user what you created, what you changed and why (which problem above), with the checker's lines before and after.

Don't start the next fix while a check fails. If a limit can't be met, say why and ask.

## Where each problem is first fixed
| Problems | Task |
|---|---|
| Tokens, spacing, type, corners and colour (1, 2, 4, 7), and fonts (6) | 4.1 |
| Tab bar, nav, gutters and the laptop shell (9, 10) | 4.3 |
| First screen, boxes and photo loading (3, 4, 6) | 4.4 and 4.5 |
| Buttons, chips and fields (8, 9) | 4.6 |
| Loot Drop object and the sealed card (5) | 4.8 |
| Spotify mark (7) | 4.15 |
