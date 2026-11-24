import { problemMessage } from './api/problem';
import { useMe } from './api/users';

// The starter page (tasks 4.1 and 4.2). Task 4.2 makes it greet the current user through useMe(), the first
// screen data to come from the API. Task 4.3 replaces it with the router and the app shell, built from
// docs/design/DESIGN.md (D-41).
export default function App() {
  const me = useMe();

  return (
    <main className="flex flex-col gap-2 px-4 py-8 md:px-6 lg:px-8">
      <h1 className="font-display text-heading">memoirist</h1>
      {/* The line keeps its height in every state, so nothing below it moves when the name arrives. */}
      {me.isPending ? (
        <p className="text-body text-ink-2" role="status">
          Opening your journal…
        </p>
      ) : me.isError ? (
        <p className="text-body text-danger" role="alert">
          {problemMessage(me.error)}
        </p>
      ) : (
        <p className="text-body text-ink-2">Welcome back, {me.data.displayName}.</p>
      )}
    </main>
  );
}
