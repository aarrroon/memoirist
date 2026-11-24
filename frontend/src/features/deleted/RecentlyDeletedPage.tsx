import { Page, Pending } from '../../components/Page';

// Recently deleted, at /recently-deleted: entries wait 30 days to be restored or erased (DESIGN.md §6, D-30).
// Task 4.12 builds it.
export function RecentlyDeletedPage() {
  return (
    <Page title="Recently deleted">
      <Pending>Deleted entries wait here for 30 days. The list arrives with task 4.12.</Pending>
    </Page>
  );
}
