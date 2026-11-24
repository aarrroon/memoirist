# Prompt: build Sprint 4 overnight

Builds the rest of Sprint 4 (the React UI, tasks 4.2 to 4.17) unattended, and paces itself against the usage limits. **Part A** runs while you're still awake: about 30 minutes of questions and one install to approve. **Part B** runs while you sleep and never waits for you.

**Before pasting:**
1. Task 4.1 is committed on `phase-1-sprint-4` (branched from `phase-1-sprint-3`) and pushed, with its font question answered.
2. No other session is working in this folder.
3. Docker Desktop is running. The laptop is plugged in with its lid open, and Windows sleep is set to *Never* while plugged in. This matters: the app keeps the computer awake only while a session is working, so it can sleep while the run waits for a limit to reset, and then the check-ins never fire.
4. If your app offers *continue when the usage limit resets* ([WORKFLOW.md §5](../WORKFLOW.md#5-overnight-runs)), turn it on. The prompt's own check-ins work without it.
5. Start a **new session in auto mode** with the `learn-later` output style (this project's default), and paste everything below the line.

**If it stopped early** (the weekly limit, or morning came): start a new session the same way and paste:
`Continue the Sprint 4 overnight run in docs/prompts/overnight-sprint-4.md. In Part A, do only steps 1, 2, 5 and 6, then run Part B from the first unticked task.`

---

Build the rest of memoirist's Sprint 4 (the React UI): tasks 4.2 to 4.17, skipping any task already ticked in `docs/SPRINT_PLAN.md`. **Use a workflow** for the building. The run has two parts: Part A happens while I'm still here and may ask me questions; Part B happens while I'm asleep and never waits for me.

## Usage limits (the numbers are mine to edit)
The 5-hour limit will run out several times tonight, and the weekly limit is shared with my daytime work. Read both with `mcp__ccd_session_mgmt__get_usage`: its `plan.windows` lists "5-hour limit" and "Weekly · all models", each with `percentUsed` and `resetsAt`.

| Check | Rule |
|---|---|
| Before each task | Read the usage. Start the task only if the 5-hour window has at least as much left (100 − `percentUsed`) as the most any one task has used so far tonight. Assume 30% until a task has been measured, and never ask for more than 70%. |
| Weekly | At 90% or more of "Weekly · all models", start no new task: do the wrap-up and leave the rest for another night. |
| Not enough left | Don't start the task. Schedule a one-off check-in with CronCreate for 5 minutes after `resetsAt`, and stop working until it fires. |
| Measure | After each task, read the usage again. If the window didn't reset during the task, the difference is that task's cost. Keep the largest. |
| `unavailable` | Try again in 10 minutes. After 3 misses in a row, carry on and rely on the safety net. |
| Log | Add a row to the Usage table in `docs/OVERNIGHT_REPORT.md` for every check: the time, both percentages, and the decision (start, wait until a time, or wrap up). |

**Safety net:** the limit can still run out in the middle of a task. At the start of Part B, create a recurring check-in with CronCreate every 30 minutes. Each time it fires:
- If a workflow run is going, do nothing.
- If a run stopped because an agent returned nothing (the usage limit or an API error) and the 5-hour window has reset, resume it with `resumeFromRunId`, so the finished agents replay from cache.
- Never restart a stopped task by hand, and never `git reset`, `git checkout --` or `git clean` its half-done work: the resumed agent carries on from it.

Delete every check-in you created when the run ends.

## Part A: with me (I'm still awake)
1. **Preflight.** Check each of these. If one fails, stop and tell me which, because nothing can be built or verified without it:
   - `java -version` reports 25. `docker info` succeeds, and `docker compose up -d` brings Postgres and SeaweedFS up healthy.
   - `gh auth status` is logged in. `.env` exists (check that it exists; never read it).
   - The branch is `phase-1-sprint-4`, `git log` has a `Sprint 4.1:` commit, and the branch is pushed.
   - The working tree is clean. If the only changes are `docs/prompts/overnight-sprint-4.md`, `docs/CURRENT_SKILLS.md`, `docs/WORKFLOW.md` and `docs/prompts/overnight-phase-1.md`, commit them first as `Docs: add the Sprint 4 overnight prompt` and push.
   - No other session in this folder is running (list my sessions). If one is, tell me which.
   - `cd frontend && npm ci && npm test && npm run lint && npm run typecheck && npm run build` passes.
2. **Ask the app to keep the computer awake** for the run.
3. **Sprint readiness.** Follow the `/sprint-ready 4` skill. For each blocking question, gap or open Q-x that Sprint 4 needs, ask me with AskUserQuestion (each option with its trade-offs, and a recommended default with its reason), several questions per call. Record each answer with the `/record-decision` process. The Sprint 3 run left these open; ask about each unless a D-entry already settles it:
   - Is `tagIds` required on `PUT /entries`? (capture, 4.6; tags, 4.11)
   - Is a page `size` over 50 a `400` or a clamp? (every list)
   - Does a `PUT` while an Era's cover is hidden clear the cover? (4.14)
   - Should the Era page hide empty weeks? (4.9)
   - Can `dateUnknown` entries appear in On this day? (4.14)
   - Should uploads far over 25 MB get `max-swallow-size=64MB`, so the browser sees a `413` instead of a dropped connection? (4.6)
   - Q29, smaller copies of photos: build the default (serve the originals) and report the slow-phone number? (4.4, 4.5)
   - Where the font files come from, if 4.1 left the question open.
4. **Libraries.** Nothing may run `npm install` in Part B, because it asks for approval and would stall the run. So install everything Sprint 4 needs now, in one command:
   - List the packages tasks 4.2 to 4.17 need from D-13's main table: TanStack Query, Zustand, React Router, `openapi-fetch`, Embla Carousel, `lucide-react`, `date-fns` and `@date-fns/tz`, `react-hook-form` and `zod`, the Radix UI primitives behind the shadcn/ui components, Sonner, dnd-kit, Vaul, react-day-picker, browser-image-compression, yet-another-react-lightbox, react-error-boundary, TipTap with StarterKit and Link, and Motion. Add Playwright for 4.17. Check each name and current version with context7 or the npm registry. Leave out D-13's "added when the task needs them" table (TanStack Virtual, wavesurfer.js, Storybook).
   - Flag anything not named in D-13, for example shadcn's helpers (`clsx`, `tailwind-merge`, `class-variance-authority`), `@hookform/resolvers` and `@playwright/test`. Ask me about those with AskUserQuestion.
   - Show me the one `npm install` command, then run it; I'll approve it. Then run `npx playwright install chromium`.
   - Check that the frontend checks still pass, update TECH_STACK.md §3 if it lists versions, and commit as `Sprint 4: add the Sprint 4 libraries (D-13)`. Push.
5. **The workflow script.** Load the `workflow-authoring` skill first. Start from the Sprint 3 script, which worked: find it with Glob at `~/.claude/projects/*memoirist*/*/workflows/scripts/memoirist-sprint-build-*.js`. Keep its shape (per task: a builder, the `spec-reviewer`, a fix step if needed, and a close-out; then a sprint wrap-up) and its structured results. Change its environment notes and gates for the frontend, as Part B says. If the script is gone, write a new one with the same shape.
6. **Write the run's brief** in the script's arguments:
   - the branch (`phase-1-sprint-4`, base `phase-1-sprint-3`) and the tasks left
   - the machine facts: Windows and Git Bash, Node and npm versions, JDK 25, Docker, `gh`
   - the decisions recorded in step 3
   - the Sprint 1–3 assumptions that touch the frontend (`docs/OVERNIGHT_REPORT.md`)
7. **Tell me Part A is done** in three or four lines: what I decided, what was installed, and how many tasks are left. Then start Part B straight away; don't wait for a reply.

## Part B: while I'm asleep
Never ask me anything or wait for me from here on. Build one task at a time, in order. Run the workflow **for one task per run** (pass that task alone in its arguments), so you can read the usage between tasks. After the last task, run the sprint wrap-up as its own run.

### Each task (one fresh builder agent)
The builder follows the `/implement-task <id>` process unattended, in the `learn-later` style: complete, working code, with one or two practice blocks (`TODO(human) PRACTICE` with the answer in a marked block). Good frontend practice blocks:
- a TanStack Query hook and its cache key
- a function in `src/lib/time.ts`
- an MSW test of an error state
- a component test that holds a sealed entry

Never put one in a generated file (`schema.d.ts`), a config file or a migration.

**Gates, in order, before a task counts as done:**
1. Every **Done when** item is checked by running something, with the decisive output quoted.
2. `cd frontend && npm test && npm run lint && npm run typecheck && npm run build` all pass with the practice answers in place. If the backend changed (task 4.5), `./gradlew spotlessApply` then `./gradlew build` ends in `BUILD SUCCESSFUL`.
3. **Screens:** every screen the task built or changed:
   - passes `node docs/design/explorations/tools/audit.mjs <url>` at 375 and 1440 px, in light and dark ("No check failed")
   - is used by keyboard alone, and tried with reduced motion
   - gets a 375 px screenshot, light and dark, saved in `docs/build-journal/sprint-4/screens/` for my morning review
4. Any list, count or filter of entries has a test whose data holds a sealed entry, a Recently deleted entry and another user's entry (non-negotiable 4). No sealed content shows anywhere.
5. The `spec-reviewer` subagent reviews the change; fix every blocker and major finding.
6. Docs change with the code; run `/sync-docs` after task 4.5's backend work.
7. The build journal entry `docs/build-journal/sprint-4/4.N-short-name.md` is written as `docs/build-journal/TEMPLATE.md` says, with 4.1's entry as the model. It includes the decisions with their alternatives and everything that went wrong.
8. Tick the task in `docs/SPRINT_PLAN.md` and move CLAUDE.md's Current focus to the next task. Commit as `Sprint 4.N: <summary>` with no Claude attribution, and push `phase-1-sprint-4`.

**Task notes:**
| Task | Note |
|---|---|
| 4.2 | `npm run gen:api` needs the backend: start `./gradlew bootRun --args='--spring.profiles.active=local'` in the background from `backend/`, generate, then stop it. |
| 4.5 | Backend first: the `/new-migration` process for `V2__media_dimensions.sql` (check that no V2 exists yet; never edit V1), then the media endpoint, `@Schema(nullable = true)`, `ApiDocsContractTest`, `/sync-docs`, and `npm run gen:api` again. |
| 4.7 | wavesurfer.js isn't installed: use a plain audio player. |
| 4.8 | The travelling star is one SVG in `src/assets/`, drawn from DESIGN.md §5's geometry. Never a padlock. |
| 4.15 | There are no Spotify credentials: build against the backend's `503 music-search-unavailable` and an MSW mock, and list the setup I need. |
| 4.17 | Playwright runs against Compose and the dev servers. The CI frontend job must pass on GitHub: after pushing, wait for the run with `gh run watch`. |

**Every screen:** build from `docs/design/DESIGN.md` and `.claude/rules/design.md`, with tokens only. Compare with the round 3 mockup's screenshots (`docs/design/explorations/round-3/screenshots/`), but never copy its code. When DESIGN.md doesn't give a look, follow the round 3 mockup and list it as an assumption.

**Running things:** start the Vite dev server and the backend in the background when a task needs them. Stop everything you started before the task ends; only the Compose services stay up. Bash commands time out after 10 minutes, so pass `timeout: 600000` for builds.

### Never stop for these: work around them and note them
| Situation | What to do |
|---|---|
| The usage limit | Follow the usage rules above: wait for the reset, then carry on or resume |
| An open question (Q-x) or flow gap (G-xx) | Use its "Default if nobody decides" from DECISIONS.md or USER_FLOWS.md. Don't record a decision; list it as an assumption |
| Two docs disagree | README wins on what and when, DECISIONS.md on how (D-27). Follow the winner, leave the other alone, and list it |
| A library that isn't installed | Never run `npm install`. Build it with what's installed, or leave the smallest stub with a `TODO(human) DECIDE:` comment, and list it |
| A look DESIGN.md doesn't give | Follow the round 3 mockup and list it |
| Tests still failing after 3 fix attempts | Commit with `[needs-attention]` at the end of the commit title, record the failing test and its output, and carry on with the next task |
| Anything that would change the README | Don't change it; list what you'd change and why |
| A push or `gh` command fails | Keep working locally, retry at the end, and list it |

**Never:** read `.env`, run `npm install` or `npm uninstall`, run `docker compose down`, edit a committed migration, push to `main`, force-push, or merge a pull request.

### Wrap-up (after the last task, or when the weekly limit says stop)
1. Write `docs/build-journal/sprint-4/README.md`:
   - what the sprint built, and how the tasks fit together
   - a Mermaid diagram of the frontend: routes, Query hooks, the API client and the stores
   - the screens, with their screenshots
   - the test strategy, how to run everything, and the lessons

   Update the table in `docs/build-journal/README.md`. If the run stopped early, write it for the tasks done so far and say which task is next.
2. Push, then open the pull request from `phase-1-sprint-4` into `phase-1-sprint-3`. It's stacked on PR #3, so it shows only Sprint 4. Make it a draft if any task needs attention or the sprint isn't finished. If the branch already has a PR, update it with `gh pr edit`.
3. Delete every check-in you created.

## Running log
Keep a **Sprint 4** section at the top of `docs/OVERNIGHT_REPORT.md` up to date, and commit it with each task, so nothing is lost if the session is compacted or stopped. It holds one line per task (done, or needs attention, with its commit), the Usage table, the assumptions and the questions.

## The report in the chat (at the end)
1. A table: each task, its commit, and done or needs attention; then the PR link.
2. **Usage:** how many times the run paused and for how long, the largest task's cost, and the weekly percentage at the end.
3. **Assumptions** made instead of asking me, each with the Q-x, G-xx or doc it came from.
4. **Needs my attention:** failing tests, missing libraries, README changes you'd suggest, and things I need to set up.
5. **Practice blocks** to try: file:line and what each teaches.
6. **Where to start studying:** the journal entries and screenshots worth looking at first, and why.
7. **What's left:** the next task if the run stopped early, and the merge order for PR #1 through the new one.
