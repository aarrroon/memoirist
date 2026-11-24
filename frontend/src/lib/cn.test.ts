import { expect, test } from 'vitest';
import { cn } from './cn';

test('a text size and a text colour are both kept', () => {
  expect(cn('text-body', 'text-ink-2')).toBe('text-body text-ink-2');
});

test('a later text size replaces an earlier one', () => {
  expect(cn('text-body text-ink', 'text-meta')).toBe('text-ink text-meta');
});

test('a later colour replaces an earlier one, and the size stays', () => {
  expect(cn('text-small text-ink-2', 'text-ink')).toBe('text-small text-ink');
});

test('the strong weight and the float shadow merge like their Tailwind cousins', () => {
  expect(cn('font-normal', 'font-strong')).toBe('font-strong');
  expect(cn('shadow-float', 'shadow-none')).toBe('shadow-none');
});

test('falsy values are dropped', () => {
  expect(cn('px-4', false, undefined, 'py-2')).toBe('px-4 py-2');
});
