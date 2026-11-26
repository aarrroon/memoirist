import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Image, Plus } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import type { Entry } from '../../api/entries';
import { problemMessage } from '../../api/problem';
import { queryKeys } from '../../api/queryKeys';
import MemoryDayPicker from '../../components/MemoryDayPicker';
import { Button } from '../../components/ui/button';
import { Popover, PopoverTrigger } from '../../components/ui/popover';
import { fileProblem, isImageType } from '../../lib/files';
import { readPhotoDay, usableDay } from '../../lib/photoDate';
import { formatDay, formatWeekRange, todayIn, weekOf, type IsoDate } from '../../lib/time';
import { planDump, type DumpDay, type DumpPhoto, type PlannedEntry } from './dumpPlan';
import { saveEntry, type TrayItem } from './saveEntry';

// Photo dump (D-34, F-19, DESIGN.md §4 "Photo dump: a sheet", round 3 #dump, task 4.13). The user picks many photos;
// each photo's own date is read before anything shrinks it, and the photos are shown grouped by day, oldest first,
// with the undated ones last under *Date unknown*. *File them* makes one backdated entry per day (several for a day of
// more than 20 photos) through capture's own save (saveEntry: POST with the client's id, then each upload, then the
// order), so a retry after a failed upload sends the same id, gets the same entry back (D-12) and uploads only what
// is missing. The photos are held in this component's state only, never in Zustand or the draft (files can't be
// kept there): the server owns them once they are filed.

/** Only photos go in a dump: the D-17 image types (HEIC is left out, so iOS hands over a JPEG). */
const ACCEPT_PHOTOS = 'image/jpeg,image/png,image/webp';

interface DumpItem extends DumpPhoto {
  file: File;
  previewUrl: string;
  status: TrayItem['status'];
  mediaId: string | null;
  error: string | null;
}

let itemCount = 0;
const nextKey = () => `dump-${++itemCount}`;

const photos = (n: number) => (n === 1 ? '1 photo' : `${n} photos`);

export interface PhotoDumpProps {
  zone: string;
  titleId: string;
  layout: 'sheet' | 'dialog';
  onRequestClose: (dirty: boolean) => void;
  onDone: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

export function PhotoDump({
  zone,
  titleId,
  layout,
  onRequestClose,
  onDone,
  onDirtyChange,
}: PhotoDumpProps) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [items, setItems] = useState<DumpItem[]>([]);
  const [reading, setReading] = useState(0);
  const [fileMessages, setFileMessages] = useState<string[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const introId = useId();

  // One client id per planned entry, made the first time it is filed and reused by every retry (D-12).
  const clientIds = useRef(new Map<string, string>());
  // Every entry filed so far, by its plan key, for the toast that names their weeks.
  const filed = useRef(new Map<string, Entry>());
  // Once filing has started, the plan must not change, or a retry would match photos to the wrong entry.
  const [started, setStarted] = useState(false);

  const today = todayIn(zone);
  const days = planDump(items, today);
  const planned = days.flatMap((d) => d.entries);
  const dirty = items.some((i) => i.status !== 'uploaded');
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);

