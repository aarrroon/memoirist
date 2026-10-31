# Database Schema: memoirist

> PostgreSQL 17. Schema changes happen **only** through Flyway migrations in `backend/src/main/resources/db/migration/`, with `spring.jpa.hibernate.ddl-auto=validate`. Never edit a committed migration; add a new one (D-16). Once a migration exists in the codebase, it is the source of truth and this document must be kept in sync with it.

## Conventions
- **Primary keys:** `UUID PRIMARY KEY DEFAULT gen_random_uuid()` (built into Postgres 13+, so no extension is needed). Clients may supply the ID on create (D-12).
- **Instants:** `TIMESTAMPTZ`, stored in UTC. `created_at` and `updated_at` are `NOT NULL DEFAULT NOW()`. `updated_at` is maintained by Hibernate (`@UpdateTimestamp`).
- **Calendar dates:** `DATE` (memory dates, week and era boundaries), interpreted in the user's timezone.
- **Text:** use `VARCHAR`, never `CHAR`, because Hibernate's validator rejects `bpchar`. Documents use `JSONB`, not `JSON`.
- **Naming:** tables are plural snake_case. Constraints are named `uq_<table>_<cols>`, `chk_<table>_<rule>` and `idx_<table>_<cols>`. Two V1 constraints are the exception: `users.email` and `media.object_key` are declared `UNIQUE` inline, so Postgres named them `users_email_key` and `media_object_key_key`. Foreign keys keep Postgres's default names too (`<table>_<column>_fkey`). V1 is committed, so renaming them takes a new migration; name every constraint in new migrations. Code that maps a database error to a response by constraint name uses the real names (`SchemaConstraintsTest` lists them).
- **Foreign keys:** every foreign key states its `ON DELETE` behaviour.
- **No URLs:** the database never stores a URL or storage host, only object keys.
- **Ownership:** every user-owned table has `user_id` directly, except tables that hang off another owned table: `media` is owned through `entries`, `media_transcriptions` through `media`, and `entry_tags` through `entries`.

## Migration history

| Version | File | Phase | Status |
|---|---|---|---|
| V1 | `V1__core_schema.sql` | 1 | Applied (Sprint 2, task 2.3); a copy of the SQL below |
| R | `db/seed-local/R__seed_dev_user.sql` | 1 | Applied (Sprint 2, task 2.3); `local` and `test` profiles only |
| next | `V{n}__auth_and_sharing.sql` | 2 | Draft below |
| next | `V{n}__yearly_letter.sql` | 2 | Draft below (D-32) |
| next | `V{n}__product_events.sql` | 2 | Draft below (D-38) |
| next | `V{n}__groups.sql` | 3 | Draft below |
| next | `V{n}__ai_biographer.sql` | 4 | Draft below; dimension decided at Phase 4 |
| next | `V{n}__user_settings.sql` | 4 (7a) | Draft below |
| next | `V{n}__ai_suggestions.sql` | 4 (7a) | Draft below |
| next | `V{n}__era_summaries.sql` | 4 (7b) | Draft below |
| next | `V{n}__friend_profiles.sql` | 4 (7b) | Draft below |
| next | `V{n}__entry_search.sql` | 4 (7b) | Draft below |
| next | `V{n}__group_story.sql` | 4 (7b) | Draft below; needs the groups and AI tables |
| next | `V{n}__page_transcription.sql` | 4 (7c) | Draft below; the provider is decided in Q12 |
| next | `V{n}__entry_embeddings.sql` | 6 | Draft below; dimension decided at Phase 4 |

---

## Phase 1 tables

### `users`
| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| email | VARCHAR(255) | NOT NULL UNIQUE |
| display_name | VARCHAR(100) | NOT NULL DEFAULT 'Time Traveler' |
| timezone | VARCHAR(64) | NOT NULL DEFAULT 'Australia/Melbourne'. IANA region name, checked by `@IanaTimeZone` against `ZoneId.getAvailableZoneIds()` (offsets such as `+10:00` are refused; the longest name is 32 characters) |
| birthday | DATE | NULL. Optional; not after today (service check). Powers the *next birthday* unlock suggestion (D-33) and is the yearly letter's default day (D-32) |
| created_at, updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

### `eras`
A user-named chapter of life. It holds every week its dates overlap and shows the entries inside its dates; nothing links them in the database (D-22).

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| title | VARCHAR(150) | NOT NULL |
| description | TEXT | NULL |
| start_date | DATE | NOT NULL |
| end_date | DATE | NULL means ongoing. CHECK `end_date IS NULL OR end_date >= start_date` |
| soundtrack | VARCHAR(100) | NULL. A Spotify URI; the format is checked in the service (D-35) |
| cover_media_id | UUID | NULL → media ON DELETE SET NULL. One of the user's own photos (service check); hidden while its entry is sealed or in Recently deleted (D-36) |
| pinned_in_feed | BOOLEAN | NOT NULL DEFAULT FALSE. A pinned Era wins in the feed for weeks it shares with other Eras (D-22) |
| created_at, updated_at | TIMESTAMPTZ | |

