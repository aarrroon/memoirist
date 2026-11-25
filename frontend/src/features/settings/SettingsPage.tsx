import { ChevronRight } from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiProblem, problemMessage } from '../../api/problem';
import { useTags } from '../../api/tags';
import { useTemplates } from '../../api/templates';
import { useMe, useUpdateMe, type Me, type MeChange } from '../../api/users';
import { Field, FormDialog } from '../../components/FormDialog';
import { ListLoading, LoadError } from '../../components/LoadState';
import { Page } from '../../components/Page';
import { Button } from '../../components/ui/button';
import { setAppearance, useAppearance, type Appearance } from '../../lib/appearance';
import { EARLIEST_DAY, formatBirthday, todayIn, zoneLabel } from '../../lib/time';

// You, at /settings (DESIGN.md §6 `#you`, task 4.9): About you (display name, timezone, birthday, D-33), Appearance
// (kept on this device, D-41), then the rows to Tags, Templates and Recently deleted. A settings row is a button or
// a link, not a box: a hairline between rows, the value in --ink-2 and a chevron (DESIGN.md §4 Controls).

type Editing = 'displayName' | 'timezone' | 'birthday' | null;

export function SettingsPage() {
  const me = useMe();
  const [editing, setEditing] = useState<Editing>(null);
  return (
    <Page title="You">
      <section aria-labelledby="you-about" className="flex flex-col gap-4">
        <h2 id="you-about" className="font-display text-title">
          About you
        </h2>
        {me.isPending ? (
          <ListLoading label="Opening your settings…" />
        ) : me.isError ? (
          <LoadError error={me.error} onRetry={() => void me.refetch()} />
        ) : (
          <>
            <ul className="flex flex-col divide-y divide-line border-y border-line">
              <SettingRow
                label="Display name"
                value={me.data.displayName}
                onClick={() => setEditing('displayName')}
              />
              <SettingRow
                label="Timezone"
                value={zoneLabel(me.data.timezone)}
                onClick={() => setEditing('timezone')}
              />
              <SettingRow
                label="Birthday"
                value={me.data.birthday ? formatBirthday(me.data.birthday) : 'Not set'}
                onClick={() => setEditing('birthday')}
              />
            </ul>
            {editing && (
              <SettingDialog me={me.data} setting={editing} onClose={() => setEditing(null)} />
            )}
          </>
        )}
      </section>
      <AppearancePicker />
      <JournalRows />
    </Page>
  );
}

function SettingRow({
  label,
  value,
  onClick,
}: {
  label: string;
  value: string;
  onClick: () => void;
}) {
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        aria-label={`${label}: ${value}. Change`}
        className="flex min-h-14 w-full items-center gap-4 text-left text-body"
      >
        <span className="flex-1">{label}</span>
        <span className="text-ink-2">{value}</span>
        <ChevronRight size={20} aria-hidden className="shrink-0 text-ink-2" />
      </button>
    </li>
  );
}

