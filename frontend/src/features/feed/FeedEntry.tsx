import { Link } from 'react-router';
import type { Entry, Media } from '../../api/feed';
import { dayLabel, dayParts } from '../../lib/time';
import { SealedCard } from './SealedCard';

// One entry in the feed (task 4.4, DESIGN.md §4 Feed). Task 4.5 replaces the single photo with the swipe (Embla,
// every photo, its stored shape) and the plain caption with one rendered from contentJson; this is the least
// that shows the sample content faithfully until then.

const isImage = (media: Media) => media.mediaType.startsWith('image/');

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
          <EntryBody entry={entry} eagerPhotoId={eagerPhotoId} />
        )}
      </div>
    </article>
  );
}

function EntryBody({ entry, eagerPhotoId }: { entry: Entry; eagerPhotoId: string | null }) {
  const photos = entry.media.filter(isImage);
  const first = photos[0];
  const caption = entry.contentText?.trim() || null;

  return (
    <>
      {first && (
        // The frame is 4:5 until photos carry their size (D-17, task 4.5), so its space is reserved before the
        // photo arrives and nothing below it moves (layout shift ≤ 0.1, design.md §6). Edge to edge on a phone,
        // `lg` corners from the tablet width.
        <div className="-mx-4 aspect-4/5 overflow-hidden bg-surface md:mx-0 md:rounded-lg">
          <img
            src={first.fileUrl}
            alt={
              first.purpose === 'PAGE_SCAN'
                ? 'Diary page'
                : photos.length > 1
                  ? `Photo 1 of ${photos.length}`
                  : 'Photo'
            }
            decoding="async"
            {...(first.id === eagerPhotoId
              ? { loading: 'eager' as const, fetchPriority: 'high' as const }
              : { loading: 'lazy' as const })}
            className="size-full object-cover"
          />
        </div>
      )}

      <div className="flex flex-col gap-2">
        {entry.title && <h3 className="font-display text-title">{entry.title}</h3>}
        {/* Plain text, never HTML (non-negotiable 6). Task 4.5 renders the caption from contentJson instead. */}
        {caption && <p className="line-clamp-3 text-body whitespace-pre-line">{caption}</p>}
        {!entry.title && !caption && mediaLabel(entry) && (
          <p className="text-small text-ink-2">{mediaLabel(entry)}</p>
        )}
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
    </>
  );
}
