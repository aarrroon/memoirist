# Current Claude Code toolkit

Everything set up for working on memoirist with Claude Code, and when to use each one. For the order to use them in, with text to copy, see [WORKFLOW.md §2 "Step by step"](WORKFLOW.md#step-by-step-exactly-what-to-type).

**Keep this file current:** when you add, remove or rename a skill, subagent, hook or prompt, update the matching table here in the same change.

## At a glance: which tool for which moment

| You want to… | Use |
|---|---|
| Check a sprint or feature is ready to start | `/sprint-ready` |
| Settle an open question | `/record-decision` |
| Check two features can be built at the same time | `/parallel-check` |
| Build one sprint task end to end | `/implement-task` |
| Change the database | `/new-migration` |
| Add or change an endpoint | `/new-endpoint` |
| Get an independent review of your changes | `spec-reviewer` subagent |
| Make sure the docs still match the code | `/sync-docs` |
| Bring the docs in line after editing the README | `/sync-readme` |
| Split the roadmap into features | [prompts/split-into-features.md](prompts/split-into-features.md) |
| Look up current library docs | context7 |
| Get quizzed on a change before committing | `/review-with-me` |
| Learn a topic properly, with a small exercise | `/teach` |
| Record what you learned | `/log-learning` |
| Change how much Claude teaches as it works | Output style (see [LEARNING.md](LEARNING.md)) |

---

## Project skills
Type `/name` to run one. Skills marked **You only** run only when you type them; the others Claude can also start on its own when your request matches.

| Skill | What it does | When to use it | Changes files? | Who starts it |
|---|---|---|---|---|
| `/implement-task [id]` | Reads the task, its README promise and (for screens) its user flow; offers you a piece to write yourself (one for your job, one new pattern, or "not this time"); plans and waits for your OK; builds, tests and updates the docs; has spec-reviewer check it; then ticks it off | Every task in [SPRINT_PLAN.md](SPRINT_PLAN.md) | Yes | You or Claude |
| `/new-migration [description]` | The safe recipe for a database change: next version number, conventions, entities, schema doc, tests | Any time a table or column changes | Yes | You or Claude |
| `/new-endpoint [resource]` | The recipe for an API endpoint: contract → DTOs → service → mapper → controller → tests | Adding or changing anything under `/api/v1` | Yes | You or Claude |
| `/sync-docs` | Compares the code with API_CONTRACT and DATABASE_SCHEMA and fixes drift (asks you when behaviour differs) | After endpoint or schema work, and at the end of each feature | Yes (docs) | You or Claude |
| `/sync-readme [section]` | Compares the README (what users get, and when) with PRD, SPRINT_PLAN, DECISIONS, USER_FLOWS and the specs; asks you about anything that needs a decision, then fixes the drift | After you edit the README, or when a hook says the README is newer than the docs | Yes (docs, after you agree) | You or Claude |
| `/sprint-ready [n]` | Reports README items with no task, open questions and gaps that are due, untestable "Done when" items, missing doc sections, sealed-entry leaks and conflicts for a sprint. Verdict: Ready / Ready with warnings / Blocked | Before starting a sprint or feature | No | **You only** |
| `/record-decision [decision]` | Records a decision in DECISIONS.md (a new entry, or an update to the one it changes), closes the matching question or gap, and updates every doc that assumed otherwise. Asks before changing the README | Whenever you answer a Q-x or change your mind about something | Yes (docs) | You or Claude |
| `/parallel-check [P1-04 P1-06]` | Checks dependencies and file overlaps between features, reserves migration numbers, gives a merge order, and writes a kickoff note for each session | Before building two or more features at the same time | No | **You only** |
| `/review-with-me [scope]` | Tours the change file by file in reviewing order, then asks you 3–5 review questions (edge cases, design choices, spot-the-bug, what's missing) and grades each answer. Ends with a score, one strength and one thing to practise | After a task, before committing | No | **You only** |
| `/teach [topic]` | A 15-minute lesson: the idea, how it works underneath, where it shows up in memoirist, a 5–10 minute exercise, and feedback | When something in the code or specs confuses you | No | **You only** |
| `/log-learning [notes]` | Adds an entry to [LEARNING_LOG.md](LEARNING_LOG.md) from the session (or your notes) and updates the skills tracker (Seen → Tried → Can do) | End of a task, review or lesson; the other learning skills offer it | Yes (the log) | You or Claude |
| `ui-ux-pro-max` | Design intelligence: styles, palettes, fonts, UX checks (you installed it yourself) | Design work and UI reviews | Depends on the request | You or Claude |

## Subagents
A subagent starts fresh, without your conversation, so it gives a more independent opinion. Ask for it by name.

| Subagent | What it checks | How to ask | Changes files? |
|---|---|---|---|
| `spec-reviewer` | Your changes against the README, API_CONTRACT, DATABASE_SCHEMA, DECISIONS, USER_FLOWS, all four rule files (with a checklist for sealed-entry leaks) and the task's "Done when". Verdict: Pass / Pass with notes / Changes needed | "Use the spec-reviewer subagent to review my uncommitted changes for task 3.5" | No (a hook keeps it read-only) |

## Hooks
Hooks run automatically; you don't start them. They're configured in [.claude/settings.json](../.claude/settings.json).

| Hook | Runs when | What it does |
|---|---|---|
| Migration guard ([protect-migrations.mjs](../.claude/hooks/protect-migrations.mjs)) | Before Claude edits or writes a file | Blocks changes to a Flyway migration that's already committed; tells Claude to add a new one instead |
| Doc-sync reminder ([doc-sync-reminder.mjs](../.claude/hooks/doc-sync-reminder.mjs)) | When Claude is about to finish a turn | Reminds Claude when entities or migrations changed after DATABASE_SCHEMA; controllers, DTOs or mappers changed after API_CONTRACT; the README changed after the product docs (`/sync-readme`); a ticked task left CLAUDE.md's Current focus behind; or API code changed after the frontend types were generated. Each reminder at most once per session |
| Session summary ([session-context.mjs](../.claude/hooks/session-context.mjs)) | When a session starts, resumes, is cleared or is compacted | Tells Claude the next task, any open questions blocking its sprint, how many files are uncommitted, and whether the README is newer than the docs |
| Read-only guard ([readonly-bash.mjs](../.claude/hooks/readonly-bash.mjs)) | Before the spec-reviewer subagent runs a shell command | Allows only read commands (git status, diff, log and show; grep; ls; cat…), so the reviewer can't change anything |

## Rules (load automatically)
Path-scoped instructions in [.claude/rules/](../.claude/rules/). Claude reads each one when it touches matching files.

| Rule file | Loads for | Covers |
|---|---|---|
| `backend.md` | `backend/**` | Structure, Java style, persistence, time, Flyway, testing |
| `api.md` | Backend Java and API_CONTRACT.md | Paths, status codes, errors, pagination, uploads, OpenAPI, private input |
| `security.md` | Backend and frontend source | Ownership (deleted entries count as missing), the dev header, Loot Drop redaction (counts and filters too), content safety, secrets, emails and metrics without content |
| `frontend.md` | `frontend/**` | Structure, data fetching, time, editor, capture, styling, tests |
| `docs.md` | `docs/**`, `README.md` | Which doc wins, changing every mention together, IDs, README edits only with approval, house style |
| `claude-config.md` | `.claude/**`, `CLAUDE.md`, `.mcp.json` | Keeping CLAUDE.md short, where instructions belong, writing and testing hooks, keeping this file current |

## What Claude reads in every new session

| What | Where | When it loads |
|---|---|---|
| Project instructions | [CLAUDE.md](../CLAUDE.md) | Every session. Keep it under 200 lines |
| Your private notes (optional) | `CLAUDE.local.md` (gitignored) | Every session, after CLAUDE.md |
| Claude's auto memory | `~/.claude/projects/<project>/memory/` | Every session (the first 200 lines). Claude adds to it from your corrections |
| Session summary | The SessionStart hook above | At start, resume, `/clear` and after compaction |
| Skill and subagent descriptions | `.claude/skills/`, `.claude/agents/` | The descriptions always; the full instructions only when one is used |
| Rules | `.claude/rules/` | Only when Claude touches matching files |

`/context` shows what's loaded and how much room it takes. `/memory` opens CLAUDE.md, `CLAUDE.local.md` and the auto memory.

## Connections (MCP)

| Name | What it gives Claude | Where it's set up |
|---|---|---|
| context7 | Current documentation for libraries (Spring Boot 4, TipTap 3, Tailwind 4, …) | [.mcp.json](../.mcp.json); may ask you to approve or sign in the first time |

## Reusable prompts
Paste these into a new session. They live in [prompts/](prompts/).

| Prompt | What it does | Start in |
|---|---|---|
| [split-into-features.md](prompts/split-into-features.md) | Checks the phases are in a buildable order, proposes features (`Pn-xx`), then writes the feature index and Phase 1 briefs after you approve | Plan mode |

## Useful built-in commands
These come with Claude Code.

| Command | Use it for |
|---|---|
| `/clear` | Start fresh between tasks |
| `/context` | See which instruction files are loaded and how much context they use |
| `/memory` | Open CLAUDE.md, `CLAUDE.local.md` and Claude's auto memory |
| `/code-review` | A bug-focused review of your changes |
| `/security-review` | A security review before Phase 2 and Phase 3 |
| `/simplify` | Tidy up changed code (quality only, not bugs) |
| `/fewer-permission-prompts` | After a week of real work, allow the safe commands Claude keeps asking about |
| `/doctor prompt-audit` | Find contradictions across CLAUDE.md, rules and skills |
| `/output-style [name]` | Switch how much Claude teaches: `learning`, `explanatory`, `default`… (values explained in [LEARNING.md](LEARNING.md)) |

---

## Example prompts
One ready-to-copy example for each tool. Swap in your own task, feature or question.

### Skills
| Tool | Example |
|---|---|
| `/implement-task` | `/implement-task 3.5` |
| | `/implement-task` *(no ID: picks the next unticked task)* |
| `/new-migration` | `/new-migration add_location_to_entries — an optional place name on each entry, up to 150 characters` |
| `/new-endpoint` | `/new-endpoint eras CRUD — every row of API_CONTRACT §Eras` |
| `/sync-docs` | `/sync-docs` |
| | `Run /sync-docs for the Tags work only and show me the drift table before changing anything.` |
| `/sprint-ready` | `/sprint-ready 3` |
| `/sync-readme` | `/sync-readme` *(after you edit the README)* |
| `/record-decision` | `/record-decision "Q14: show the Help me think button after an 8-second pause"` |
| | `/record-decision "Use Lucide icons only; no second icon library"` |
| `/parallel-check` | `/parallel-check P1-04 P1-06` |
| `/review-with-me` | `/review-with-me` *(the uncommitted changes)* |
| | `/review-with-me 3.4` |
| `/teach` | `/teach JPA lazy loading and why it fails outside a transaction` |
| | `/teach` *(no topic: suggests three for the next sprint)* |
| `/log-learning` | `/log-learning` *(after a review or lesson)* |
| | `/log-learning finally understood why the service, not the controller, checks ownership` |
| `ui-ux-pro-max` | `Use ui-ux-pro-max to propose 3 clearly different design directions for a private journaling PWA: one scrapbook, one editorial, one dark "time capsule". Give a palette, font pairing and card style for each.` |

### Subagent
| Tool | Example |
|---|---|
| `spec-reviewer` | `Use the spec-reviewer subagent to review my uncommitted changes for task 3.5. Show me the findings before fixing anything.` |
| | `Use the spec-reviewer subagent to review everything on this branch since main against feature P1-04's brief.` |
| | `Use the spec-reviewer subagent, focusing only on Loot Drop redaction across the feed, week, timeline and single-entry responses.` |

### Hooks
Hooks run by themselves, so there's nothing to type. To check one is working:
| Hook | Example |
|---|---|
| Migration guard | `Try to add a comment to V1__core_schema.sql and tell me what the hook says.` *(after V1 is committed; it should be blocked)* |
| Doc-sync reminder | Nothing to do: if you change a controller and finish without touching the docs, Claude will be reminded to run `/sync-docs` |
| Session summary | Start a new session and ask `What's next?` |
| Read-only guard | `Ask the spec-reviewer subagent to delete a scratch file and tell me what the hook said.` *(it should be blocked)* |

### Connections
| Tool | Example |
|---|---|
| context7 | `Use context7 to check how TipTap 3 configures the Link extension to allow only http, https and mailto.` |
| | `Use context7 to confirm the Spring Boot 4 way to map a JSONB column with Hibernate 7.` |

### Reusable prompts
| Prompt | Example |
|---|---|
| [split-into-features.md](prompts/split-into-features.md) | In a new session in Plan mode, paste everything below its line |

### Built-in commands
| Command | Example |
|---|---|
| `/clear` | `/clear` *(between tasks)* |
| `/code-review` | `/code-review` |
| `/security-review` | `/security-review` *(before deploying Phase 2)* |
| `/simplify` | `/simplify` *(after a task works, to tidy the changed code)* |
| `/fewer-permission-prompts` | `/fewer-permission-prompts` |
| `/doctor prompt-audit` | `/doctor prompt-audit` |

---

## Planned, not built yet

| Name | Type | What it would do |
|---|---|---|
| `/start-feature [id]` | Skill | Chain the pre-implementation steps: readiness, decisions, library checks, specs, spec review, commit |
| `/close-feature [id]` | Skill | Run the demo, `/sync-docs` and `spec-reviewer`, mark the feature Done, propose the commit |
| `phase-retro.md` | Prompt | End-of-phase review: what to add to CLAUDE.md or the skills; tag `phase-n-done` |
| Auto-format | Hook | Run Spotless (Java) and Prettier (frontend) after each edit, once the code exists |
| `security-reviewer` | Subagent | Ownership, redaction, tokens and secrets review before Phases 2 and 3 |
