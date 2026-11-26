-- Photos store their size (D-17, task 4.5). The server reads an image's width and height from its header when it is
-- uploaded, so a screen can reserve the photo's own shape before the file arrives and nothing jumps (D-41).
-- Voice notes, and an image whose header can't be read, have no size: both columns are NULL. Rows uploaded before
-- this migration stay NULL too; the screen shows them in a 4:5 frame.
--
-- "Both or neither" is written with IS NOT NULL on each side, not as (width > 0 AND height > 0): with one column NULL
-- that expression is NULL, and a CHECK that evaluates to NULL passes, so a width without a height would get in.
ALTER TABLE media
    ADD COLUMN width INTEGER,
    ADD COLUMN height INTEGER,
    ADD CONSTRAINT chk_media_dimensions CHECK (
        (width IS NULL AND height IS NULL)
        OR (width IS NOT NULL AND height IS NOT NULL AND width > 0 AND height > 0)
    );
