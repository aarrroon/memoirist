import {
  DEV_USER,
  DEV_USER_HEADER,
  drawJpeg,
  expect,
  openCapture,
  test,
  uniqueCaption,
} from './fixtures';

// F-03 Entry with photos (docs/USER_FLOWS.md), the first half of task 4.17's smoke test: write a caption, add a
// photo, save, and see the entry in the feed with its photo. It runs the whole save path for real: POST /entries with
// a client id (D-12), the photo shrunk in the browser (D-17), the upload to SeaweedFS, the order, and a presigned URL
// the browser can load.
test('F-03: create an entry with a photo and see it in the feed', async ({
  page,
  journal,
  request,
}) => {
  const caption = uniqueCaption('F-03');
  const photo = await drawJpeg(page);

  await openCapture(page);
  // D-17: iOS converts a photo to JPEG only when the picker's accept list leaves HEIC out. This checks the half that
  // a desktop browser can see; the conversion itself needs a real iPhone (task 4.17's build journal).
  const accept = await page.getByLabel('Add photos').getAttribute('accept');
  expect(accept?.split(',')).toContain('image/jpeg');
  expect(accept).not.toMatch(/heic|heif|image\/\*/i);

  await page.getByRole('textbox', { name: 'Entry text' }).click();
  await page.keyboard.type(caption);
  // The input is hidden behind the Photo button; Playwright can hand it files directly, as the picker would.
  await page.getByLabel('Add photos').setInputFiles({
    name: 'smoke.jpg',
    mimeType: 'image/jpeg',
    buffer: photo,
  });
  // The staging strip shows one thumbnail (its options button names its place), beside the Add tile.
  await expect(
    page.getByRole('list', { name: 'Photos' }).getByRole('button', { name: /1 of 1/ }),
  ).toBeVisible();

  const upload = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      /\/api\/v1\/entries\/[^/]+\/media$/.test(response.url()),
  );
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  expect((await upload).status()).toBe(201);

  // Capture closes and the feed shows the entry, inside this week, with its photo loaded from storage.
  await expect(page.getByRole('textbox', { name: 'Entry text' })).toBeHidden();
  const entry = page.locator('article', { hasText: caption });
  await expect(entry).toBeVisible();
  const image = entry.locator('img').first();
  await expect(image).toBeVisible();
  await expect
    .poll(() =>
      image.evaluate((element: HTMLImageElement) => element.complete && element.naturalWidth),
    )
    .toBeGreaterThan(0);

  // The server holds one photo with its size (D-17), and nothing from this test is left behind (the fixture).
  expect(journal.created).toHaveLength(1);
  const [id] = journal.created;
  const saved = await request.get(`/api/v1/entries/${id}`, {
    headers: { [DEV_USER_HEADER]: DEV_USER },
  });
  expect(saved.status()).toBe(200);
  const body = (await saved.json()) as {
    media: { purpose: string; mediaType: string; width: number | null }[];
  };
  expect(body.media).toHaveLength(1);
  const [stored] = body.media;
  expect(stored?.purpose).toBe('PHOTO');
  expect(stored?.mediaType).toMatch(/^image\//);
  expect(stored?.width).toBeGreaterThan(0);
});
