# Prompt: a design round overnight

Runs one round of design explorations unattended:
- It reads the newest feedback in [FEEDBACK.md](../design/explorations/FEEDBACK.md) and plans 4–5 new designs.
- It builds each design in two colour themes, light and dark, at phone and laptop width.
- It makes screenshot boards for you to message to friends.

Repeat it each round until a direction wins. Then record the winner with `/record-decision` and write `docs/design/DESIGN.md`.

**Before pasting:**
1. Add the newest feedback to `FEEDBACK.md` as a new section, with your answers. You can also paste the chat into a session and ask Claude to add it.
2. Set the **Round** line below. Round 2 reads "Round 1 feedback"; round 3 reads "Round 2 feedback".
3. In Claude Code:
   - Turn auto mode on.
   - Turn on *continue when the usage limit resets*.
   - Run `/output-style learn-later`.
4. Set Windows sleep to *Never* while plugged in (see [WORKFLOW.md §5](../WORKFLOW.md#5-overnight-runs), items 7 and 8).
5. Check that Chrome and Node 22 or later are installed.

Then start a **new session** and paste everything below the line.

---

Run memoirist's next design round while I'm asleep.
- **Use a workflow.** Run fresh agents for the design work, as described below. About 11–15 agents in total is fine.
- **Don't wait for me.** Don't ask me anything at any point. I'll read your report on my phone in the morning.

**Round:** 2. Plan from the newest section of `docs/design/explorations/FEEDBACK.md`, and write everything into `docs/design/explorations/round-2/` (called `round-N/` below). Where that section says something different from this prompt (screens, colours, how many designs), FEEDBACK.md wins. The limits of D-40 (below) win over both.

## Ground rules
- **Where to write:**
  - Write only in `round-N/` and in `explorations/README.md` (one line for this round), plus additions to `shared/content.js` and `shared/helpers.js`.
  - Shared files get additions only: round 1 must render exactly as before, and its `console.assert` checks must still pass. Only you change `shared/`, never an agent.
  - Don't touch `frontend/`, `backend/`, other docs, `DIRECTIONS.md`, `FEEDBACK.md` or earlier rounds' files.
  - Don't create `DESIGN.md`.
- **Git:** don't commit, push or switch branches. Other sessions may share this folder.
- **Format:**
  - Plain HTML, CSS and JS with no build step.
  - One self-contained file per design, `round-N/directions/<id>.html`. It loads `../../shared/content.js` and `../../shared/helpers.js`, and gets the photos through them.
  - Design tokens are CSS variables in `:root`, for each theme and mode. Name the colours as the frontend will (`--page`, `--surface`, `--raised`, `--ink`, `--ink-2`, `--line`, `--control`, `--accent`, `--on-accent`), so the winner's values can be copied across.
- **Content:**
  - Use the same invented journal and the same "now" as round 1: Sam in Melbourne, Wednesday 28 October 2026, 7:42 pm.
  - Photos come only from `shared/photos/` (credited in its `CREDITS.md`), and fonts only from Google Fonts. Download nothing else.
- **Numbering:** carry on from the earlier rounds' boards (round 2 starts at 7), so friends never mix up the designs.

## One design system (D-40)
The design that wins must be buildable as drawn, so every design keeps the limits of the real frontend. They are in `.claude/rules/design.md`, and the reasons are in [round 2's audit](../design/explorations/round-2/AUDIT.md). They win over FEEDBACK.md and over a design's own idea.
- **One spacing scale:** every padding, margin and gap is 4, 8, 12, 16, 24, 32, 48 or 64 px. One 16 px gutter on phones.
- **Two typefaces at most** in a design: one for text, and one more for titles only. Seven text sizes at most, none under 12 px, with three weights and three line heights.
- **One accent colour** in each theme, on actions and the Loot Drop only. The Loot Drop uses the accent, not a second colour. The Spotify mark is black or white.
- **Shape:** two corner radii, plus full for pills and circles. Boxes nest two deep at most.
- **First screen:** on a phone the first entry's photo starts no lower than 35% down (284 px), after at most five rows of text and three filled boxes.
- **Controls:** every tap target is at least 44×44 px. The phone tab bar has five equal columns, with Write in the centre and no wider than its column, and one label size.
- **Loot Drop object:** one drawing per design, on one grid with one stroke width, in the theme's token colours, with no text in it and nothing rotated. The dates and the countdown are HTML text beside it. If it can't be drawn well, make the Loot Drop typographic.
- **Photos:** every photo has its space reserved, and photos below the first screen use `loading="lazy"`.

`tools/audit.mjs --mockup` checks these (Step 3). In a mockup two things can't be fixed, so they only warn: the sample photos are full size, and the fonts come from Google.

## Rules every design keeps
These come from round 1's review ([REVIEW.md](../design/explorations/REVIEW.md)) and the product docs.
- **The feed's first phone screen** (375×812) shows an entry's title and the top of its photo:
  - On this day is one short row.
  - The Era is a compact header.
  - Each week is a one-line header that opens its summary (D-25).
  - A week's entries stay in day order (F-08).
- **No day tracking:** nothing shows which days were missed (D-32).
- **Sealed entries:** they are never counted and never leak. A sealed card is a fixed size and shows only the memory date, the opening date and the countdown, and under a day heading only the last two (D-10).
- **Write:**
  - One Write button, in the centre of the phone tab bar, at least 44×44 px.
  - New entry, Photo dump and Diary page sit behind it.
  - It never looks like Edit.
- **Words:**
  - Labels use the PRD's words (§3): Eras, weeks, entries, Opens, Written, "Eras inside this one".
  - The Spotify mark is black or white (D-40; Spotify's brand rules also allow green).
- **The Loot Drop page:**
  - It shows the opening date once.
  - It uses a waiting object, not a padlock.
- **No repeats:** no fact appears twice on one screen.
- **Basics:**
  - WCAG AA contrast in every theme and mode.
  - Visible focus.
  - `prefers-reduced-motion` respected.
  - No emoji.
  - Nothing the PRD doesn't describe.

## Preflight
1. Ask the app to keep the computer awake for the run.
2. Check that `node --version` is 22 or later and that Chrome exists (`C:/Program Files/Google/Chrome/Application/chrome.exe`, or set `CHROME`). **This is the only reason to stop:** without them nothing can be screenshotted, so tell me which check failed.
3. If `round-N/REPORT.md` exists, this is a resumed run. Read it and carry on from where it stopped, without redoing finished work. Otherwise create it.
4. Start one static server in the background from `docs/design/explorations/` (`python -m http.server 8765 --bind 127.0.0.1`), for the taste skill's inspector. Stop it at the end.

## Step 1: plan (you, before any agent)
Read these first:
- `CLAUDE.md`
- `README.md` (Timeline)
- `docs/PRD.md` (§3 glossary)
- `docs/USER_FLOWS.md` (F-02, F-03, F-04, F-06, F-07, F-08, F-11, F-15, F-19)
- `docs/design/DIRECTIONS.md`
- `.claude/rules/design.md` and `docs/design/explorations/round-2/AUDIT.md` (the limits of D-40, and the ten faults they fix)
- in `docs/design/explorations/`: `README.md`, `REVIEW.md`, `FEEDBACK.md`, and the boards in `screenshots/boards/` and any `round-*/screenshots/boards/`.

Skip `docs/product/`. Use `/ui-ux-pro-max` (its design-system, colour, typography and UX searches) and `/taste-skill:design-taste-frontend` (its app-screen rules) for the decisions.

Write `round-N/PLAN.md`, then carry on without waiting for approval. It covers:
- **What the feedback means:** keep, change and avoid, as a short table.
- **The designs**, as many as FEEDBACK.md asks for. For each:
  - Its number, name and one-line idea.
  - What it keeps from earlier rounds, and why.
  - The layout at 375 and at 1440.
  - Its fonts and its signature detail.
  - Its Loot Drop: the sealed card in the feed, the sealed page, and what gives it personality. Use a waiting object, like round 1's film canister, with no film, tape or padlock.
- **Round 2's starting ideas** (the plan may change them; later rounds start from FEEDBACK.md):
  - Golden Hour refined: the familiar feed with a signature of its own, and no orange.
  - Observatory softened: its structure and star Loot Drop, with a lighter night and a real light mode.
  - The familiar feed at night: 4's layout with 3's mood.
  - Memoir made modern: 2's calm and type, with 4's ease.
  - A wildcard built from the feedback, such as 6's colours and object-like Loot Drop without the film.
- **Colour:**
  - Two themes per design, from FEEDBACK.md's palettes plus up to two new ones you propose, with reasons.
  - Every palette has one accent (D-40). Where a palette's Loot Drop colour differs from its accent, use the answer FEEDBACK.md gives for it.
  - Spread them so each palette appears on two different designs where possible. Then friends judge colour apart from layout.
  - Design each theme's light and dark mode on its own; don't invert one into the other.
- **The Write screen:** the capture view from F-02, F-03, F-04 and F-06, mid-entry. It shows the memory date, an optional title, formatted text, photos, a voice note, tags, a template (F-11), a soundtrack, and *Seal as Loot Drop* with its quick dates. It's a full-screen sheet on phones and a panel or dialog on laptops.
- **URLs:** `round-N/directions/<id>.html?theme=<a|b>&mode=<light|dark>#<feed|entry|era|loot|write>`. With no parameters, show theme a in the device's light or dark setting.
- **The screenshot list and the board layout** (Step 5).

## Step 2: shared additions (you)
- Add what the plan needs to `shared/content.js`, for example the Write screen's half-written entry.
- Add what it needs to `shared/helpers.js`, for example a helper that reads `theme` and `mode` from the URL and sets `data-theme` and `data-mode` on `<html>`.
- Then shoot one round 1 screen with `tools/shoot.mjs` (its options are at the top of the file) and check it reports no page problems.

## Step 3: build (one fresh agent per design, in parallel)
Give each agent its section of PLAN.md, the rules above and the paths. Each agent:
1. Builds `round-N/directions/<id>.html`: every screen, both themes, light and dark, at 375 and 1440. It uses `/frontend-design:frontend-design` for the build and the taste skill's rules for app screens.
2. Runs the taste skill's checks:
   - `lint.mjs --app` on its file.
   - `inspect.mjs --app --widths 1440,375` on every screen of theme a in both modes, through `http://127.0.0.1:8765/round-N/...`, with `--out` in its own scratchpad.
   - It fixes every FAIL, or records an allowance with its reason in PLAN.md.
3. Runs the design check on every screen of theme a, and on theme b's feed and Loot Drop, in both modes: `node tools/audit.mjs --mockup "http://127.0.0.1:8765/round-N/directions/<id>.html?theme=a&mode=light#feed"` (several targets fit in one command; it loads each at 375×812 and at 1440×900). It fixes every FAIL: D-40's limits take no allowances. It quotes the last line of the result in REPORT.md.
4. Looks at its own screenshots from `tools/shoot.mjs`: phones at 375×812, 2x, with `mobile: true`; laptops at 1440×900, 1x. It fixes the three worst problems, then looks once more.
5. Checks these and quotes the decisive output in REPORT.md:
   - No horizontal scroll at 375 (the probe `document.documentElement.scrollWidth` gives 375).
   - No page problems.
   - The first phone feed screen shows an entry.
   - The sealed card leaks nothing.
   - The countdown says "in 362 days".
   - The design check prints "No check failed." after the last edit.
6. Saves its final screenshots:
   - `round-N/screenshots/<id>/<theme>-<mode>/<screen>-375.jpg` and `-1440.jpg`.
   - `feed-sealed-375.jpg`, using `scrollTo: "[data-sealed]"` and `scrollOffset: 300`.
   - Theme b needs only the phone feed, entry and Loot Drop, in both modes.
7. Adds a line to REPORT.md.

## Step 4: review and fix (one fresh agent per design, after its build)
This agent is a fresh pair of eyes, like round 1's REVIEW.md.
1. It checks the screenshots and the code against the rules above and against FEEDBACK.md:
   - No orange, and no pages flooded with colour.
   - No film or school props.
   - A dark mode that isn't too dark.
   - A Loot Drop with personality.
   - A familiar layout with a signature of its own.
   - D-40's limits: it runs `tools/audit.mjs --mockup` again on every screen it changes. A FAIL is a P1.
   - A Loot Drop drawing that looks improvised is a P1: redraw it, or make the Loot Drop typographic.
2. It ranks the problems from P1 to P3.
3. It fixes the P1s and P2s, and retakes the affected screenshots.
4. It records what changed in REPORT.md.

It gets one round only.

## Step 5: boards (one fresh agent, after every review)
- **`round-N/index.html`**, like round 1's: an overview board, a colour board and one board per design, built only from this round's screenshots.
- **Overview board:**
  - For each design: its phone feed in light and dark, its sealed Loot Drop, its number and name, and a one-line note on who it suits.
  - Four questions people can answer on instinct:
    - "Which would you open at 11pm to write about your day?"
    - "Which Loot Drop makes you most want to seal something?"
    - "Light or dark, and which colours?"
    - "What would you change?"
  - Ask for a top two, not a ranking.
- **Colour board:** one screen in every palette, light and dark, so people can pick colours apart from layouts.
- **Each design's board:**
  - All its phone screens in theme a, light.
  - The feed, entry and Loot Drop in theme a dark and in theme b.
  - The laptop feed and entry.
  - Good and Watch out notes.
  - The photo credits.
  - It must be readable on a phone when zoomed: about 12 phone screens at most. Split it into two boards if needed.
- **Nothing internal on any board:** no file names, round numbers or "From DIRECTIONS.md". Photo credits go on every board, because CC BY-SA requires them.
- **Export:**
  - Use `tools/shoot.mjs` with `selector` clips, 1600 px wide at 1.5x.
  - Save to `round-N/screenshots/boards/`: `0-overview.jpg`, `1-colours.jpg`, then one per design.
  - Look at every image before calling it done.

## Practice blocks
Follow `.claude/output-styles/learn-later.md`. Mark one or two real pieces of logic in the round as practice blocks: `TODO(human) PRACTICE`, with the answer in a marked block. Good candidates are the theme and mode switch in helpers.js, or a contrast check. Never use content, markup or styles.

## Never stop for these: work around them and note them
| Situation | What to do |
|---|---|
| A design idea doesn't hold up once you see it | Change it in PLAN.md and say why. Four strong designs beat five weak ones |
| The feedback and a doc disagree | README.md wins on what users get, and DECISIONS.md on how it works (D-27). The feedback wins on look and feel, inside D-40's limits. Note it |
| A design's idea needs a second accent, a third typeface or a size off the scale | D-40 wins. Change the idea, and note what was given up |
| A feature or screen the PRD doesn't describe would help | Don't add it. List it as a suggestion |
| A taste-skill rule fights a design's identity | Record an allowance with its reason |
| A font fails to load or doesn't fit | Pick another Google Font in the same spirit and note it |
| Chrome crashes or a screenshot times out | Retry once, then carry on and list it |
| The run stops at the usage limit | When it resumes, read REPORT.md and carry on from there |

## Running log
Update `round-N/REPORT.md` after each step: the plan, the shared additions, each build, each review and the boards. Then nothing is lost if the session is compacted or interrupted.

## When everything is done
1. Add round N's folder, and what it explored, to `explorations/README.md`. Stop the local server.
2. Send me the boards with SendUserFile (status proactive), overview first, so they reach my phone.
3. Post a short report:
   - A table: each design's number, name, idea, two palettes and board.
   - How the round answered the feedback: one line per point in FEEDBACK.md.
   - Allowances, assumptions and anything skipped.
   - The practice blocks: file:line and what each teaches.
   - Text I can paste with the boards when I message friends.
   - What the friends' answers should decide for the next round.
