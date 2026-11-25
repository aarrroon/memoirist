import type { Era, EraDetail, EraWeek } from '../../api/eras';
import type { Template } from '../../api/templates';

// Sample Eras and templates for task 4.9's tests, shaped as the server sends them. An Era page's weeks come already
// filtered and counted by the server (D-22): a week whose only entries are sealed, in Recently deleted or outside
// the Era's dates is not in `weeklyLogs` at all (EraWeeksTest.onlyWeeksWithAnEntryToShowAreListed proves that).

const STAMP = '2026-11-01T00:00:00Z';

export function eraOf(
  id: string,
  title: string,
  startDate: string,
  endDate: string | null,
  overrides: Partial<Era> = {},
): Era {
  return {
    id,
    title,
    description: null,
    startDate,
    endDate,
    soundtrack: null,
    cover: null,
    pinnedInFeed: false,
    createdAt: STAMP,
    updatedAt: STAMP,
    ...overrides,
  };
}

export function eraWeek(
  id: string,
  startDate: string,
  endDate: string,
  entryCount: number,
): EraWeek {
  return { id, title: null, startDate, endDate, isClosed: false, entryCount };
}

export function detailOf(era: Era, weeklyLogs: EraWeek[]): EraDetail {
  return { ...era, weeklyLogs };
}

/** Latest start first, as GET /eras sends them. Grampians and Japan sit inside Brunswick. */
export const brunswick = eraOf('era-bru', 'The Brunswick Years', '2024-03-02', null, {
  description: 'Five of us, one bathroom, a lemon tree that never fruits.',
  soundtrack: 'spotify:track:4uLU6hMCjMI75M1A2tKUQC',
});
export const grampians = eraOf('era-gra', 'Grampians Long Weekend', '2026-10-23', '2026-10-25');
export const japan = eraOf('era-jap', 'Japan, finally', '2025-04-05', '2025-04-19');
export const uni = eraOf('era-uni', 'Uni Days', '2019-02-25', '2023-11-24');
export const sampleEras: Era[] = [grampians, japan, brunswick, uni];

export function template(id: string, name: string, subheadings: string[]): Template {
  return { id, name, subheadings, createdAt: STAMP, updatedAt: STAMP };
}
