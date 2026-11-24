import { problemMessage } from '../../api/problem';
import { useMe } from '../../api/users';
import { Page, Pending } from '../../components/Page';

// The Journal, at / (DESIGN.md §6). Task 4.4 builds the feed here. Until then it keeps 4.2's greeting from
// GET /users/me, the first screen data to come from the API. The moon line above it names the day, so the
// heading is for screen readers only.
export function FeedPage() {
  const me = useMe();

  return (
    <Page title="Journal" hideTitle>
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
        <p className="text-body">Welcome back, {me.data.displayName}.</p>
      )}
      <Pending>Your entries, filed into weeks and Eras, arrive with the feed (task 4.4).</Pending>
    </Page>
  );
}
