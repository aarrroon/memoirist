import { Page, Pending } from '../../components/Page';

// Tags, at /tags: rows with a rename and a delete (DESIGN.md §6, D-26). Task 4.11 builds it.
export function TagsPage() {
  return (
    <Page title="Tags">
      <Pending>Renaming and deleting tags arrives with task 4.11.</Pending>
    </Page>
  );
}
