import { Page, Pending } from '../../components/Page';

// The Eras, at /eras (DESIGN.md §6). Task 4.9 builds the list, the Era page and New Era.
export function ErasPage() {
  return (
    <Page title="Eras">
      <Pending>Your Eras arrive with task 4.9.</Pending>
    </Page>
  );
}
