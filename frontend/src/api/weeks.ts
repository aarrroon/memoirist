import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Week } from './feed';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';

// One week (task 4.10, D-25, F-15): GET /weekly-logs/{id} is the whole week with its Eras and its entries, sealed
// ones already redacted by the server's EntryMapper (D-10). Renaming, closing and reopening (F-12) answer with the
// whole week too.

export const weekQuery = (weekId: string) =>
  queryOptions({
    queryKey: queryKeys.week(weekId),
    queryFn: async ({ signal }) =>
      unwrap(
        await api.GET('/api/v1/weekly-logs/{logId}', {
          params: { path: { logId: weekId } },
          signal,
        }),
      ),
  });

export function useWeek(weekId: string) {
  return useQuery(weekQuery(weekId));
}

/**
 * After a change the server's answer is the week, so it goes straight into the cache; the feed and the Eras show the
 * week's title, so they refetch (frontend.md: invalidate, don't patch other caches by hand).
 */
function useAfterWeekChange() {
  const queryClient = useQueryClient();
  return (week: Week) => {
    queryClient.setQueryData(queryKeys.week(week.id), week);
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.feed() }),
      queryClient.invalidateQueries({ queryKey: queryKeys.eras() }),
    ]);
  };
}

/** What Edit week changes (F-12): the name and, since task 4.15, the soundtrack (D-35). */
export interface WeekFields {
  title: string;
  /** A Spotify URI, or null for none. */
  soundtrack: string | null;
}

export function useEditWeek(week: Week) {
  const after = useAfterWeekChange();
  return useMutation({
    // PUT is a full replace (D-26): both fields go every time, or the one left out is cleared (task 3.14). A blank
    // title clears the name, and the week is called by its dates again. The generated request type has no null (the
    // record's fields lack @Schema(nullable = true)), so "none" is a field left out, which this full replace reads as
    // null (API_CONTRACT, Weekly logs).
    mutationFn: async ({ title, soundtrack }: WeekFields) => {
      const name = title.trim();
      return unwrap(
        await api.PUT('/api/v1/weekly-logs/{logId}', {
          params: { path: { logId: week.id } },
          body: {
            ...(name ? { title: name } : {}),
            ...(soundtrack ? { soundtrack } : {}),
          },
        }),
      );
    },
    onSuccess: after,
  });
}

/** Close a week, or reopen a closed one (F-12). Both are safe to repeat; in Phase 1 closing only marks the week. */
export function useSetWeekClosed(weekId: string) {
  const after = useAfterWeekChange();
  return useMutation({
    mutationFn: async (closed: boolean) => {
      const params = { params: { path: { logId: weekId } } };
      return unwrap(
        closed
          ? await api.POST('/api/v1/weekly-logs/{logId}/close', params)
          : await api.POST('/api/v1/weekly-logs/{logId}/reopen', params),
      );
    },
    onSuccess: after,
  });
}
