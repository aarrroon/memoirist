import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import {
  useCreateEra,
  useDeleteEra,
  useUpdateEra,
  type Era,
  type EraDetail,
  type EraFields,
} from '../../api/eras';
import { ApiProblem, problemMessage } from '../../api/problem';
import { CheckRow, Field, FormDialog } from '../../components/FormDialog';
import { Button } from '../../components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '../../components/ui/dialog';
import { EARLIEST_DAY } from '../../lib/time';
import { SoundtrackField } from '../music/SoundtrackField';
import { CoverPicker } from './CoverPicker';

type FieldName = 'title' | 'startDate' | 'endDate' | 'description';
type Errors = Partial<Record<FieldName, string>>;

// The app's own sentences for each field (the server's text is never shown, task 4.6's rule).
const MESSAGES: Record<FieldName, string> = {
  title: 'Give the Era a name of at most 150 characters.',
  startDate: 'Pick the day it started, from 1900 on.',
  endDate: 'The last day can’t be before the first.',
  description: 'A description is at most 5000 characters.',
};

/** What the form can say before it sends anything. The server checks the same again (API_CONTRACT §Eras). */
function checkEra(fields: EraFields): Errors {
  const errors: Errors = {};
  const title = fields.title.trim();
  if (title.length === 0 || title.length > 150) errors.title = MESSAGES.title;
  if (!fields.startDate || fields.startDate < EARLIEST_DAY) errors.startDate = MESSAGES.startDate;
  if (fields.endDate && fields.startDate && fields.endDate < fields.startDate) {
    errors.endDate = MESSAGES.endDate;
  }
  return errors;
}

function serverErrors(error: unknown): Errors {
  if (!(error instanceof ApiProblem) || !error.fieldErrors) return {};
  const errors: Errors = {};
  for (const name of Object.keys(MESSAGES) as FieldName[]) {
    if (error.fieldErrors[name]) errors[name] = MESSAGES[name];
  }
  return errors;
}

function initial(era?: Era | EraDetail): EraFields {
  return {
    title: era?.title ?? '',
    description: era?.description ?? '',
    startDate: era?.startDate ?? '',
    endDate: era?.endDate ?? null,
    pinnedInFeed: era?.pinnedInFeed ?? false,
    // The cover shown now, or none. A hidden cover reads as none here and is kept by the server (D-36).
    coverMediaId: era?.cover?.id ?? null,
    soundtrack: era?.soundtrack ?? null,
  };
}

/**
 * New Era, or Edit on an Era page (task 4.9, D-22). An Era is its name and dates: the weeks it holds follow from
 * them on the server, so the form never lists weeks. "Show first in the feed" is the pin that makes this Era win
 * the weeks it shares with another one.
 */
export function EraForm({
  era,
  open,
  onOpenChange,
}: {
  era?: Era | EraDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return era ? (
    <EditEra era={era} open={open} onOpenChange={onOpenChange} />
  ) : (
    <NewEra open={open} onOpenChange={onOpenChange} />
  );
}

function NewEra({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const create = useCreateEra();
  const navigate = useNavigate();
  return (
    <EraFieldsDialog
      key={String(open)}
      title="New Era"
      open={open}
      onOpenChange={onOpenChange}
      mutation={create}
      onSaved={(era) => {
        onOpenChange(false);
        void navigate(`/eras/${era.id}`);
      }}
    />
  );
}

function EditEra({
  era,
  open,
  onOpenChange,
}: {
  era: Era | EraDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const update = useUpdateEra(era);
  const remove = useDeleteEra();
  const navigate = useNavigate();
  const [asking, setAsking] = useState(false);
  return (
    <>
      <EraFieldsDialog
        // Remade on each opening, so the fields start from the Era as it is then and the dialog remembers the
        // button that opened it. Not keyed by updatedAt: a save would remake it mid-close and lose the focus return.
        key={String(open)}
        title="Edit Era"
        era={era}
        open={open}
        onOpenChange={onOpenChange}
        mutation={update}
        onSaved={() => onOpenChange(false)}
        extra={
          <Button variant="quiet" onClick={() => setAsking(true)}>
            Delete
          </Button>
        }
      />
      <AlertDialog open={asking} onOpenChange={setAsking}>
        <AlertDialogContent>
          <AlertDialogTitle className="font-display text-title">Delete this Era?</AlertDialogTitle>
          <AlertDialogDescription className="text-body">
            Only the Era goes. Its weeks and their entries stay in your journal.
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
                  // Stay open until the server answers, so a failure can be read here.
                  event.preventDefault();
                  remove.mutate(era.id, {
                    onSuccess: () => {
                      setAsking(false);
                      onOpenChange(false);
                      void navigate('/eras');
                    },
                  });
                }}
              >
                Delete Era
              </Button>
            </AlertDialogAction>
          </div>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

function EraFieldsDialog({
  title,
  era,
  open,
  onOpenChange,
  mutation,
  onSaved,
  extra,
}: {
  title: string;
  era?: Era | EraDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mutation: ReturnType<typeof useCreateEra>;
  onSaved: (era: Era) => void;
  extra?: ReactNode;
}) {
  const [fields, setFields] = useState<EraFields>(() => initial(era));
  const [errors, setErrors] = useState<Errors>({});
  const set = (change: Partial<EraFields>) => setFields((now) => ({ ...now, ...change }));

  const submit = () => {
    const found = checkEra(fields);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    mutation.mutate(fields, {
      onSuccess: onSaved,
      onError: (error) => setErrors(serverErrors(error)),
    });
  };
  const fieldErrorShown = Object.keys(errors).length > 0;

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      onSubmit={submit}
      pending={mutation.isPending}
      error={mutation.isError && !fieldErrorShown ? problemMessage(mutation.error) : null}
      extra={extra}
    >
      <Field
        label="Name"
        value={fields.title}
        onChange={(value) => set({ title: value })}
        maxLength={150}
        autoComplete="off"
        error={errors.title}
      />
      <div className="flex flex-col gap-2">
        <div className="grid grid-cols-2 gap-4">
          <Field
            label="First day"
            type="date"
            min={EARLIEST_DAY}
            value={fields.startDate}
            onChange={(value) => set({ startDate: value })}
            error={errors.startDate}
          />
          <Field
            label="Last day"
            type="date"
            min={fields.startDate || EARLIEST_DAY}
            value={fields.endDate ?? ''}
            onChange={(value) => set({ endDate: value || null })}
            error={errors.endDate}
          />
        </div>
        <p className="text-small text-ink-2">Leave the last day empty while it’s still going.</p>
      </div>
      <Field
        label="Description (optional)"
        multiline
        maxLength={5000}
        value={fields.description}
        onChange={(value) => set({ description: value })}
        error={errors.description}
      />
      <CheckRow
        label="Show first in the feed"
        hint="Weeks it shares with another Era sit under this one."
        checked={fields.pinnedInFeed}
        onChange={(checked) => set({ pinnedInFeed: checked })}
      />
      {/* Only an Era page's own copy knows its weeks, where the photos are (task 4.14). */}
      {era && 'weeklyLogs' in era && (
        <CoverPicker
          era={era}
          value={fields.coverMediaId ?? null}
          onChange={(coverMediaId) => set({ coverMediaId })}
        />
      )}
      {/* Task 4.15 (D-35): paste a Spotify link or search for one. */}
      <SoundtrackField value={fields.soundtrack} onChange={(soundtrack) => set({ soundtrack })} />
    </FormDialog>
  );
}
