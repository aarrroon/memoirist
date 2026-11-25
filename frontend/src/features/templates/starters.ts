import type { TemplateFields } from '../../api/templates';

// The starter gallery (D-06, task 4.9). It lives in the app, not the database, so it can change without a
// migration; adding one makes the user's own copy with POST /templates, which they can then change however they
// like. Names from D-06; the prompts are the round 3 mockup's (DESIGN.md §6 `#templates`).
export const STARTERS: readonly TemplateFields[] = [
  { name: 'Trip day', subheadings: ['Best moment', 'What I want to remember'] },
  {
    name: 'Spiralling check-in',
    subheadings: ['What is on my mind', 'What I can control', 'One kind thing for tomorrow'],
  },
  { name: 'Night out', subheadings: ['Who was there', 'The best bit'] },
  { name: 'Letter to future me', subheadings: ['Dear future me', 'What I hope has changed'] },
  { name: 'My week', subheadings: ['The highlight', 'What I would do again'] },
];

/** A name compared as the user would: ignoring case and spaces at the ends. */
function key(name: string): string {
  return name.trim().toLocaleLowerCase('en-AU');
}

/** The starters the user doesn't already have a template of the same name for, so the gallery never offers a twin. */
export function startersToOffer(own: readonly { name: string }[]): TemplateFields[] {
  const names = new Set(own.map((template) => key(template.name)));
  return STARTERS.filter((starter) => !names.has(key(starter.name)));
}
