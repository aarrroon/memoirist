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
| Split the roadmap into features | [prompts/split-into-features.md](prompts/split-into-features.md) |
| Look up current library docs | context7 |

---

## Project skills
Type `/name` to run one. Skills marked **You only** run only when you type them; the others Claude can also start on its own when your request matches.

| Skill | What it does | When to use it | Changes files? | Who starts it |
|---|---|---|---|---|
| `/implement-task [id]` | Plans one sprint task, waits for your OK, then builds it, tests it, updates the docs and ticks it off | Every task in [SPRINT_PLAN.md](SPRINT_PLAN.md) | Yes | You or Claude |
| `/new-migration [description]` | The safe recipe for a database change: next version number, conventions, entities, schema doc, tests | Any time a table or column changes | Yes | You or Claude |
| `/new-endpoint [resource]` | The recipe for an API endpoint: contract → DTOs → service → mapper → controller → tests | Adding or changing anything under `/api/v1` | Yes | You or Claude |
| `/sync-docs` | Compares the code with API_CONTRACT and DATABASE_SCHEMA and fixes drift (asks you when behaviour differs) | After endpoint or schema work, and at the end of each feature | Yes (docs) | You or Claude |
| `/sprint-ready [n]` | Reports blocking questions, untestable "Done when" items, missing doc sections and conflicts for a sprint. Verdict: Ready / Ready with warnings / Blocked | Before starting a sprint or feature | No | **You only** |
| `/record-decision [decision]` | Adds a D-entry to DECISIONS.md, closes the matching question, and updates every doc that assumed otherwise | Whenever you answer a Q-x or change your mind about something | Yes (docs) | You or Claude |
| `/parallel-check [P1-04 P1-06]` | Checks dependencies and file overlaps between features, reserves migration numbers, gives a merge order, and writes a kickoff note for each session | Before building two or more features at the same time | No | **You only** |
| `ui-ux-pro-max` | Design intelligence: styles, palettes, fonts, UX checks (you installed it yourself) | Design work and UI reviews | Depends on the request | You or Claude |

## Subagents
A subagent starts fresh, without your conversation, so it gives a more independent opinion. Ask for it by name.

| Subagent | What it checks | How to ask | Changes files? |
|---|---|---|---|
| `spec-reviewer` | Your changes against API_CONTRACT, DATABASE_SCHEMA, DECISIONS, the security rules, the time rules, and the task's "Done when". Verdict: Pass / Pass with notes / Changes needed | "Use the spec-reviewer subagent to review my uncommitted changes for task 3.5" | No |

## Hooks
Hooks run automatically; you don't start them. They're configured in [.claude/settings.json](../.claude/settings.json).

| Hook | Runs when | What it does |
|---|---|---|
| Migration guard ([protect-migrations.mjs](../.claude/hooks/protect-migrations.mjs)) | Before Claude edits or writes a file | Blocks changes to a Flyway migration that's already committed; tells Claude to add a new one instead |
| Doc-sync reminder ([doc-sync-reminder.mjs](../.claude/hooks/doc-sync-reminder.mjs)) | When Claude is about to finish a turn | If controllers, DTOs, entities or migrations changed but the spec docs didn't, asks Claude to run `/sync-docs` or explain why not. At most once per turn |

## Rules (load automatically)
Path-scoped instructions in [.claude/rules/](../.claude/rules/). Claude reads each one when it touches matching files.

| Rule file | Loads for | Covers |
|---|---|---|
| `backend.md` | `backend/**` | Structure, Java style, persistence, time, Flyway, testing |
| `api.md` | Backend Java and API_CONTRACT.md | Paths, status codes, errors, pagination, uploads, OpenAPI |
| `security.md` | Backend and frontend source | Ownership, the dev header, Loot Drop redaction, content safety, secrets |
| `frontend.md` | `frontend/**` | Structure, data fetching, time, editor, capture, styling, tests |

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
| `/code-review` | A bug-focused review of your changes |
| `/security-review` | A security review before Phase 2 and Phase 5 |
| `/simplify` | Tidy up changed code (quality only, not bugs) |
| `/fewer-permission-prompts` | After a week of real work, allow the safe commands Claude keeps asking about |
| `/doctor prompt-audit` | Find contradictions across CLAUDE.md, rules and skills |

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
| `/record-decision` | `/record-decision "Q1: new weeks do NOT join Eras automatically; users assign them by hand"` |
| | `/record-decision "Use Lucide icons only; no second icon library"` |
| `/parallel-check` | `/parallel-check P1-04 P1-06` |
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
| `security-reviewer` | Subagent | Ownership, redaction, tokens and secrets review before Phases 2 and 5 |