  // Object URLs hold the picked files in memory until they are revoked.
  const urls = useRef(new Set<string>());
  useEffect(() => {
    const held = urls.current;
    return () => held.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  const patchItem = (key: string, patch: Partial<TrayItem>) =>
    setItems((list) =>
      list.map((i) =>
        i.key === key
          ? {
              ...i,
              ...(patch.status ? { status: patch.status } : {}),
              ...(patch.mediaId !== undefined ? { mediaId: patch.mediaId } : {}),
              ...(patch.error !== undefined ? { error: patch.error } : {}),
            }
          : i,
      ),
    );

  const addFiles = async (files: File[]) => {
    const problems: string[] = [];
    const added: DumpItem[] = [];
    setReading(files.length);
    for (const file of files) {
      if (!isImageType(file.type) || fileProblem({ type: file.type, size: 0 })) {
        // HEIC, video, a voice note: this file is marked, the rest carry on (F-19).
        problems.push(`${file.name}: not supported. Use a JPEG, PNG or WebP photo.`);
        setReading((n) => n - 1);
        continue;
      }
      // The date first: shrinking, which happens as each photo uploads, drops it (D-34).
      const day = usableDay(await readPhotoDay(file, zone), today);
      const previewUrl = URL.createObjectURL(file);
      urls.current.add(previewUrl);
      added.push({
        key: nextKey(),
        day,
        file,
        previewUrl,
        status: 'waiting',
        mediaId: null,
        error: null,
      });
      setReading((n) => n - 1);
    }
    setItems((list) => [...list, ...added]);
    setFileMessages(problems);
  };

  /** The undated photos get a real day before filing: they join that day, and aren't marked date unknown. */
  const setUndatedDay = (day: IsoDate) =>
    setItems((list) => list.map((i) => (i.day === null ? { ...i, day } : i)));

  const fileThem = useMutation({
    mutationFn: async () => {
      let failed = 0;
      for (const entry of planned) {
        // A day whose photos are all in is done; a retry leaves it alone.
        if (entry.photos.every((p) => p.status === 'uploaded')) continue;
        let clientId = clientIds.current.get(entry.key);
        if (!clientId) {
          clientId = crypto.randomUUID();
          clientIds.current.set(entry.key, clientId);
        }
        try {
          const result = await saveEntry(
            {
              clientId,
              // Always a create with the same id: a retry of an entry that exists gets it back (200), so nothing
              // is duplicated, and an entry erased because none of its photos uploaded (D-23) is made again.
              saved: null,
              fields: {
                entryDate: entry.entryDate,
                templateId: null,
                title: null,
                contentJson: null,
                tagIds: [],
                soundtrack: null,
                replyToEntryId: null,
                dateUnknown: entry.dateUnknown,
              },
              items: entry.photos.map(toTrayItem),
              removedMediaIds: [],
            },
            patchItem,
          );
          failed += result.failed;
          if (result.entry) filed.current.set(entry.key, result.entry);
          else filed.current.delete(entry.key);
        } catch (error) {
          // The create itself failed: every photo of this entry waits for Try again.
          const why = problemMessage(error);
          for (const photo of entry.photos) {
            if (photo.status === 'uploaded') continue;
            failed++;
            patchItem(photo.key, { status: 'failed', error: why });
          }
        }
      }
      return failed;
    },
    onMutate: () => {
      setMessage(null);
      setStarted(true);
    },
    onSuccess: async (failed) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.onThisDay() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.weeks() }),
        queryClient.invalidateQueries({ queryKey: queryKeys.timelines() }),
      ]);
      if (failed === 0) {
        announce([...filed.current.values()]);
        onDone();
        return;
      }
      setMessage(
        failed === 1
          ? '1 photo didn’t upload. The rest are filed. Try again to send it.'
          : `${failed} photos didn’t upload. The rest are filed. Try again to send them.`,
      );
    },
  });

  /** F-19 step 4: a toast names the weeks the entries landed in, with Go there (D-23). */
  const announce = (entries: Entry[]) => {
    if (entries.length === 0) return;
    const sorted = [...entries].sort((a, b) => a.entryDate.localeCompare(b.entryDate));
    const first = sorted[0] as Entry;
    const weeks = new Set(sorted.map((e) => weekOf(e.entryDate).start));
    const { start, end } = weekOf(first.entryDate);
    const count = sorted.length === 1 ? '1 entry' : `${sorted.length} entries`;
    const where =
      weeks.size === 1
        ? `in the week of ${formatWeekRange(start, end)}`
        : `in ${weeks.size} weeks, from the week of ${formatWeekRange(start, end)}`;
    toast(`Filed ${count} ${where}`, {
      action: { label: 'Go there', onClick: () => void navigate(`/weeks/${first.weeklyLogId}`) },
    });
  };

  const total = items.length;
  const done = items.filter((i) => i.status === 'uploaded').length;
  const failedCount = items.filter((i) => i.status === 'failed').length;
  const status =
    reading > 0
      ? 'Reading the photos’ dates…'
      : fileThem.isPending
        ? `Filing ${done} of ${photos(total)}…`
        : total > 0
          ? photos(total)
          : '';

  const pick = () => inputRef.current?.click();

  return (
    <form
      className="flex min-h-0 flex-1 flex-col"
      aria-describedby={introId}
      onSubmit={(event) => {
        event.preventDefault();
        if (total > 0 && reading === 0 && !fileThem.isPending) fileThem.mutate();
      }}
    >
      {/* The 56 px bar, as in capture: Cancel, the heading over the count, and the one primary button. */}
      <div className="grid min-h-14 shrink-0 grid-cols-[1fr_auto_1fr] items-center gap-2 border-b border-line px-4">
        <Button
          variant="quiet"
          className="justify-self-start"
          onClick={() => onRequestClose(dirty)}
        >
          Cancel
        </Button>
        <div className="flex flex-col items-center">
          <h2 id={titleId} className="text-body font-strong">
            Photo dump
          </h2>
          <p className="text-meta text-ink-2" aria-live="polite">
            {status}
          </p>
        </div>
        <Button
          type="submit"
          variant="primary"
          className="justify-self-end"
          disabled={total === 0 || reading > 0 || fileThem.isPending}
        >
          {failedCount > 0 && !fileThem.isPending ? 'Try again' : 'File them'}
        </Button>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-8 overflow-y-auto px-4 py-4 lg:px-8 lg:py-6">
        <div className="flex flex-col items-start gap-4">
          <p id={introId} className="text-body text-ink-2">
            One entry for each day the photos were taken. Add words to them later.
          </p>
          {total === 0 && reading === 0 && (
            <Button onClick={pick}>
              <Image size={20} aria-hidden />
              Choose photos
            </Button>
          )}
          {message && (
            <p role="alert" className="text-body">
              {message}
            </p>
          )}
          {fileMessages.length > 0 && (
            <ul role="alert" className="flex flex-col gap-1 text-small text-danger">
              {fileMessages.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          )}
        </div>

        {days.map((day) => (
          <DaySection
            key={day.day ?? 'unknown'}
            day={day}
            zone={zone}
            today={today}
            layout={layout}
            locked={started || fileThem.isPending}
            onDate={setUndatedDay}
          />
        ))}

        {total > 0 && !started && !fileThem.isPending && (
          <Button className="self-start" onClick={pick} disabled={reading > 0}>
            <Plus size={20} aria-hidden />
            Add more photos
          </Button>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ACCEPT_PHOTOS}
        aria-label="Photos to file"
        className="sr-only"
        tabIndex={-1}
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          if (files.length > 0) void addFiles(files);
        }}
      />
    </form>
  );
}

