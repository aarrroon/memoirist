// The query-key factory (.claude/rules/frontend.md, task 4.2). Every cache key is built here, so a mutation
// can invalidate a whole family with one prefix, and two hooks can't name the same data differently.
// Later tasks add their families (feed, weeks, entries, eras, tags, templates) beside `me`.

// TODO(human) PRACTICE: Write the key factory. `queryKeys.all` is the root every key starts with;
// `queryKeys.me()` is the key for GET /users/me. Keys must be readonly tuples, so TypeScript knows each
// element and `invalidateQueries({ queryKey: queryKeys.all })` matches every key below it.
// Hint: TanStack Query matches keys by prefix, element by element. `as const` turns ['memoirist', 'me'] from
// string[] into readonly ['memoirist', 'me']; spreading `all` into each key keeps the hierarchy in one place.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
const all = ['memoirist'] as const;

export const queryKeys = {
  all,
  me: () => [...all, 'me'] as const,
  // Task 4.4. One key for every page of the feed: an infinite query keeps its pages under a single key, so a
  // save (task 4.6) invalidates the feed with this one prefix and every loaded page refetches in order.
  feed: () => [...all, 'feed'] as const,
  // Task 4.6, capture. Tags and templates are short lists read whole (GET /tags, GET /templates); creating a tag
  // inline invalidates `tags`. One entry is read back when a save meets 409 entry-stale (D-23).
  tags: () => [...all, 'tags'] as const,
  templates: () => [...all, 'templates'] as const,
  entry: (id: string) => [...all, 'entries', id] as const,
  // Task 4.11: every single entry at once, for a change that shows on all of them (a tag renamed or deleted).
  entries: () => [...all, 'entries'] as const,
  // Task 4.8. The Opened strip (GET /entries/opened, D-10). Not under 'entries', so a family invalidation of one
  // entry never refetches it by accident, and it can be invalidated on its own when a Loot Drop is dismissed.
  opened: () => [...all, 'opened'] as const,
  // Task 4.14. The On this day strip (GET /entries/on-this-day, D-32). Not under 'entries' either, for the same
  // reason. Every entry write that changes a date, a seal or Recently deleted invalidates it with the feed.
  onThisDay: () => [...all, 'on-this-day'] as const,
  // Task 4.9. GET /eras is the list; one Era (GET /eras/{id}) sits under it, so invalidating `eras()` after a
  // create, edit or delete refetches the list and every Era page with one prefix.
  eras: () => [...all, 'eras'] as const,
  era: (id: string) => [...all, 'eras', id] as const,
  // Task 4.10. One week's summary (GET /weekly-logs/{id}) sits under `weeks`. A rename or a close invalidates the
  // week, the feed (its line shows the title) and the Eras (an Era page lists the week by name). Every entry write
  // that invalidates the feed (save, seal, delete, an unlock) invalidates `weeks()` too, or a cached week page would
  // show a sealed entry's title or a deleted entry for up to the 30-minute staleTime (D-10, D-30).
  weeks: () => [...all, 'weeks'] as const,
  week: (id: string) => [...all, 'weeks', id] as const,
  // Task 4.11. The Timeline (GET /entries): one infinite query per filter, all under `timelines`, so a tag rename or
  // any entry write refetches every filtered copy with one prefix. Not under 'entries': invalidating one entry must
  // not refetch a 50-row list. `null` is All entries.
  timelines: () => [...all, 'timeline'] as const,
  timeline: (tagId: string | null) => [...all, 'timeline', { tagId }] as const,
  // Task 4.12. Recently deleted (GET /entries/deleted, D-30): the screen's list and the count on You, both under
  // `deleted`, so a delete, a restore or an erase refetches both with one prefix. Not under 'entries' either.
  deleted: () => [...all, 'deleted'] as const,
  deletedList: () => [...all, 'deleted', 'list'] as const,
  deletedCount: () => [...all, 'deleted', 'count'] as const,
  // Task 4.15. One music search (POST /music/search, D-35) per set of words. The words stay in memory only: the
  // query cache is never persisted, and the request carries them in its body, never in a URL (API_CONTRACT §Music).
  musicSearch: (words: string) => [...all, 'music-search', words] as const,
};
// └── END ANSWER ▲ ──
