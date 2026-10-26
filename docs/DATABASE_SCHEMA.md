# Database Schema: memoirist

> PostgreSQL 17. Schema changes happen **only** through Flyway migrations in `backend/src/main/resources/db/migration/`, with `spring.jpa.hibernate.ddl-auto=validate`. Never edit a committed migration; add a new one (D-16). Once a migration exists in the codebase, it is the source of truth and this document must be kept in sync with it.

## Conventions
- **Primary keys:** `UUID PRIMARY KEY DEFAULT gen_random_uuid()` (built into Postgres 13+, so no extension is needed). Clients may supply the ID on create (D-12).
- **Instants:** `TIMESTAMPTZ`, stored in UTC. `created_at` and `updated_at` are `NOT NULL DEFAULT NOW()`. `updated_at` is maintained by Hibernate (`@UpdateTimestamp`).
- **Calendar dates:** `DATE` (memory dates, week and era boundaries), interpreted in the user's timezone.
- **Text:** use `VARCHAR`, never `CHAR`, because Hibernate's validator rejects `bpchar`. Documents use `JSONB`, not `JSON`.
- **Naming:** tables are plural snake_case. Constraints are named `uq_<table>_<cols>`, `chk_<table>_<rule>` and `idx_<table>_<cols>`.
- **Foreign keys:** every foreign key states its `ON DELETE` behaviour.
- **No URLs:** the database never stores a URL or storage host, only object keys.
- **Ownership:** every user-owned table has `user_id` directly, except tables that hang off another owned table: `media` is owned through `entries`, `media_transcriptions` through `media`, `entry_tags` through `entries`, and `weekly_log_eras` through `weekly_logs`.

## Migration history

| Version | File | Phase | Status |
|---|---|---|---|
| V1 | `V1__core_schema.sql` | 1 | Planned (Sprint 2, task 2.3) |
| R | `db/seed-local/R__seed_dev_user.sql` | 1 | Planned (Sprint 2, task 2.3); `local` and `test` profiles only |
| next | `V{n}__auth_and_sharing.sql` | 2 | Draft below |
| next | `V{n}__ai_biographer.sql` | 3 | Draft below; dimension decided at Phase 3 |
| next | `V{n}__user_settings.sql` | 3 (6a) | Draft below |
| next | `V{n}__ai_suggestions.sql` | 3 (6a) | Draft below |
| next | `V{n}__tags.sql` | 3 (6a) | Draft below |
| next | `V{n}__era_summaries.sql` | 3 (6b) | Draft below |
| next | `V{n}__friend_profiles.sql` | 3 (6b) | Draft below |
| next | `V{n}__page_transcription.sql` | 3 (6c) | Draft below; the provider is decided in Q12 |
| next | `V{n}__groups.sql` | 5 (8a) | Draft below |
| next | `V{n}__group_story_and_cast_links.sql` | 5 (8b) | Draft below; needs the Phase 3 tables |
| next | `V{n}__entry_embeddings.sql` | 6 | Draft below; dimension decided at Phase 3 |

---

## Phase 1 tables

### `users`
| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| email | VARCHAR(255) | NOT NULL UNIQUE |
| display_name | VARCHAR(100) | NOT NULL DEFAULT 'Time Traveler' |
| timezone | VARCHAR(64) | NOT NULL DEFAULT 'Australia/Melbourne'. IANA zone, validated with `ZoneId.of` |
| created_at, updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

### `eras`
A user-named chapter of life that groups weeks.

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| title | VARCHAR(150) | NOT NULL |
| description | TEXT | NULL |
| start_date | DATE | NOT NULL |
| end_date | DATE | NULL means ongoing. CHECK `end_date IS NULL OR end_date >= start_date` |
| created_at, updated_at | TIMESTAMPTZ | |

### `templates`
| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| name | VARCHAR(100) | NOT NULL |
| subheadings | JSONB | NOT NULL DEFAULT '[]'. Array of strings (up to 20 items, each up to 200 characters, enforced in the service). CHECK that it is an array |
| created_at, updated_at | TIMESTAMPTZ | |

### `weekly_logs`
Created automatically, one per user per Monday–Sunday week in the user's timezone (D-08).

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| title | VARCHAR(200) | NULL |
| start_date | DATE | NOT NULL. Must be a Monday. UNIQUE `(user_id, start_date)` |
| end_date | DATE | NOT NULL. CHECK `end_date = start_date + 6` |
| is_closed | BOOLEAN | NOT NULL DEFAULT FALSE. Closed weeks stay editable and can be reopened |
| created_at, updated_at | TIMESTAMPTZ | |

### `weekly_log_eras`
Links a week to the Eras it belongs to. A week can be in several Eras, so chapters can overlap (D-22). A week with no rows is in no Era.

| Column | Type | Constraints and notes |
|---|---|---|
| weekly_log_id | UUID | NOT NULL → weekly_logs ON DELETE CASCADE |
| era_id | UUID | NOT NULL → eras ON DELETE CASCADE. The Era must belong to the same user as the week (checked in the service) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

