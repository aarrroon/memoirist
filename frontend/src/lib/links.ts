// Links inside an entry's own text (task 4.5). The server already refuses a caption whose link isn't http, https
// or mailto (D-05, EntryContentValidator.isAllowedLink), so every href that reaches the screen should pass. The
// screen checks again anyway: an href is the one place a stored caption could run script in the reader's browser
// (`javascript:`), and the cost of the second check is one function.

/** Control characters (U+0000 to U+001F and U+007F). Browsers delete tabs and line breaks inside an address before
 *  they read its scheme, so "java\tscript:" would run as "javascript:". */
// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f]/;

// TODO(human) PRACTICE: Write safeHref(href): the address to put in <a href>, or null when the link must be shown
// as plain text. Allow http:, https: and mailto: only, in any capitals, with something after the scheme.
// Hint: an allow-list of what you accept, never a deny-list of what you fear ("javascript:", "data:", "vbscript:"
// and whatever comes next). Mirror the server's rule in TECH_STACK (Content, "Links are an allow-list of prefixes"):
// strip spaces at both ends, refuse any control character anywhere, and don't use `new URL()`, which accepts
// "javascript:alert(1)" as a perfectly good URL. Return the trimmed address, so a stray space doesn't reach the DOM.
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
export function safeHref(href: unknown): string | null {
  if (typeof href !== 'string') return null;
  const address = href.trim();
  if (CONTROL.test(address)) return null;
  const lower = address.toLowerCase();
  for (const scheme of ['http://', 'https://', 'mailto:']) {
    if (lower.startsWith(scheme) && address.length > scheme.length) return address;
  }
  return null;
}
// └── END ANSWER ▲ ──
