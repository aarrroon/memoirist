import useEmblaCarousel from 'embla-carousel-react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent,
} from 'react';
import type { Media } from '../api/feed';
import { cn } from '../lib/cn';
import { frameRatio } from '../lib/photoFrame';

// The swipe (task 4.5, DESIGN.md §4 Swipe, D-41): an entry's photos in one row that scrolls sideways and snaps to
// each photo, edge to edge on a phone. Embla (D-13) does the dragging and snapping; this component adds the frame,
// the dots, the laptop arrows, the keyboard and the loading rules.
//
// - The frame takes the first photo's shape, kept between 4:5 and 1.91:1 (frameRatio), and the others are
//   cropped to it. Its space is reserved from the stored size before any photo arrives (D-17, layout shift <= 0.1).
// - Only the first photo on the screen loads at once, with high priority; every other is lazy. A photo after the
//   first gets its file only once the reader starts to swipe, then always one ahead of the one shown, so a feed
//   of busy days asks for one photo per entry (design.md §6, Q29).
// - The dots are decoration (aria-hidden). The swipe is a group labelled "Photos, 4 photos" that takes focus, so
//   the arrow keys move it, and each photo has its own alt text.

/** "Photo 2 of 4", or "Diary page" for a photo of a paper page (D-20). */
function altText(photo: Media, index: number, count: number): string {
  const kind = photo.purpose === 'PAGE_SCAN' ? 'Diary page' : 'Photo';
  return count > 1 ? `${kind} ${index + 1} of ${count}` : kind;
}

/** Reduced motion makes the arrows and the keys jump instead of glide (DESIGN.md §7). Read at the moment of the
 *  press, so a change of the setting applies without a reload. */
const prefersReducedMotion = () =>
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export function Swipe({
  photos,
  eagerPhotoId,
}: {
  photos: readonly Media[];
  eagerPhotoId: string | null;
}) {
  const count = photos.length;
  const [viewport, embla] = useEmblaCarousel({
    align: 'start',
    containScroll: 'trimSnaps',
    active: count > 1,
  });
  const [selected, setSelected] = useState(0);
  // The highest photo that may load its file. 0 until the reader shows interest in the swipe (design.md §6).
  const [loadUpTo, setLoadUpTo] = useState(0);
  const [canPrev, setCanPrev] = useState(false);
  const [canNext, setCanNext] = useState(count > 1);

  const warmUp = useCallback(
    (index: number) => setLoadUpTo((upTo) => Math.max(upTo, Math.min(count - 1, index + 1))),
    [count],
  );

  useEffect(() => {
    if (!embla) return;
    const sync = () => {
      const index = embla.selectedScrollSnap();
      setSelected(index);
      setCanPrev(embla.canScrollPrev());
      setCanNext(embla.canScrollNext());
      warmUp(index);
    };
    const onPointerDown = () => warmUp(embla.selectedScrollSnap());
    embla.on('select', sync);
    embla.on('reInit', sync);
    embla.on('pointerDown', onPointerDown);
    return () => {
      embla.off('select', sync);
      embla.off('reInit', sync);
      embla.off('pointerDown', onPointerDown);
    };
  }, [embla, warmUp]);

  const go = useCallback(
    (index: number) => {
      const target = Math.max(0, Math.min(count - 1, index));
      warmUp(target);
      // Set here as well as from Embla's `select`, so the dots, the alt text and the next press agree at once,
      // even before Embla has measured anything (and in jsdom, which measures nothing).
      setSelected(target);
      embla?.scrollTo(target, prefersReducedMotion());
    },
    [embla, count, warmUp],
  );

  // An arrow disappears at its end of the row. If it had focus, focus would fall to the page's top, so it moves
  // to the swipe itself, where the arrow keys carry on.
  const viewportNode = useRef<HTMLDivElement | null>(null);
  const setViewport = useCallback(
    (node: HTMLDivElement | null) => {
      viewportNode.current = node;
      viewport(node);
    },
    [viewport],
  );
  const press = (index: number) => {
    go(index);
    if (index <= 0 || index >= count - 1) viewportNode.current?.focus();
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowRight') go(selected + 1);
    else if (event.key === 'ArrowLeft') go(selected - 1);
    else if (event.key === 'Home') go(0);
    else if (event.key === 'End') go(count - 1);
    else return;
    event.preventDefault();
  };

  const first = photos[0];
  if (!first) return null;
  const ratio = frameRatio(first);
  const many = count > 1;

  return (
    <div className="flex flex-col gap-2">
      <div className="relative -mx-4 md:mx-0">
        <div
          ref={setViewport}
          // A group the keyboard can reach only when there is something to move (design.md §9).
          {...(many
            ? {
                role: 'group',
                'aria-roledescription': 'carousel',
                'aria-label': `Photos, ${count} photos`,
                tabIndex: 0,
                onKeyDown,
                onFocus: () => warmUp(selected),
              }
            : {})}
          // The ratio is data, not a design value, so it travels as a custom property (D-40: no one-off sizes).
          className="aspect-(--frame) overflow-hidden bg-surface md:rounded-lg"
          style={{ '--frame': String(ratio) } as CSSProperties}
        >
          <div className="flex h-full">
            {photos.map((photo, index) => {
              const eager = photo.id === eagerPhotoId;
              return (
                <div
                  key={photo.id}
                  className="h-full min-w-0 shrink-0 grow-0 basis-full"
                  aria-hidden={many && index !== selected ? true : undefined}
                >
                  {index <= loadUpTo && (
                    <img
                      src={photo.fileUrl}
                      alt={altText(photo, index, count)}
                      width={photo.width ?? undefined}
                      height={photo.height ?? undefined}
                      decoding="async"
                      {...(eager
                        ? { loading: 'eager' as const, fetchPriority: 'high' as const }
                        : { loading: 'lazy' as const })}
                      draggable={false}
                      className="size-full object-cover"
                    />
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {many && (
          // Laptop only: 44 px round arrows inside the edges (DESIGN.md §4 Swipe). A phone swipes.
          <>
            {canPrev && (
              <ArrowButton side="left" label="Previous photo" onClick={() => press(selected - 1)} />
            )}
            {canNext && (
              <ArrowButton side="right" label="Next photo" onClick={() => press(selected + 1)} />
            )}
          </>
        )}
      </div>

      {many && (
        <div aria-hidden className="flex justify-center gap-2">
          {photos.map((photo, index) => (
            <span
              key={photo.id}
              data-current={index === selected || undefined}
              className={cn(
                'size-1.5 rounded-full',
                index === selected ? 'bg-accent' : 'bg-control',
              )}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function ArrowButton({
  side,
  label,
  onClick,
}: {
  side: 'left' | 'right';
  label: string;
  onClick: () => void;
}) {
  const Icon = side === 'left' ? ChevronLeft : ChevronRight;
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={cn(
        'absolute top-1/2 hidden size-11 -translate-y-1/2 items-center justify-center rounded-full bg-page text-ink shadow-float lg:flex',
        side === 'left' ? 'left-4' : 'right-4',
      )}
    >
      <Icon size={20} aria-hidden />
    </button>
  );
}
