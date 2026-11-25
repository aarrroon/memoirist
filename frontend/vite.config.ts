/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    // The API runs on :8080 (TECH_STACK §1). Proxying /api keeps the browser on one origin, so the backend
    // needs no CORS rules in development. The path is kept as it is: /api/v1/... reaches /api/v1/....
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Above the 5 s findBy wait (src/test/setup.ts) plus the 10 s ones of the lazy capture and seal panels, so a
    // slow lookup fails as "Unable to find", not as a test timeout that hides which element was missing.
    testTimeout: 20_000,
    // Vitest swaps CSS for an empty string unless a file is listed here; tokens.test.ts reads the token file.
    css: { include: [/src\/index\.css/] },
  },
});
