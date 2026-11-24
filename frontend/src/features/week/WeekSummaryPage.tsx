import { Page, Pending } from '../../components/Page';

// A week's summary, at /weeks/:weekId (DESIGN.md §6, D-25, F-15). Task 4.10 builds it from GET /weeks/{id};
// the route already carries the id, read there with useParams().
export function WeekSummaryPage() {
  return (
    <Page title="Week">
      <Pending>This week’s summary arrives with task 4.10.</Pending>
    </Page>
  );
}