### `templates`
| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| name | VARCHAR(100) | NOT NULL |
| subheadings | JSONB | NOT NULL DEFAULT '[]'. Array of strings (up to 20 items, each up to 200 characters, enforced when the request is read, task 3.4: the limits are constants on `Template`). CHECK that it is an array |
| created_at, updated_at | TIMESTAMPTZ | |

### `weekly_logs`
Created automatically, one per user per Monday–Sunday week in the user's timezone (D-08). It belongs to every Era whose dates overlap it, worked out from the dates (D-22).

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| title | VARCHAR(200) | NULL |
| start_date | DATE | NOT NULL. Must be a Monday. UNIQUE `(user_id, start_date)` |
| end_date | DATE | NOT NULL. CHECK `end_date = start_date + 6` |
| is_closed | BOOLEAN | NOT NULL DEFAULT FALSE. Closed weeks stay editable and can be reopened |
| soundtrack | VARCHAR(100) | NULL. A Spotify URI (D-35) |
| created_at, updated_at | TIMESTAMPTZ | |

### `entries`
One Instagram-style post. `user_id` is duplicated from the week so ownership checks are a single-column lookup. The service keeps it equal to the week's owner.

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK (may be client-generated) |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| weekly_log_id | UUID | NOT NULL → weekly_logs ON DELETE CASCADE. The week containing `entry_date` |
| template_id | UUID | NULL → templates ON DELETE SET NULL |
| title | VARCHAR(150) | NULL. Optional title (D-24). If set, not blank |
| entry_date | DATE | NOT NULL. Memory date; not after today in the user's zone (service check) and not before 1900-01-01 (CHECK, D-09) |
| content_json | JSONB | NULL means no caption. TipTap doc; allow-listed and at most 200 KB (D-05) |
| content_text | TEXT | NOT NULL DEFAULT ''. Plain text derived from `content_json` by the server |
| preview_line | VARCHAR(120) | NULL. Derived by the server when content is saved: the first non-empty line that isn't a heading, or the first heading if there's nothing else (D-24) |
| soundtrack | VARCHAR(100) | NULL. A Spotify URI; hidden while sealed (D-35) |
| reply_to_entry_id | UUID | NULL → entries ON DELETE SET NULL. The user's own opened Loot Drop that this entry answers (service check, D-33) |
| unlock_date | TIMESTAMPTZ | NULL. Sealed while `unlock_date > now()` (D-10) |
| unlock_notified | BOOLEAN | NOT NULL DEFAULT FALSE. Set by the background unlock job (unlock emails in Phase 2, refreshing AI results in Phase 4) |
| unlock_seen_at | TIMESTAMPTZ | NULL. When the user saw the opened Loot Drop in the *Opened* strip; cleared whenever the entry is sealed (D-10) |
| deleted_at | TIMESTAMPTZ | NULL. Set when the entry is deleted: it waits in Recently deleted, hidden everywhere else, until it is restored or erased. A daily sweep erases it 30 days later (D-30) |
| date_unknown | BOOLEAN | NOT NULL DEFAULT FALSE. The memory date is a guess (a photo-dump photo with no date of its own); cleared when the date is changed (D-34) |
| version | BIGINT | NOT NULL DEFAULT 0. Optimistic lock, mapped with `@Version`; a stale `PUT` is `409 entry-stale` (D-23). Raised by one for each request that changes the row (an edit, a seal, a delete into Recently deleted); a `PUT` that changes nothing writes nothing |
| created_at, updated_at | TIMESTAMPTZ | |

### `media`
Carousel items (images and voice notes) belonging to an entry.

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| entry_id | UUID | NOT NULL → entries ON DELETE CASCADE. Read the object keys *before* the cascade removes the rows, and delete the stored files after the commit: `EntryErasure` does both (TECH_STACK §2 Storage) |
| object_key | VARCHAR(512) | NOT NULL UNIQUE. `users/{userId}/entries/{entryId}/{mediaId}.{ext}` |
| media_type | VARCHAR(100) | NOT NULL. One of the D-17 MIME types |
| purpose | VARCHAR(20) | NOT NULL DEFAULT 'PHOTO'. `PHOTO`, or `PAGE_SCAN` for a photo of a paper diary page, which must be an image. Checked in the service like `media_type`, so a new purpose needs no migration (D-20) |
| display_order | INTEGER | NOT NULL, ≥ 0. UNIQUE `(entry_id, display_order)` DEFERRABLE INITIALLY DEFERRED, so a reorder, a delete that closes a gap and an insert in the middle can pass through a moment of repeated positions inside one transaction. The service keeps an entry's values as `0, 1, 2, …` with no gap, under a lock on the entry's row (task 3.6) |
| file_size_bytes | BIGINT | NOT NULL, > 0, ≤ 25 MB (service check) |
| uploaded_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