The primary key is `(weekly_log_id, era_id)`. A week can be in at most 10 Eras (service check). Deleting an Era removes only its links.

### `entries`
One Instagram-style post. `user_id` is duplicated from the week so ownership checks are a single-column lookup. The service keeps it equal to the week's owner.

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK (may be client-generated) |
| user_id | UUID | NOT NULL → users ON DELETE CASCADE |
| weekly_log_id | UUID | NOT NULL → weekly_logs ON DELETE CASCADE. The week containing `entry_date` |
| template_id | UUID | NULL → templates ON DELETE SET NULL |
| entry_date | DATE | NOT NULL. Memory date; not after today in the user's zone (service check) |
| content_json | JSONB | NULL means no caption. TipTap doc; allow-listed and at most 200 KB (D-05) |
| content_text | TEXT | NOT NULL DEFAULT ''. Plain text derived from `content_json` by the server |
| unlock_date | TIMESTAMPTZ | NULL. Sealed while `unlock_date > now()` (D-10) |
| unlock_notified | BOOLEAN | NOT NULL DEFAULT FALSE. Hook for a future unlock-notification job |
| created_at, updated_at | TIMESTAMPTZ | |

### `media`
Carousel items (images and voice notes) belonging to an entry.

| Column | Type | Constraints and notes |
|---|---|---|
| id | UUID | PK |
| entry_id | UUID | NOT NULL → entries ON DELETE CASCADE. Delete the stored objects in the service *before* the cascade (TECH_STACK §2 Storage) |
| object_key | VARCHAR(512) | NOT NULL UNIQUE. `users/{userId}/entries/{entryId}/{mediaId}.{ext}` |
| media_type | VARCHAR(100) | NOT NULL. One of the D-17 MIME types |
| display_order | INTEGER | NOT NULL, ≥ 0. UNIQUE `(entry_id, display_order)` DEFERRABLE INITIALLY DEFERRED, so a reorder can swap positions in one transaction |
| file_size_bytes | BIGINT | NOT NULL, > 0, ≤ 25 MB (service check) |
| uploaded_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

At most 10 media items per entry (service check).

### V1 SQL: `V1__core_schema.sql`

```sql
-- V1__core_schema.sql  (Phase 1)

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL DEFAULT 'Time Traveler',
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'Australia/Melbourne',
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
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_weekly_logs_user_start UNIQUE (user_id, start_date),
    CONSTRAINT chk_weekly_logs_monday CHECK (EXTRACT(ISODOW FROM start_date) = 1),
    CONSTRAINT chk_weekly_logs_seven_days CHECK (end_date = start_date + 6)
);

-- A week can be in several Eras (D-22). Deleting an Era removes only its links.
CREATE TABLE weekly_log_eras (
    weekly_log_id  UUID NOT NULL REFERENCES weekly_logs (id) ON DELETE CASCADE,
    era_id         UUID NOT NULL REFERENCES eras (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (weekly_log_id, era_id)
);

CREATE TABLE entries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    weekly_log_id    UUID NOT NULL REFERENCES weekly_logs (id) ON DELETE CASCADE,
    template_id      UUID REFERENCES templates (id) ON DELETE SET NULL,
    entry_date       DATE NOT NULL,
    content_json     JSONB,
    content_text     TEXT NOT NULL DEFAULT '',
    unlock_date      TIMESTAMPTZ,
    unlock_notified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE media (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id         UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    object_key       VARCHAR(512) NOT NULL UNIQUE,
    media_type       VARCHAR(100) NOT NULL,
    display_order    INTEGER NOT NULL,
    file_size_bytes  BIGINT NOT NULL,
    uploaded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_media_entry_order UNIQUE (entry_id, display_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_media_display_order CHECK (display_order >= 0),
    CONSTRAINT chk_media_file_size CHECK (file_size_bytes > 0)
);

-- uq_weekly_logs_user_start already indexes (user_id, start_date);
-- uq_media_entry_order already indexes (entry_id, display_order).
CREATE INDEX idx_eras_user_start ON eras (user_id, start_date DESC);
CREATE INDEX idx_templates_user ON templates (user_id);
-- The primary key of weekly_log_eras already indexes (weekly_log_id, era_id).
CREATE INDEX idx_weekly_log_eras_era ON weekly_log_eras (era_id);
CREATE INDEX idx_entries_week_date ON entries (weekly_log_id, entry_date, created_at);
CREATE INDEX idx_entries_user_date ON entries (user_id, entry_date DESC);
CREATE INDEX idx_entries_template ON entries (template_id);
CREATE INDEX idx_entries_pending_unlock ON entries (unlock_date)
    WHERE unlock_date IS NOT NULL AND unlock_notified = FALSE;
```

### Local and test seed: `db/seed-local/R__seed_dev_user.sql`
This is loaded only by the `local` and `test` profiles (D-11), so it never runs in production.

```sql
INSERT INTO users (id, email, display_name, timezone)
VALUES ('00000000-0000-0000-0000-000000000001', 'dev@memoirist.local', 'Dev User', 'Australia/Melbourne')
ON CONFLICT (id) DO NOTHING;
```

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

