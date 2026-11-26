import { Link } from 'react-router';
import type { Entry } from '../../api/feed';
import { RichText } from '../../components/RichText';
import { Swipe } from '../../components/Swipe';
import { VoiceNote } from '../../components/VoiceNote';
import { isPicture as isImage } from '../../lib/media';
import { hasText } from '../../lib/tiptap';
import { entryDisplayTitle } from '../../lib/displayTitle';
import { dayLabel, dayParts, formatDay } from '../../lib/time';
import { DateUnknownBadge } from './DateUnknownBadge';
import { SoundtrackPlayer } from '../music/SoundtrackPlayer';
import { EntryMenu } from './EntryMenu';
import { SealedCard } from './SealedCard';

// One entry in the feed (tasks 4.4 and 4.5, DESIGN.md §4 Feed, D-41): the day line, the swipe of its photos,
// then 12 px below the title, the caption (three lines, drawn from its TipTap JSON), its voice notes, its soundtrack
// (task 4.15: a placeholder row until Play is tapped, D-35) and its tags.

/** D-24's label for an entry with no title and no caption: what its first media item is. An entry with nothing
 *  at all can't be saved (D-23), and D-24 names no label for it, so it shows none. */
function mediaLabel(entry: Entry): string | null {
  const first = entry.media[0];
  if (!first) return null;
  if (first.purpose === 'PAGE_SCAN') return 'Diary page';
  return isImage(first) ? 'Photo' : 'Voice note';
}

export function FeedEntry({
  entry,
  showDay,
  eagerPhotoId,
  zone,
  now,
}: {
  entry: Entry;
  /** Only the first entry of a day has the day line (DESIGN.md §4 Feed). */
  showDay: boolean;
  /** The one photo on the screen that loads at once, with high priority; every other is lazy (design.md §6). */
  eagerPhotoId: string | null;
  zone: string;
  now: Date;
}) {
  const day = showDay ? dayLabel(entry.entryDate, zone, now) : null;
  const margin = showDay ? dayParts(entry.entryDate) : null;

  return (
    <article
      aria-label={entry.isLocked ? 'Sealed Loot Drop' : undefined}
      className="flex flex-col gap-3 lg:grid lg:feed-grid lg:gap-x-8 lg:gap-y-0"
    >
      {/* The day: a line above the entry on a phone, the margin beside it on a laptop. One is always hidden. */}
      {day && <p className="text-small font-strong lg:hidden">{day}</p>}
      <div className="hidden lg:flex lg:flex-col lg:items-end">
        {margin && (
          <>
            <span className="text-meta text-ink-2">
              {day === 'Today' || day === 'Yesterday' ? day : margin.weekday}
            </span>
            <span className="font-display text-title">{margin.short}</span>
          </>
        )}
      </div>

      <div className="flex min-w-0 flex-col gap-3">
        {entry.isLocked ? (
          <SealedCard entry={entry} zone={zone} now={now} />
        ) : (
          <EntryBody entry={entry} eagerPhotoId={eagerPhotoId} zone={zone} />
        )}
      </div>
    </article>
  );
}

function EntryBody({
  entry,
  eagerPhotoId,
  zone,
}: {
  entry: Entry;
  eagerPhotoId: string | null;
  zone: string;
}) {
  const photos = entry.media.filter(isImage);
  const notes = entry.media.filter((media) => !isImage(media));
  const caption = hasText(entry.contentJson);

  return (
    <>
      {photos.length > 0 && <Swipe photos={photos} eagerPhotoId={eagerPhotoId} />}

      {/* Task 4.12: the text, with the entry's more-actions menu at its top right (F-09). */}
      <div className="flex items-start gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          {/* A photo dump's undated photos (D-34, task 4.13). Only on an open entry: a sealed card shows its dates
              and countdown and nothing else (D-10, design.md §5), and its date can't be changed while sealed. */}
          {entry.dateUnknown && <DateUnknownBadge entry={entry} zone={zone} />}
          {entry.title && <h3 className="font-display text-title">{entry.title}</h3>}
          {/* Drawn from the JSON as React elements, never as HTML (non-negotiable 6). */}
          {caption && <RichText document={entry.contentJson} clamp className="text-body" />}
          {!entry.title && !caption && mediaLabel(entry) && (
            <p className="text-small text-ink-2">{mediaLabel(entry)}</p>
          )}
          {notes.map((note, index) => (
            <VoiceNote
              key={note.id}
              src={note.fileUrl}
              name={notes.length > 1 ? `voice note ${index + 1} of ${notes.length}` : 'voice note'}
            />
          ))}
          {/* A sealed entry never gets here, and the server sends its soundtrack as null anyway (D-10). */}
          {entry.soundtrack && <SoundtrackPlayer uri={entry.soundtrack} />}
          {entry.tags.length > 0 && (
            // Each tag opens the timeline filtered by it (DESIGN.md §4 Feed; the filter is task 4.11). Every link is
            // at least 44 × 44 px (design.md §9), so a short tag such as "Trip" keeps a blank tail; that is why they
            // are spaced rather than joined by commas as in the mockup.
            <ul aria-label="Tags" className="flex flex-wrap gap-x-4">
              {entry.tags.map((tag) => (
                <li key={tag.id} className="text-small text-ink-2">
                  <Link
                    to={`/entries?tag=${encodeURIComponent(tag.id)}`}
                    className="inline-flex min-h-11 min-w-11 items-center"
                  >
                    {tag.name}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
        <EntryMenu
          entryId={entry.id}
          name={entryDisplayTitle(entry) ?? formatDay(entry.entryDate, { year: true })}
        />
      </div>
    </>
  );
}
