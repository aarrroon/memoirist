import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';
import { createQueryClient } from './api/queryClient';
import { createAppRouter } from './router';
import './index.css';

const root = document.getElementById('root');
if (!root) throw new Error('index.html has no #root element');

// TanStack Query owns every piece of server data (D-13); the defaults are in api/queryClient.ts.
const queryClient = createQueryClient();
// The routes and the shell are in router.tsx (task 4.3). RouterProvider comes from react-router/dom, the build
// that knows about the browser's DOM.
const router = createAppRouter();

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
