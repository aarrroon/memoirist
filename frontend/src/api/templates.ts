import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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

/** What the template form edits: a name and its prompts, in order (D-06). */
export interface TemplateFields {
  name: string;
  subheadings: string[];
}

function templateBody(fields: TemplateFields) {
  return {
    name: fields.name.trim(),
    // An empty prompt row is a row the user didn't fill: it is dropped, never sent as a 400 (API_CONTRACT §Templates).
    subheadings: fields.subheadings.map((s) => s.trim()).filter((s) => s.length > 0),
  };
}

// Task 4.9. Editing or deleting a template never changes an entry (D-06), so only the templates list refetches.
function useInvalidateTemplates() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.templates() });
}

/** Creates a template, or adds a starter's copy from the gallery with the same POST (D-06). */
export function useCreateTemplate() {
  const invalidate = useInvalidateTemplates();
  return useMutation({
    mutationFn: async (fields: TemplateFields) =>
      unwrap(
        await api.POST('/api/v1/templates', {
          body: { id: crypto.randomUUID(), ...templateBody(fields) },
        }),
      ),
    onSuccess: invalidate,
  });
}

export function useUpdateTemplate(templateId: string) {
  const invalidate = useInvalidateTemplates();
  return useMutation({
    // PUT is a full replace: the name and the whole list of prompts go every time.
    mutationFn: async (fields: TemplateFields) =>
      unwrap(
        await api.PUT('/api/v1/templates/{templateId}', {
          params: { path: { templateId } },
          body: templateBody(fields),
        }),
      ),
    onSuccess: invalidate,
  });
}

export function useDeleteTemplate() {
  const invalidate = useInvalidateTemplates();
  return useMutation({
    mutationFn: async (templateId: string) =>
      unwrap(
        await api.DELETE('/api/v1/templates/{templateId}', { params: { path: { templateId } } }),
      ),
    onSuccess: invalidate,
  });
}
