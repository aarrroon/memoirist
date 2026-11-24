import { describe, expect, it } from 'vitest';
import { safeHref } from './links';

describe('safeHref', () => {
  it('keeps http, https and mailto links, in any capitals', () => {
    expect(safeHref('https://example.com/a?b=c')).toBe('https://example.com/a?b=c');
    expect(safeHref('http://example.com')).toBe('http://example.com');
    expect(safeHref('mailto:someone@example.com')).toBe('mailto:someone@example.com');
    expect(safeHref('HTTPS://EXAMPLE.COM')).toBe('HTTPS://EXAMPLE.COM');
  });

  it('trims spaces at both ends', () => {
    expect(safeHref('  https://example.com  ')).toBe('https://example.com');
  });

  it('refuses every other scheme', () => {
    for (const href of [
      'javascript:alert(1)',
      'JavaScript:alert(1)',
      ' javascript:alert(1)',
      'data:text/html,<script>alert(1)</script>',
      'vbscript:msgbox(1)',
      'file:///etc/passwd',
      'ftp://example.com',
      '//example.com',
      '/relative/path',
      'example.com',
    ]) {
      expect(safeHref(href), href).toBeNull();
    }
  });

  it('refuses a control character anywhere, which a browser would delete before reading the scheme', () => {
    expect(safeHref('java\tscript:alert(1)')).toBeNull();
    expect(safeHref('https://exa\nmple.com')).toBeNull();
    expect(safeHref('https://example.com\u0000')).toBeNull();
  });

  it('refuses a scheme with nothing after it, and anything that is not a string', () => {
    expect(safeHref('https://')).toBeNull();
    expect(safeHref('mailto:')).toBeNull();
    expect(safeHref('')).toBeNull();
    expect(safeHref(undefined)).toBeNull();
    expect(safeHref(42)).toBeNull();
    expect(safeHref({ href: 'https://example.com' })).toBeNull();
  });
});
