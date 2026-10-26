# Prompt: split the phases into features

Paste everything below the line into a **new Claude Code session** opened in this repo. Start it in **Plan mode** so nothing is written until you approve.

---

I want to break memoirist's roadmap into **features**: self-contained pieces I can each build in their own Claude Code session (or a short series of them). Don't write any application code. This is planning only.

## Read first
- `CLAUDE.md`
- `docs/PRD.md` (roadmap §5 and glossary §3)
- `docs/SPRINT_PLAN.md` (the existing task list)
- `docs/DECISIONS.md` (decisions win over every other doc; note the open questions)
- `docs/USER_FLOWS.md` (flow IDs `F-xx`)
- Skim `docs/API_CONTRACT.md` and `docs/DATABASE_SCHEMA.md` for which sections belong to which phase.

## What a feature is
- Something a user (or I, as the developer) can **see working at the end**, such as "Loot Drops", "Tags", "Feed", or "Local infrastructure".
- Small enough for **1–4 sprint tasks**. Split anything bigger.
- It belongs to exactly one phase, and lists the features it depends on.
- Every existing sprint task belongs to exactly one feature. Don't invent work that isn't in the docs. If you think something is missing, list it as a gap instead of adding it.

## Verify the build order first
Before proposing features, check that the phases (and the sprints inside them) can each be built on top of the ones before. Work it out from the docs; don't assume the current order is right.

1. **List what each phase needs and provides.** For every phase, list what it *needs* (tables, columns, endpoints, services such as `EntryMapper` or `AiContentSource`, settings, accounts, decisions) and what it *provides* for later phases.
2. **Check there are no forward references.** Nothing may need something that only a later phase provides. Look especially at:
   - schema: every table or column a migration references exists in an earlier or the same migration
   - API: every endpoint a phase's UI calls is built in the same or an earlier phase
   - shared code: redaction (`EntryMapper`), seal rules, ownership checks, consent and `FeatureGate`, `AiContentSource`
   - accounts: anything involving more than one person (sharing, groups) comes after real auth (Phase 2)
   - decisions: every open question (Q-x) is due before the first task that depends on it
3. **Check the stated flexibility.** The docs say Phases 4 and 5 can swap, Phase 5b needs Phase 3, and Phase 6 needs Phase 3. Confirm each claim, or say what breaks.
4. **Check foundations come early.** Look for anything a later phase would force us to *change* in an earlier phase (a breaking API change, or moving data between tables). D-22 and D-26 are examples where this was caught early. Flag any more.
5. **Report**, before the feature table:
   - a Mermaid `flowchart` of phase-level dependencies (and sprint-level where it matters)
   - a table: `Problem | Where | Why it breaks the order | Suggested fix`
   - a verdict: **Order works**, **Works with fixes**, or **Needs reordering**

Don't change any doc to fix the order without my approval. If I approve a change, record it with `/record-decision`.

## What to produce
1. **A proposal first, as a table in chat**, for every phase (1–6), then stop and wait for my approval:

   | ID | Feature | Phase | Sprint tasks | User flows | Depends on | Open questions that block it |
   |---|---|---|---|---|---|---|

   Use IDs `P{phase}-{nn}`, for example `P1-03`. Don't use `F-` (that's for user flows) or `D-`/`Q-`.
   Order features within a phase so dependencies come first, and mark which ones could run in parallel.
   Every **Depends on** entry must point to a feature in the same or an earlier phase. Check this row by row and say so explicitly.

2. **After I approve**, create:
   - `docs/features/README.md`: the feature index (the approved table plus a **Status** column: `Planned`, `Ready`, `In progress`, `Done`), the dependency diagram, and a short "how to use these files" note.
   - One brief per feature for **Phase 1 only**: `docs/features/phase-1/P1-nn-short-name.md`, using the template below. Later phases stay as rows in the index; their briefs get written when that phase is next, because their open questions aren't settled yet.
   - In `docs/SPRINT_PLAN.md`, add the feature ID to each task's heading (for example `**3.5 Entries … (P1-04)**`). Keep task IDs and ticks unchanged.
   - In `CLAUDE.md`, add one row to the doc map: "Starting a feature → its brief in `docs/features/`".

## Feature brief template
```markdown
# P1-nn Feature name
**Status:** Planned · **Phase:** 1 · **Depends on:** P1-xx · **Sprint tasks:** 3.5, 3.6

## Goal
One or two sentences: what works at the end, from the user's point of view.

## In scope / out of scope
- In: …
- Out (and which feature or phase owns it): …

## Specs this feature touches
Links to sections, never copies:
- Decisions: D-xx, D-yy
- Flows: F-xx
- API: API_CONTRACT.md §Section
- Schema: DATABASE_SCHEMA.md §table
- Rules: .claude/rules/…

## Open questions
Q-xx (needed before task n.n), or "None".

## Done when
The feature-level check, in addition to each task's own "Done when". For example, a Playwright flow or a manual demo script.

## Changelog
- 2026-10-27 Brief created.
```

## Rules
- **Link, never copy.** A brief points to sections of the existing docs. It doesn't repeat schemas, endpoints or decisions, so there's only ever one place to update.
- Ask me before resolving any conflict between docs or any open question. Use `/record-decision` when I answer.
- Finish with a short list of gaps or inconsistencies you noticed, with a suggested fix for each.
