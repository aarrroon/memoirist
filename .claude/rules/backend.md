---
paths:
  - "backend/**"
---

# Backend rules (Spring Boot 4, Java 25)

## Structure
- Organise by feature under `com.memoirist`: `common`, `user`, `era`, `template`, `tag`, `music`, `week`, `entry`, `media`, `storage`, and `dev` (sample content for the `local` profile only).
- Each feature has `XController` (HTTP only), `XService` (transactions, ownership, rules), `XRepository`, entity `X`, `XMapper` and `dto/` records.
- Controllers stay thin: no repository calls and no business rules.
- Services are `@Transactional`; read paths use `@Transactional(readOnly = true)`.

## Java style
- DTOs are records. Request records carry Jakarta validation annotations.
- Boolean DTO components named `isX` get `@JsonProperty("isX")`, so JSON shows `isLocked`/`isClosed`, not `locked`/`closed`. Assert the JSON in a test.
- No Lombok and no MapStruct. Write mappers by hand.
- `Optional` only as a return type. Never return `null` collections.
- Configuration goes in `@ConfigurationProperties` records (`StorageProperties`, …), not scattered `@Value`.

## Persistence
- Entities extend a base class with `@CreationTimestamp`/`@UpdateTimestamp` `Instant createdAt`/`updatedAt`.
- Entities implement `Persistable<UUID>`. Use a `@Transient boolean isNew = true` that is cleared in `@PostLoad`/`@PostPersist`. IDs are set in the constructor: either the client's ID or `UUID.randomUUID()`.
- JSONB fields use `@JdbcTypeCode(SqlTypes.JSON)`.
- Every association is `LAZY`.
  - Never fetch-join two collections in one query (`MultipleBagFetchException`).
  - `Entry.media` uses `@OrderBy("displayOrder ASC") @BatchSize(size = 50)`.
  - For pages, load the IDs first, then the children with `IN (…)` queries.
- Repositories expose user-scoped finders (`findByIdAndUserId`, `findByIdAndEntryUserId`). Services never use a bare `findById` on user data.
- Find-or-create a week with a native `INSERT … ON CONFLICT (user_id, start_date) DO NOTHING`, then select it.
- A week's Eras come from dates, not a table (D-22): load the user's Eras once and match each week by overlap. An Era's counts include only entries whose memory date is inside its dates.
- Entries in Recently deleted (`deleted_at` set, D-30) are left out of every entry query by default. Only the Recently deleted list, restore, erase and the daily sweep read them.
- Derived columns (`content_text`, `preview_line`) are computed by the server on every content write, never sent by the client (D-05, D-24).

## Time
- `Instant` ↔ `TIMESTAMPTZ`, `LocalDate` ↔ `DATE`, `ZoneId.of(user.getTimezone())` for the user's zone. Never use `LocalDateTime`, `Date` or `Calendar`.
- Inject `java.time.Clock` and use `Instant.now(clock)` / `LocalDate.now(clock.withZone(zone))`. Tests use a fixed clock.
- "Today", the memory-date check and week boundaries are computed in the **user's** zone, never the JVM's (which is UTC).

## Flyway
- Use `/new-migration`. Name files `V{n}__snake_case.sql` in `src/main/resources/db/migration/`. One logical change per file.
- A committed migration is read-only. The PreToolUse hook blocks Edit/Write; don't get around it with shell edits. Write a new migration to fix a mistake.
- The dev seed is `db/seed-local/R__seed_dev_user.sql`, loaded only by the `local` and `test` profiles.
- After any schema change, update the entities, `docs/DATABASE_SCHEMA.md` and the tests in the same change.

## Testing
- Anything touching the database or storage gets a `@SpringBootTest` with Testcontainers:
  - Postgres image `pgvector/pgvector:pg17`, wired with `@ServiceConnection`.
  - SeaweedFS as a `GenericContainer`.
  - Share containers across test classes through one base class or config.
- Pure logic (`WeekCalendar`, `EntryContentValidator`, `ContentTextExtractor`, `EntryMapper`) gets plain unit tests with a fixed `Clock`.
- Every endpoint is tested for: the happy path, `400` validation, `404` for another user's resource, and `409 entry-sealed` wherever sealing applies.
- Run `./gradlew test` before saying a task is done. Docker must be running.

## Spring Boot 4 notes
- Boot 4 uses Jackson 3, Hibernate 7 and Spring Framework 7. Package names and APIs differ from Boot 3 examples, so check current docs before copying a snippet.
- Use the springdoc-openapi line that supports Boot 4.
