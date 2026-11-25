import { Ellipsis, Maximize2, Trash2 } from 'lucide-react';
import { useRef, useState } from 'react';
import { Link } from 'react-router';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu';
import { DeleteEntryDialog } from '../deleted/DeleteEntryDialog';

/**
 * An entry's *more actions* menu in the feed (F-09 "Starts from: an entry's more actions menu", task 4.12): Open the
 * entry, and Delete, which asks first and moves it to Recently deleted (D-30). Edit joins it when a task builds the
 * edit view. A quiet 44 px icon button beside the entry's text, so it adds no row to the first screen (D-25).
 */
export function EntryMenu({ entryId, name }: { entryId: string; name: string }) {
  const [deleting, setDeleting] = useState(false);
  // The dialog (and its delete mutation) is mounted the first time Delete is chosen, not once per card in the feed,
  // and then stays, so closing it never unmounts it mid-close.
  const [asked, setAsked] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            ref={trigger}
            type="button"
            aria-label={`More actions for ${name}`}
            className="-my-3 -mr-3 flex size-11 shrink-0 items-center justify-center rounded-full text-ink-2"
          >
            <Ellipsis size={20} aria-hidden />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem asChild>
            <Link to={`/entries/${entryId}`}>
              <Maximize2 size={20} aria-hidden />
              Open
            </Link>
          </DropdownMenuItem>
          {/* The dialog opens once the menu has closed, so the menu's own focus return can't pull focus back. */}
          <DropdownMenuItem
            onSelect={() => {
              setAsked(true);
              setDeleting(true);
            }}
          >
            <Trash2 size={20} aria-hidden />
            Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      {asked && (
        <DeleteEntryDialog
          entryId={entryId}
          sealed={false}
          open={deleting}
          onOpenChange={setDeleting}
          returnFocus={trigger}
        />
      )}
    </>
  );
}