At most 20 media items per entry (service check, D-17). The checks of an upload are in API_CONTRACT (Media).

### `tags`
The user's own categories for entries (D-26).

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| name | VARCHAR(50) | NOT NULL, not blank. Unique per user ignoring case (`uq_tags_user_name` on `lower(name)`) |
| created_at, updated_at | TIMESTAMPTZ | |

### `entry_tags`
Links entries to tags. Owned through `entries`; the service checks the tag belongs to the same user. At most 20 tags per entry (service check).

| Column | Type | Constraints and notes |
|---|---|---|
| entry_id | UUID | NOT NULL → entries ON DELETE CASCADE |
| tag_id | UUID | NOT NULL → tags ON DELETE CASCADE |
| source | VARCHAR(10) | NOT NULL DEFAULT 'USER'. `AI` is used from Phase 4, when the user accepts a tag suggestion |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

The primary key is `(entry_id, tag_id)`.

### V1 SQL: `V1__core_schema.sql`
Task 2.3 copied this block unchanged to `backend/src/main/resources/db/migration/V1__core_schema.sql`. From the first commit of that file, the file is the source of truth (D-16): if the two ever differ, fix this block. `SchemaTest` and `SchemaConstraintsTest` check the result against this document.

```sql
-- V1__core_schema.sql  (Phase 1)

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL DEFAULT 'Time Traveler',
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'Australia/Melbourne',
    birthday      DATE,  -- optional; the next-birthday unlock suggestion (D-33)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE eras (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title        VARCHAR(150) NOT NULL,
    description  TEXT,
    start_date   DATE NOT NULL,
    end_date     DATE,
    soundtrack   VARCHAR(100),  -- a Spotify URI (D-35); cover_media_id is added after media below
    pinned_in_feed BOOLEAN NOT NULL DEFAULT FALSE,  -- wins in the feed for shared weeks (D-22)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_eras_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE TABLE templates (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    subheadings  JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_templates_subheadings_array CHECK (jsonb_typeof(subheadings) = 'array')
);

CREATE TABLE weekly_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(200),
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    is_closed   BOOLEAN NOT NULL DEFAULT FALSE,
    soundtrack  VARCHAR(100),  -- a Spotify URI (D-35)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_weekly_logs_user_start UNIQUE (user_id, start_date),
    CONSTRAINT chk_weekly_logs_monday CHECK (EXTRACT(ISODOW FROM start_date) = 1),
    CONSTRAINT chk_weekly_logs_seven_days CHECK (end_date = start_date + 6)
);

-- No week–Era table: a week belongs to every Era whose dates overlap it (D-22).

CREATE TABLE entries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    weekly_log_id    UUID NOT NULL REFERENCES weekly_logs (id) ON DELETE CASCADE,
    template_id      UUID REFERENCES templates (id) ON DELETE SET NULL,
    title            VARCHAR(150),
    entry_date       DATE NOT NULL,
    content_json     JSONB,
    content_text     TEXT NOT NULL DEFAULT '',
    preview_line     VARCHAR(120),  -- derived on save: first non-heading line (D-24)
    soundtrack       VARCHAR(100),  -- a Spotify URI (D-35)
    reply_to_entry_id UUID REFERENCES entries (id) ON DELETE SET NULL,  -- the opened Loot Drop it answers (D-33)
    unlock_date      TIMESTAMPTZ,
    unlock_notified  BOOLEAN NOT NULL DEFAULT FALSE,
    unlock_seen_at   TIMESTAMPTZ,  -- the user saw it in the Opened strip; cleared on every seal (D-10)
    deleted_at       TIMESTAMPTZ,  -- in Recently deleted since then; erased 30 days later (D-30)
    date_unknown     BOOLEAN NOT NULL DEFAULT FALSE,  -- the date is a guess; cleared when it's changed (D-34)
    version          BIGINT NOT NULL DEFAULT 0,  -- optimistic lock (@Version); stale saves get 409 (D-23)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_entries_title CHECK (title IS NULL OR length(btrim(title)) > 0),
    CONSTRAINT chk_entries_entry_date_min CHECK (entry_date >= DATE '1900-01-01')
);

CREATE TABLE media (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id         UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    object_key       VARCHAR(512) NOT NULL UNIQUE,
    media_type       VARCHAR(100) NOT NULL,
    purpose          VARCHAR(20) NOT NULL DEFAULT 'PHOTO',  -- PHOTO or PAGE_SCAN, checked in the service (D-20)
    display_order    INTEGER NOT NULL,
    file_size_bytes  BIGINT NOT NULL,
    uploaded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_media_entry_order UNIQUE (entry_id, display_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_media_display_order CHECK (display_order >= 0),
    CONSTRAINT chk_media_file_size CHECK (file_size_bytes > 0)
);

-- An Era's cover is one of the user's own photos (D-36). It's added here because media comes after eras.
ALTER TABLE eras
    ADD COLUMN cover_media_id UUID REFERENCES media (id) ON DELETE SET NULL;

-- The user's own tags (D-26). Names are unique per user, ignoring case.
CREATE TABLE tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tags_name CHECK (length(btrim(name)) > 0)
);

CREATE UNIQUE INDEX uq_tags_user_name ON tags (user_id, lower(name));

-- Owned through entries and tags; the service keeps both with the same user.
CREATE TABLE entry_tags (
    entry_id    UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    source      VARCHAR(10) NOT NULL DEFAULT 'USER',  -- AI from Phase 4, when the user accepts a suggestion
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entry_id, tag_id),
    CONSTRAINT chk_entry_tags_source CHECK (source IN ('USER', 'AI'))
);

-- uq_weekly_logs_user_start already indexes (user_id, start_date);
-- uq_media_entry_order already indexes (entry_id, display_order).
CREATE INDEX idx_eras_user_start ON eras (user_id, start_date DESC);
CREATE INDEX idx_templates_user ON templates (user_id);
CREATE INDEX idx_entries_week_date ON entries (weekly_log_id, entry_date, created_at);
CREATE INDEX idx_entries_user_date ON entries (user_id, entry_date DESC);
CREATE INDEX idx_entries_template ON entries (template_id);
CREATE INDEX idx_entries_pending_unlock ON entries (unlock_date)
    WHERE unlock_date IS NOT NULL AND unlock_notified = FALSE;
-- Opened Loot Drops the user hasn't seen yet (D-10).
CREATE INDEX idx_entries_opened_unseen ON entries (user_id, unlock_date)
    WHERE unlock_date IS NOT NULL AND unlock_seen_at IS NULL;
-- Recently deleted: its list and the daily sweep (D-30).
CREATE INDEX idx_entries_deleted ON entries (user_id, deleted_at)
    WHERE deleted_at IS NOT NULL;
-- Replies to an opened Loot Drop (D-33).
CREATE INDEX idx_entries_reply_to ON entries (reply_to_entry_id)
    WHERE reply_to_entry_id IS NOT NULL;
CREATE INDEX idx_eras_cover ON eras (cover_media_id)
    WHERE cover_media_id IS NOT NULL;
-- The primary key of entry_tags already indexes (entry_id, tag_id).
CREATE INDEX idx_entry_tags_tag ON entry_tags (tag_id);
```