const APPEARANCES: { value: Appearance; label: string }[] = [
  { value: 'system', label: 'Match phone' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
];

/**
 * The segmented control (DESIGN.md §4 Controls): three 44 px segments in a full-corner --control border, the
 * chosen one filled with the accent. Native radios underneath, so arrow keys move the choice and a screen reader
 * hears "Light, radio button, 2 of 3". A choice applies to the whole app at once (lib/appearance.ts).
 */
function AppearancePicker() {
  const choice = useAppearance();
  const name = useId();
  return (
    <section aria-labelledby="you-appearance" className="flex flex-col gap-4">
      <h2 id="you-appearance" className="font-display text-title">
        Appearance
      </h2>
      <div
        role="radiogroup"
        aria-labelledby="you-appearance"
        className="flex gap-1 rounded-full border border-control p-1"
      >
        {/* flex-auto, not equal thirds: "Match phone" needs more room than "Dark" to stay on one line at 375 px. */}
        {APPEARANCES.map((option) => (
          <label
            key={option.value}
            className="flex min-h-11 flex-auto cursor-pointer items-center justify-center rounded-full px-3 text-body whitespace-nowrap has-checked:bg-accent has-checked:font-strong has-checked:text-on-accent has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-accent"
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={choice === option.value}
              onChange={() => setAppearance(option.value)}
              className="sr-only"
            />
            {option.label}
          </label>
        ))}
      </div>
      <p className="text-small text-ink-2">Kept on this device.</p>
    </section>
  );
}

function JournalRows() {
  const tags = useTags();
  const templates = useTemplates();
  const rows = [
    { to: '/tags', label: 'Tags', count: tags.data?.length },
    { to: '/templates', label: 'Templates', count: templates.data?.length },
    { to: '/recently-deleted', label: 'Recently deleted', count: undefined },
  ];
  return (
    <section aria-labelledby="you-journal" className="flex flex-col gap-4">
      <h2 id="you-journal" className="font-display text-title">
        Your journal
      </h2>
      <ul className="flex flex-col divide-y divide-line border-y border-line">
        {rows.map((row) => (
          <li key={row.to}>
            <Link to={row.to} className="flex min-h-14 items-center gap-4 text-body">
              <span className="flex-1">{row.label}</span>
              {row.count !== undefined && <span className="text-ink-2">{row.count}</span>}
              <ChevronRight size={20} aria-hidden className="shrink-0 text-ink-2" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

const FIELD_MESSAGES = {
  displayName: 'A name is 1 to 100 characters.',
  timezone: 'Pick a timezone from the list.',
  birthday: 'Pick a day from 1900 to today.',
} as const;

/** Every IANA zone the browser knows, so the server's list and Intl's agree (API_CONTRACT §Users). */
function zones(current: string): string[] {
  const known = Intl.supportedValuesOf('timeZone');
  return [...new Set([current, 'UTC', ...known])].sort((a, b) => a.localeCompare(b, 'en'));
}

function SettingDialog({
  me,
  setting,
  onClose,
}: {
  me: Me;
  setting: Exclude<Editing, null>;
  onClose: () => void;
}) {
  const update = useUpdateMe();
  const [name, setName] = useState(me.displayName);
  const [zone, setZone] = useState(me.timezone);
  const [birthday, setBirthday] = useState(me.birthday ?? '');
  const [error, setError] = useState<string>();
  const selectId = useId();
  const today = todayIn(me.timezone);

  const save = (change: MeChange) => {
    update.mutate(change, {
      onSuccess: onClose,
      onError: (problem) => {
        if (problem instanceof ApiProblem && problem.fieldErrors?.[setting]) {
          setError(FIELD_MESSAGES[setting]);
        }
      },
    });
  };
  const submit = () => {
    setError(undefined);
    if (setting === 'displayName') {
      const trimmed = name.trim();
      if (trimmed.length === 0 || trimmed.length > 100) return setError(FIELD_MESSAGES.displayName);
      save({ displayName: trimmed });
    } else if (setting === 'timezone') {
      save({ timezone: zone });
    } else {
      if (birthday && (birthday > today || birthday < EARLIEST_DAY)) {
        return setError(FIELD_MESSAGES.birthday);
      }
      // An empty field removes the birthday: PATCH reads null as "remove" (D-33).
      save({ birthday: birthday || null });
    }
  };

  const titles = { displayName: 'Display name', timezone: 'Timezone', birthday: 'Birthday' };
  return (
    <FormDialog
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      title={titles[setting]}
      onSubmit={submit}
      pending={update.isPending}
      error={update.isError && !error ? problemMessage(update.error) : null}
      extra={
        setting === 'birthday' && me.birthday ? (
          <Button variant="quiet" onClick={() => save({ birthday: null })}>
            Remove
          </Button>
        ) : undefined
      }
    >
      {setting === 'displayName' && (
        <Field
          label="Display name"
          value={name}
          onChange={setName}
          maxLength={100}
          autoComplete="nickname"
          error={error}
        />
      )}
      {setting === 'timezone' && (
        <div className="flex flex-col gap-2">
          <label htmlFor={selectId} className="text-small font-strong">
            Timezone
          </label>
          <select
            id={selectId}
            value={zone}
            onChange={(event) => setZone(event.target.value)}
            aria-describedby={`${selectId}-hint`}
            aria-invalid={error ? true : undefined}
            className="min-h-11 w-full rounded-sm border border-control bg-page px-3 text-body text-ink"
          >
            {zones(me.timezone).map((option) => (
              <option key={option} value={option}>
                {option.replaceAll('_', ' ')}
              </option>
            ))}
          </select>
          <p id={`${selectId}-hint`} className="text-small text-ink-2">
            Today, your weeks and every time you see follow it.
          </p>
          {error && <p className="text-small text-danger">{error}</p>}
        </div>
      )}
      {setting === 'birthday' && (
        <Field
          label="Birthday"
          type="date"
          min={EARLIEST_DAY}
          max={today}
          value={birthday}
          onChange={setBirthday}
          hint="Write can offer it as a day for a Loot Drop to open."
          error={error}
        />
      )}
    </FormDialog>
  );
}
