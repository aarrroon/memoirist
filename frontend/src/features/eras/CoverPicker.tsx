import { useState, type ReactNode } from 'react';
import { COVER_WEEKS_STEP, useEraPhotos, type CoverChoice, type EraDetail } from '../../api/eras';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/cn';
import { pictureUrl } from '../../lib/media';
import { formatDay } from '../../lib/time';

/**
 * The cover picker in Edit Era (task 4.14, D-36): one radio group of square tiles, *No cover* first, then the photos
 * of the Era's own entries, newest week first, read six weeks at a time. Arrow keys move between tiles, as in any
 * radio group. Each tile draws the photo's thumbnail (D-43), and every tile is lazy.
 *
 * A cover the user can't see (its entry sealed or in Recently deleted) is not here at all: the Era says `cover: null`,
 * so *No cover* starts selected, and saving that leaves the field out, which the server reads as "keep it" (D-36).
 */
export function CoverPicker({
  era,
  value,
  onChange,
}: {
  era: EraDetail;
  value: string | null;
  onChange: (mediaId: string | null) => void;
}) {
  const [weekCount, setWeekCount] = useState(COVER_WEEKS_STEP);
  const found = useEraPhotos(era, weekCount);
  // The cover shown now stays on offer even when its week hasn't been read yet, so the selection is always visible.
  const current: CoverChoice[] =
    era.cover && !found.photos.some((choice) => choice.photo.id === era.cover?.id)
      ? [{ photo: era.cover, entryDate: '' }]
      : [];
  const choices = [...current, ...found.photos];

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-small font-strong">Cover</legend>
      <p className="text-small text-ink-2">
        A photo from this Era opens it, here and in your journal.
      </p>
      <div className="grid grid-cols-3 gap-2">
        <Tile
          name="cover"
          checked={value === null}
          onSelect={() => onChange(null)}
          label="No cover"
        >
          <span className="flex size-full items-center justify-center bg-surface text-small text-ink-2">
            No cover
          </span>
        </Tile>
        {choices.map(({ photo, entryDate }) => (
          <Tile
            key={photo.id}
            name="cover"
            checked={value === photo.id}
            onSelect={() => onChange(photo.id)}
            label={
              entryDate ? `Photo from ${formatDay(entryDate, { year: true })}` : 'The cover now'
            }
          >
            <img
              src={pictureUrl(photo, 'thumbnail')}
              alt=""
              width={photo.width ?? 120}
              height={photo.height ?? 120}
              loading="lazy"
              decoding="async"
              className="size-full object-cover"
            />
          </Tile>
        ))}
      </div>
      {found.pending && (
        <p role="status" className="text-small text-ink-2">
          Finding photos…
        </p>
      )}
      {found.failed && (
        <p role="alert" className="text-small text-ink-2">
          Some photos couldn’t be loaded. Close and open the form to try again.
        </p>
      )}
      {!found.pending && choices.length === 0 && (
        <p className="text-small text-ink-2">No photos in this Era yet.</p>
      )}
      {found.hasMore && !found.pending && (
        <div>
          <Button variant="quiet" onClick={() => setWeekCount((count) => count + COVER_WEEKS_STEP)}>
            Show more photos
          </Button>
        </div>
      )}
    </fieldset>
  );
}

/** One square choice: a visually hidden radio, so the browser gives the group its arrow keys and its name. */
function Tile({
  name,
  checked,
  onSelect,
  label,
  children,
}: {
  name: string;
  checked: boolean;
  onSelect: () => void;
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="relative block aspect-square">
      <input
        type="radio"
        name={name}
        checked={checked}
        onChange={onSelect}
        aria-label={label}
        className="peer sr-only"
      />
      <span
        className={cn(
          'block size-full overflow-hidden rounded-sm border border-control',
          // The selected tile carries the accent (design.md §7); focus is the usual 2 px accent outline (§8).
          'peer-checked:border-accent peer-checked:outline-2 peer-checked:outline-accent',
          'peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent',
        )}
      >
        {children}
      </span>
    </label>
  );
}
