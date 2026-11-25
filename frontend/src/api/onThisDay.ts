import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';

// On this day (task 4.14, D-32): GET /entries/on-this-day is a plain array of summaries from today's day and month
// in earlier years, newest year first, at most 20. "Today" is the user's today on the server. Sealed entries,
// entries in Recently deleted and entries whose date is a guess (dateUnknown) never arrive, so the client draws
// exactly what comes back and filters nothing.
export const onThisDayQuery = () =>
  queryOptions({
    queryKey: queryKeys.onThisDay(),
    queryFn: async ({ signal }) => unwrap(await api.GET('/api/v1/entries/on-this-day', { signal })),
  });

export function useOnThisDay() {
  return useQuery(onThisDayQuery());
}
