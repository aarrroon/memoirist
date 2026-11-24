import { setupServer } from 'msw/node';

// One mock server for every test file (D-14). Handlers that many tests share are added here from task 4.2;
// a single test adds its own with server.use(), and setup.ts removes them again after each test.
export const server = setupServer();
