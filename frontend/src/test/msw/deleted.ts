import { http, HttpResponse } from 'msw';
import type { DeletedPage } from '../../api/deleted';
import type { Entry } from '../../api/entries';
import { weekOf } from '../../lib/time';
import { doc, entry, page, sealedEntry, week } from './feed';
import { devUser } from './handlers';
import {
  isSealed,
  problem,
  toRow,
  type SeenRequest,
  type StoredEntry,
  type TimelineJournal,
} from './timeline';

// The stand-in server behind Recently deleted (task 4.12), over the Timeline's journal (timeline.ts), which holds an
// entry in Recently deleted, a sealed Loot Drop in Recently deleted, another user's deleted entry and a live sealed
// Loot Drop. The handlers follow API_CONTRACT §Entries, Recently deleted: the list is the user's deleted entries only,
// the most recently deleted first, with sealed rows redacted (D-10); restore and erase see only those entries, and
// anything else is 404; DELETE moves a live entry there. GET /feed is built from the same journal, so a test can show
// a restored entry back in its week and a deleted one gone from it. The backend's own tests (task 3.11) prove the
// real server.

const weekId = (day: string) => `week-${weekOf(day).start}`;

/** The entry as the server's mapper would answer a restore: whole, or redacted while sealed (D-10). */
function toEntry(stored: StoredEntry): Entry {
  if (isSealed(stored)) {
    return {
      ...sealedEntry(stored.id, stored.entryDate, stored.unlockDate!),
      weeklyLogId: weekId(stored.entryDate),
    };
  }
  return entry(stored.id, stored.entryDate, {
    weeklyLogId: weekId(stored.entryDate),
    title: stored.title,
    contentJson: stored.previewLine ? doc(stored.previewLine) : null,
    contentText: stored.previewLine,
    unlockDate: stored.unlockDate,
  });
}

const findMine = (journal: TimelineJournal, id: unknown) =>
  journal.entries.find((e) => e.id === id && e.userId === devUser.id);

export function deletedHandlers(journal: TimelineJournal, seen: SeenRequest[] = []) {
  return [
    http.get('*/api/v1/entries/deleted', ({ request }) => {
      const url = new URL(request.url);
      seen.push({ method: 'GET', url });
      const pageNumber = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 10);
      if (size < 1 || size > 50 || pageNumber < 0) return problem(400, 'validation-failed');
      const rows = journal.entries
        .filter((e) => e.userId === devUser.id && e.deletedAt !== null)
        .sort((a, b) => b.deletedAt!.localeCompare(a.deletedAt!) || a.id.localeCompare(b.id));
      const body: DeletedPage = {
        items: rows
          .slice(pageNumber * size, pageNumber * size + size)
          .map((e) => ({ ...toRow(journal, e), deletedAt: e.deletedAt })),
        page: pageNumber,
        size,
        totalItems: rows.length,
        totalPages: Math.ceil(rows.length / size),
      };
      return HttpResponse.json(body);
    }),
    http.post('*/api/v1/entries/:entryId/restore', ({ params, request }) => {
      seen.push({ method: 'POST', url: new URL(request.url) });
      const found = findMine(journal, params.entryId);
      if (!found || found.deletedAt === null) return problem(404, 'not-found');
      found.deletedAt = null;
      return HttpResponse.json(toEntry(found));
    }),
    http.post('*/api/v1/entries/:entryId/erase', ({ params, request }) => {
      seen.push({ method: 'POST', url: new URL(request.url) });
      const found = findMine(journal, params.entryId);
      if (!found || found.deletedAt === null) return problem(404, 'not-found');
      journal.entries.splice(journal.entries.indexOf(found), 1);
      return new HttpResponse(null, { status: 204 });
    }),
    http.delete('*/api/v1/entries/:entryId', ({ params, request }) => {
      seen.push({ method: 'DELETE', url: new URL(request.url) });
      const found = findMine(journal, params.entryId);
      if (!found || found.deletedAt !== null) return problem(404, 'not-found');
      found.deletedAt = new Date().toISOString();
      return new HttpResponse(null, { status: 204 });
    }),
    // The feed, from the same journal: the user's live entries in their weeks, newest first, in no Era.
    http.get('*/api/v1/feed', () => {
      const live = journal.entries
        .filter((e) => e.userId === devUser.id && e.deletedAt === null)
        .sort((a, b) => b.entryDate.localeCompare(a.entryDate) || b.id.localeCompare(a.id));
      const weeks = new Map<string, StoredEntry[]>();
      for (const e of live)
        weeks.set(weekId(e.entryDate), [...(weeks.get(weekId(e.entryDate)) ?? []), e]);
      return HttpResponse.json(
        page(
          [...weeks.entries()].map(([id, entries]) => {
            const { start, end } = weekOf(entries[0]!.entryDate);
            return week(id, start, end, [], entries.map(toEntry));
          }),
        ),
      );
    }),
  ];
}
