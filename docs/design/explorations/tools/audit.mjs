// audit.mjs: checks pages against memoirist's design rule (.claude/rules/design.md, D-40) in headless Chrome.
// No dependencies: Node 22+ (for WebSocket) and Chrome (set CHROME if it isn't in the default place).
//
// usage: node audit.mjs [--serve <folder>] [--slow] [--dark] [--out <folder>] <target> [<target> ...]
//        node audit.mjs --self-test
//   <target>          a URL, or with --serve a path inside the folder, e.g. "round-2/directions/folio.html#feed"
//   --serve <folder>  serve that folder on a local port for the run (the explorations need no build)
//   --slow            also load each target on a slow phone (slow 4G, a 4x slower processor, an empty cache)
//   --dark            ask for the dark colour scheme (prefers-color-scheme: dark)
//   --out <folder>    keep each result as JSON, and a screenshot of each width, in that folder
//   --mockup          for the explorations: their sample photos are full size and their fonts come from Google, so photo size and calls to other hosts only warn
//   --self-test       load a page with known faults and a clean page, and check that the right checks fail
// Every target is loaded at phone width (375x812, touch) and at laptop width (1440x900).
// The exit code is 1 when any check FAILs, so a script can stop on it.
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { readFileSync, writeFileSync, mkdirSync, rmSync, existsSync, statSync } from 'node:fs';
import { join, extname, normalize, sep, resolve } from 'node:path';
import { tmpdir } from 'node:os';

// The limits come from .claude/rules/design.md. Change them there first, then here.
const LIMITS = {
  spacing: [4, 8, 12, 16, 24, 32, 48, 64], // px: every padding and gap
  sizes: 7, minSize: 12, families: 2, weights: 3, leadings: 3,
  radii: 2, // besides "full" (pills and circles)
  hues: 1, target: 44, nest: 2, tabGap: 8,
  oversize: 2, cls: 0.1, lcpWarn: 2500, lcpFail: 4000, fontKb: 150,
};
const PHONE = { name: 'phone', width: 375, height: 812, dpr: 2, mobile: true };
const LAPTOP = { name: 'laptop', width: 1440, height: 900, dpr: 1, mobile: false };

