import { DEV_USER, DEV_USER_HEADER, expect, openCapture, test, uniqueCaption } from './fixtures';

// F-06 Seal an entry as a Loot Drop (docs/USER_FLOWS.md), the second half of task 4.17's smoke test: write an
// entry, seal it for a month from capture, confirm the question that repeats the date, and see a sealed card in the
// feed that shows nothing of what was written (D-10, non-negotiable 4).
test('F-06: seal an entry and see only its sealed card', async ({
  page,
  journal,
  request,
}, testInfo) => {
  const caption = uniqueCaption('F-06');

  await openCapture(page);
  await page.getByRole('textbox', { name: 'Entry text' }).click();
  await page.keyboard.type(caption);

  // The seal is in a panel from the add bar on a phone, and in the dialog's right column on a laptop (DESIGN.md §4).
  const phone = testInfo.project.name === 'phone';
  if (phone) await page.getByRole('button', { name: 'Seal', exact: true }).click();
  const seal = phone
    ? page.getByRole('dialog', { name: 'Seal as Loot Drop' })
    : page.getByRole('region', { name: 'Seal as Loot Drop' });
  // The radio itself is visually hidden and drawn by a ring beside it (SealForm's RadioRow), so the row's label is
  // what a person clicks; the check that follows proves the click reached the radio.
  await seal.getByText('In a month').click();
  await expect(seal.getByRole('radio', { name: /In a month/ })).toBeChecked();
  await expect(seal.getByText(/at 9:00/)).toBeVisible();
  await seal.getByRole('button', { name: 'Seal it' }).click();

  // The question repeats the date and how far away it is (F-06 step 3), then seals.
  const question = page.getByRole('alertdialog', { name: 'Seal this Loot Drop?' });
  await expect(question).toContainText(/in (1 month|4 weeks|30 days)|in a month/i);
  const sealed = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      /\/api\/v1\/entries\/[^/]+\/seal$/.test(response.url()),
  );
  await question.getByRole('button', { name: 'Seal it' }).click();
  expect((await sealed).status()).toBe(200);
  await expect(page.getByText('Sealed as a Loot Drop.', { exact: false })).toBeVisible();

  // The feed: a sealed card for this entry, and its words nowhere on the page.
  expect(journal.created).toHaveLength(1);
  const [id] = journal.created;
  await expect(page.getByRole('textbox', { name: 'Entry text' })).toBeHidden();
  const card = page.locator(`a[href="/entries/${id}"]`);
  await expect(card).toBeVisible();
  await expect(card).toHaveAttribute('aria-label', /^Sealed Loot Drop\. Opens /);
  await expect(page.getByText(caption)).toHaveCount(0);

  // The Loot Drop page shows the wait, never the entry (D-10).
  await card.click();
  await expect(page).toHaveURL(new RegExp(`/entries/${id}$`));
  await expect(page.getByText(/Opens/).first()).toBeVisible();
  await expect(page.getByText(caption)).toHaveCount(0);

  // And the server agrees: the entry reads as locked, with nothing private in it.
  const read = await request.get(`/api/v1/entries/${id}`, {
    headers: { [DEV_USER_HEADER]: DEV_USER },
  });
  expect(read.status()).toBe(200);
  const body = (await read.json()) as { isLocked: boolean; contentJson: unknown; media: unknown[] };
  expect(body.isLocked).toBe(true);
  expect(body.contentJson).toBeNull();
  expect(body.media).toEqual([]);
});
