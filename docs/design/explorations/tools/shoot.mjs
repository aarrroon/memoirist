// shoot.mjs: JPEG screenshots of the design explorations through Chrome's DevTools Protocol.
// No dependencies: Node 22+ (for WebSocket) and Chrome (set CHROME if it isn't in the default place).
// usage: node shoot.mjs jobs.json
//   jobs: [{ url, out, width, height, dpr = 1, mobile = false, full = false, maxHeight, quality = 85, waitMs = 400,
//            selector, scrollTo, scrollOffset = 160, probe }]
//   url:      file:///… works (Chrome runs with --allow-file-access-from-files); a hash such as #loot picks the screen.
//   mobile:   emulates a phone (touch, mobile viewport); use with width 375, height 812, dpr 2.
//   full:     capture the whole page height (capped at maxHeight) instead of the viewport.
//   selector: capture just that element, e.g. a board on index.html ('#board-overview').
//   scrollTo: scroll that element to scrollOffset px below the top first, e.g. '[data-sealed]'.
//   probe:    a JS expression to evaluate and print, for checks such as overflow or the countdown text.
// Prints each file written, then PAGE PROBLEMS: exceptions, console errors, failed console.assert calls and load timeouts.
import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';

const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PORT = 9339 + Math.floor(Math.random() * 300);
const jobs = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const profile = join(tmpdir(), 'shoot-profile-' + PORT);
const chrome = spawn(CHROME, [
  '--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--hide-scrollbars', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--force-color-profile=srgb', '--allow-file-access-from-files', 'about:blank',
], { stdio: 'ignore' });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function pageSocketUrl() {
  for (let i = 0; i < 150; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      const page = list.find((t) => t.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch {}
    await sleep(100);
  }
  throw new Error('Chrome did not start');
}

const ws = new WebSocket(await pageSocketUrl());
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
let nextId = 0;
const pending = new Map();
const listeners = new Set();
ws.addEventListener('message', (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    const { res, rej } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? rej(new Error(msg.error.message)) : res(msg.result);
  } else if (msg.method) for (const fn of listeners) fn(msg);
});
const send = (method, params = {}) => new Promise((res, rej) => {
  const id = ++nextId;
  pending.set(id, { res, rej });
  ws.send(JSON.stringify({ id, method, params }));
});
const waitFor = (method, timeoutMs = 20000) => new Promise((res) => {
  const fn = (m) => { if (m.method === method) { listeners.delete(fn); res(true); } };
  listeners.add(fn);
  setTimeout(() => { listeners.delete(fn); res(false); }, timeoutMs);
});

const problems = [];
listeners.add((m) => {
  if (m.method === 'Runtime.exceptionThrown') problems.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
  if (m.method === 'Runtime.consoleAPICalled' && ['error', 'assert', 'warning'].includes(m.params.type)) {
    problems.push(`${m.params.type}: ` + m.params.args.map((a) => a.value ?? a.description).join(' '));
  }
  if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') problems.push(`${m.params.entry.source}: ${m.params.entry.text} ${m.params.entry.url || ''}`);
});
await send('Page.enable');
await send('Runtime.enable');
await send('Log.enable');

for (const job of jobs) {
  const dpr = job.dpr || 1;
  await send('Emulation.setDeviceMetricsOverride', { width: job.width, height: job.height, deviceScaleFactor: dpr, mobile: !!job.mobile });
  // Go through about:blank so a hash-only change still reloads the page and fires its load event.
  await send('Page.navigate', { url: 'about:blank' });
  const loaded = waitFor('Page.loadEventFired');
  await send('Page.navigate', { url: job.url });
  if (!(await loaded)) problems.push(`timeout loading ${job.url}`);
  await send('Runtime.evaluate', { expression: 'document.fonts.ready.then(() => true)', awaitPromise: true });
  if (job.probe) {
    const pr = await send('Runtime.evaluate', { returnByValue: true, expression: job.probe });
    console.log('probe', JSON.stringify(pr.result.value ?? pr.exceptionDetails?.text));
  }
  if (job.scrollTo) {
    const sc = await send('Runtime.evaluate', { returnByValue: true, expression: `(() => { const el = document.querySelector(${JSON.stringify(job.scrollTo)}); const y = el.getBoundingClientRect().top + scrollY - ${job.scrollOffset ?? 160}; window.scrollTo(0, Math.max(0, y)); window.dispatchEvent(new Event('scroll')); return [y, scrollY, document.scrollingElement.scrollHeight, innerHeight]; })()` });
    console.log('scroll', JSON.stringify(sc.result.value ?? sc.exceptionDetails?.text));
  }
  await sleep(job.waitMs ?? 400);
  let height = job.height;
  let clip = null;
  if (job.selector) {
    const { result } = await send('Runtime.evaluate', { returnByValue: true, expression: `(() => { const r = document.querySelector(${JSON.stringify(job.selector)}).getBoundingClientRect(); return { x: r.left + scrollX, y: r.top + scrollY, width: r.width, height: r.height }; })()` });
    clip = { ...result.value, scale: 1 };
  } else if (job.full) {
    const { result } = await send('Runtime.evaluate', { expression: 'Math.ceil(document.documentElement.scrollHeight)', returnByValue: true });
    height = Math.min(result.value, job.maxHeight || 100000);
  }
  const scrolled = job.scrollTo ? (await send('Runtime.evaluate', { expression: 'scrollY', returnByValue: true })).result.value : 0;
  const { data } = await send('Page.captureScreenshot', {
    format: 'jpeg', quality: job.quality || 85, captureBeyondViewport: !!(job.full || job.selector),
    clip: clip || { x: 0, y: scrolled, width: job.width, height, scale: 1 },
  });
  mkdirSync(dirname(job.out), { recursive: true });
  writeFileSync(job.out, Buffer.from(data, 'base64'));
  console.log(`${job.out}  ${job.width}x${height} @${dpr}x`);
}

if (problems.length) console.log('PAGE PROBLEMS:\n- ' + [...new Set(problems)].join('\n- '));
ws.close();
chrome.kill();
await sleep(300);
try { rmSync(profile, { recursive: true, force: true }); } catch {}
