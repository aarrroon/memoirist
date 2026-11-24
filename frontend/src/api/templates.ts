import { queryOptions, useQuery } from '@tanstack/react-query';
import { api } from './client';
import { unwrap } from './problem';
import { queryKeys } from './queryKeys';
import type { components } from './schema';

export type Template = components['schemas']['TemplateResponse'];

// The user's templates, by name (D-06). Capture's template chip reads them (task 4.6); the Templates screen and
// the starter gallery are task 4.9.
export const templatesQuery = () =>
  queryOptions({
    queryKey: queryKeys.templates(),
    queryFn: async ({ signal }) => unwrap(await api.GET('/api/v1/templates', { signal })),
  });

export function useTemplates() {
  return useQuery(templatesQuery());
}
