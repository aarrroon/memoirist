---
paths:
  - "frontend/**"
---

# Frontend rules (React 19, Vite, TypeScript)

## Structure
- `src/api/`:
  - `schema.d.ts` is **generated** by `npm run gen:api`. Never hand-edit it, and never hand-write API types.
  - `client.ts` is the `openapi-fetch` client.
  - `queryKeys.ts` is the query-key factory.
- `src/features/<feature>/` holds pages and feature components: `feed`, `entry`, `eras`, `templates`, `settings`.
- `src/components/` holds shared UI primitives; `src/lib/` holds pure helpers (`time.ts`); `src/stores/` holds Zustand stores.

## Data
- **Server data:** TanStack Query owns all of it. Use hooks such as `useFeed`, `useEntry` and `useCreateEntry`; components never call `fetch` directly.
- **UI state:** Zustand holds only UI state (open modal, draft text, selected era). Never copy server data into Zustand.
- **Cache timing:** presigned media URLs expire after 60 minutes. Keep `staleTime` at 30 minutes or less and refetch on window focus.
- **After mutations:** invalidate the affected keys (feed, the week, the entry) instead of patching caches by hand, unless the update is optimistic on purpose.
- **Errors:** API errors are `ProblemDetail`. Show `fieldErrors` on their form fields, and a friendly message for `409 entry-sealed`.
- **Dev user:** send `X-Dev-User-Id` only when `import.meta.env.DEV`.

## Time
- All formatting and "today" go through `src/lib/time.ts`, using the user's timezone from `GET /users/me` with `@date-fns/tz`.
- Never use `toLocaleString()` without a zone, and never use the browser's zone as a stand-in.
- Memory-date pickers default to today in the user's zone and never allow future dates.

## Editor and content
- TipTap with StarterKit and Link only. Link protocols are limited to `http`, `https` and `mailto`. Extensions must match the backend allow-list (TECH_STACK §2, D-05).
- Read-only captions are rendered from `contentJson` (TipTap's static renderer or a small JSON→React renderer). Never `dangerouslySetInnerHTML`.
- Choosing a template inserts its subheadings as `heading` nodes at creation time.

## Capture and media
- **Order:** `POST /entries` with `id: crypto.randomUUID()` → upload each file → `PUT …/media/order` → optional seal (after a confirmation dialog that repeats the date).
- **File input:** `accept="image/jpeg,image/png,image/webp,audio/webm,audio/mp4,audio/mpeg"`. Check size and count client-side as well (25 MB, 10 files).
- **Voice notes:** `MediaRecorder` with the first supported of `audio/webm;codecs=opus` and `audio/mp4`.

## Styling and accessibility
- Tailwind v4 uses a CSS-first config (`@theme` in `src/index.css`); there is no `tailwind.config.js`. Import Motion from `motion/react`.
- Honour `prefers-reduced-motion`.
- Every interactive element is reachable by keyboard and has an accessible name. The carousel works with arrow keys. Contrast meets WCAG AA.

## Testing
- Vitest + React Testing Library + MSW for components and hooks. Plain Vitest for `lib/`.
- Playwright for the smoke flows from task 4.12 onwards, named after the flow IDs in `docs/USER_FLOWS.md`.
- Run `npm test && npm run lint && npm run typecheck` before saying a task is done.
