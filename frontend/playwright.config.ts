import { defineConfig, devices } from '@playwright/test';

// The end-to-end smoke tests (task 4.17). They drive the real app in Chromium against the real stack: the Compose
// services (Postgres, SeaweedFS), the backend in the `local` profile on :8080 and the Vite dev server on :5173, which
// proxies /api to the backend. Nothing is mocked, so a pass means the screens, the generated client, the API and the
// storage agree (docs/DEVELOPMENT.md, "End-to-end tests").
//
// Playwright starts the Vite dev server itself, or reuses one that is already up. It does not start the backend:
// Gradle's bootRun reads ../.env and needs JDK 25, so it stays a command you run yourself. e2e/global-setup.ts checks
// that the backend answers before any test runs, and says how to start it when it doesn't.
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  // Both tests write to the one seeded dev journal (D-11), so they run one at a time: two captures saving at once
  // would make each test's "the newest entry is mine" check depend on timing.
  fullyParallel: false,
  workers: 1,
  // A test.only left in by mistake fails the run instead of quietly skipping the rest.
  forbidOnly: true,
  // No retries: a smoke test that passes on the second try is hiding a real race.
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: 'http://localhost:5173',
    // The trace (DOM snapshots, network, console) is kept only for a failed test: `npx playwright show-trace`.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // The app works out "today" in the user's zone (users.timezone), never the browser's, so the browser is put in
    // another zone on purpose: a date that leaned on the browser's clock would show up here (CLAUDE.md, rule 2).
    timezoneId: 'UTC',
    locale: 'en-AU',
  },
  // Capture is a Vaul sheet on phones and a dialog on laptops (DESIGN.md §4 Write), and the seal lives in a
  // different place in each, so both layouts are driven. Chromium only (TECH_STACK §3, D-13).
  projects: [
    {
      name: 'phone',
      use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } },
    },
    {
      name: 'laptop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
