import { isRouteErrorResponse, Link, useRouteError } from 'react-router';
import { Page } from './Page';

// An address no route knows. The shell stays around it, with a back link to the Journal (places.ts).
export function NotFoundPage() {
  return (
    <Page title="Not here">
      <p className="text-body text-ink-2">There’s nothing at this address.</p>
      <Link to="/" className="flex min-h-11 items-center text-body font-strong text-accent">
        Go to your journal
      </Link>
    </Page>
  );
}

// A screen that threw while drawing. It replaces the shell, so it carries its own way home. Never technical detail
// (DESIGN.md §7): the error is for the console, not the page.
export function RouteErrorPage() {
  const error = useRouteError();
  const missing = isRouteErrorResponse(error) && error.status === 404;
  return (
    <main className="mx-auto flex w-full max-w-160 flex-col gap-4 px-4 py-8 md:px-6">
      <title>Something went wrong · memoirist</title>
      <h1 className="font-display text-heading">{missing ? 'Not here' : 'Something went wrong'}</h1>
      <p className="text-body text-ink-2">Anything you were writing is still here.</p>
      <Link to="/" className="flex min-h-11 items-center text-body font-strong text-accent">
        Go to your journal
      </Link>
    </main>
  );
}
