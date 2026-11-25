import { useCallback, useEffect, useMemo, useRef, type ReactNode } from 'react';
import { useFeed, type Week } from '../../api/feed';
import { problemMessage } from '../../api/problem';
import { useOpened } from '../../api/lootDrops';
import { useMe } from '../../api/users';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/button';
import { useNow } from '../../lib/useNow';
import { OpenedRow } from '../lootdrop/OpenedRow';
import { useUnlockWatch } from '../lootdrop/useUnlockWatch';
import { FeedEmpty, FeedError, FeedLoading } from './FeedStates';
import { groupFeed, type FeedSection } from './grouping';
import { WeekSection } from './WeekSection';

// The Journal, at / (task 4.4, DESIGN.md §4 Feed and §6, F-08): the user's weeks, newest first, filed under their
// Eras (D-22), loaded a page at a time as the reader nears the end. The moon line above names the day, so the
// heading is for screen readers only. The Opened row (task 4.8) sits at the top while a Loot Drop has just opened;
// On this day joins in task 4.14, below the first entry while the Opened row shows (D-41).
export function FeedPage() {
  const me = useMe();
  const feed = useFeed();
  const opened = useOpened();
  const now = useNow();

  const weeks = useMemo(() => feed.data?.pages.flatMap((page) => page.items) ?? [], [feed.data]);
  const groups = useMemo(() => groupFeed(weeks), [weeks]);
  const eagerPhotoId = useMemo(() => firstPhotoId(weeks), [weeks]);
  const sealed = useMemo(
    () => weeks.flatMap((week) => week.entries.filter((entry) => entry.isLocked)),
    [weeks],
  );
  // A sealed card on screen turns into its entry, and the Opened row appears, when it opens (F-07).
  useUnlockWatch(sealed, now);
  const { fetchNextPage } = feed;
  const loadMore = useCallback(() => void fetchNextPage(), [fetchNextPage]);

  let body;
  // The Opened row is waited for too (a failure counts as nothing to show), so it never pushes the feed down after
  // the first paint: layout shift stays at 0.1 or less (design.md §6).
  if (!feed.data || !me.data || opened.isPending) {
    // Every date needs the user's timezone (non-negotiable 2), so the feed waits for both. The error screen is
    // only for when nothing could be shown: a failed next page, or a failed refetch in the background, keeps the
    // weeks already on screen (FeedFoot).
    body =
      feed.isError || me.isError ? (
        <FeedError
          error={feed.error ?? me.error ?? new Error()}
          onRetry={() => {
            if (me.isError) void me.refetch();
            if (feed.isError) void feed.refetch();
          }}
        />
      ) : (
        <FeedLoading />
      );
  } else if (weeks.length === 0) {
    body = <FeedEmpty />;
  } else {
    body = (
      <div className="flex flex-col gap-8">
        {opened.data && opened.data.length > 0 && <OpenedRow rows={opened.data} />}
        {groups.map((group) => (
          <section
            key={group.key}
            aria-label={group.era ? group.era.title : 'Weeks in no Era'}
            className="flex flex-col gap-8"
          >
            {group.sections.map((section) => (
              <Section
                key={section.weeks[0]?.week.id}
                section={section}
                outer={group.era?.title ?? null}
              >
                {section.weeks.map((placed) => (
                  <WeekSection
                    key={placed.week.id}
                    placed={placed}
                    eagerPhotoId={eagerPhotoId}
                    zone={me.data.timezone}
                    now={now}
                  />
                ))}
              </Section>
            ))}
          </section>
        ))}
        <FeedFoot
          hasNextPage={feed.hasNextPage}
          isFetchingNextPage={feed.isFetchingNextPage}
          failed={feed.isFetchNextPageError}
          error={feed.error}
          fetchNextPage={loadMore}
        />
      </div>
    );
  }

  return (
    <Page title="Journal" hideTitle>
      {body}
    </Page>
  );
}

/** Weeks nested in a smaller Era (a trip inside "Uni Days", D-22) are their own section, one level deep. */
function Section({
  section,
  outer,
  children,
}: {
  section: FeedSection;
  outer: string | null;
  children: ReactNode;
}) {
  if (!section.nested) return <>{children}</>;
  return (
    <section
      aria-label={outer ? `${section.nested.title}, inside ${outer}` : section.nested.title}
      className="flex flex-col gap-8"
    >
      {children}
    </section>
  );
}

/** The first photo in the feed: the only one that loads at once (design.md §6). */
function firstPhotoId(weeks: readonly Week[]): string | null {
  for (const week of weeks) {
    for (const entry of week.entries) {
      const photo = entry.media.find((media) => media.mediaType.startsWith('image/'));
      if (photo) return photo.id;
    }
  }
  return null;
}

/**
 * The foot of the feed (F-08): it asks for the next page when it comes within a screen of view, so scrolling
 * rarely meets the end; a keyboard or a browser without IntersectionObserver uses the button. A failed page keeps
 * the weeks already loaded and offers *Try again* here.
 */
function FeedFoot({
  hasNextPage,
  isFetchingNextPage,
  failed,
  error,
  fetchNextPage,
}: {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  failed: boolean;
  error: Error | null;
  fetchNextPage: () => void;
}) {
  const foot = useRef<HTMLDivElement>(null);
  const wanted = hasNextPage && !isFetchingNextPage && !failed;

  useEffect(() => {
    const node = foot.current;
    if (!wanted || !node || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      (records) => {
        if (records.some((record) => record.isIntersecting)) fetchNextPage();
      },
      { rootMargin: '100% 0px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [wanted, fetchNextPage]);

  if (failed) {
    return (
      <div role="alert" className="flex flex-col items-start gap-4">
        <p className="text-body text-ink-2">{problemMessage(error)}</p>
        <Button variant="secondary" onClick={fetchNextPage}>
          Try again
        </Button>
      </div>
    );
  }
  if (isFetchingNextPage) {
    return (
      <p role="status" className="flex min-h-11 items-center text-small text-ink-2">
        Loading earlier weeks…
      </p>
    );
  }
  if (!hasNextPage) return null;
  return (
    <div ref={foot}>
      <Button variant="quiet" onClick={fetchNextPage}>
        Show earlier weeks
      </Button>
    </div>
  );
}
