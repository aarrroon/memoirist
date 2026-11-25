import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { JSONContent } from '@tiptap/react';
import { Image, Mic, Tag } from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { getEntry, type Entry } from '../../api/entries';
import { afterSeal, sealEntry } from '../../api/lootDrops';
import { ApiProblem, problemMessage } from '../../api/problem';
import { queryKeys } from '../../api/queryKeys';
import { StarMark } from '../../components/TravellingStar';
import { Button } from '../../components/ui/button';
import { fileProblem, isImageType, MAX_FILES, shrinkPhoto } from '../../lib/files';
import { canRecord } from '../../lib/recording';
import { captionForSave, insertTemplateHeadings, isBlankDoc } from '../../lib/templateDoc';
import {
  addDays,
  chipDay,
  formatClock,
  formatWeekRange,
  isWithin,
  latestSealDay,
  todayIn,
  unlockInstant,
  weekOf,
  type IsoDate,
} from '../../lib/time';
import type { CaptureKind } from '../../stores/ui';
import SealPanel from '../lootdrop/SealPanel';
import { SealForm } from '../lootdrop/SealForm';
import { sealProblem } from '../lootdrop/sealProblem';
import { DateChip } from './DateChip';
import { clearDraft, readDraft, writeDraft, type Draft } from './draft';
import { EntryEditor } from './Editor';
import { useEntryEditor } from './editorSetup';
import { MediaTray } from './MediaTray';
import { picturesFirst, saveEntry, type SaveResult, type TrayItem } from './saveEntry';
import { TagPicker } from './TagPicker';
import { TemplateChip } from './TemplateChip';
import { useRecorder, type Recording } from './useRecorder';
import { VoiceRecorder } from './VoiceRecorder';

// Capture: New entry and Diary page (DESIGN.md §4 Write, F-02 to F-05, task 4.6). Everything the user types lives in
// this component's state, never in Zustand (frontend.md, Data): it is a form, and the server owns the entry once it
// is saved. A draft of it goes to local storage as it changes (D-23). Photo dump (D-34) is task 4.13.

/** What one Save (or Seal it) did: the save's own result, the entry as saved before any seal, and the seal's fate. */
type SaveOutcome = SaveResult & {
  savedEntry: Entry | null;
  /** The opening day, when the entry is now sealed. */
  sealed: IsoDate | null;
  /** Why the seal failed, when it was asked for and the save worked. */
  sealError: unknown;
};

const lowerFirst = (text: string) => text.charAt(0).toLowerCase() + text.slice(1);

/** Which fields the API named in a 400 (API_CONTRACT §Entries), shown beside each one. */
type FieldMessages = Partial<Record<'title' | 'entryDate' | 'contentJson' | 'tagIds', string>>;

export interface CaptureFormProps {
  kind: CaptureKind;
  zone: string;
  /** The Cancel button, Escape and the scrim all ask the frame; the frame asks here whether to confirm first. */
  onRequestClose: (dirty: boolean) => void;
  /** Closes for good, after a save or a discard. */
  onDone: () => void;
  /** Keeps the frame told whether closing now would lose something (D-23). */
  onDirtyChange: (dirty: boolean) => void;
  /** The frame's title id, so the sheet or dialog is named by the visible heading. */
  titleId: string;
  layout: 'sheet' | 'dialog';
}

function freshDraft(kind: CaptureKind, zone: string): Draft {
  return {
    kind,
    clientId: crypto.randomUUID(),
    entryId: null,
    entryVersion: null,
    entryDate: todayIn(zone),
    templateId: null,
    prompts: [],
    title: '',
    content: null,
    tagIds: [],
    savedAt: new Date().toISOString(),
  };
}

let itemCount = 0;
const nextKey = () => `item-${++itemCount}`;

/** A photo already on the server, as a tray item. */
const uploadedItem = (m: Entry['media'][number]): TrayItem => ({
  key: nextKey(),
  file: null,
  previewUrl: m.fileUrl,
  mediaType: m.mediaType,
  purpose: m.purpose === 'PAGE_SCAN' ? 'PAGE_SCAN' : 'PHOTO',
  status: 'uploaded',
  mediaId: m.id,
  error: null,
});