## Phase 3 draft: AI biographer
Review this at the start of Sprint 6. `<DIM>` depends on the embedding model chosen then (D-18).

```sql
-- V{n}__ai_biographer.sql  (Phase 3 — draft)

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
-- A mention counts toward every Era its week belongs to (D-22).
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

## Phase 3 draft: paper diaries (page scans and transcription)
Review this at the start of Sprint 6c, after open question Q12 is answered (D-20). It doesn't use the vector extension, so it can ship before or after the AI biographer migration.

A photographed diary page is an ordinary `media` row and needs no schema change in Phase 1. This migration only labels a media item as a page and stores the optional result of reading it.

`media_transcriptions` is owned through `media`, as `media` is through `entries`, so it has no `user_id`. It holds the user's private writing, so it is deleted with its media item or entry, and group endpoints (D-19) never return it.

```sql
-- V{n}__page_transcription.sql  (Phase 3, Sprint 6c — draft)

-- What a media item is for. The service validates the value against its own list, as it does
-- for media_type, so a new purpose later needs no migration.
-- PHOTO is an ordinary photo. PAGE_SCAN is a photo of a paper diary page and must be an image.
ALTER TABLE media
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'PHOTO';

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

## Phase 3 draft: settings, suggestions, tags, Era summaries and friend profiles
Review this at the start of Sprint 6 (D-21). Each block below is its own migration. Everything the AI makes carries a provider and a model, and narratives carry an `is_stale` flag, so they can be rebuilt without their sources. These rows hold text derived from the user's entries, so each one is deleted with its entry, Era, character or user.

```sql
-- V{n}__user_settings.sql  (Phase 3, task 6.9 — draft)

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
-- V{n}__ai_suggestions.sql  (Phase 3, task 6.2 — draft)

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

```sql
-- V{n}__tags.sql  (Phase 3, task 6.11 — draft)

CREATE TABLE tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tags_name CHECK (length(btrim(name)) > 0)
);

-- Names are unique per user, ignoring case.
CREATE UNIQUE INDEX uq_tags_user_name ON tags (user_id, lower(name));

-- Owned through entries and tags; the service keeps both with the same user.
CREATE TABLE entry_tags (
    entry_id    UUID NOT NULL REFERENCES entries (id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    source      VARCHAR(10) NOT NULL DEFAULT 'USER',  -- AI when the user accepted a suggestion
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entry_id, tag_id),
    CONSTRAINT chk_entry_tags_source CHECK (source IN ('USER', 'AI'))
);

CREATE INDEX idx_entry_tags_tag ON entry_tags (tag_id);
```

```sql
-- V{n}__era_summaries.sql  (Phase 3, task 6.13 — draft)

-- Built from the Era's weekly summaries, never from raw entries.
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
-- V{n}__friend_profiles.sql  (Phase 3, task 6.14 — draft)

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

**Left open on purpose:**
- `kind`, `provider` and `model` are plain strings checked in the service, and `payload` and `sources` are JSONB, so a new kind of card needs no migration. Only the status lifecycle is constrained in the database.
- A new kind of narrative (a year in review, say) is one more table with a real foreign key and the same three columns: `is_stale`, provider and model.
- Merging two people (task 6.14) is a service step: move `character_mentions` rows (skipping duplicates), add the old name to `aliases`, and combine their `friendship_edges`.

## Phase 5 draft: groups and shared memories
Review this at the start of Sprint 8, after open questions Q8–Q11 are answered (D-19). There are two migrations: 8a has no AI, and 8b needs the Phase 3 tables.

Group tables are owned through membership rather than a single `user_id`. In `group_entries`, `user_id` is the contributor, and the service keeps it equal to the entry's owner. Members are addressed by `group_members.id`, never by user ID, so no request carries another user's ID.

```sql
-- V{n}__groups.sql  (Phase 5a — draft)

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
    -- Opt-in: may this member's contributions feed the shared story? (Q9)
    ai_consent  BOOLEAN NOT NULL DEFAULT FALSE,
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

```sql
-- V{n}__group_story_and_cast_links.sql  (Phase 5b — draft; needs the Phase 3 tables)

-- The shared story. The service deletes the row when a contribution is withdrawn,
-- a member opts out or a member leaves, so it never outlives the content it was built from.
CREATE TABLE group_stories (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id           UUID NOT NULL UNIQUE REFERENCES groups (id) ON DELETE CASCADE,
    narrative_summary  TEXT NOT NULL,
    generated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- A Cast of Characters entry can point at a real group member. It exposes nothing of their journal.
ALTER TABLE characters
    ADD COLUMN linked_user_id UUID REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_characters_linked_user ON characters (linked_user_id) WHERE linked_user_id IS NOT NULL;
```

## Phase 6 draft: entry embeddings
Review this at the start of Sprint 9, after open questions Q15 and Q16 (D-21). Proactive insights and the writing coach need no other new table: they are `ai_suggestions` rows with new kinds.

`<DIM>` is the dimension chosen in Phase 3. If a later embedding model changes the dimension, add a new table and backfill it. The `model` column says which model made each row.

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
