---
name: new-migration
description: Add a Flyway migration to the memoirist backend — new table, column, index or constraint. Use whenever the database schema must change; committed migrations are never edited.
argument-hint: "[short description, e.g. add_entry_location]"
---

# Add a Flyway migration

Change: **$ARGUMENTS**

1. **Pick the version.**
   - List `backend/src/main/resources/db/migration/` and take the highest `V{n}` plus 1.
   - Name the file `V{n}__{snake_case_description}.sql`. No phase numbers in the name.

2. **Never edit a committed migration.**
   - A PreToolUse hook blocks edits to committed `V*.sql` files. Don't get around it with shell commands.
   - If an existing migration is wrong, write a new one that corrects it.
   - A migration that is *not yet committed* may still be edited. If it already ran against your local database, tell the user: resetting with `docker compose down -v` deletes **all** local data, so ask first.

3. **Write the SQL** (conventions are in `docs/DATABASE_SCHEMA.md`):
   - Primary keys are `UUID PRIMARY KEY DEFAULT gen_random_uuid()`. Every foreign key states `ON DELETE`. Index foreign-key columns used in lookups.
   - Use `TIMESTAMPTZ` for instants (`created_at`/`updated_at NOT NULL DEFAULT NOW()`) and `DATE` for calendar days.
   - Use `VARCHAR`, never `CHAR` (Hibernate validation fails on `bpchar`). Use `JSONB`, never `JSON`.
   - Name constraints `uq_<table>_<cols>`, `chk_<table>_<rule>` and `idx_<table>_<cols>`.
   - To add a `NOT NULL` column to a table that has rows: add it nullable, backfill, then `SET NOT NULL`, all in the same file.
   - One logical change per file. Only objects the current phase needs.

4. **Match the code.** Update the JPA entities and mappings; `ddl-auto=validate` fails startup on any mismatch. New JSONB fields need `@JdbcTypeCode(SqlTypes.JSON)`.

5. **Update the docs.** In `docs/DATABASE_SCHEMA.md`, update the table spec and add a row to the migration history.

6. **Verify.** Run `cd backend && ./gradlew test`. Testcontainers applies every migration to a fresh database, and validation must pass. Quote the test summary.
