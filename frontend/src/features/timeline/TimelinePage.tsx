import { Page, Pending } from '../../components/Page';

// The Timeline, at /entries: every entry, newest first, one row each (DESIGN.md §6). Task 4.11 builds it.
export function TimelinePage() {
  return (
    <Page title="Timeline">
      <Pending>Every entry, newest first, arrives with task 4.11.</Pending>
    </Page>
  );
}
