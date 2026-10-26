# Design Directions (pre-design)

> Early options for how memoirist should look and feel. **Nothing here is decided.** When a direction is chosen, record it with `/record-decision` and write the result to `docs/design/DESIGN.md`. **Not needed for coding tasks** until DESIGN.md exists.

**Status:** exploring · **Needed by:** Sprint 4 (React UI)

## Candidate directions

1. **Scrapbook / analogue:** paper textures, polaroid frames, a handwritten accent font, warm cream and ink. This fits the paper-diary scanning feature (D-20).
2. **Quiet editorial:** lots of white space, serif headings, muted colours, like a printed memoir. Calm and timeless.
3. **Night sky / time capsule:** dark-first, deep blues, soft glows, starfield vault cards for Loot Drops. Leans into the "capsule" idea.
4. **Warm Instagram-modern:** familiar social-app layout, rounded cards, a sunset gradient accent. The lowest learning curve for friends in Phase 5 groups.

## How choosing will work

1. Build the same 3 screens in each direction (feed, capture modal, sealed Loot Drop card), with realistic sample entries: a Grampians trip, a sealed drop, a photographed diary page.
2. Compare them side by side, on desktop and at phone width, in light and dark mode.
3. Pick one, or mix them ("direction 2's fonts with direction 1's cards").
4. Write `docs/design/DESIGN.md` from the result: colour and type tokens for Tailwind `@theme`, a screen list, states, motion rules, and accessibility checks.

## Ways to generate the mockups

- **Plain Claude:** ask for several deliberately different directions.
- **`ui-ux-pro-max` skill** (installed): ask for 3 *different* styles, not one "best" answer.
- **`frontend-design` plugin:** pushes towards bolder, less generic designs.
- **Mixing sources** gives more varied options than asking one source four times.

Mockups go in `docs/design/mockups/` (one HTML file per direction).

## Notes and ideas
_Add anything you like here as it comes up: apps whose look you like, colours, fonts, screenshots._