const toTrayItem = (item: DumpItem): TrayItem => ({
  key: item.key,
  file: item.status === 'uploaded' ? null : item.file,
  previewUrl: item.previewUrl,
  mediaType: item.file.type,
  purpose: 'PHOTO',
  status: item.status,
  mediaId: item.mediaId,
  error: item.error,
});

/** One day of the dump: its heading and count, how many entries it makes, then its photos three across. */
function DaySection({
  day,
  zone,
  today,
  layout,
  locked,
  onDate,
}: {
  day: DumpDay<DumpItem>;
  zone: string;
  today: IsoDate;
  layout: 'sheet' | 'dialog';
  locked: boolean;
  onDate: (day: IsoDate) => void;
}) {
  const headingId = useId();
  const [picking, setPicking] = useState(false);
  const heading =
    day.day === null
      ? 'Date unknown'
      : formatDay(day.day, { year: day.day.slice(0, 4) !== today.slice(0, 4) });

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <div className="flex items-baseline justify-between gap-4">
        <h3 id={headingId} className="text-body font-strong">
          {heading}
        </h3>
        <p className="shrink-0 text-small text-ink-2">{photos(day.photos.length)}</p>
      </div>
      {day.entries.length > 1 && (
        <p className="text-small text-ink-2">
          Filed as {day.entries.length} entries, as an entry holds 20 photos at most.
        </p>
      )}
      {day.day === null && (
        <div className="flex items-center justify-between gap-4">
          <p className="text-small text-ink-2">Filed under today until you change its date.</p>
          {!locked && (
            <Popover open={picking} onOpenChange={setPicking}>
              <PopoverTrigger asChild>
                <Button className="shrink-0">Change date</Button>
              </PopoverTrigger>
              <MemoryDayPicker
                day={null}
                zone={zone}
                label="Pick the day these photos were taken"
                onPick={(picked) => {
                  setPicking(false);
                  onDate(picked);
                }}
              />
            </Popover>
          )}
        </div>
      )}
      <ul
        aria-label={`Photos, ${heading}`}
        className={
          layout === 'dialog' ? 'grid grid-cols-5 gap-2' : 'grid grid-cols-3 gap-2 md:grid-cols-4'
        }
      >
        {day.photos.map((photo, index) => (
          <DumpThumbnail key={photo.key} photo={photo} index={index} />
        ))}
      </ul>
      {day.entries.map((entry) => (
        <EntryErrors key={entry.key} entry={entry} />
      ))}
    </section>
  );
}

function DumpThumbnail({ photo, index }: { photo: DumpItem; index: number }) {
  return (
    <li className="relative aspect-square overflow-hidden rounded-sm bg-surface">
      <img
        src={photo.previewUrl}
        alt={`Photo ${index + 1}`}
        width={120}
        height={120}
        loading="lazy"
        decoding="async"
        className={
          photo.status === 'uploading'
            ? 'size-full object-cover opacity-60'
            : 'size-full object-cover'
        }
      />
      {photo.status === 'failed' && (
        <span className="absolute inset-0 flex items-center justify-center bg-scrim text-on-band">
          <CircleAlert size={20} aria-hidden />
          <span className="sr-only">Didn’t upload</span>
        </span>
      )}
    </li>
  );
}

/** Why an entry's photos didn't upload, once, under its day (the words come from problemMessage, never the server). */
function EntryErrors({ entry }: { entry: PlannedEntry<DumpItem> }) {
  const errors = [...new Set(entry.photos.map((p) => p.error).filter((e): e is string => !!e))];
  if (errors.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1 text-small text-danger">
      {errors.map((e) => (
        <li key={e}>{e}</li>
      ))}
    </ul>
  );
}