### Local and test seed: `db/seed-local/R__seed_dev_user.sql`
This is loaded only by the `local` and `test` profiles (D-11), so it never runs in production (`MigrationsTest` runs Flyway with the `prod` location alone and finds no users).

It is a repeatable migration (`R__`): Flyway runs it after the versioned migrations, and again whenever the file's checksum changes. `ON CONFLICT (id) DO NOTHING` makes a re-run safe, and it also means that editing the values here never changes the dev user a database already has. To change the dev user in an existing local database, update the row with SQL, or reset the data with `docker compose down -v` (which deletes everything).

```sql
INSERT INTO users (id, email, display_name, timezone)
VALUES ('00000000-0000-0000-0000-000000000001', 'dev@memoirist.local', 'Dev User', 'Australia/Melbourne')
ON CONFLICT (id) DO NOTHING;
```

### Entity classes (task 2.4)
Each Phase 1 table has one JPA entity, in the feature package named in [TECH_STACK.md §2](TECH_STACK.md#layout). `ddl-auto=validate` makes the application refuse to start if an entity maps a table or column that V1 doesn't have, and `EntityMappingTest` saves every column and reads it back.

| Table | Entity | Extends | Mapping notes |
|---|---|---|---|
| `users` | `User` | `TimestampedEntity` | |
| `eras` | `Era` | `TimestampedEntity` | `cover_media_id` is a plain `UUID` |
| `templates` | `Template` | `TimestampedEntity` | `subheadings` is a `List<String>` with `@JdbcTypeCode(SqlTypes.JSON)` |
| `weekly_logs` | `WeeklyLog` | `TimestampedEntity` | `is_closed` is the field `closed`; `start_date` and `end_date` are never updated; `@DynamicUpdate`, so an `UPDATE` writes only the columns that changed (the title and the closed flag are saved by separate requests and the row has no `@Version`) |
| `entries` | `Entry` | `TimestampedEntity` | `content_json` is a Jackson 3 `JsonNode` (`@JdbcTypeCode(SqlTypes.JSON)`); `version` is `@Version` (D-23); `@SQLRestriction("deleted_at IS NULL")` hides Recently deleted entries (D-30); `media` and `tags` below |
| `media` | `Media` | `BaseEntity` | No `created_at` or `updated_at` (D-12); `uploaded_at` is `@CreationTimestamp`; `entry` is a `LAZY` `@ManyToOne`; `purpose` and `media_type` are strings checked in the service |
| `tags` | `Tag` | `TimestampedEntity` | |
| `entry_tags` | no entity | | `Entry.tags` is a `@ManyToMany` through this table. Hibernate writes only `entry_id` and `tag_id`, so `source` (`'USER'`) and `created_at` come from their column defaults. Phase 4 needs an `EntryTag` entity to record `'AI'` |

- `user_id`, `weekly_log_id`, `template_id`, `reply_to_entry_id` and `cover_media_id` are plain `UUID` fields, not associations. The only associations are `Entry.media` (`@OneToMany`, `@OrderBy("displayOrder ASC")`, `@BatchSize(50)`, no cascade), `Media.entry` and `Entry.tags` (`@BatchSize(50)`).
- Deleting an entry (the sweep, or erase) deletes one `entries` row and leaves its `media` rows to the foreign key's `ON DELETE CASCADE` (a test shows Hibernate never touches `media`). A query that finds an entry's media rows through the entry, JPQL or a derived finder, also applies the entry's `@SQLRestriction`, so it finds none for an entry in Recently deleted; `EntryErasure` reads the keys with a native query for that reason (task 3.6). Its `entry_tags` rows go by cascade or by a Hibernate `delete from entry_tags` (not asserted by a test; the result is the same).
- A text column that the schema constrains in length (`VARCHAR(150)` for a title) is only checked by the database and the service; the entity records the length for readers but Hibernate doesn't enforce it.

---

## Phase 2 draft: auth and sharing
Review this at the start of Sprint 5, after open questions Q2–Q3 are answered.

```sql
-- V{n}__auth_and_sharing.sql  (Phase 2 — draft)

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN auth_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL';

CREATE TABLE shared_links (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    weekly_log_id  UUID REFERENCES weekly_logs (id) ON DELETE CASCADE,
    entry_id       UUID REFERENCES entries (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex; the raw token is shown to the user once, never stored
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_shared_links_one_target CHECK ((weekly_log_id IS NULL) <> (entry_id IS NULL))
);

CREATE INDEX idx_shared_links_user ON shared_links (user_id);
```

```sql
-- V{n}__yearly_letter.sql  (Phase 2, task 5.10 — draft)

-- The yearly letter's day as MM-DD; NULL turns it off. The service sets it to the birthday's
-- day when a birthday is first saved (D-32). The sent year stops a second email in one year.
ALTER TABLE users
    ADD COLUMN yearly_letter_on        VARCHAR(5),
    ADD COLUMN yearly_letter_sent_year INTEGER,
    ADD CONSTRAINT chk_users_yearly_letter_on
        CHECK (yearly_letter_on IS NULL OR yearly_letter_on ~ '^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$');
```

```sql
-- V{n}__product_events.sql  (Phase 2, task 5.11 — draft)

-- Counts only, never content (D-38). Deleting a user deletes their events.
CREATE TABLE product_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind         VARCHAR(40) NOT NULL,  -- SIGNED_UP, ENTRY_CREATED, LOOT_DROP_SEALED, WEEK_CLOSED, INVITE_ACCEPTED; checked in the service
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_events_kind_time ON product_events (kind, occurred_at);
CREATE INDEX idx_product_events_user ON product_events (user_id);
```

## Phase 3 draft: groups
Review this at the start of Sprint 6, after open questions Q8, Q11 and Q26 are answered (D-19). Groups need no AI; the shared story and each member's opt-in to it are a Phase 4 migration (D-31).

Group tables are owned through membership rather than a single `user_id`. In `group_entries`, `user_id` is the contributor, and the service keeps it equal to the entry's owner. Members are addressed by `group_members.id`, never by user ID, so no request carries another user's ID.

```sql
-- V{n}__groups.sql  (Phase 3 — draft)

CREATE TABLE groups (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(150) NOT NULL,
    description  TEXT,
    start_date   DATE,
    end_date     DATE,
    -- Group Loot Drop: contributions are sealed until at least this instant.
    -- It can only move later (service rule, as for entries).
    unlock_date  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_groups_dates CHECK (end_date IS NULL OR (start_date IS NOT NULL AND end_date >= start_date))
);

CREATE TABLE group_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_group_members_group_user UNIQUE (group_id, user_id),
    CONSTRAINT chk_group_members_role CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE TABLE group_invites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex; the raw token is shown once, never stored
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- A contribution is a link, never a copy. The composite foreign key deletes a member's
-- contributions when their membership ends; the entries themselves are untouched.
CREATE TABLE group_entries (
    group_id        UUID NOT NULL,
    entry_id        UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,
    contributed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, entry_id),
    FOREIGN KEY (group_id, user_id) REFERENCES group_members (group_id, user_id) ON DELETE CASCADE
);

-- A member's own Era created from a trip. If the group goes away the Era stays, unlinked.
ALTER TABLE eras
    ADD COLUMN group_id UUID REFERENCES groups (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_eras_user_group ON eras (user_id, group_id) WHERE group_id IS NOT NULL;
CREATE INDEX idx_eras_group ON eras (group_id) WHERE group_id IS NOT NULL;
CREATE INDEX idx_group_members_user ON group_members (user_id);
CREATE INDEX idx_group_invites_group ON group_invites (group_id);
CREATE INDEX idx_group_entries_entry ON group_entries (entry_id);
CREATE INDEX idx_group_entries_group_user ON group_entries (group_id, user_id);
```

## Phase 4 draft: AI biographer
Review this at the start of Sprint 7. `<DIM>` depends on the embedding model chosen then (D-18).

```sql
-- V{n}__ai_biographer.sql  (Phase 4 — draft)

CREATE EXTENSION IF NOT EXISTS vector;

-- Facts from answered Help me think questions (never recorded for sealed entries)
ALTER TABLE entries
    ADD COLUMN ai_metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN ai_confirmed_at TIMESTAMPTZ;

-- Weekly summary + embedding (sealed entries excluded from the input)
CREATE TABLE ai_summaries (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    weekly_log_id      UUID NOT NULL UNIQUE REFERENCES weekly_logs (id) ON DELETE CASCADE,
    narrative_summary  TEXT,
    structured_themes  JSONB NOT NULL DEFAULT '[]'::jsonb,
    emotional_state    VARCHAR(100),
    embedding          vector(<DIM>),
    is_stale           BOOLEAN NOT NULL DEFAULT FALSE,  -- set when a source entry changes; rebuilt in the background (D-21)
    provider           VARCHAR(50) NOT NULL,
    model              VARCHAR(100),
    generated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_summaries_embedding_hnsw
    ON ai_summaries USING hnsw (embedding vector_cosine_ops);

-- Cast of Characters
CREATE TABLE characters (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                  VARCHAR(150) NOT NULL,
    relationship_summary  TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_characters_user_name UNIQUE (user_id, name)
);

CREATE TABLE character_mentions (
    character_id  UUID NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    entry_id      UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    mentioned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (character_id, entry_id)
);

-- One undirected edge per pair per era: store the smaller UUID first.
-- A mention counts toward every Era whose dates contain its entry's memory date (D-22).
CREATE TABLE friendship_edges (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    character_id_1        UUID NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    character_id_2        UUID NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    era_id                UUID REFERENCES eras (id) ON DELETE SET NULL,
    interaction_strength  INTEGER NOT NULL DEFAULT 1,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_friendship_edges_ordered CHECK (character_id_1 < character_id_2),
    CONSTRAINT uq_friendship_edges_pair_era UNIQUE NULLS NOT DISTINCT (user_id, character_id_1, character_id_2, era_id)
);

CREATE INDEX idx_characters_user ON characters (user_id);
CREATE INDEX idx_friendship_edges_user_era ON friendship_edges (user_id, era_id);
```

## Phase 4 draft: paper diaries (page scans and transcription)
Review this at the start of Sprint 7c, after open question Q12 is answered (D-20). It doesn't use the vector extension, so it can ship before or after the AI biographer migration.

A photographed diary page is an ordinary `media` row, marked by `media.purpose` from V1 (D-20). This migration only stores the optional result of reading it.

`media_transcriptions` is owned through `media`, as `media` is through `entries`, so it has no `user_id`. It holds the user's private writing, so it is deleted with its media item or entry, and group endpoints (D-19) never return it.

```sql
-- V{n}__page_transcription.sql  (Phase 4, Sprint 7c — draft)

-- At most one transcription per media item. Reading a READY or FAILED page again replaces the row.
CREATE TABLE media_transcriptions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id         UUID NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transcript_text  TEXT,                  -- NULL until READY; capped at 50 KB (service check)
    language         VARCHAR(35),           -- BCP 47 tag such as 'en' or 'en-AU'
    detected_date    DATE,                  -- a date read from the page, if it had one
    confidence       NUMERIC(4,3),          -- 0 to 1, advisory; NULL if the provider gives none
    provider         VARCHAR(50) NOT NULL,  -- which TranscriptionProvider adapter produced it
    model            VARCHAR(100),          -- the provider's model or version, so a page can be read again later
    failure_reason   VARCHAR(100),          -- a short code such as 'timeout'; never page content
    requested_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMPTZ,
    CONSTRAINT uq_media_transcriptions_media UNIQUE (media_id),
    CONSTRAINT chk_media_transcriptions_status CHECK (status IN ('PENDING', 'READY', 'FAILED')),
    CONSTRAINT chk_media_transcriptions_ready CHECK (status <> 'READY' OR transcript_text IS NOT NULL),
    CONSTRAINT chk_media_transcriptions_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

-- uq_media_transcriptions_media already indexes media_id.
-- This one lets the timeout sweep find rows stuck in PENDING.
CREATE INDEX idx_media_transcriptions_pending ON media_transcriptions (requested_at) WHERE status = 'PENDING';
```

**Left open on purpose:**
- `purpose`, `provider`, `model` and `language` are plain strings checked in the service. Only the status lifecycle is constrained in the database.
- Because every result records its provider and model, a page can be read again with a better one without changing the schema.
- A bulk notebook import (Q13) can add a batch table later that points at ordinary entries. No column here needs to change.

## Phase 4 draft: settings, suggestions, tags, Era summaries, friend profiles and search
Review this at the start of Sprint 7 (D-21). Each block below is its own migration. Everything the AI makes carries a provider and a model, and narratives carry an `is_stale` flag, so they can be rebuilt without their sources. These rows hold text derived from the user's entries, so each one is deleted with its entry, Era, character or user.

```sql
-- V{n}__user_settings.sql  (Phase 4, task 7.9 — draft)

-- No row means defaults. `settings` holds the per-feature switches and options as a typed record,
-- and the API rejects unknown keys. Consent is in columns, not JSON, because every AI path reads it.
CREATE TABLE user_settings (
    user_id             UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    settings            JSONB NOT NULL DEFAULT '{}'::jsonb,
    ai_consent_at       TIMESTAMPTZ,  -- NULL: not accepted, or withdrawn
    ai_consent_version  INTEGER,      -- which wording they accepted
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_settings_object CHECK (jsonb_typeof(settings) = 'object'),
    CONSTRAINT chk_user_settings_consent CHECK ((ai_consent_at IS NULL) = (ai_consent_version IS NULL))
);
```

```sql
-- V{n}__ai_suggestions.sql  (Phase 4, task 7.2 — draft)

-- Every question, tag suggestion, writing insight and insight card is one row.
CREATE TABLE ai_suggestions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- The entry it is about. NULL for a draft that isn't saved yet, and for journal-wide cards.
    entry_id     UUID REFERENCES entries (id) ON DELETE CASCADE,
    -- QUESTION and TAG now; later WRITING_INSIGHT, PATTERN, GROWTH_REMINDER, SIMILAR_PAST and
    -- HIGHLIGHT_REEL. Checked in the service, so a new kind needs no migration.
    kind         VARCHAR(40) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    -- What to show. For a question this also holds the server-only effect of each option.
    payload      JSONB NOT NULL,
    -- What it was built from: [{ "type": "ENTRY", "id": "..." }]. Types: ENTRY, WEEKLY_SUMMARY, ERA_SUMMARY.
    sources      JSONB NOT NULL DEFAULT '[]'::jsonb,
    answer       JSONB,                 -- { "optionId": "..." } or { "feedback": "USEFUL" }
    dedupe_key   VARCHAR(120),          -- stops the same card being offered twice
    provider     VARCHAR(50) NOT NULL,  -- 'internal' for a card made without a model
    model        VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at  TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,           -- cards from unsaved drafts expire within hours; NULL means no expiry
    CONSTRAINT chk_ai_suggestions_status CHECK (status IN ('PENDING', 'OPEN', 'ANSWERED', 'DISMISSED', 'EXPIRED', 'FAILED')),
    CONSTRAINT chk_ai_suggestions_sources_array CHECK (jsonb_typeof(sources) = 'array')
);

CREATE UNIQUE INDEX uq_ai_suggestions_user_dedupe ON ai_suggestions (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_ai_suggestions_entry ON ai_suggestions (entry_id) WHERE entry_id IS NOT NULL;
CREATE INDEX idx_ai_suggestions_user_open ON ai_suggestions (user_id, created_at DESC) WHERE status IN ('PENDING', 'OPEN');
CREATE INDEX idx_ai_suggestions_expiry ON ai_suggestions (expires_at) WHERE expires_at IS NOT NULL AND status IN ('PENDING', 'OPEN');
-- Finds the cards that cite an entry when it is edited, sealed or deleted.
CREATE INDEX idx_ai_suggestions_sources ON ai_suggestions USING gin (sources jsonb_path_ops);
```

Tags (`tags`, `entry_tags`) are in V1 since D-26; Phase 4 only starts writing `source = 'AI'`.

```sql
-- V{n}__era_summaries.sql  (Phase 4, task 7.13 — draft)

-- Closing an Era writes its summary (D-28).
ALTER TABLE eras
    ADD COLUMN is_closed BOOLEAN NOT NULL DEFAULT FALSE;

-- Built from the Era's weekly summaries, never from raw entries; Q28 covers weeks the Era only partly overlaps.
CREATE TABLE era_summaries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    era_id        UUID NOT NULL UNIQUE REFERENCES eras (id) ON DELETE CASCADE,
    narrative     TEXT NOT NULL,
    themes        JSONB NOT NULL DEFAULT '[]'::jsonb,
    week_count    INTEGER NOT NULL,  -- how many weekly summaries it was built from
    is_stale      BOOLEAN NOT NULL DEFAULT FALSE,
    provider      VARCHAR(50) NOT NULL,
    model         VARCHAR(100),
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_era_summaries_week_count CHECK (week_count > 0),
    CONSTRAINT chk_era_summaries_themes_array CHECK (jsonb_typeof(themes) = 'array')
);
```

```sql
-- V{n}__friend_profiles.sql  (Phase 4, task 7.14 — draft)

-- `relationship_summary` (from the AI biographer migration) holds the generated profile text.
-- Mention counts, first and last mention and the Eras a person appears in are computed from
-- character_mentions, so they are never stored and never come from the AI.
ALTER TABLE characters
    ADD COLUMN aliases               JSONB NOT NULL DEFAULT '[]'::jsonb,  -- other names for the same person, added by merging
    ADD COLUMN user_notes            TEXT,                               -- the user's own words; the AI never edits them
    ADD COLUMN is_hidden             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN profile_is_stale      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN profile_provider      VARCHAR(50),
    ADD COLUMN profile_model         VARCHAR(100),
    ADD COLUMN profile_generated_at  TIMESTAMPTZ,
    ADD CONSTRAINT chk_characters_aliases_array CHECK (jsonb_typeof(aliases) = 'array');
```

```sql
-- V{n}__entry_search.sql  (Phase 4, task 7.5 — draft)

-- Word search over titles and captions (D-29). Queries must use the same expression and
-- always filter to the caller's unsealed entries. Confirm the text-search configuration
-- ('simple' here) when this migration is written.
CREATE INDEX idx_entries_search ON entries
    USING gin (to_tsvector('simple', coalesce(title, '') || ' ' || content_text));
```

**Left open on purpose:**
- `kind`, `provider` and `model` are plain strings checked in the service, and `payload` and `sources` are JSONB, so a new kind of card needs no migration. Only the status lifecycle is constrained in the database.
- A new kind of narrative (a year in review, say) is one more table with a real foreign key and the same three columns: `is_stale`, provider and model.
- Merging two people (task 7.14) is a service step: move `character_mentions` rows (skipping duplicates), add the old name to `aliases`, and combine their `friendship_edges`.

## Phase 4 draft: shared story
Review this when task 7.17 starts, after open question Q9 is answered (D-19, D-31). It needs the Phase 3 groups migration.

```sql
-- V{n}__group_story.sql  (Phase 4, task 7.17 — draft; needs the groups tables)

-- Opt-in: may this member's contributions feed the shared story? (Q9)
ALTER TABLE group_members
    ADD COLUMN ai_consent BOOLEAN NOT NULL DEFAULT FALSE;

-- The shared story. The service deletes the row when a contribution is withdrawn,
-- a member opts out or a member leaves, so it never outlives the content it was built from.
CREATE TABLE group_stories (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id           UUID NOT NULL UNIQUE REFERENCES groups (id) ON DELETE CASCADE,
    narrative_summary  TEXT NOT NULL,
    generated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

## Phase 6 draft: entry embeddings
Review this at the start of Sprint 9, after open questions Q15 and Q16 (D-21). Proactive insights and the writing coach need no other new table: they are `ai_suggestions` rows with new kinds.

`<DIM>` is the dimension chosen in Phase 4. If a later embedding model changes the dimension, add a new table and backfill it. The `model` column says which model made each row.

```sql
-- V{n}__entry_embeddings.sql  (Phase 6 — draft)

-- Sealed entries have no rows: the service removes them when an entry is sealed and adds them
-- when it unlocks. `user_id` is repeated here so a search can filter to one user.
CREATE TABLE entry_embeddings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    entry_id     UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    chunk_index  INTEGER NOT NULL,
    embedding    vector(<DIM>) NOT NULL,
    model        VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_entry_embeddings_entry_chunk UNIQUE (entry_id, chunk_index),
    CONSTRAINT chk_entry_embeddings_chunk CHECK (chunk_index >= 0)
);

CREATE INDEX idx_entry_embeddings_hnsw ON entry_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_entry_embeddings_user ON entry_embeddings (user_id);
```

A search that filters one user's rows out of an HNSW index can return too few results when many users share the table. The same applies to `ai_summaries`. Task 9.1 checks this with a multi-user test and, if needed, uses pgvector's iterative index scans or partitions by user.
