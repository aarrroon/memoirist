import {
  closestCenter,
  DndContext,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import { arrayMove, rectSortingStrategy, SortableContext, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { AudioLines, CircleAlert, Plus } from 'lucide-react';
import { useRef, useState } from 'react';
import { Popover, PopoverContent, PopoverTrigger } from '../../components/ui/popover';
import { ACCEPT, MAX_FILES } from '../../lib/files';
import type { TrayItem } from './saveEntry';

// The photos and files in capture (DESIGN.md §4 Write, task 4.6): 72 px thumbnails with `sm` corners and a dashed Add
// tile. Order is the order they'll show in the swipe. A mouse drags them, and a finger after a long press (dnd-kit,
// D-13); every thumbnail is also a button whose menu has Move earlier and Move later, so reordering works from the
// keyboard and a screen reader without a drag. The same menu marks a photo as a diary page before it uploads (D-20),
// and offers Try again and Remove for one that failed (D-23, G-03).

export function MediaTray({
  items,
  onAddFiles,
  onReorder,
  onRemove,
  onTogglePage,
  onRetry,
  addRef,
}: {
  items: TrayItem[];
  onAddFiles: (files: File[]) => void;
  onReorder: (items: TrayItem[]) => void;
  onRemove: (key: string) => void;
  onTogglePage: (key: string) => void;
  onRetry: () => void;
  addRef: React.RefObject<HTMLInputElement | null>;
}) {
  // Long press on touch, so a finger that starts on a photo can still scroll the sheet.
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 8 } }),
  );
  const draggedRef = useRef(false);

  const move = (from: number, to: number) => onReorder(arrayMove(items, from, to));

  const onDragEnd = ({ active, over }: DragEndEvent) => {
    draggedRef.current = true;
    if (!over || active.id === over.id) return;
    const from = items.findIndex((i) => i.key === active.id);
    const to = items.findIndex((i) => i.key === over.id);
    if (from >= 0 && to >= 0) move(from, to);
  };

  return (
    <div className="flex flex-col gap-2">
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={items.map((i) => i.key)} strategy={rectSortingStrategy}>
          <ul aria-label="Photos" className="flex flex-wrap gap-4">
            {items.map((item, index) => (
              <Thumbnail
                key={item.key}
                item={item}
                index={index}
                count={items.length}
                draggedRef={draggedRef}
                onMove={(to) => move(index, to)}
                onRemove={() => onRemove(item.key)}
                onTogglePage={() => onTogglePage(item.key)}
                onRetry={onRetry}
              />
            ))}
            {items.length < MAX_FILES && (
              <li>
                <button
                  type="button"
                  onClick={() => addRef.current?.click()}
                  className="flex size-18 flex-col items-center justify-center gap-1 rounded-sm border border-dashed border-control text-meta text-ink-2"
                >
                  <Plus size={20} aria-hidden className="text-ink" />
                  Add
                </button>
              </li>
            )}
          </ul>
        </SortableContext>
      </DndContext>
      <input
        ref={addRef}
        type="file"
        multiple
        accept={ACCEPT}
        aria-label="Add photos"
        className="sr-only"
        tabIndex={-1}
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          // Cleared so picking the same file again after removing it still fires a change.
          event.target.value = '';
          if (files.length > 0) onAddFiles(files);
        }}
      />
    </div>
  );
}

function Thumbnail({
  item,
  index,
  count,
  draggedRef,
  onMove,
  onRemove,
  onTogglePage,
  onRetry,
}: {
  item: TrayItem;
  index: number;
  count: number;
  draggedRef: React.RefObject<boolean>;
  onMove: (to: number) => void;
  onRemove: () => void;
  onTogglePage: () => void;
  onRetry: () => void;
}) {
  const [open, setOpen] = useState(false);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.key,
  });
  const isImage = item.mediaType.startsWith('image/');
  const page = item.purpose === 'PAGE_SCAN';
  const kind = page ? 'Diary page' : isImage ? 'Photo' : 'Voice note';
  const state =
    item.status === 'failed'
      ? 'Didn’t upload'
      : item.status === 'uploading'
        ? 'Uploading'
        : page
          ? 'Diary page'
          : null;
  const row =
    'flex min-h-11 items-center rounded-sm px-3 text-left text-body hover:bg-raised focus-visible:-outline-offset-2 disabled:text-ink-2';
  const act = (fn: () => void) => () => {
    setOpen(false);
    fn();
  };

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={isDragging ? 'relative z-10 flex w-18 flex-col gap-1' : 'flex w-18 flex-col gap-1'}
    >
      <Popover
        open={open}
        onOpenChange={(next) => {
          // A drag ends with a click on the same thumbnail; that click isn't a request for the menu.
          if (next && draggedRef.current) {
            draggedRef.current = false;
            return;
          }
          setOpen(next);
        }}
      >
        <PopoverTrigger asChild>
          <button
            type="button"
            {...attributes}
            {...listeners}
            // dnd-kit's attributes make it role="button" with its own description; it's already a button, and its
            // label below says everything.
            aria-roledescription={undefined}
            aria-describedby={undefined}
            aria-label={`${kind} ${index + 1} of ${count}${item.status === 'failed' ? ', didn’t upload' : ''}${item.status === 'uploading' ? ', uploading' : ''}. Options`}
            // A new press starts fresh; dnd-kit's own mouse and touch listeners (spread above) still run.
            onPointerDown={() => {
              draggedRef.current = false;
            }}
            className="relative flex size-18 touch-manipulation items-center justify-center overflow-hidden rounded-sm bg-surface"
          >
            {isImage ? (
              <img
                src={item.previewUrl}
                alt=""
                width={72}
                height={72}
                loading="lazy"
                decoding="async"
                className={
                  item.status === 'uploading'
                    ? 'size-full object-cover opacity-60'
                    : 'size-full object-cover'
                }
              />
            ) : (
              <AudioLines size={20} aria-hidden className="text-ink-2" />
            )}
            {item.status === 'failed' && (
              <span className="absolute inset-0 flex items-center justify-center bg-scrim text-on-band">
                <CircleAlert size={20} aria-hidden />
              </span>
            )}
          </button>
        </PopoverTrigger>
        <PopoverContent aria-label={`${kind} ${index + 1}`} className="w-56 gap-1">
          {item.status === 'failed' && (
            <button type="button" className={row} onClick={act(onRetry)}>
              Try again
            </button>
          )}
          {isImage && item.status !== 'uploaded' && item.status !== 'uploading' && (
            <button type="button" className={row} onClick={act(onTogglePage)}>
              {page ? 'Mark as a photo' : 'Mark as a diary page'}
            </button>
          )}
          <button
            type="button"
            className={row}
            disabled={index === 0}
            onClick={act(() => onMove(index - 1))}
          >
            Move earlier
          </button>
          <button
            type="button"
            className={row}
            disabled={index === count - 1}
            onClick={act(() => onMove(index + 1))}
          >
            Move later
          </button>
          <button
            type="button"
            className={row}
            disabled={item.status === 'uploading'}
            onClick={act(onRemove)}
          >
            Remove
          </button>
        </PopoverContent>
      </Popover>
      {state && (
        <span
          className={item.status === 'failed' ? 'text-meta text-danger' : 'text-meta text-ink-2'}
        >
          {state}
        </span>
      )}
    </li>
  );
}
