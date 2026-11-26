import { setupServer } from 'msw/node';
import { handlers } from './handlers';

// One mock server for every test file (D-14). It starts with the shared handlers in handlers.ts; a single
// test adds its own with server.use(), and setup.ts removes them again after each test.
export const server = setupServer(...handlers);
