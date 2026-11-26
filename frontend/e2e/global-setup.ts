import { request } from '@playwright/test';
import { DEV_USER, DEV_USER_HEADER } from './fixtures';

// Runs once, after Playwright has started (or found) the Vite dev server and before any test (task 4.17). The smoke
// tests need the real backend, which Playwright does not start (playwright.config.ts), so this asks it for the
// seeded dev user directly on :8080. Without it every test would fail later with a vague "element not found".
export default async function globalSetup(): Promise<void> {
  const api = await request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const me = await api.get('/api/v1/users/me', { headers: { [DEV_USER_HEADER]: DEV_USER } });
    if (!me.ok()) throw new Error(`GET /api/v1/users/me answered ${me.status()}`);
  } catch (cause) {
    throw new Error(
      'The backend is not answering on http://localhost:8080. Start the Compose services and the backend first:\n' +
        '  docker compose up -d\n' +
        "  cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'\n" +
        '(docs/DEVELOPMENT.md, "End-to-end tests")',
      { cause },
    );
  } finally {
    await api.dispose();
  }
}
