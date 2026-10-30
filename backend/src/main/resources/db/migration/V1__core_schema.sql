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
