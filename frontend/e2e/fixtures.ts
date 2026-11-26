import { expect, test as base, type Page, type Response } from '@playwright/test';

// Shared pieces of the smoke tests (task 4.17).
//
// Every test runs as the seeded dev user (backend db/seed-local, D-11): the app's client sends this header in
// development (src/api/client.ts), and the clean-up below sends it too. These are not imported from src/: the tests
// run in Node, outside Vite, and a test that imports the code it checks can't notice when that code changes.
export const DEV_USER_HEADER = 'X-Dev-User-Id';
export const DEV_USER = '00000000-0000-0000-0000-000000000001';

/** The entries one test created, so they can be erased when it ends. */
export interface Journal {
  /** The ids of every entry this test's page created with POST /api/v1/entries, in order. */
  created: string[];
}

function isCreate(response: Response): boolean {
  const url = new URL(response.url());
  return response.request().method() === 'POST' && url.pathname === '/api/v1/entries';
}

export const test = base.extend<{ journal: Journal }>({
  // The first letter (task 4.16, F-01) is offered after the first entry of an empty journal. Marking it skipped
  // keeps the tests the same whether or not the dev journal had sample content. It only touches local storage,
  // exactly as the screen's own Skip does (src/lib/firstLetter.ts).
  page: async ({ page }, use) => {
    await page.addInitScript(() => localStorage.setItem('memoirist.first-letter', 'skipped'));
    await use(page);
  },

  // TODO(human) PRACTICE: Write a Playwright fixture that leaves the dev journal as it found it. While the test runs,
  // record the id of every entry the page creates (listen to the page's responses; POST /api/v1/entries answers 201,
  // or 200 for a replay of the same client id, D-12). After the test, even a failed one, remove each entry for good.
  // Hint: code before `await use(value)` is setup and code after it is teardown, so the teardown runs whatever the
  // test did. Removing for good takes two calls: DELETE /api/v1/entries/{id} moves it to Recently deleted, then
  // POST /api/v1/entries/{id}/erase (D-30). A sealed entry can be deleted and erased too (F-07). Which statuses mean
  // "already gone" rather than "the clean-up failed"? And what happens to a test that never asks for `journal`?
  // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
  journal: [
    async ({ page, request }, use) => {
      const journal: Journal = { created: [] };
      const pending: Promise<void>[] = [];
      page.on('response', (response) => {
        if (!isCreate(response) || !response.ok()) return;
        pending.push(
          response.json().then((body: { id: string }) => {
            if (!journal.created.includes(body.id)) journal.created.push(body.id);
          }),
        );
      });

      await use(journal);

      await Promise.allSettled(pending);
      const headers = { [DEV_USER_HEADER]: DEV_USER };
      for (const id of journal.created) {
        const deleted = await request.delete(`/api/v1/entries/${id}`, { headers });
        // 404: the test deleted it itself, or DELETE erased an empty entry at once (D-30).
        expect([204, 404], `DELETE entry ${id}`).toContain(deleted.status());
        const erased = await request.post(`/api/v1/entries/${id}/erase`, { headers });
        expect([204, 404], `erase entry ${id}`).toContain(erased.status());
      }
    },
    // auto: a fixture runs only for a test that names it, so a new test that forgot `journal` would leave its entries
    // in the dev journal. auto runs it for every test (found by task 4.17's red check, which left one behind).
    { auto: true },
  ],
  // └── END ANSWER ▲ ──
});

export { expect };

/** A caption no other entry has, so a test can find its own entry in a journal full of others. */
export function uniqueCaption(flow: string): string {
  return `${flow} smoke test ${Date.now().toString(36)}`;
}

/** Opens capture from its link, as a home-screen shortcut and the design check do (task 4.6). */
export async function openCapture(page: Page): Promise<void> {
  await page.goto('/?write=entry');
  await expect(page.getByRole('textbox', { name: 'Entry text' })).toBeVisible();
}

/**
 * A real JPEG drawn in the browser: 1200 × 900 with a gradient, so it has a size the server stores (D-17) and
 * enough detail for browser-image-compression to do real work. Made fresh each run, so no binary sits in the repo.
 */
export async function drawJpeg(page: Page): Promise<Buffer> {
  const dataUrl = await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 1200;
    canvas.height = 900;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('No 2D canvas in this browser');
    const gradient = context.createLinearGradient(0, 0, 1200, 900);
    gradient.addColorStop(0, 'rgb(30, 60, 120)');
    gradient.addColorStop(1, 'rgb(230, 180, 80)');
    context.fillStyle = gradient;
    context.fillRect(0, 0, 1200, 900);
    return canvas.toDataURL('image/jpeg', 0.9);
  });
  return Buffer.from(dataUrl.slice(dataUrl.indexOf(',') + 1), 'base64');
}
