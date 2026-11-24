import createClient, { type Middleware } from 'openapi-fetch';
import { networkProblem } from './problem';
import type { paths } from './schema';

// The typed API client (D-13, task 4.2). Endpoints are called by path, as in `api.GET('/api/v1/users/me')`,
// never by springdoc's operation ids (get_1, update_4), which shift when an endpoint is added.

/** The seeded dev user (backend db/seed-local, D-11). The backend uses it when no header is sent, too. */
export const DEV_USER_ID = '00000000-0000-0000-0000-000000000001';

// X-Dev-User-Id is read only in the local and test profiles (D-11, CLAUDE.md non-negotiable 7), so only the
// dev server sends it. `import.meta.env.DEV` is a constant in a production build, so Vite drops this branch
// and the id from the bundle. It is read per request, not once, so a test can switch modes with vi.stubEnv.
const devUser: Middleware = {
  onRequest({ request }) {
    if (import.meta.env.DEV) request.headers.set('X-Dev-User-Id', DEV_USER_ID);
    return request;
  },
};

// openapi-fetch throws when no response comes back. Turning that into an ApiProblem here means every hook
// sees one kind of error, whether the server answered 500 or never answered (problem.ts).
// A request TanStack Query cancelled on purpose (its AbortSignal) is left as it is, so the query can tell a
// cancellation from a failure. It is matched by name, not `instanceof DOMException`: the error can come from
// another realm (Node's fetch under jsdom in tests), where that check is false.
const noResponse: Middleware = {
  onError({ error }) {
    if (isAbort(error)) return undefined;
    return networkProblem();
  },
};

function isAbort(error: unknown): boolean {
  return (
    typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError'
  );
}

export const api = createClient<paths>({
  // The schema's paths already start with /api/v1, so the base is the page's own origin: the Vite proxy in
  // development, the same host in Phase 2. An absolute URL, because Node's Request (in tests) has no page
  // to resolve a relative one against.
  baseUrl: globalThis.location.origin,
  // Looked up on each call, not captured now: MSW replaces globalThis.fetch when a test server starts,
  // which is after this module is imported.
  fetch: (request) => globalThis.fetch(request),
});

api.use(devUser, noResponse);
