---
name: parallel-check
description: Check whether two or more memoirist features can be built in parallel (separate sessions and branches) — dependencies, files and doc sections each will touch, overlaps, migration numbers — and write the kickoff note for each session. Read-only.
argument-hint: "[feature IDs, e.g. P1-04 P1-06]"
disable-model-invocation: true
allowed-tools: Read, Grep, Glob
---

# Can these features run in parallel?

Features: **$ARGUMENTS** (two or more feature IDs). If fewer than two are given, ask which ones.

This is **read-only**. Don't edit any file.

## 1. Load each feature
- Read each brief in `docs/features/` (and its row in `docs/features/README.md`), plus the sprint tasks, flows, decisions, API sections and schema sections it links to.
- If a brief doesn't exist yet, use its row in the feature index and its sprint tasks in `docs/SPRINT_PLAN.md`, and say the check is less certain.

## 2. Dependencies
- If one feature is in the other's "Depends on", directly or through other features, the answer is **No**: build them in order.
- If either feature has an open question (Q-x) blocking it, say so.

## 3. Predict what each feature touches
For each feature, list what it will **create** and **change**:
- code: packages and key classes (backend), feature folders and shared components (frontend)
- migrations: will it need one?
- doc sections: API_CONTRACT, DATABASE_SCHEMA, USER_FLOWS, DECISIONS, TECH_STACK, `.claude/rules/`
- generated or shared files: `frontend/src/api/schema.d.ts`, `package.json`, `build.gradle.kts`, `docker-compose.yml`, `.env.example`

If code already exists, check it with Grep and Glob rather than guessing.

## 4. Find overlaps
Report every overlap as a table: `Area | Feature A | Feature B | Risk (blocker / merge conflict / none) | How to handle`.

Always check these known hotspots:

| Hotspot | Rule |
|---|---|
| Migration numbers | Each feature that needs a migration gets its own reserved number (step 5) |
| `EntryMapper`, `EntryService`, `CurrentUserProvider`, the error handler | Shared core: two features both changing one of these is a **blocker** |
| `package.json`, `build.gradle.kts` | Both adding dependencies causes conflicts; one adds first, or both accept a lockfile merge |
| `schema.d.ts` | Never edited in feature branches; regenerated on `main` after merging |
| CLAUDE.md "Current focus", SPRINT_PLAN ticks of other features, feature index status | Not edited in feature branches; updated on `main` after merging |
| Same section of API_CONTRACT or DATABASE_SCHEMA | Merge conflict; different sections of the same file are usually fine |

## 5. Reserve migration numbers
- Find the highest existing migration: `backend/src/main/resources/db/migration/V*.sql`. If there are none yet, use the migration history table in `docs/DATABASE_SCHEMA.md`.
- Give each feature that needs a migration the next free number, in the order the features are listed. A feature with no schema change gets none.

## 6. Verdict
One of:
- **Safe in parallel**: no blockers; only routine merge conflicts.
- **Safe with care**: list the conditions (for example, "only P1-04 adds npm dependencies").
- **Not safe**: name the blocker and the order to build them in instead.

Also give the **merge order** (usually the one with more shared-core changes merges first).

## 7. Kickoff notes (only if safe)
For each feature, output a ready-to-paste note in a code block. Fill in the real IDs, the reserved migration number (or "no migrations"), and the overlap areas from step 4:

```text
You're building <THIS-ID> on its own branch. <OTHER-IDS> <is/are> being built in parallel in another session.
Stay inside <THIS-ID>'s scope (docs/features/…/<THIS-ID>-….md).
Use migration <Vn> only. / This feature needs no migrations; don't add one.
Don't edit: CLAUDE.md's Current focus, SPRINT_PLAN ticks or feature-index status for other features, frontend/src/api/schema.d.ts.
Also stay out of: <overlap areas owned by the other feature, from step 4>.
If you need to change a file outside your feature's area, stop and ask me.
```

End with the steps: start one worktree session per feature, paste its note, then merge in the stated order and sync the second branch with `main` before merging it.
