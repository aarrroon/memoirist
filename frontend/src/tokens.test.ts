import { describe, expect, test } from 'vitest';
import css from './index.css?raw';

// Guards the design tokens (D-40) against a change to DESIGN.md §1 or a slip in index.css.
// The audit (tools/audit.mjs) checks contrast only on what a screen renders; this checks every token pair
// that a screen may use, in both modes, before any screen uses it.

/** The custom properties declared in the first CSS block whose header matches, e.g. `:root {`. */
function declarations(header: RegExp): Map<string, string> {
  const start = css.search(header);
  if (start < 0) throw new Error(`index.css has no block matching ${header}`);
  const body = css.slice(css.indexOf('{', start) + 1, css.indexOf('}', start));
  const found = new Map<string, string>();
  for (const [, name, value] of body.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    if (name && value) found.set(name, value.trim());
  }
  return found;
}

const light = declarations(/^:root\s*\{/m);
const dark = declarations(/^\[data-theme=["']?dark["']?\]\s*\{/m);
const utilities = declarations(/^@theme inline\s*\{/m);
const modes = { light, dark };

// TODO(human) PRACTICE: write contrast(a, b), the WCAG 2 contrast ratio of two "#rrggbb" colours (1 to 21).
// Hint: each channel is linearised (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4, with c from 0 to 1),
//       luminance = 0.2126 R + 0.7152 G + 0.0722 B, and the ratio is (lighter + 0.05) / (darker + 0.05).
//       The order of a and b must not matter.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
function luminance(hex: string): number {
  const [r = 0, g = 0, b = 0] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number];
  return (lighter + 0.05) / (darker + 0.05);
}
// └── END ANSWER ▲ ──

describe('the token sets', () => {
  // TODO(human) PRACTICE: assert that dark mode declares exactly the tokens light mode does, and that every one
  // of them is mapped to a Tailwind utility in `@theme inline` (as `var(--name)`), so a token can't be added
  // to one mode only, or exist with no class to use it.
  // Hint: compare sorted name lists, so the failure message names the missing token. This is the same idea as
  //       the backend's EntryMapperIsTheOnlyPathTest: a test that enforces a convention, not a behaviour.
  // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
  test('dark mode declares every token light mode does, and no others', () => {
    expect([...dark.keys()].sort()).toEqual([...light.keys()].sort());
  });

  test('every mode token is a Tailwind utility', () => {
    const mapped = new Set(utilities.values());
    const unmapped = [...light.keys()].filter((name) => !mapped.has(`var(${name})`));
    expect(unmapped).toEqual([]);
  });
  // └── END ANSWER ▲ ──
});

// Text is 4.5:1 (WCAG 1.4.3); a control's border is 3:1 against what it sits on (WCAG 1.4.11, design.md §8).
// The pairs are DESIGN.md §1's. The two it forbids, accent text on raised in light (4.26:1) and a control
// border on raised in dark (2.7:1), are left out on purpose.
const text = 4.5;
const nonText = 3;
const pairs: [foreground: string, background: string, minimum: number][] = [
  ['--ink', '--page', text],
  ['--ink', '--surface', text],
  ['--ink', '--raised', text],
  ['--ink-2', '--page', text],
  ['--ink-2', '--surface', text],
  ['--ink-2', '--raised', text],
  ['--accent', '--page', text],
  ['--accent', '--surface', text],
  ['--on-accent', '--accent', text],
  ['--danger', '--page', text],
  ['--danger', '--surface', text],
  ['--on-band', '--band', text],
  ['--on-band-2', '--band', text],
  ['--control', '--page', nonText],
  ['--control', '--surface', nonText],
];

describe.each(Object.entries(modes))('%s mode', (mode, tokens) => {
  test.each(pairs)('%s on %s is at least %d:1', (foreground, background, minimum) => {
    // contrast() reads "#rrggbb" only, so a missing token or another colour syntax fails here, by name.
    const [fg = '', bg = ''] = [tokens.get(foreground), tokens.get(background)];
    expect(fg, `${foreground} in ${mode} mode`).toMatch(/^#[0-9a-f]{6}$/i);
    expect(bg, `${background} in ${mode} mode`).toMatch(/^#[0-9a-f]{6}$/i);
    expect(contrast(fg, bg)).toBeGreaterThanOrEqual(minimum);
  });
});

test('contrast() gives the known extremes', () => {
  expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 5);
  expect(contrast('#ffffff', '#000000')).toBeCloseTo(21, 5);
  expect(contrast('#777777', '#777777')).toBe(1);
});
