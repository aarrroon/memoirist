import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createQueryClient } from './api/queryClient';
import App from './App';
import './index.css';

const root = document.getElementById('root');
if (!root) throw new Error('index.html has no #root element');

// TanStack Query owns every piece of server data (D-13); the defaults are in api/queryClient.ts.
const queryClient = createQueryClient();

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
