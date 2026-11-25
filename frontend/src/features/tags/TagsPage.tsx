import { useState } from 'react';
import { ApiProblem, problemMessage } from '../../api/problem';
import { useDeleteTag, useRenameTag, useTags, type Tag } from '../../api/tags';
import { Field, FormDialog } from '../../components/FormDialog';
import { ListLoading, LoadError } from '../../components/LoadState';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogTitle,
} from '../../components/ui/dialog';

// Tags, at /tags (task 4.11, D-26, F-17 step 4). DESIGN.md §6: it follows the Templates screen, one row per tag with
// a rename and a delete. A row shows the tag's name and how many entries carry it; the server's entryCount already
// leaves out sealed Loot Drops and Recently deleted (D-10, D-30), so the number is shown as it comes. Tags are made
// in Write's tag picker (task 4.6), so there is no New button here.

const NAME_ERROR = 'A tag name is 1 to 50 characters.';

const entries = (n: number) => `${n} ${n === 1 ? 'entry' : 'entries'}`;

export function TagsPage() {
  const tags = useTags();
  const [renaming, setRenaming] = useState<Tag | null>(null);
  const [deleting, setDeleting] = useState<Tag | null>(null);
  const [done, setDone] = useState('');

  return (
    <Page title="Tags">
      {/* What the last rename or delete did, for screen readers: the dialog that did it has closed. */}
      <p role="status" className="sr-only">
        {done}
      </p>
      {tags.isPending ? (
        <ListLoading label="Opening your tags…" />
      ) : tags.isError ? (
        <LoadError error={tags.error} onRetry={() => void tags.refetch()} />
      ) : tags.data.length === 0 ? (
        <p className="text-body text-ink-2">
          Tags you add to entries in Write are listed here, to rename or delete.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-line border-y border-line">
          {tags.data.map((tag) => (
            <li key={tag.id} className="flex min-h-14 items-center gap-2 py-2">
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-body font-strong">{tag.name}</span>
                <span className="text-small text-ink-2">{entries(tag.entryCount)}</span>
              </span>
              <Button
                aria-label={`Rename ${tag.name}`}
                onClick={() => setRenaming(tag)}
                className="shrink-0"
              >
                Rename
              </Button>
              <Button
                variant="quiet"
                aria-label={`Delete ${tag.name}`}
                onClick={() => setDeleting(tag)}
                className="shrink-0"
              >
                Delete
              </Button>
            </li>
          ))}
        </ul>
      )}
      {renaming && (
        <RenameTag
          tag={renaming}
          others={(tags.data ?? []).filter((t) => t.id !== renaming.id)}
          onClose={(renamed) => {
            setRenaming(null);
            if (renamed) setDone(`Renamed to ${renamed}.`);
          }}
        />
      )}
      {deleting && (
        <DeleteTag
          tag={deleting}
          onClose={(deleted) => {
            setDeleting(null);
            if (deleted) setDone(`${deleting.name} is deleted.`);
          }}
        />
      )}
    </Page>
  );
}

/**
 * The message for a rename that meets 409 conflict (F-17 edge case: "Message suggesting the other tag"). The server
 * never repeats the clashing name (`detail` never echoes input), so the client finds it in the list it already has,
 * ignoring case as the server does.
 */
function conflictMessage(name: string, others: readonly Tag[]): string {
  const other = others.find((t) => t.name.toLowerCase() === name.trim().toLowerCase());
  return other
    ? `You already have a tag called ${other.name}. Use that one, or pick another name.`
    : 'You already have a tag with that name. Pick another name.';
}

/** Rename (PUT /tags/{id}): one field. Every entry shows the new name at once; none of them is changed (D-26). */
function RenameTag({
  tag,
  others,
  onClose,
}: {
  tag: Tag;
  others: readonly Tag[];
  onClose: (renamed?: string) => void;
}) {
  const [name, setName] = useState(tag.name);
  const [fieldError, setFieldError] = useState<string>();
  const rename = useRenameTag();

  const submit = () => {
    const trimmed = name.trim();
    if (trimmed.length === 0 || trimmed.length > 50) {
      setFieldError(NAME_ERROR);
      return;
    }
    setFieldError(undefined);
    rename.mutate(
      { tagId: tag.id, name: trimmed },
      {
        onSuccess: (saved) => onClose(saved.name),
        onError: (error) => {
          if (!(error instanceof ApiProblem)) return;
          if (error.kind === 'conflict') setFieldError(conflictMessage(trimmed, others));
          else if (error.fieldErrors?.name) setFieldError(NAME_ERROR);
        },
      },
    );
  };

  return (
    <FormDialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      title="Rename tag"
      pending={rename.isPending}
      error={rename.isError && !fieldError ? problemMessage(rename.error) : null}
      onSubmit={submit}
    >
      <Field
        label="Name"
        hint="Every entry with this tag shows the new name."
        value={name}
        onChange={setName}
        maxLength={50}
        autoComplete="off"
        error={fieldError}
      />
    </FormDialog>
  );
}

/** Delete (DELETE /tags/{id}), after asking: the entries lose the tag and keep everything else (D-26, F-17). */
function DeleteTag({ tag, onClose }: { tag: Tag; onClose: (deleted?: boolean) => void }) {
  const remove = useDeleteTag();
  // Opened from state, not a Radix trigger, so focus goes back by hand to the Delete button that opened it, as
  // FormDialog does (DESIGN.md §8). After a delete that button has gone with its row, so focus goes to the
  // screen (<main>) instead of being lost.
  const [opener] = useState(() =>
    document.activeElement instanceof HTMLElement ? document.activeElement : null,
  );
  return (
    <AlertDialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <AlertDialogContent
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          if (opener?.isConnected) opener.focus();
          else document.getElementById('main')?.focus();
        }}
      >
        <AlertDialogTitle className="font-display text-title">Delete {tag.name}?</AlertDialogTitle>
        <AlertDialogDescription className="text-body">
          Entries tagged {tag.name} lose the tag and keep everything else.
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
              aria-busy={remove.isPending}
              onClick={(event) => {
                event.preventDefault();
                remove.mutate(tag.id, { onSuccess: () => onClose(true) });
              }}
            >
              Delete tag
            </Button>
          </AlertDialogAction>
        </div>
      </AlertDialogContent>
    </AlertDialog>
  );
}
