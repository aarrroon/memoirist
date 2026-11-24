import { http, HttpResponse } from 'msw';
import type { Me } from '../../api/users';

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

export const handlers = [http.get('*/api/v1/users/me', () => HttpResponse.json(devUser))];
