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
};
// └── END ANSWER ▲ ──
