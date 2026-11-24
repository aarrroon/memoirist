import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router';
import { Page, Pending } from '../../components/Page';

// You, at /settings (DESIGN.md §6). Task 4.9 adds display name, timezone, birthday and Appearance. The rows to
// Tags, Templates and Recently deleted are here now, because on a phone this screen is the only way to them.
// A settings row is a link, not a box: no fill, a hairline between rows and a chevron (DESIGN.md §4 Controls).
const ROWS = [
  { to: '/tags', label: 'Tags' },
  { to: '/templates', label: 'Templates' },
  { to: '/recently-deleted', label: 'Recently deleted' },
] as const;

export function SettingsPage() {
  return (
    <Page title="You">
      <Pending>Your name, timezone, birthday and Appearance arrive with task 4.9.</Pending>
      <ul className="flex flex-col divide-y divide-line border-y border-line">
        {ROWS.map((row) => (
          <li key={row.to}>
            <Link
              to={row.to}
              className="flex min-h-14 items-center justify-between gap-4 text-body"
            >
              {row.label}
              <ChevronRight size={20} aria-hidden className="text-ink-2" />
            </Link>
          </li>
        ))}
      </ul>
    </Page>
  );
}
