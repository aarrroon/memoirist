import { Plus, X } from 'lucide-react';
import { useId, useState } from 'react';
import { ApiProblem, problemMessage } from '../../api/problem';
import { useCreateTag, useTags } from '../../api/tags';
import { Chip } from '../../components/ui/chip';
import { Popover, PopoverContent, PopoverTrigger } from '../../components/ui/popover';

// The tags on the entry (D-26, task 4.6): one chip per tag with a button to take it off, then "+ Tag", which opens a
// field that finds one of the user's tags or makes a new one inline. At most 20 tags on an entry.

export const MAX_TAGS = 20;

export function TagPicker({
  selected,
  onChange,
  open,
  onOpenChange,
  error,
}: {
  selected: string[];
  onChange: (tagIds: string[]) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  error?: string;
}) {
  const tags = useTags();
  const createTag = useCreateTag();
  const [query, setQuery] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const fieldId = useId();

  const all = tags.data ?? [];
  const chosen = selected.map((id) => all.find((t) => t.id === id)).filter((t) => t !== undefined);
  const wanted = query.trim();
  const matches = all.filter(
    (t) => !selected.includes(t.id) && t.name.toLowerCase().includes(wanted.toLowerCase()),
  );
  const exact = all.find((t) => t.name.toLowerCase() === wanted.toLowerCase());
  const full = selected.length >= MAX_TAGS;

  const add = (id: string) => {
    if (!selected.includes(id) && !full) onChange([...selected, id]);
    setQuery('');
    setProblem(null);
    onOpenChange(false);
  };

  const make = () => {
    if (wanted === '' || full) return;
    if (exact) return add(exact.id);
    // POST /tags returns an existing tag with the same name (ignoring case) instead of failing, so a tag another tab
    // made a moment ago is simply picked.
    createTag.mutate(wanted, {
      onSuccess: (tag) => add(tag.id),
      onError: (e) =>
        setProblem(
          e instanceof ApiProblem && e.fieldErrors.name
            ? 'A tag name is 1 to 50 characters.'
            : problemMessage(e),
        ),
    });
  };

  return (
    <div className="flex flex-col gap-2">
      <ul aria-label="Tags" className="flex flex-wrap items-center gap-2">
        {chosen.map((tag) => (
          <li key={tag.id}>
            <Chip
              aria-label={`Remove tag ${tag.name}`}
              onClick={() => onChange(selected.filter((id) => id !== tag.id))}
            >
              {tag.name}
              <X size={16} aria-hidden />
            </Chip>
          </li>
        ))}
        <li>
          <Popover
            open={open}
            onOpenChange={(next) => {
              onOpenChange(next);
              if (!next) setProblem(null);
            }}
          >
            <PopoverTrigger asChild>
              <Chip disabled={full} aria-label={full ? 'Tags: 20 is the most' : 'Add a tag'}>
                <Plus size={16} aria-hidden />
                Tag
              </Chip>
            </PopoverTrigger>
            <PopoverContent aria-label="Add a tag" className="w-72 gap-2 p-4">
              <label htmlFor={fieldId} className="text-small font-strong">
                Find or make a tag
              </label>
              <input
                id={fieldId}
                value={query}
                maxLength={50}
                autoComplete="off"
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    make();
                  }
                }}
                className="min-h-11 rounded-sm border border-control bg-page px-3 text-body text-ink"
              />
              <div className="flex max-h-56 flex-col overflow-y-auto">
                {matches.map((tag) => (
                  <button
                    key={tag.id}
                    type="button"
                    onClick={() => add(tag.id)}
                    className="flex min-h-11 items-center rounded-sm px-3 text-left text-body hover:bg-raised focus-visible:-outline-offset-2"
                  >
                    {tag.name}
                  </button>
                ))}
                {wanted !== '' && !exact && (
                  <button
                    type="button"
                    onClick={make}
                    disabled={createTag.isPending}
                    className="flex min-h-11 items-center gap-2 rounded-sm px-3 text-left text-body font-strong hover:bg-raised focus-visible:-outline-offset-2"
                  >
                    <Plus size={16} aria-hidden />
                    {`Make “${wanted}”`}
                  </button>
                )}
                {wanted === '' && matches.length === 0 && (
                  <p className="px-3 text-small text-ink-2">Type a name to make your first tag.</p>
                )}
              </div>
              {problem && (
                <p role="alert" className="text-small text-danger">
                  {problem}
                </p>
              )}
            </PopoverContent>
          </Popover>
        </li>
      </ul>
      {error && <p className="text-small text-danger">{error}</p>}
    </div>
  );
}
