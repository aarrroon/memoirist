---
name: sync-docs
description: Check that docs/API_CONTRACT.md and docs/DATABASE_SCHEMA.md still match the memoirist code, and fix drift. Use after changing endpoints, DTOs, migrations or entities, at the end of a sprint, or when asked whether the docs are up to date.
---

# Sync docs with code

Code is the source of truth for **what exists**. The docs are the source of truth for **intended behaviour**.

1. **Schema.** Read the migrations in `backend/src/main/resources/db/migration/` in version order and build the resulting tables. Compare them with `docs/DATABASE_SCHEMA.md`: tables, columns, types, nullability, defaults, constraints, indexes and the migration-history table.

2. **API.**
   - If the backend is running, fetch `http://localhost:8080/v3/api-docs` with `curl -s`.
   - Otherwise read the controllers and the DTO records.
   - Compare with `docs/API_CONTRACT.md`: paths, methods, status codes, error types, field names and types, nullability and limits.

3. **Decisions.** Flag code that contradicts a D-xx entry in `docs/DECISIONS.md`, and choices in code that no decision records.

4. **Report** a table before changing anything:

   | Area | Doc says | Code does | Proposed fix |
   |---|---|---|---|

5. **Fix.**
   - Where the doc is merely out of date (the code is the intended behaviour), update the doc.
   - Where the code's *behaviour* differs from the documented intent, ask the user which is right before changing code or docs.
   - Keep doc edits minimal and in the existing style.

6. **Finish.** Say "no drift" or list what was fixed and what still needs a decision.
