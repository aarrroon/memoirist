import { expect, test } from 'vitest';
import html from '../index.html?raw';
import css from './index.css?raw';

// Guards the font wiring (DESIGN.md §2, design.md §6): a renamed file, a token naming a face the app doesn't
// serve, or a preload of the wrong file would each fail quietly in the browser, with a fallback face or a
// double download, and pass every other check.

// The files the app serves from public/fonts. import.meta.glob only lists them; nothing is loaded.
const served = Object.keys(import.meta.glob('../public/fonts/*.woff2')).map((path) =>
  path.replace('../public', ''),
);

const faces = [...css.matchAll(/@font-face\s*\{([^}]*)\}/g)].map(([, body = '']) => ({
  family: /font-family:\s*['"]([^'"]+)['"]/.exec(body)?.[1],
  src: /url\(['"]?([^'")]+)['"]?\)/.exec(body)?.[1],
  display: /font-display:\s*([\w-]+)/.exec(body)?.[1],
}));

test('every @font-face points at a file the app serves, and swaps while it loads', () => {
  expect(faces).not.toHaveLength(0);
  for (const face of faces) {
    expect(served).toContain(face.src);
    expect(face.display).toBe('swap');
  }
});

test.each(['--font-text', '--font-display'])('%s starts with a face the app serves', (token) => {
  const first = new RegExp(`${token}:\\s*['"]?([^'",;]+)`).exec(css)?.[1];
  expect(faces.map((face) => face.family)).toContain(first);
});

test('index.html preloads the text face, and only it', () => {
  const preloads = [...html.matchAll(/<link\b[^>]*>/g)]
    .map(([tag]) => tag)
    .filter((tag) => /rel="preload"/.test(tag) && /as="font"/.test(tag))
    .map((tag) => /href="([^"]+)"/.exec(tag)?.[1]);
  const textFace = faces.find((face) => face.family === 'Instrument Sans');

  expect(preloads).toEqual([textFace?.src]);
});
