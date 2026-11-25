import { http, HttpResponse } from 'msw';
import type { Me } from '../../api/users';
import { page, sampleWeeks } from './feed';

// Answers most tests share (task 4.2). A test that needs another answer adds its own with server.use().
// Paths start with '*' so they match whatever origin jsdom gives the page.

/** The seeded dev user, as GET /users/me returns it locally (backend db/seed-local). */
export const devUser: Me = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'dev@memoirist.local',
  displayName: 'Dev User',
  timezone: 'Australia/Melbourne',
  birthday: null,
};

export const handlers = [
  http.get('*/api/v1/users/me', () => HttpResponse.json(devUser)),
  // The Journal is the app's home, so most screens' tests load the feed too (task 4.4): one page of the sample.
  http.get('*/api/v1/feed', () => HttpResponse.json(page(sampleWeeks))),
  // The Opened strip (task 4.8): empty unless a test says a Loot Drop has opened.
  http.get('*/api/v1/entries/opened', () => HttpResponse.json([])),
  // Recently deleted (task 4.12): empty, for You's count, unless a test serves a journal (msw/deleted.ts).
  http.get('*/api/v1/entries/deleted', () =>
    HttpResponse.json({ items: [], page: 0, size: 1, totalItems: 0, totalPages: 0 }),
  ),
];
