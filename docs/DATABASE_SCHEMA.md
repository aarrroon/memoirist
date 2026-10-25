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
- **Ownership:** every user-owned table has `user_id` directly, except `media`, which is owned through `entries`.

## Migration history

| Version | File | Phase | Status |
|---|---|---|---|
| V1 | `V1__core_schema.sql` | 1 | Planned (Sprint 2, task 2.3) |
| R | `db/seed-local/R__seed_dev_user.sql` | 1 | Planned (Sprint 2, task 2.3); `local` and `test` profiles only |
| next | `V{n}__auth_and_sharing.sql` | 2 | Draft below |
| next | `V{n}__ai_biographer.sql` | 3 | Draft below; dimension decided at Phase 3 |

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
| era_id | UUID | NULL → eras ON DELETE SET NULL. The era must belong to the same user (checked in the service) |
| title | VARCHAR(200) | NULL |
| start_date | DATE | NOT NULL. Must be a Monday. UNIQUE `(user_id, start_date)` |
| end_date | DATE | NOT NULL. CHECK `end_date = start_date + 6` |
| is_closed | BOOLEAN | NOT NULL DEFAULT FALSE. Closed weeks stay editable and can be reopened |
| created_at, updated_at | TIMESTAMPTZ | |

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
    era_id      UUID REFERENCES eras (id) ON DELETE SET NULL,
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
CREATE INDEX idx_weekly_logs_era ON weekly_logs (era_id);
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

-- Per-entry analysis and one-tap confirmations (never run on sealed entries)
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