// ---------- arguments ----------
const args = process.argv.slice(2);
const flag = (name) => args.includes(name);
const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : null);
const SELF_TEST = flag('--self-test');
const SERVE = option('--serve');
const OUT = option('--out');
const targets = args.filter((a, i) => !a.startsWith('--') && !['--serve', '--out'].includes(args[i - 1]));
if (!SELF_TEST && targets.length === 0) {
  console.log(readFileSync(new URL(import.meta.url), 'utf8').split('\n').slice(0, 14).map((l) => l.replace(/^\/\/ ?/, '')).join('\n'));
  process.exit(2);
}
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------- a small static server (for --serve and the self-test) ----------
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css', '.jpg': 'image/jpeg', '.png': 'image/png', '.json': 'application/json', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const SELF_PAGES = {
  // Every rule broken once: the self-test expects each of these checks to FAIL.
  '/faults.html': `<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>
    body { margin: 0; font: 16px/1.5 Arial; background: #fff; color: #000 }
    .a { font-family: Georgia; font-size: 11px; color: #999; padding: 5px } .b { font-family: "Courier New"; font-size: 13px; line-height: 1.1 }
    .c { font-family: Verdana; font-size: 15px; line-height: 1.3; font-weight: 700 } .d { font-size: 17px; font-weight: 300; line-height: 1.7 }
    .e { font-size: 19px; font-weight: 500 } .f { font-size: 21px } .g { font-size: 23px } .h { font-size: 25px }
    button { width: 20px; height: 20px; padding: 0; border: 0; background: #c00; border-radius: 3px }
    .box { background: #eee; border-radius: 10px; padding: 8px } .box .box { background: #ddd; border-radius: 6px } .box .box .box { background: #ccc; border-radius: 14px }
    .wide { width: 600px; height: 10px; background: #00c } .hue { background: #0a0; color: #fff; padding: 8px }
    </style><p class="a">tiny grey text</p><p class="b">mono</p><p class="c">bold</p><p class="d">d</p><p class="e">e</p><p class="f">f</p><p class="g">g</p><p class="h">h</p>
    <button aria-label="small"></button><div class="box">one<div class="box">two<div class="box">three, nested three deep</div></div></div><div class="wide"></div><div class="hue">green</div>`,
  // Nothing broken: the self-test expects no FAIL here.
  '/clean.html': `<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>
    body { margin: 0; padding: 16px; font: 16px/1.5 Arial; background: #f5f5f5; color: #111 } h1 { font-size: 28px; line-height: 1.2; font-weight: 600; margin: 0 0 16px }
    .card { background: #fff; border-radius: 16px; padding: 16px } .meta { font-size: 14px; color: #555 }
    button { min-height: 44px; min-width: 44px; padding: 8px 16px; border: 0; border-radius: 999px; background: #2e5a44; color: #fff; font: inherit; font-weight: 600 }
    </style><h1>Monday on the 19</h1><div class="card"><p class="meta">Mon 26 Oct</p><p>Back on the 19 tram with everyone.</p><button>Write</button></div>`,
};
const ROOT = SERVE ? resolve(SERVE) : null;
const server = createServer((req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (SELF_TEST && SELF_PAGES[pathname]) { res.writeHead(200, { 'content-type': TYPES['.html'] }); res.end(SELF_PAGES[pathname]); return; }
  const path = ROOT ? normalize(join(ROOT, pathname)) : null;
  if (!path || !path.startsWith(normalize(ROOT + sep)) || !existsSync(path) || !statSync(path).isFile()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': TYPES[extname(path)] || 'application/octet-stream', 'cache-control': 'no-store' });
  res.end(readFileSync(path));
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const BASE = `http://127.0.0.1:${server.address().port}`;
const urlOf = (t) => (/^(https?|file):/.test(t) ? t : `${BASE}/${t.replace(/^\//, '')}`);

// ---------- the probe: this function runs inside the page ----------
async function probe() {
  await new Promise((r) => setTimeout(r, 400));
  const vw = innerWidth, vh = innerHeight;
  const round = (n, d = 1) => Math.round(n * 10 ** d) / 10 ** d;
  const bump = (map, k, by = 1) => { map[k] = (map[k] || 0) + by; };
  const parseColor = (s) => {
    const m = /rgba?\(([^)]+)\)/.exec(s || '');
    if (!m) return null;
    const p = m[1].split(/[\s,/]+/).filter(Boolean).map(parseFloat);
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  };
  const hex = (c) => '#' + [c.r, c.g, c.b].map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');
  // WCAG 2 relative luminance and contrast ratio.
  const lum = ({ r, g, b }) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
  const over = (top, base) => ({ r: top.r * top.a + base.r * (1 - top.a), g: top.g * top.a + base.g * (1 - top.a), b: top.b * top.a + base.b * (1 - top.a), a: 1 });
  const sat = ({ r, g, b }) => { const mx = Math.max(r, g, b) / 255, mn = Math.min(r, g, b) / 255; const l = (mx + mn) / 2; return mx === mn ? 0 : (mx - mn) / (1 - Math.abs(2 * l - 1)); };
  const hue = ({ r, g, b }) => { const mx = Math.max(r, g, b), mn = Math.min(r, g, b); if (mx === mn) return 0; const d = mx - mn; let h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4; h *= 60; return h < 0 ? h + 360 : h; };

  // rendered: the browser paints it. shown: it is also there for everyone (not inert, not hidden from screen readers).
  const rendered = (el) => {
    if (el.closest('[hidden]')) return false;
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden') return false;
    const r = el.getBoundingClientRect();
    return r.width > 2 && r.height > 2;
  };
  const shown = (el) => !el.closest('[inert], [aria-hidden="true"]') && rendered(el);
  // Text laid over a drawing or a photo has no background colour of its own to measure, so a person must look.
  const overArt = (el) => {
    el.scrollIntoView({ block: 'center' });
    const r = el.getBoundingClientRect();
    const stack = document.elementsFromPoint(r.left + r.width / 2, r.top + r.height / 2);
    const below = stack.slice(stack.indexOf(el) + 1).find((n) => !n.contains(el));
    return !!below && below.closest('svg, img, canvas, video') !== null;
  };
  const ownText = (el) => [...el.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent).join(' ').replace(/\s+/g, ' ').trim();
  const opacityOf = (el) => { let o = 1; for (let n = el; n && n.nodeType === 1; n = n.parentElement) o *= parseFloat(getComputedStyle(n).opacity); return o; };

  // TODO(human) PRACTICE: write backdrop(el): the colour a reader sees behind an element's text.
  // An element often has no background of its own, and a parent's can be see-through (a scrim, a tinted chip).
  // Hint: walk up the parents collecting backgrounds until one is opaque, then blend them from the bottom up
  //       with over(). Stop early at an opaque layer; anything further up is hidden. Start from white.
  // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
  const backdrop = (el) => {
    const layers = [];
    let image = false;
    for (let n = el; n && n.nodeType === 1; n = n.parentElement) {
      const cs = getComputedStyle(n);
      if (cs.backgroundImage && cs.backgroundImage !== 'none') image = true;
      const c = parseColor(cs.backgroundColor);
      if (c && c.a > 0) { layers.push(c); if (c.a >= 1) break; }
    }
    let base = { r: 255, g: 255, b: 255, a: 1 };
    for (let i = layers.length - 1; i >= 0; i--) base = over(layers[i], base);
    return { colour: base, image };
  };
  // └── END ANSWER ▲ ──

  const visible = [...document.body.querySelectorAll('*')].filter((el) => !['SCRIPT', 'STYLE', 'NOSCRIPT'].includes(el.tagName) && shown(el));

  // ---------- text: sizes, weights, families, line heights, contrast, clipping ----------
  const sizes = {}, weights = {}, families = {}, leading = {}, textColours = {};
  const lowContrast = [], tiny = [], clipped = [], onArt = [];
  let textRuns = 0, drawnText = 0;
  for (const el of visible) {
    const own = ownText(el);
    if (!own) continue;
    const cs = getComputedStyle(el);
    const svg = el instanceof SVGElement;
    const fs = round(parseFloat(cs.fontSize) * (svg && el.getScreenCTM ? Math.abs(el.getScreenCTM()?.a || 1) : 1), 1);
    const fw = parseInt(cs.fontWeight, 10) || 400;
    textRuns++;
    if (svg) drawnText++;
    else { bump(sizes, fs); bump(weights, fw); bump(leading, cs.lineHeight === 'normal' ? 'normal' : round(Math.round((parseFloat(cs.lineHeight) / parseFloat(cs.fontSize)) * 20) / 20, 2)); }
    bump(families, cs.fontFamily.split(',')[0].replace(/["']/g, '').trim());
    const raw = parseColor(svg ? cs.fill : cs.color);
    if (raw && !svg) {
      const bg = backdrop(el);
      const fg = over({ ...raw, a: raw.a * opacityOf(el) }, bg.colour);
      bump(textColours, hex(fg));
      const cr = ratio(fg, bg.colour);
      const large = fs >= 24 || (fs >= 18.66 && fw >= 700);
      if (cr < (large ? 3 : 4.5)) (bg.image || overArt(el) ? onArt : lowContrast).push(`${round(cr, 2)}:1 ${fs}px ${hex(fg)} on ${hex(bg.colour)} "${own.slice(0, 28)}"`);
    }
    if (fs < 12) tiny.push(`${fs}px "${own.slice(0, 24)}"`);
    if (!svg && (cs.overflow !== 'visible' || cs.textOverflow === 'ellipsis')) {
      const clamped = cs.webkitLineClamp && cs.webkitLineClamp !== 'none';
      if (el.scrollWidth > el.clientWidth + 1 && !clamped) clipped.push(`"${own.slice(0, 32)}"`);
    }
  }
  window.scrollTo(0, 0); // overArt() may have scrolled; everything below measures the first screen

  // ---------- spacing: every padding and gap, as the browser computed it ----------
  const spacing = {};
  for (const el of visible) {
    const cs = getComputedStyle(el);
    const values = ['paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft', 'rowGap', 'columnGap'].map((k) => parseFloat(cs[k]));
    for (const v of values) if (Number.isFinite(v) && v >= 3) bump(spacing, Math.round(v * 2) / 2);
  }

  // ---------- shapes: corner radii, boxes inside boxes, the hues that fill something ----------
  const radii = {}, bgColours = {}, borderColours = {}, drawnColours = {};
  const isBox = (el) => {
    const cs = getComputedStyle(el);
    const bg = parseColor(cs.backgroundColor);
    const border = parseFloat(cs.borderTopWidth) > 0 && cs.borderTopStyle !== 'none';
    const r = el.getBoundingClientRect();
    return ((bg && bg.a > 0.2) || border) && parseFloat(cs.borderTopLeftRadius) > 0 && r.width >= 40 && r.height >= 28 && r.width < vw * 0.98 && cs.position !== 'fixed';
  };
  for (const el of visible) {
    const cs = getComputedStyle(el);
    const bg = parseColor(cs.backgroundColor);
    const border = ['Top', 'Right', 'Bottom', 'Left'].some((s) => parseFloat(cs[`border${s}Width`]) > 0 && cs[`border${s}Style`] !== 'none');
    if ((bg && bg.a > 0) || border || el.tagName === 'IMG') {
      const rad = parseFloat(cs.borderTopLeftRadius);
      const r = el.getBoundingClientRect();
      if (rad > 0) bump(radii, cs.borderTopLeftRadius.includes('%') || rad >= Math.min(r.width, r.height) / 2 - 0.5 ? 'full' : `${round(rad, 1)}px`);
    }
    if (bg && bg.a > 0) bump(bgColours, hex(over(bg, backdrop(el.parentElement || el).colour)));
    if (border) { const bc = parseColor(cs.borderTopColor); if (bc && bc.a > 0) bump(borderColours, hex(bc)); }
  }
  for (const el of document.querySelectorAll('svg *')) {
    if (!shown(el)) continue;
    const cs = getComputedStyle(el);
    for (const k of ['fill', 'stroke']) { const c = parseColor(cs[k]); if (c && c.a > 0) bump(drawnColours, hex(c)); }
  }
  const hueFamilies = (maps) => {
    const buckets = {};
    for (const m of maps) for (const h of Object.keys(m)) {
      const c = { r: parseInt(h.slice(1, 3), 16), g: parseInt(h.slice(3, 5), 16), b: parseInt(h.slice(5, 7), 16) };
      const l = lum(c);
      if (sat(c) >= 0.3 && l > 0.03 && l < 0.92) { const b = (Math.round(hue(c) / 30) * 30) % 360; (buckets[b] = buckets[b] || []).push(h); }
    }
    return buckets;
  };
  const boxes = visible.filter(isBox);
  const depthOf = (el) => { let d = 0; for (let n = el; n && n !== document.body; n = n.parentElement) if (isBox(n)) d++; return d; };

  // ---------- things to tap, and the bar at the bottom of a phone screen ----------
  const small = [];
  let tappable = 0;
  for (const el of visible) {
    if (!el.matches('a[href], button, input, select, textarea, summary, [role="button"], [role="tab"], [role="link"], [role="menuitem"], [tabindex]:not([tabindex="-1"])') || el.disabled) continue;
    if (getComputedStyle(el).display === 'inline') continue; // a link inside a sentence
    const r = el.getBoundingClientRect();
    let w = r.width, h = r.height;
    for (const pseudo of ['::before', '::after']) { // a see-through pseudo-element can enlarge the hit area
      const ps = getComputedStyle(el, pseudo);
      if (ps.content === 'none' || ps.position !== 'absolute') continue;
      const [t, rt, b, l] = ['top', 'right', 'bottom', 'left'].map((k) => parseFloat(ps[k]));
      if ([t, rt, b, l].every(Number.isFinite)) { w = Math.max(w, r.width - l - rt); h = Math.max(h, r.height - t - b); }
    }
    tappable++;
    if (w < 43.5 || h < 43.5) small.push(`"${(el.getAttribute('aria-label') || el.textContent || el.tagName).replace(/\s+/g, ' ').trim().slice(0, 22)}" ${Math.round(w)}x${Math.round(h)}`);
  }
  const bar = visible.find((el) => { const r = el.getBoundingClientRect(); return getComputedStyle(el).position === 'fixed' && r.bottom >= vh - 2 && r.width >= vw * 0.9 && r.height < 120 && r.top > vh / 2; });
  let tabBar = null;
  if (bar) {
    const items = [...bar.querySelectorAll('a, button')].filter(shown).map((el) => {
      const range = document.createRange(); range.selectNodeContents(el);
      const text = range.getBoundingClientRect();
      const painted = [el, ...el.querySelectorAll('*')].filter(shown).map((n) => ({ r: n.getBoundingClientRect(), bg: parseColor(getComputedStyle(n).backgroundColor) })).filter((x) => x.bg && x.bg.a > 0.5).map((x) => x.r);
      return { left: Math.min(text.left, ...painted.map((p) => p.left)), right: Math.max(text.right, ...painted.map((p) => p.right)), size: parseFloat(getComputedStyle(el).fontSize) };
    });
    tabBar = { height: Math.round(bar.getBoundingClientRect().height), items: items.length, minGap: Math.round(Math.min(...items.slice(1).map((it, i) => it.left - items[i].right))), labelSizes: [...new Set(items.map((i) => i.size))] };
  }

  // ---------- images ----------
  const dpr = devicePixelRatio;
  // Every image the browser paints counts here, even one hidden from screen readers or under a dialog: it still downloads.
  const images = [...document.images].filter(rendered).map((img) => {
    const r = img.getBoundingClientRect();
    return { file: (img.currentSrc || img.src).split('/').pop().split('?')[0].slice(0, 28), top: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height), oversize: round(img.naturalWidth / Math.max(1, r.width * dpr), 1), lazy: img.loading === 'lazy', priority: img.fetchPriority === 'high', behind: !!img.closest('[inert]') };
  });
  const big = images.find((i) => !i.behind && i.w >= Math.min(vw, 760) * 0.5 && i.h >= 120);
  const rowsAbove = big ? new Set(visible.filter((el) => ownText(el) && el.getBoundingClientRect().bottom + scrollY <= big.top).map((el) => Math.round(el.getBoundingClientRect().top / 6))).size : null;

  // ---------- the emptiest stretch of the first screen ----------
  const cols = new Array(Math.ceil(vw / 8)).fill(0), rows = new Array(Math.ceil(vh / 8)).fill(0);
  for (const el of visible) {
    if (!ownText(el) && el.tagName !== 'IMG' && el.tagName !== 'svg') continue;
    const r = el.getBoundingClientRect();
    if (r.bottom < 0 || r.top > vh) continue;
    for (let x = Math.max(0, Math.floor(r.left / 8)); x < Math.min(cols.length, Math.ceil(r.right / 8)); x++) cols[x] = 1;
    for (let y = Math.max(0, Math.floor(r.top / 8)); y < Math.min(rows.length, Math.ceil(Math.min(r.bottom, vh) / 8)); y++) rows[y] = 1;
  }
  const longestEmpty = (arr) => { let best = 0, run = 0; for (const v of arr) { run = v ? 0 : run + 1; best = Math.max(best, run); } return best * 8; };

  // ---------- loading ----------
  const observe = (type) => new Promise((res) => { try { const seen = []; const po = new PerformanceObserver((l) => seen.push(...l.getEntries())); po.observe({ type, buffered: true }); setTimeout(() => { po.disconnect(); res(seen); }, 120); } catch { res([]); } });
  const lcps = await observe('largest-contentful-paint');
  const shifts = await observe('layout-shift');
  const lcp = lcps[lcps.length - 1];
  const paints = Object.fromEntries(performance.getEntriesByType('paint').map((p) => [p.name, Math.round(p.startTime)]));

  return {
    vw, vh, scrollWidth: document.documentElement.scrollWidth, textRuns, drawnText,
    sizes, weights, families, leading, tiny, lowContrast, onArt, clipped, spacing,
    radii, hues: hueFamilies([textColours, bgColours, borderColours]), drawnHues: hueFamilies([drawnColours]),
    boxes: { total: boxes.length, firstScreen: boxes.filter((b) => { const r = b.getBoundingClientRect(); return r.top < vh && r.bottom > 0; }).length, depth: Math.max(0, ...boxes.map(depthOf)) },
    tappable, small, tabBar, images, firstPhotoTop: big ? big.top : null, rowsAbove,
    emptyAcross: longestEmpty(cols), emptyDown: longestEmpty(rows),
    timing: { fcp: paints['first-contentful-paint'] ?? null, lcp: lcp ? Math.round(lcp.startTime) : null, lcpWhat: lcp?.element ? `${lcp.element.tagName.toLowerCase()}${lcp.url ? ' ' + lcp.url.split('/').pop().split('?')[0] : ''}` : null, cls: round(shifts.filter((s) => !s.hadRecentInput).reduce((a, s) => a + s.value, 0), 3) },
  };
}

// ---------- judging a result against the limits ----------
const list = (map, n = 12) => Object.entries(map).sort((a, b) => b[1] - a[1]).slice(0, n).map(([k, v]) => `${k}×${v}`).join(' ');
function judge(r, vp, net) {
  const out = [];
  const add = (ok, name, text, soft = false) => out.push({ level: ok ? 'PASS' : soft ? 'WARN' : 'FAIL', name, text });
  const off = Object.fromEntries(Object.entries(r.spacing).filter(([k]) => !LIMITS.spacing.includes(Number(k))));
  add(Object.keys(off).length === 0, 'spacing', `${Object.keys(r.spacing).length} padding and gap values${Object.keys(off).length ? `, ${Object.keys(off).length} off the scale: ${list(off).replace(/×/g, 'px×')}` : ', all on the scale'}`);
  const sizes = Object.keys(r.sizes).map(Number).sort((a, b) => a - b);
  add(sizes.length <= LIMITS.sizes && r.tiny.length === 0 && Object.keys(r.weights).length <= LIMITS.weights && Object.keys(r.leading).length <= LIMITS.leadings, 'type',
    `${sizes.length} sizes (max ${LIMITS.sizes}): ${sizes.join(' ')}; ${Object.keys(r.weights).length} weights (max ${LIMITS.weights}); ${Object.keys(r.leading).length} line heights (max ${LIMITS.leadings})${r.tiny.length ? `; under ${LIMITS.minSize}px: ${r.tiny.slice(0, 3).join(', ')}` : ''}`);
  add(Object.keys(r.families).length <= LIMITS.families, 'fonts', `${Object.keys(r.families).length} typefaces (max ${LIMITS.families}): ${Object.keys(r.families).join(', ')}`);
  const radii = Object.keys(r.radii).filter((k) => k !== 'full');
  add(radii.length <= LIMITS.radii, 'corners', `${radii.length} radii besides full (max ${LIMITS.radii}): ${radii.join(' ') || 'none'}`);
  add(r.boxes.depth <= LIMITS.nest, 'boxes', `${r.boxes.firstScreen} rounded boxes on the first screen, nested ${r.boxes.depth} deep (max ${LIMITS.nest})`);
  const hues = new Set([...Object.keys(r.hues), ...Object.keys(r.drawnHues)]);
  add(hues.size <= LIMITS.hues, 'accent', `${hues.size} hue${hues.size === 1 ? '' : 's'} besides photos (max ${LIMITS.hues}): ${[...hues].map((h) => `${h}° ${(r.hues[h] || r.drawnHues[h])[0]}`).join(', ') || 'none'}`);
  add(r.lowContrast.length === 0, 'contrast', r.lowContrast.length ? `${r.lowContrast.length} of ${r.textRuns} text runs too faint: ${r.lowContrast.slice(0, 3).join('; ')}` : `all ${r.textRuns} text runs pass`);
  if (vp.mobile) add(r.small.length === 0, 'targets', r.small.length ? `${r.small.length} of ${r.tappable} under ${LIMITS.target}px: ${r.small.slice(0, 5).join(', ')}` : `all ${r.tappable} are ${LIMITS.target}px or more`);
  if (vp.mobile && r.tabBar) add(r.tabBar.minGap >= LIMITS.tabGap && r.tabBar.labelSizes.length === 1, 'tab bar', `${r.tabBar.items} items, closest neighbours ${r.tabBar.minGap}px apart (min ${LIMITS.tabGap}), label sizes ${r.tabBar.labelSizes.join(' and ')}px`);
  // Compare with the device's width, not innerWidth: a phone widens its layout to fit content that overflows.
  add(r.scrollWidth <= vp.width, 'overflow', `page is ${r.scrollWidth}px wide on a ${vp.width}px screen`);
  add(r.clipped.length === 0, 'clipped', r.clipped.length ? `${r.clipped.length} cut off: ${r.clipped.slice(0, 3).join(', ')}` : 'no text cut off', true);
  add(r.drawnText === 0 && r.onArt.length === 0, 'text on art', `${r.drawnText} text runs inside drawings, ${r.onArt.length} laid over a drawing or photo${r.onArt.length ? ` (check by eye: ${r.onArt.slice(0, 2).join('; ')})` : ''}`, true);
  const eager = r.images.filter((i) => !i.lazy && i.top > r.vh * 1.5), heavy = r.images.filter((i) => i.oversize > LIMITS.oversize);
  const mockup = flag('--mockup'); // a mockup can't shrink its sample photos or host its own fonts
  if (r.images.length) add(eager.length === 0 && (mockup || heavy.length === 0), 'images', `${r.images.length} images: ${eager.length} below the first screen load at once, ${heavy.length} are over ${LIMITS.oversize}x the width they are shown at${heavy.length ? ` (worst ${Math.max(...heavy.map((i) => i.oversize))}x, ${heavy.sort((a, b) => b.oversize - a.oversize)[0].file})` : ''}`);
  add(r.timing.cls <= LIMITS.cls, 'shift', `layout shift ${r.timing.cls} (max ${LIMITS.cls})`);
  const others = [...new Set(net.map((n) => { try { return new URL(n.url).host; } catch { return ''; } }).filter((h) => h && !/^(127\.0\.0\.1|localhost)(:|$)/.test(h) && h !== new URL(r.url).host))];
  add(others.length === 0, 'other hosts', others.length ? `the page calls ${others.join(', ')}` : 'no requests to other hosts', mockup);
  out.push({ level: 'INFO', name: 'first screen', text: `${r.firstPhotoTop == null ? 'no large photo' : `first photo starts ${r.firstPhotoTop}px down (${Math.round((r.firstPhotoTop / r.vh) * 100)}%), after ${r.rowsAbove} rows of text`}; emptiest stretch ${r.emptyAcross}px across, ${r.emptyDown}px down` });
  return out;
}
function judgeSlow(r, net) {
  const kb = (type) => Math.round(net.filter((n) => !type || n.type === type).reduce((a, n) => a + n.bytes, 0) / 1024);
  const { lcp, cls, lcpWhat } = r.timing;
  return [
    { level: lcp == null || lcp > LIMITS.lcpFail ? 'FAIL' : lcp > LIMITS.lcpWarn ? 'WARN' : 'PASS', name: 'slow phone', text: `largest thing on screen (${lcpWhat}) painted at ${lcp == null ? '?' : (lcp / 1000).toFixed(1)}s (good is ${LIMITS.lcpWarn / 1000}s, fail over ${LIMITS.lcpFail / 1000}s); shift ${cls}; ${kb()} KB in ${net.length} requests (images ${kb('Image')} KB, fonts ${kb('Font')} KB, scripts ${kb('Script')} KB)` },
    { level: kb('Font') > LIMITS.fontKb ? 'FAIL' : 'PASS', name: 'font weight', text: `${kb('Font')} KB of fonts (max ${LIMITS.fontKb})` },
  ];
}

// ---------- Chrome, over the DevTools Protocol ----------
const PORT = 9400 + Math.floor(Math.random() * 500);
const profile = join(tmpdir(), 'audit-profile-' + PORT);
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, '--hide-scrollbars', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--force-color-profile=srgb', '--allow-file-access-from-files', 'about:blank'], { stdio: 'ignore' });
async function pageSocketUrl() {
  for (let i = 0; i < 150; i++) {
    try { const tabs = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); const page = tabs.find((t) => t.type === 'page'); if (page) return page.webSocketDebuggerUrl; } catch {}
    await sleep(100);
  }
  throw new Error(`Chrome did not start (${CHROME}). Set CHROME to its path.`);
}
const ws = new WebSocket(await pageSocketUrl());
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
let nextId = 0;
const pending = new Map(), listeners = new Set();
ws.addEventListener('message', (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) { const { res, rej } = pending.get(msg.id); pending.delete(msg.id); msg.error ? rej(new Error(msg.error.message)) : res(msg.result); }
  else if (msg.method) for (const fn of listeners) fn(msg);
});
const send = (method, params = {}) => new Promise((res, rej) => { const id = ++nextId; pending.set(id, { res, rej }); ws.send(JSON.stringify({ id, method, params })); });

let net = new Map(), problems = [], lastActivity = Date.now();
const inflight = new Set();
listeners.add((m) => {
  if (m.method === 'Network.requestWillBeSent') { inflight.add(m.params.requestId); lastActivity = Date.now(); }
  if (m.method === 'Network.responseReceived') net.set(m.params.requestId, { url: m.params.response.url, type: m.params.type, bytes: 0 });
  if (m.method === 'Network.loadingFinished' || m.method === 'Network.loadingFailed') { inflight.delete(m.params.requestId); lastActivity = Date.now(); if (net.has(m.params.requestId)) net.get(m.params.requestId).bytes = m.params.encodedDataLength || 0; }
  if (m.method === 'Runtime.exceptionThrown') problems.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
  if (m.method === 'Runtime.consoleAPICalled' && ['error', 'assert'].includes(m.params.type)) problems.push(`${m.params.type}: ` + m.params.args.map((a) => a.value ?? a.description).join(' '));
});
await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');

async function load(url, vp, slow) {
  await send('Emulation.setDeviceMetricsOverride', { width: vp.width, height: vp.height, deviceScaleFactor: vp.dpr, mobile: vp.mobile });
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: flag('--dark') ? 'dark' : 'light' }] });
  await send('Network.setCacheDisabled', { cacheDisabled: slow });
  // Lighthouse's phone profile: a slow 4G line (1.6 Mbps down, 150 ms delay) and a mid-range phone's processor.
  await send('Network.emulateNetworkConditions', slow ? { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 } : { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
  await send('Emulation.setCPUThrottlingRate', { rate: slow ? 4 : 1 });
  await send('Page.navigate', { url: 'about:blank' });
  await sleep(150);
  net = new Map(); problems = []; inflight.clear(); lastActivity = Date.now();
  await send('Page.navigate', { url });

  // TODO(human) PRACTICE: wait until the page has really finished loading, or give up after a deadline.
  // Waiting for the first "load" event measures too early: about:blank fires one too, and a page that draws
  // itself with script asks for its photos after its own load event.
  // Hint: ready = the real page says readyState "complete" AND no request is in flight AND the network has been
  //       quiet for a moment (longer on the slow line). Poll for it; never wait without a deadline.
  // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
  const deadline = Date.now() + (slow ? 180000 : 30000);
  const quietFor = slow ? 2500 : 500;
  let ready = false;
  while (Date.now() < deadline && !ready) {
    const state = await send('Runtime.evaluate', { returnByValue: true, expression: `location.href !== 'about:blank' && document.readyState === 'complete'` }).catch(() => null);
    ready = state?.result?.value === true && inflight.size === 0 && Date.now() - lastActivity > quietFor;
    if (!ready) await sleep(150);
  }
  // └── END ANSWER ▲ ──
  if (!ready) problems.push(`gave up waiting for ${url} to finish loading`);
  await send('Runtime.evaluate', { expression: 'document.fonts.ready.then(() => true)', awaitPromise: true });
  const ev = await send('Runtime.evaluate', { expression: `(${probe.toString()})()`, awaitPromise: true, returnByValue: true });
  if (!ev.result?.value) throw new Error(`probe failed on ${url}: ${ev.exceptionDetails?.exception?.description || ev.exceptionDetails?.text}`);
  return { ...ev.result.value, url, network: [...net.values()], problems: [...new Set(problems)] };
}

// ---------- run ----------
const jobs = SELF_TEST ? ['faults.html', 'clean.html'] : targets;
const fails = [];
const seen = {}; // target -> phone checks, for the self-test
if (OUT) mkdirSync(OUT, { recursive: true });
try {
  for (const target of jobs) {
    const url = urlOf(target);
    const slug = target.replace(/^https?:\/\//, '').replace(/[^a-z0-9#.-]+/gi, '_').replace(/#/g, '.');
    for (const vp of [PHONE, LAPTOP]) {
      const r = await load(url, vp, false);
      const checks = judge(r, vp, r.network);
      if (vp.mobile) seen[target] = checks;
      console.log(`\n${target}  ${vp.name} ${vp.width}x${vp.height}`);
      for (const c of checks) console.log(`  ${c.level} ${c.name.padEnd(12)} ${c.text}`);
      if (r.problems.length) console.log(`  PAGE PROBLEMS: ${r.problems.join(' | ').slice(0, 300)}`);
      fails.push(...checks.filter((c) => c.level === 'FAIL').map((c) => `${target} ${vp.name}: ${c.name}`));
      if (OUT) {
        writeFileSync(join(OUT, `${slug}.${vp.name}.json`), JSON.stringify({ target, viewport: vp.name, checks, ...r }, null, 1));
        const shot = await send('Page.captureScreenshot', { format: 'jpeg', quality: 75, clip: { x: 0, y: 0, width: vp.width, height: vp.height, scale: 1 } });
        writeFileSync(join(OUT, `${slug}.${vp.name}.jpg`), Buffer.from(shot.data, 'base64'));
      }
    }
    if (flag('--slow')) {
      const r = await load(url, PHONE, true);
      const checks = judgeSlow(r, r.network);
      for (const c of checks) console.log(`  ${c.level} ${c.name.padEnd(12)} ${c.text}`);
      fails.push(...checks.filter((c) => c.level === 'FAIL').map((c) => `${target} slow phone: ${c.name}`));
      if (OUT) writeFileSync(join(OUT, `${slug}.slow.json`), JSON.stringify({ target, checks, timing: r.timing, network: r.network }, null, 1));
    }
  }
} finally {
  ws.close(); chrome.kill(); server.close();
  await sleep(300);
  try { rmSync(profile, { recursive: true, force: true }); } catch {}
}

if (SELF_TEST) {
  // The checker must be able to fail: every fault planted in faults.html is reported, and clean.html raises none.
  const failed = (t) => seen[t].filter((c) => c.level === 'FAIL').map((c) => c.name);
  const expected = ['spacing', 'type', 'fonts', 'corners', 'boxes', 'accent', 'contrast', 'targets', 'overflow'];
  const missed = expected.filter((name) => !failed('faults.html').includes(name));
  const falseAlarms = failed('clean.html');
  console.log(`\nSELF-TEST: ${missed.length === 0 && falseAlarms.length === 0 ? 'passed' : 'FAILED'}${missed.length ? `; not caught: ${missed.join(', ')}` : ''}${falseAlarms.length ? `; false alarms on the clean page: ${falseAlarms.join(', ')}` : ''}`);
  process.exit(missed.length === 0 && falseAlarms.length === 0 ? 0 : 1);
}
console.log(`\n${fails.length === 0 ? 'No check failed.' : `${fails.length} checks failed.`}${OUT ? ` Results and screenshots: ${OUT}` : ''}`);
process.exit(fails.length === 0 ? 0 : 1);
