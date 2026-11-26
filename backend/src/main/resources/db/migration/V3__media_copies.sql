-- Smaller copies of photos (D-43, task 4.18). The browser makes a thumbnail (480 px on the longest side) and a screen
-- copy (1600 px) of every photo and diary page when it uploads it, and sends them in the same request. They are stored
-- next to the original and named on the same row, so removing the photo or erasing its entry finds every file (D-30).
--
-- Both are nullable: a photo already no bigger than a copy gets no copy of that size, a voice note gets none, and rows
-- from before this migration (or from a client that makes no copies) have none. The screen falls back to the next size
-- up, ending at the original. Like object_key, each is unique: two rows never point at one file, so deleting one row's
-- files can't take another's.
--
-- Only an image may have copies. The check reads media_type, which the service has already narrowed to the D-17 list.
ALTER TABLE media
    ADD COLUMN thumbnail_key VARCHAR(512) UNIQUE,
    ADD COLUMN screen_key VARCHAR(512) UNIQUE,
    ADD CONSTRAINT chk_media_copies_images_only CHECK (
        (thumbnail_key IS NULL AND screen_key IS NULL) OR media_type LIKE 'image/%'
    );