export function CaptureForm({
  kind,
  zone,
  onRequestClose,
  onDone,
  onDirtyChange,
  titleId,
  layout,
}: CaptureFormProps) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  // One draft at a time (D-23): a reload brings it back, whichever Write choice opened capture.
  const [restored] = useState(() => readDraft());
  const [start] = useState(() => restored ?? freshDraft(kind, zone));
  const [draftSavedAt, setDraftSavedAt] = useState<string | null>(restored?.savedAt ?? null);

  const [clientId, setClientId] = useState(start.clientId);
  const [saved, setSaved] = useState(
    start.entryId !== null && start.entryVersion !== null
      ? { id: start.entryId, version: start.entryVersion }
      : null,
  );
  const [entryDate, setEntryDate] = useState(start.entryDate);
  const [templateId, setTemplateId] = useState(start.templateId);
  const [prompts, setPrompts] = useState(start.prompts);
  const [title, setTitle] = useState(start.title);
  const [content, setContent] = useState<JSONContent | null>(start.content);
  const [tagIds, setTagIds] = useState(start.tagIds);
  const [items, setItems] = useState<TrayItem[]>([]);
  const [removedMediaIds, setRemovedMediaIds] = useState<string[]>([]);
  // What the server last said about fields capture doesn't edit, sent back on every PUT (D-26).
  const [kept, setKept] = useState<{ soundtrack: string | null; replyToEntryId: string | null }>({
    soundtrack: null,
    replyToEntryId: null,
  });

  const [fieldErrors, setFieldErrors] = useState<FieldMessages>({});
  const [message, setMessage] = useState<string | null>(null);
  const [fileMessages, setFileMessages] = useState<string[]>([]);
  const [stale, setStale] = useState(false);
  const [tagsOpen, setTagsOpen] = useState(false);
  // Sealing (task 4.8): the phone's seal panel, and what went wrong with the last seal, shown inside it.
  const [sealOpen, setSealOpen] = useState(false);
  const [sealError, setSealError] = useState<string | null>(null);
  const addRef = useRef<HTMLInputElement>(null);
  const titleFieldId = useId();
  const sealTitleId = useId();
  // Voice (task 4.7): hidden when this browser can't record one of our formats (F-03), decided once.
  const [recordable] = useState(canRecord);
  const voiceTrigger = useRef<HTMLButtonElement | null>(null);
  // The seal panel opens from state, so Escape hands focus back to the Seal button by hand (task 4.8).
  const sealTrigger = useRef<HTMLButtonElement>(null);
  const kindOfDraft = restored?.kind ?? kind;

  const editor = useEntryEditor(start.content, setContent);

  // A recording in progress is held by the recorder until Stop, so it is declared before `dirty`, which counts it.
  // The recorder calls back after Stop; the ref lets that call reach addRecording (declared further down, as it needs
  // the tray) as it is in the latest render.
  const recordedRef = useRef<(recording: Recording) => void>(() => undefined);
  const recorder = useRecorder((done) => recordedRef.current(done));
  const recording = recorder.state.status === 'recording' || recorder.state.status === 'asking';

  const caption = captionForSave(content, prompts);
  const hasContent = title.trim() !== '' || caption !== null || items.length > 0;
  const dirty =
    recording ||
    title.trim() !== '' ||
    !isBlankDoc(content) ||
    items.some((i) => i.status !== 'uploaded') ||
    removedMediaIds.length > 0 ||
    tagIds.length > 0 ||
    templateId !== null ||
    entryDate !== todayIn(zone);

  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);

  // A restored draft whose entry already exists (a save whose photos partly failed, D-23): the photos that did upload
  // are on the server, not in the draft, so read them back into the tray, where they can be seen, moved and removed.
  // The typed text and the version stay the draft's, so a change made elsewhere still meets 409 entry-stale.
  const restoredId = restored?.entryId ?? null;
  const restoring = useRef(false);
  // Save waits for this read: until it answers, `kept` is empty, and a PUT would clear the soundtrack and the reply
  // (D-26). A sealed entry keeps Save off for good (D-10). Review 2 fix, task 4.6.
  const [restore, setRestore] = useState<'reading' | 'done' | 'sealed'>(
    restoredId === null ? 'done' : 'reading',
  );
  useEffect(() => {
    if (restoredId === null || restoring.current) return;
    restoring.current = true;
    queryClient
      .fetchQuery({
        queryKey: queryKeys.entry(restoredId),
        queryFn: ({ signal }) => getEntry(restoredId, signal),
        staleTime: 0,
      })
      .then((latest) => {
        if (latest.isLocked) {
          // Sealed somewhere else: nothing of it can be shown or changed (D-10).
          setMessage('This entry is now a sealed Loot Drop, so it can’t be changed.');
          setRestore('sealed');
          return;
        }
        setKept({ soundtrack: latest.soundtrack, replyToEntryId: latest.replyToEntryId });
        setItems((list) => [...latest.media.map(uploadedItem), ...list]);
        setRestore('done');
      })
      .catch((error: unknown) => {
        if (error instanceof ApiProblem && error.kind === 'not-found') {
          // Deleted elsewhere: the next save makes a new entry instead of replacing a missing one.
          setSaved(null);
          setClientId(crypto.randomUUID());
          setRestore('done');
          return;
        }
        // Any other failure: Save stays on, and the PUT's own answer (409 entry-stale, 404) handles the rest.
        setMessage(problemMessage(error));
        setRestore('done');
      });
  }, [restoredId, queryClient]);
  const canSave = hasContent && restore === 'done';

  // The draft follows the form, a moment after the typing stops (D-23).
  useEffect(() => {
    if (!dirty && saved === null) return;
    const id = setTimeout(() => {
      const savedAt = new Date().toISOString();
      writeDraft({
        kind: kindOfDraft,
        clientId,
        entryId: saved?.id ?? null,
        entryVersion: saved?.version ?? null,
        entryDate,
        templateId,
        prompts,
        title,
        content,
        tagIds,
        savedAt,
      });
      setDraftSavedAt(savedAt);
    }, 400);
    return () => clearTimeout(id);
  }, [dirty, kindOfDraft, clientId, saved, entryDate, templateId, prompts, title, content, tagIds]);

  // Object URLs hold the picked files in memory until they are revoked.
  const urls = useRef(new Set<string>());
  useEffect(() => {
    const held = urls.current;
    return () => held.forEach((url) => URL.revokeObjectURL(url));
  }, []);

  const patchItem = (key: string, patch: Partial<TrayItem>) =>
    setItems((list) => list.map((i) => (i.key === key ? { ...i, ...patch } : i)));

  const addFiles = async (files: File[]) => {
    const problems: string[] = [];
    const room = MAX_FILES - items.length;
    if (files.length > room)
      problems.push(
        `An entry holds 20 photos and voice notes at most, so ${files.length - room} weren’t added.`,
      );
    for (const picked of files.slice(0, Math.max(room, 0))) {
      // The type first, so nothing unsupported is shrunk; then the size of what will really be sent (D-17).
      const typeProblem = fileProblem({ type: picked.type, size: 0 });
      if (typeProblem) {
        problems.push(`${picked.name}: ${typeProblem}`);
        continue;
      }
      const file = await shrinkPhoto(picked);
      const sizeProblem = fileProblem(file);
      if (sizeProblem) {
        problems.push(`${picked.name}: ${sizeProblem}`);
        continue;
      }
      const previewUrl = URL.createObjectURL(file);
      urls.current.add(previewUrl);
      const item: TrayItem = {
        key: nextKey(),
        file,
        previewUrl,
        mediaType: file.type,
        // Diary page marks every photo it adds as a page (D-20); New entry adds photos.
        purpose: kindOfDraft === 'page' && isImageType(file.type) ? 'PAGE_SCAN' : 'PHOTO',
        status: 'waiting',
        mediaId: null,
        error: null,
      };
      // A photo picked after a recording still goes before it (D-24; task 4.7 review fix).
      setItems((list) => picturesFirst([...list, item]));
    }
    setFileMessages(problems);
  };

  /** A finished recording joins the tray like a picked file, with its bars and length for its row. */
  const addRecording = ({ file, seconds, levels }: Recording) => {
    const problem =
      items.length >= MAX_FILES
        ? 'An entry holds 20 photos and voice notes at most.'
        : fileProblem(file);
    if (problem) {
      setFileMessages([`Voice note: ${problem}`]);
      return;
    }
    const previewUrl = URL.createObjectURL(file);
    urls.current.add(previewUrl);
    setItems((list) => [
      ...list,
      {
        key: nextKey(),
        file,
        previewUrl,
        mediaType: file.type,
        purpose: 'PHOTO',
        status: 'waiting',
        mediaId: null,
        error: null,
        levels,
        seconds,
      },
    ]);
    setFileMessages([]);
  };

  useEffect(() => {
    recordedRef.current = addRecording;
  });

  /** Voice: start recording, remembering which button asked, so focus can go back to it afterwards. */
  const startVoice = (event: React.MouseEvent<HTMLButtonElement>) => {
    voiceTrigger.current = event.currentTarget;
    recorder.start();
  };
  const refocusVoice = () => requestAnimationFrame(() => voiceTrigger.current?.focus());
  const voiceBusy = recording || items.length >= MAX_FILES;

  const removeItem = (key: string) => {
    const item = items.find((i) => i.key === key);
    if (!item) return;
    if (item.mediaId) setRemovedMediaIds((ids) => [...ids, item.mediaId as string]);
    setItems((list) => list.filter((i) => i.key !== key));
  };

  const togglePage = (key: string) =>
    setItems((list) =>
      list.map((i) =>
        i.key === key ? { ...i, purpose: i.purpose === 'PAGE_SCAN' ? 'PHOTO' : 'PAGE_SCAN' } : i,
      ),
    );

  // Save, and Seal it (task 4.8): a seal is the last step of the same save (D-10, frontend.md: create → upload →
  // reorder → seal), and only when every file is on the server, so nothing is sealed half-finished. A seal that fails
  // leaves the entry saved and unsealed, and says so (F-06).
  const save = useMutation({
    mutationFn: async (sealDay: IsoDate | null): Promise<SaveOutcome> => {
      const result = await saveEntry(
        {
          clientId,
          saved,
          fields: {
            entryDate,
            templateId,
            title: title.trim() === '' ? null : title.trim(),
            contentJson: caption,
            tagIds,
            soundtrack: kept.soundtrack,
            replyToEntryId: kept.replyToEntryId,
          },
          items,
          removedMediaIds,
        },
        patchItem,
      );
      if (!sealDay || !result.entry || result.failed > 0) {
        return { ...result, savedEntry: result.entry, sealed: null, sealError: null };
      }
      try {
        const sealedEntry = await sealEntry(result.entry.id, unlockInstant(sealDay, zone));
        return {
          ...result,
          savedEntry: result.entry,
          entry: sealedEntry,
          sealed: sealDay,
          sealError: null,
        };
      } catch (error) {
        return { ...result, savedEntry: result.entry, sealed: null, sealError: error };
      }
    },
    onMutate: () => {
      setFieldErrors({});
      setMessage(null);
      setSealError(null);
      setStale(false);
    },
    onSuccess: async ({ entry, savedEntry, failed, sealed, sealError }, sealDay) => {
      setRemovedMediaIds([]);
      // A seal goes through afterSeal, as useSeal's does: it overwrites any whole copy of this entry that capture
      // read back when it restored the draft, so the Loot Drop page never shows a sealed entry's content (D-10).
      // A save also refreshes the weeks: Go there opens the entry's week, which must show the new entry (task 4.10).
      await (sealed && entry
        ? afterSeal(queryClient, entry)
        : Promise.all([
            queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
            queryClient.invalidateQueries({ queryKey: queryKeys.weeks() }),
            // Task 4.11: the Timeline lists the entry, with its title and tags.
            queryClient.invalidateQueries({ queryKey: queryKeys.timelines() }),
          ]));
      void queryClient.invalidateQueries({ queryKey: queryKeys.tags() });
      if (sealed && entry) {
        clearDraft();
        setSealOpen(false);
        announceSaved(entry, sealed);
        onDone();
        return;
      }
      if (sealError && savedEntry) {
        // Saved, not sealed: the next Save or Seal it replaces this entry instead of making another (D-12).
        setSaved({ id: savedEntry.id, version: savedEntry.version });
        setKept({ soundtrack: savedEntry.soundtrack, replyToEntryId: savedEntry.replyToEntryId });
        setSealError(`Saved, but ${lowerFirst(sealProblem(sealError))}`);
        return;
      }
      if (failed === 0 && entry) {
        clearDraft();
        announceSaved(entry);
        onDone();
        return;
      }
      if (entry === null) {
        // Nothing but photos, and none uploaded: the empty entry was erased (D-23), so the next try starts anew.
        setSaved(null);
        setClientId(crypto.randomUUID());
        setMessage(
          'Nothing was saved, because the photos didn’t upload. Try again, or remove them.',
        );
        return;
      }
      setSaved({ id: entry.id, version: entry.version });
      setKept({ soundtrack: entry.soundtrack, replyToEntryId: entry.replyToEntryId });
      setSealOpen(false);
      setMessage(
        (failed === 1
          ? 'Saved, but 1 photo didn’t upload. Open it to try again or remove it.'
          : `Saved, but ${failed} photos didn’t upload. Open them to try again or remove them.`) +
          // Seal last (D-10): a Loot Drop is never sealed with a photo missing.
          (sealDay ? ' It isn’t sealed yet: seal it once every photo is saved.' : ''),
      );
    },
    onError: (error) => {
      // The save itself failed, so the message belongs to the form, which the seal panel would cover.
      setSealOpen(false);
      if (error instanceof ApiProblem && error.kind === 'entry-stale') {
        // D-23: keep every word typed, and offer the latest version instead of overwriting it.
        setStale(true);
        return;
      }
      if (error instanceof ApiProblem && error.kind === 'validation-failed') {
        setFieldErrors(fieldMessages(error));
      }
      if (error instanceof ApiProblem && error.kind === 'payload-too-large') {
        setFieldErrors({ contentJson: 'This entry is too long to save. Shorten the text.' });
      }
      setMessage(problemMessage(error));
    },
  });

  /**
   * After a save: a backdated entry lands outside the week on screen, so a toast names its week (G-04). A Loot Drop
   * always says when it opens (task 4.8), with the same Go there when it is backdated.
   */
  const announceSaved = (entry: Entry, sealed: IsoDate | null = null) => {
    const thisWeek = weekOf(todayIn(zone));
    const backdated = !isWithin(entry.entryDate, thisWeek.start, thisWeek.end);
    const week = weekOf(entry.entryDate);
    const goThere = backdated
      ? {
          action: {
            label: 'Go there',
            onClick: () => void navigate(`/weeks/${entry.weeklyLogId}`),
          },
        }
      : {};
    if (sealed) {
      toast(`Sealed as a Loot Drop. Opens ${chipDay(sealed, zone)}.`, goThere);
      return;
    }
    if (!backdated) return;
    toast(`Saved in the week of ${formatWeekRange(week.start, week.end)}`, goThere);
  };

  const loadLatest = useMutation({
    mutationFn: () =>
      queryClient.fetchQuery({
        queryKey: queryKeys.entry(saved?.id ?? ''),
        queryFn: ({ signal }) => getEntry(saved?.id ?? '', signal),
        staleTime: 0,
      }),
    onSuccess: (latest) => {
      if (latest.isLocked) {
        // Sealed somewhere else: nothing of it can be shown or changed (D-10).
        setStale(false);
        setMessage('This entry is now a sealed Loot Drop, so it can’t be changed.');
        return;
      }
      setSaved({ id: latest.id, version: latest.version });
      setKept({ soundtrack: latest.soundtrack, replyToEntryId: latest.replyToEntryId });
      setEntryDate(latest.entryDate);
      setTemplateId(latest.templateId);
      setTitle(latest.title ?? '');
      setTagIds(latest.tags.map((t) => t.id));
      const doc = (latest.contentJson as JSONContent | null) ?? { type: 'doc', content: [] };
      editor.commands.setContent(doc);
      setContent(editor.getJSON());
      setRemovedMediaIds([]);
      setItems((list) => [
        ...latest.media.map(uploadedItem),
        // Files that never reached the server stay, so they can still be sent.
        ...list.filter((i) => i.status !== 'uploaded'),
      ]);
      setStale(false);
    },
    onError: (error) => setMessage(problemMessage(error)),
  });

  const chooseTemplate = (template: { id: string; subheadings: string[] }) => {
    editor.commands.setContent(insertTemplateHeadings(editor.getJSON(), template.subheadings));
    setContent(editor.getJSON());
    setTemplateId(template.id);
    // Prompts from an earlier choice stay in the list, so they are tidied away on save too.
    setPrompts((list) => [...list, ...template.subheadings.filter((p) => !list.includes(p))]);
  };

  const heading = kindOfDraft === 'page' ? 'Diary page' : 'New entry';
  const failedCount = items.filter((i) => i.status === 'failed').length;

  const writing = (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        <DateChip
          day={entryDate}
          zone={zone}
          onChange={setEntryDate}
          error={fieldErrors.entryDate}
        />
        <TemplateChip chosen={templateId} onChoose={chooseTemplate} />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor={titleFieldId} className="sr-only">
          Title (optional)
        </label>
        <input
          id={titleFieldId}
          value={title}
          maxLength={150}
          placeholder="Title"
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={fieldErrors.title ? true : undefined}
          className="min-h-11 bg-page font-display text-title text-ink placeholder:text-ink-2"
        />
        {fieldErrors.title && <p className="text-small text-danger">{fieldErrors.title}</p>}
      </div>
      <EntryEditor editor={editor} error={fieldErrors.contentJson} />
    </div>
  );

  // Seal it saves the entry, then seals it (D-10: seal last). Nothing to save means nothing to seal (D-23), and a
  // save under way (uploads included) keeps it unavailable (F-06).
  const sealForm = {
    zone,
    earliest: addDays(todayIn(zone), 1),
    latest: latestSealDay(zone),
    busy: save.isPending,
    disabled: !canSave,
    error: sealError,
    onSeal: (day: IsoDate) => save.mutate(day),
  };

  const extras = (
    <div className="flex flex-col gap-4">
      <MediaTray
        items={items}
        addRef={addRef}
        onAddFiles={(files) => void addFiles(files)}
        onReorder={setItems}
        onRemove={removeItem}
        onTogglePage={togglePage}
        onRetry={() => save.mutate(null)}
      />
      {fileMessages.length > 0 && (
        <ul role="alert" className="flex flex-col gap-1 text-small text-danger">
          {fileMessages.map((m) => (
            <li key={m}>{m}</li>
          ))}
        </ul>
      )}
      <VoiceRecorder
        recorder={{
          ...recorder,
          stop: () => {
            recorder.stop();
            refocusVoice();
          },
          discard: () => {
            recorder.discard();
            refocusVoice();
          },
        }}
        onClose={() => {
          recorder.discard();
          refocusVoice();
        }}
      />
      {layout === 'dialog' && recordable && !recording && (
        <Button className="self-start" disabled={voiceBusy} onClick={startVoice}>
          <Mic size={20} aria-hidden />
          Record a voice note
        </Button>
      )}
      <TagPicker
        selected={tagIds}
        onChange={setTagIds}
        open={tagsOpen}
        onOpenChange={setTagsOpen}
        error={fieldErrors.tagIds}
      />
      {/* Laptop: the Seal as Loot Drop box on the first screen, in the right column (DESIGN.md §4 Write, D-41). Save
          stays the one primary button, so Seal it is secondary here. */}
      {layout === 'dialog' && (
        <section
          aria-labelledby={sealTitleId}
          className="flex flex-col gap-4 rounded-lg bg-surface p-4"
        >
          <h3 id={sealTitleId} className="font-display text-title">
            Seal as Loot Drop
          </h3>
          <p className="text-small text-ink-2">
            Nobody, including you, can open it until the day you pick. You can make the wait longer
            later, never shorter.
          </p>
          <SealForm {...sealForm} primary={false} />
        </section>
      )}
    </div>
  );

  return (
    <form
      className="flex min-h-0 flex-1 flex-col"
      onSubmit={(event) => {
        event.preventDefault();
        if (canSave && !save.isPending) save.mutate(null);
      }}
    >
      {/* The 56 px bar: Cancel, the heading over "Draft saved 7:41 pm", and the one primary button, Save. */}
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
            {heading}
          </h2>
          <p className="text-meta text-ink-2" aria-live="polite">
            {save.isPending
              ? 'Saving…'
              : draftSavedAt && dirty
                ? `Draft saved ${formatClock(draftSavedAt, zone)}`
                : ''}
          </p>
        </div>
        <Button
          type="submit"
          variant="primary"
          className="justify-self-end"
          disabled={!canSave || save.isPending}
          aria-disabled={!canSave || save.isPending}
        >
          {failedCount > 0 && saved ? 'Try again' : 'Save'}
        </Button>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 py-4 lg:px-8 lg:py-6">
        {stale && (
          <div role="alert" className="flex flex-col items-start gap-2">
            <p className="text-body">
              This entry changed somewhere else. Everything you typed is still here.
            </p>
            <Button onClick={() => loadLatest.mutate()} disabled={loadLatest.isPending}>
              Load the latest version
            </Button>
          </div>
        )}
        {message && !stale && (
          <p role="alert" className="text-body">
            {message}
          </p>
        )}
        {layout === 'dialog' ? (
          <div className="grid grid-cols-[1fr_20rem] gap-8">
            {writing}
            {extras}
          </div>
        ) : (
          <>
            {writing}
            {extras}
          </>
        )}
      </div>

      {/* The seal panel opens from the bottom over the sheet (task 4.8). */}
      {layout === 'sheet' && (
        <SealPanel
          open={sealOpen}
          onOpenChange={setSealOpen}
          title="Seal as Loot Drop"
          returnFocus={sealTrigger}
          primary
          {...sealForm}
        />
      )}

      {/* The add bar (DESIGN.md §4 Write): fixed at the foot of the phone sheet. Voice (4.7) shows when this browser can
          record; Song (4.15) joins when its task builds it. */}
      {layout === 'sheet' && (
        <div className="grid h-16 shrink-0 grid-cols-5 border-t border-line">
          <button
            type="button"
            onClick={() => addRef.current?.click()}
            className="flex flex-col items-center justify-center gap-1 text-meta text-ink-2 focus-visible:-outline-offset-2"
          >
            <Image size={20} aria-hidden className="text-ink" />
            Photo
          </button>
          {recordable && (
            <button
              type="button"
              onClick={startVoice}
              disabled={voiceBusy}
              className="flex flex-col items-center justify-center gap-1 text-meta text-ink-2 focus-visible:-outline-offset-2 disabled:opacity-60"
            >
              <Mic size={20} aria-hidden className="text-ink" />
              Voice
            </button>
          )}
          <button
            type="button"
            onClick={() => setTagsOpen(true)}
            className="flex flex-col items-center justify-center gap-1 text-meta text-ink-2 focus-visible:-outline-offset-2"
          >
            <Tag size={20} aria-hidden className="text-ink" />
            Tag
          </button>
          {/* Seal, in the accent with the star mark, so sealing is on the first screen (DESIGN.md §4 Write). It sits
              in the fifth column whatever is missing before it; Song (4.15) takes the fourth. */}
          <button
            type="button"
            ref={sealTrigger}
            onClick={() => setSealOpen(true)}
            disabled={!canSave || save.isPending}
            className="col-start-5 flex flex-col items-center justify-center gap-1 text-meta text-accent focus-visible:-outline-offset-2 disabled:text-ink-2"
          >
            <StarMark size={20} />
            Seal
          </button>
        </div>
      )}
    </form>
  );
}

/** The API's field names in a 400, folded onto capture's four fields ("tagIds[2]" is the tags). */
function fieldMessages(problem: ApiProblem): FieldMessages {
  const out: FieldMessages = {};
  for (const [field, text] of Object.entries(problem.fieldErrors)) {
    const name = field.replace(/\[\d+\]$/, '');
    if (name === 'title') out.title = sentence('title', text);
    else if (name === 'entryDate') out.entryDate = sentence('entryDate', text);
    else if (name === 'contentJson') out.contentJson = 'Something in the text can’t be saved.';
    else if (name === 'tagIds') out.tagIds = 'An entry holds 20 tags at most.';
  }
  return out;
}

// The server's messages are written for developers (problem.ts); these are the user's words for the ones capture can
// meet. The server never repeats what was sent, so nothing typed comes back through here.
function sentence(field: 'title' | 'entryDate', serverText: string): string {
  if (field === 'title') return 'A title is at most 150 characters.';
  if (serverText.includes('after today')) return 'The memory date can’t be after today.';
  if (serverText.includes('1900')) return 'The memory date can’t be before 1900.';
  return 'Check the memory date.';
}
