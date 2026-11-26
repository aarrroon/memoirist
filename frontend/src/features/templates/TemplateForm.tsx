import { Plus, X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { ApiProblem, problemMessage } from '../../api/problem';
import {
  useCreateTemplate,
  useDeleteTemplate,
  useUpdateTemplate,
  type Template,
  type TemplateFields,
} from '../../api/templates';
import { Field, FormDialog } from '../../components/FormDialog';
import { Button } from '../../components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '../../components/ui/dialog';

const MAX_PROMPTS = 20;
const NAME_ERROR = 'Give the template a name of at most 100 characters.';
const PROMPT_ERROR = 'Each prompt is at most 200 characters.';

/**
 * New template, or a template of the user's (task 4.9, D-06): a name and up to 20 prompts, in order. Choosing it in
 * Write puts the prompts in as headings, and the ones left empty go when the entry is saved. Editing or deleting a
 * template never changes an entry that used it.
 */
export function TemplateForm({
  template,
  open,
  onOpenChange,
}: {
  template?: Template;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return template ? (
    <EditTemplate template={template} open={open} onOpenChange={onOpenChange} />
  ) : (
    <NewTemplate open={open} onOpenChange={onOpenChange} />
  );
}

function NewTemplate({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const create = useCreateTemplate();
  return (
    <TemplateFieldsDialog
      key={String(open)}
      title="New template"
      open={open}
      onOpenChange={onOpenChange}
      mutation={create}
    />
  );
}

function EditTemplate({
  template,
  open,
  onOpenChange,
}: {
  template: Template;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const update = useUpdateTemplate(template.id);
  const remove = useDeleteTemplate();
  const [asking, setAsking] = useState(false);
  return (
    <>
      <TemplateFieldsDialog
        key={String(open)}
        title="Edit template"
        template={template}
        open={open}
        onOpenChange={onOpenChange}
        mutation={update}
        extra={
          <Button variant="quiet" onClick={() => setAsking(true)}>
            Delete
          </Button>
        }
      />
      <AlertDialog open={asking} onOpenChange={setAsking}>
        <AlertDialogContent>
          <AlertDialogTitle className="font-display text-title">
            Delete this template?
          </AlertDialogTitle>
          <AlertDialogDescription className="text-body">
            Entries you wrote with it keep everything in them.
          </AlertDialogDescription>
          {remove.isError && (
            <p role="alert" className="text-small text-danger">
              {problemMessage(remove.error)}
            </p>
          )}
          <div className="flex justify-end gap-2">
            <AlertDialogCancel asChild>
              <Button variant="quiet">Keep it</Button>
            </AlertDialogCancel>
            <AlertDialogAction asChild>
              <Button
                className="border-danger text-danger"
                disabled={remove.isPending}
                onClick={(event) => {
                  event.preventDefault();
                  remove.mutate(template.id, {
                    onSuccess: () => {
                      setAsking(false);
                      onOpenChange(false);
                    },
                  });
                }}
              >
                Delete template
              </Button>
            </AlertDialogAction>
          </div>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

function TemplateFieldsDialog({
  title,
  template,
  open,
  onOpenChange,
  mutation,
  extra,
}: {
  title: string;
  template?: Template;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mutation: ReturnType<typeof useCreateTemplate>;
  extra?: ReactNode;
}) {
  const [fields, setFields] = useState<TemplateFields>(() => ({
    name: template?.name ?? '',
    // A new template starts with one empty prompt row, so the form shows what a prompt is.
    subheadings: template ? [...template.subheadings] : [''],
  }));
  const [nameError, setNameError] = useState<string>();
  const [promptError, setPromptError] = useState<string>();

  const setPrompt = (index: number, value: string) =>
    setFields((now) => ({
      ...now,
      subheadings: now.subheadings.map((s, i) => (i === index ? value : s)),
    }));
  const removePrompt = (index: number) =>
    setFields((now) => ({ ...now, subheadings: now.subheadings.filter((_, i) => i !== index) }));
  const addPrompt = () => setFields((now) => ({ ...now, subheadings: [...now.subheadings, ''] }));

  const submit = () => {
    const name = fields.name.trim();
    const badName = name.length === 0 || name.length > 100;
    const badPrompt = fields.subheadings.some((s) => s.trim().length > 200);
    setNameError(badName ? NAME_ERROR : undefined);
    setPromptError(badPrompt ? PROMPT_ERROR : undefined);
    if (badName || badPrompt) return;
    mutation.mutate(fields, {
      onSuccess: () => onOpenChange(false),
      onError: (error) => {
        if (!(error instanceof ApiProblem) || !error.fieldErrors) return;
        const keys = Object.keys(error.fieldErrors);
        if (keys.includes('name')) setNameError(NAME_ERROR);
        if (keys.some((k) => k.startsWith('subheadings'))) setPromptError(PROMPT_ERROR);
      },
    });
  };

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      onSubmit={submit}
      pending={mutation.isPending}
      error={mutation.isError && !nameError && !promptError ? problemMessage(mutation.error) : null}
      extra={extra}
    >
      <Field
        label="Name"
        value={fields.name}
        onChange={(name) => setFields((now) => ({ ...now, name }))}
        maxLength={100}
        autoComplete="off"
        error={nameError}
      />
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-small font-strong">Prompts</legend>
        <ol className="flex flex-col gap-2">
          {fields.subheadings.map((prompt, index) => (
            // Prompts have no id of their own; the position is what the server keeps (the order matters).
            <li key={index} className="flex items-center gap-2">
              <input
                aria-label={`Prompt ${index + 1}`}
                value={prompt}
                maxLength={200}
                autoComplete="off"
                onChange={(event) => setPrompt(index, event.target.value)}
                className="min-h-11 w-full rounded-sm border border-control bg-page px-3 text-body text-ink"
              />
              <Button
                variant="quiet"
                aria-label={`Remove prompt ${index + 1}`}
                onClick={() => removePrompt(index)}
                className="size-11 shrink-0 px-0"
              >
                <X size={20} aria-hidden />
              </Button>
            </li>
          ))}
        </ol>
        {promptError && <p className="text-small text-danger">{promptError}</p>}
        {fields.subheadings.length < MAX_PROMPTS && (
          <Button variant="quiet" className="self-start" onClick={addPrompt}>
            <Plus size={20} aria-hidden />
            Add a prompt
          </Button>
        )}
        <p className="text-small text-ink-2">Empty prompts aren’t kept.</p>
      </fieldset>
    </FormDialog>
  );
}
