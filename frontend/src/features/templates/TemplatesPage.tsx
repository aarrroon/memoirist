import { ChevronRight, Plus } from 'lucide-react';
import { useState } from 'react';
import { problemMessage } from '../../api/problem';
import { useCreateTemplate, useTemplates, type Template } from '../../api/templates';
import { ListLoading, LoadError } from '../../components/LoadState';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/button';
import { startersToOffer } from './starters';
import { TemplateForm } from './TemplateForm';

// Templates, at /templates (DESIGN.md §6 `#templates`, D-06, task 4.9): the user's own, each opening its form, then
// the starter gallery, where Add makes an editable copy in one tap. A row is a link-like button, not a box: a
// hairline between rows and a chevron (DESIGN.md §4 Controls).
export function TemplatesPage() {
  const templates = useTemplates();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Template | null>(null);
  return (
    <Page
      title="Templates"
      action={
        <Button onClick={() => setCreating(true)}>
          <Plus size={20} aria-hidden />
          New
        </Button>
      }
    >
      {templates.isPending ? (
        <ListLoading label="Opening your templates…" />
      ) : templates.isError ? (
        <LoadError error={templates.error} onRetry={() => void templates.refetch()} />
      ) : (
        <div className="flex flex-col gap-8">
          <section aria-labelledby="templates-yours" className="flex flex-col gap-4">
            <h2 id="templates-yours" className="font-display text-title">
              Yours
            </h2>
            {templates.data.length === 0 ? (
              <p className="text-body text-ink-2">
                A template gives an entry its prompts. Make one, or add one from the gallery below.
              </p>
            ) : (
              <ul className="flex flex-col divide-y divide-line border-y border-line">
                {templates.data.map((template) => (
                  <li key={template.id}>
                    <button
                      type="button"
                      onClick={() => setEditing(template)}
                      className="flex min-h-14 w-full items-center gap-4 py-3 text-left"
                    >
                      <TemplateText name={template.name} prompts={template.subheadings} />
                      <ChevronRight size={20} aria-hidden className="shrink-0 text-ink-2" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <StarterGallery own={templates.data} />
        </div>
      )}
      <TemplateForm open={creating} onOpenChange={setCreating} />
      {editing && (
        <TemplateForm
          template={editing}
          open
          onOpenChange={(open) => {
            if (!open) setEditing(null);
          }}
        />
      )}
    </Page>
  );
}

function TemplateText({ name, prompts }: { name: string; prompts: readonly string[] }) {
  return (
    <span className="flex min-w-0 flex-1 flex-col">
      <span className="text-body font-strong">{name}</span>
      <span className="text-small text-ink-2">
        {prompts.length > 0 ? prompts.join(' · ') : 'No prompts'}
      </span>
    </span>
  );
}

/** The starters the user hasn't added yet (D-06), each with Add. */
function StarterGallery({ own }: { own: readonly Template[] }) {
  const create = useCreateTemplate();
  const [added, setAdded] = useState<string | null>(null);
  const offer = startersToOffer(own);
  return (
    <section aria-labelledby="templates-starters" className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <h2 id="templates-starters" className="font-display text-title">
          Starter gallery
        </h2>
        <p className="text-body text-ink-2">Add a copy, then change it however you like.</p>
      </div>
      <p role="status" className="text-small text-ink-2 empty:hidden">
        {added ? `${added} is in your templates now.` : ''}
      </p>
      {create.isError && (
        <p role="alert" className="text-small text-danger">
          {problemMessage(create.error)}
        </p>
      )}
      {offer.length === 0 ? (
        <p className="text-body text-ink-2">
          You have every starter. Change them however you like.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-line border-y border-line">
          {offer.map((starter) => (
            <li key={starter.name} className="flex min-h-14 items-center gap-4 py-3">
              <TemplateText name={starter.name} prompts={starter.subheadings} />
              <Button
                aria-label={`Add ${starter.name}`}
                disabled={create.isPending}
                onClick={() =>
                  create.mutate(starter, { onSuccess: (template) => setAdded(template.name) })
                }
                className="shrink-0"
              >
                Add
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
