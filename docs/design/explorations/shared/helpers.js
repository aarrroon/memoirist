// Pure functions every direction uses, so behaviour is identical everywhere and only the design differs:
// dates in Sam's timezone, counts that leave sealed entries out (D-10), display titles (D-24), safe HTML.
// Load after content.js. Exposes window.M.
(function () {
  const C = window.MEMOIRIST_CONTENT;
  const TZ = C.user.timezone;
  const LOCALE = 'en-AU';
  const NOW = new Date(C.now);
  const PHOTOS = (document.currentScript?.src || '').replace(/helpers\.js(\?.*)?$/, 'photos/');

  // ---------- Safe HTML ----------
  // A tagged template: every interpolated value is escaped unless it is itself html`…` (or raw()),
  // the same idea as JSX escaping. Entry text never reaches innerHTML unescaped.
  class Html { constructor(s) { this.s = s; } toString() { return this.s; } }
  const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
  const esc = (v) => String(v).replace(/[&<>"']/g, (c) => ESC[c]);
  const part = (v) => (v == null || v === false ? '' : v instanceof Html ? v.s : Array.isArray(v) ? v.map(part).join('') : esc(v));
  const html = (strings, ...values) => new Html(strings.reduce((out, s, i) => out + s + (i < values.length ? part(values[i]) : ''), ''));
  const raw = (s) => new Html(s); // only for our own markup, such as icon SVG

  // ---------- Dates ----------
  // A calendar date (YYYY-MM-DD) is formatted in UTC so it never shifts a day; an instant uses Sam's timezone.
  const asDate = (d) => new Date(`${d}T00:00:00Z`);
  const STYLES = {
    day: { weekday: 'short', day: 'numeric', month: 'short' },                  // Sat 24 Oct
    weekday: { weekday: 'long' },                                                // Saturday
    weekdayShort: { weekday: 'short' },                                          // Sat
    dayMonth: { day: 'numeric', month: 'short' },                                // 24 Oct
    dayMonthLong: { day: 'numeric', month: 'long' },                             // 24 October
    dayLong: { weekday: 'long', day: 'numeric', month: 'long' },                 // Saturday 24 October
    long: { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' },   // Saturday 24 October 2026
    medium: { day: 'numeric', month: 'short', year: 'numeric' },                 // 24 Oct 2026
    plain: { day: 'numeric', month: 'long', year: 'numeric' },                   // 24 October 2026
    monthYear: { month: 'long', year: 'numeric' },                               // October 2026
    month: { month: 'short' },                                                   // Oct
    year: { year: 'numeric' },                                                   // 2026
  };
  // formatToParts, joined with single spaces: en-AU would otherwise write "Sat, 24 Oct".
  const date = (d, style = 'medium') => new Intl.DateTimeFormat(LOCALE, { timeZone: 'UTC', ...STYLES[style] })
    .formatToParts(asDate(d)).filter((x) => x.type !== 'literal').map((x) => x.value).join(' ');
  const instant = (iso, opts) => new Intl.DateTimeFormat(LOCALE, { timeZone: TZ, ...opts }).format(new Date(iso));
  const time = (iso) => instant(iso, { hour: 'numeric', minute: '2-digit' });          // 8:48 pm
  const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(NOW);  // 2026-10-28
  const weekdayIndex = (d) => (asDate(d).getUTCDay() + 6) % 7;                         // Monday 0 … Sunday 6
  const dayNumber = (d) => asDate(d).getUTCDate();

  // "19–25 Oct 2026", "26 Oct – 1 Nov 2026", "2 Mar 2024 – now"
  function range(start, end) {
    if (!end) return `${date(start, 'medium')} – now`;
    const [ys, ms] = start.split('-');
    const [ye, me] = end.split('-');
    if (ys === ye && ms === me) return `${dayNumber(start)}–${dayNumber(end)} ${date(end, 'month')} ${ye}`;
    if (ys === ye) return `${date(start, 'dayMonth')} – ${date(end, 'dayMonth')} ${ye}`;
    return `${date(start, 'medium')} – ${date(end, 'medium')}`;
  }

  // "2 years, 7 months" from an Era's start to its end (or today)
  function span(start, end) {
    const [ys, ms, ds] = start.split('-').map(Number);
    const [ye, me, de] = (end || today()).split('-').map(Number);
    let months = (ye - ys) * 12 + (me - ms) - (de < ds ? 1 : 0);
    if (months < 1) {
      const days = Math.round((asDate(end || today()) - asDate(start)) / 86400000) + 1;
      return `${days} day${days === 1 ? '' : 's'}`;
    }
    const y = Math.floor(months / 12), m = months % 12;
    return [y && `${y} year${y === 1 ? '' : 's'}`, m && `${m} month${m === 1 ? '' : 's'}`].filter(Boolean).join(', ');
  }

  function yearsAgo(d) {
    const n = Number(today().slice(0, 4)) - Number(d.slice(0, 4));
    return n === 1 ? 'One year ago' : `${n} years ago`;
  }

  const duration = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; // 1:12

  // ---------- Loot Drops ----------
  // TODO(human) PRACTICE: describe when a sealed Loot Drop opens, for the vault card and the seal confirmation
  // (F-06, F-07). describeUnlock('2027-10-24T22:00:00Z', new Date('2026-10-28T08:42:00Z'), 'Australia/Melbourne')
  // should return { opens: 'Opens Monday 25 October 2027 at 9:00 am', countdown: 'in 362 days' }.
  // Hint: format the instant in the user's timezone, not the device's (rule 2), and count calendar days, not
  // 24-hour periods: only 361 days and 13 hours remain, but a person would say 362. Reload any #loot screen to check.
  function describeUnlock(unlockAt, now, timeZone) {
    // ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
    const when = new Date(unlockAt);
    const opens = new Intl.DateTimeFormat('en-AU', {
      timeZone, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit',
    }).format(when);
    const calendarDay = (d) => Date.parse(new Intl.DateTimeFormat('en-CA', { timeZone }).format(d));
    const days = Math.round((calendarDay(when) - calendarDay(now)) / 86400000);
    const countdown = new Intl.RelativeTimeFormat('en-AU', { numeric: 'auto' }).format(days, 'day');
    return { opens: `Opens ${opens}`, countdown };
    // └── END ANSWER ▲ ──
  }
  const opening = (entry) => describeUnlock(entry.unlockDate, NOW, TZ);

  // ---------- Entries ----------
  const isSealed = (e) => e.isLocked === true;
  const LABELS = { PHOTO: 'Photo', PAGE_SCAN: 'Diary page', VOICE_NOTE: 'Voice note' };
  const textOf = (node) => (node.text || '') + (node.content || []).map(textOf).join('');
  function blocks(doc) { // paragraphs, headings and list items as plain text, in order
    const out = [];
    for (const node of doc?.content || []) {
      if (node.type === 'bulletList') node.content.forEach((li) => out.push({ type: 'item', text: textOf(li) }));
      else out.push({ type: node.type, text: textOf(node) });
    }
    return out.filter((b) => b.text.trim());
  }
  // D-24: the first line that isn't a heading, or the first heading when there is nothing else.
  function previewLine(e) {
    const bs = blocks(e.contentJson);
    const line = (bs.find((b) => b.type !== 'heading') || bs[0])?.text;
    return line ? (line.length > 120 ? `${line.slice(0, 119)}…` : line) : null;
  }
  // D-24: title, else preview line, else a label for the first media item. Sealed entries have none.
  const displayTitle = (e) => (isSealed(e) ? null : e.title || previewLine(e) || LABELS[e.media[0]?.kind] || 'Untitled');
  const images = (e) => e.media.filter((m) => m.kind === 'PHOTO' || m.kind === 'PAGE_SCAN');
  const voiceNotes = (e) => e.media.filter((m) => m.kind === 'VOICE_NOTE');
  const src = (m) => (m.file ? PHOTOS + m.file : null);

  // The caption, from TipTap JSON to escaped HTML. Template headings come out as <h3>.
  function caption(docJson, { headingTag = 'h3' } = {}) {
    const marked = (t) => {
      let s = esc(t.text);
      for (const m of t.marks || []) s = m.type === 'italic' ? `<em>${s}</em>` : m.type === 'bold' ? `<strong>${s}</strong>` : s;
      return s;
    };
    const inline = (n) => (n.content || []).map(marked).join('');
    const node = (n) => {
      if (n.type === 'paragraph') return `<p>${inline(n)}</p>`;
      if (n.type === 'heading') return `<${headingTag}>${inline(n)}</${headingTag}>`;
      if (n.type === 'bulletList') return `<ul>${n.content.map((li) => `<li>${li.content.map(inline).join('')}</li>`).join('')}</ul>`;
      return '';
    };
    return raw((docJson?.content || []).map(node).join(''));
  }

  const allEntries = () => [...C.weeks.flatMap((w) => w.entries), ...C.onThisDay];
  const entry = (id) => allEntries().find((e) => e.id === id);
  const weekOf = (id) => C.weeks.find((w) => w.entries.some((e) => e.id === id));

  // ---------- Weeks ----------
  const isCurrentWeek = (w) => w.startDate <= today() && today() <= w.endDate;
  const weekLabel = (w) => w.title || (isCurrentWeek(w) ? 'This week' : range(w.startDate, w.endDate));

  // The week summary's facts (D-25). Sealed entries never count (D-10); they appear only as locked bars.
  function weekStats(w) {
    const open = w.entries.filter((e) => !isSealed(e));
    const days = Array(7).fill(false);
    open.forEach((e) => { days[weekdayIndex(e.entryDate)] = true; });
    const tagCounts = new Map();
    open.flatMap((e) => e.tags).forEach((t) => tagCounts.set(t.name, (tagCounts.get(t.name) || 0) + 1));
    return {
      entryCount: open.length,
      photoCount: open.reduce((n, e) => n + images(e).length, 0), // diary pages count as photos (API_CONTRACT)
      pageCount: open.reduce((n, e) => n + e.media.filter((m) => m.kind === 'PAGE_SCAN').length, 0),
      voiceNoteCount: open.reduce((n, e) => n + voiceNotes(e).length, 0),
      sealedCount: w.entries.length - open.length,
      days,
      tags: [...tagCounts].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name)),
      thumbs: open.flatMap(images),
    };
  }

  // Feed groups: consecutive weeks under the Era that started most recently (Q18); the rest are chips.
  function feedGroups() {
    const groups = [];
    for (const w of C.weeks) {
      const lead = w.eras[0] ? era(w.eras[0].id) : null;
      const last = groups[groups.length - 1];
      if (last && last.era?.id === lead?.id) last.weeks.push(w);
      else groups.push({ era: lead, weeks: [w] });
    }
    return groups;
  }

  // ---------- Eras ----------
  const era = (id) => C.eras.find((e) => e.id === id);
  const cover = (e) => (e?.cover ? PHOTOS + e.cover : null);
  const inEra = (e, d) => e.startDate <= d && (!e.endDate || d <= e.endDate);
  const overlaps = (e, w) => e.startDate <= w.endDate && (!e.endDate || e.endDate >= w.startDate);

  // The Era page (F-10, D-22): its weeks newest first, counting only entries inside its dates.
  function eraPage(id) {
    const e = era(id);
    const page = C.eraPages[id] || { olderWeeks: [], notListed: null, chaptersInside: [] };
    const recent = C.weeks.filter((w) => overlaps(e, w)).map((w) => {
      const inside = { ...w, entries: w.entries.filter((x) => inEra(e, x.entryDate)) };
      const s = weekStats(inside);
      return { id: w.id, title: weekLabel(w), startDate: w.startDate, endDate: w.endDate, entryCount: s.entryCount,
        photoCount: s.photoCount, voiceNoteCount: s.voiceNoteCount, sealedCount: s.sealedCount, thumbs: s.thumbs, isCurrent: isCurrentWeek(w) };
    });
    const older = page.olderWeeks.map((w) => ({ ...w, title: w.title || range(w.startDate, w.endDate), thumbs: [], sealedCount: 0, isCurrent: false }));
    const rows = [...recent, ...older];
    const rest = page.notListed || { weekCount: 0, entryCount: 0, photoCount: 0, voiceNoteCount: 0 };
    const sum = (k) => rows.reduce((n, r) => n + r[k], 0) + rest[k];
    return {
      era: e, rows, notListedWeeks: rest.weekCount,
      totals: { weekCount: rows.length + rest.weekCount, entryCount: sum('entryCount'), photoCount: sum('photoCount'), voiceNoteCount: sum('voiceNoteCount') },
      chaptersInside: page.chaptersInside.map(era),
      span: span(e.startDate, e.endDate),
    };
  }

  // ---------- Moon (for directions that show the sky) ----------
  // Days since a known new moon (6 Jan 2000, 18:14 UTC), over the synodic month.
  function moon(d) {
    const SYNODIC = 29.530588853;
    const age = (((asDate(d) - Date.UTC(2000, 0, 6, 18, 14)) / 86400000 + 0.5) % SYNODIC + SYNODIC) % SYNODIC;
    const phase = age / SYNODIC;
    const illumination = (1 - Math.cos(2 * Math.PI * phase)) / 2;
    const waxing = phase < 0.5;
    const name = illumination >= 0.985 ? 'Full moon' : illumination <= 0.015 ? 'New moon'
      : Math.abs(illumination - 0.5) < 0.06 ? (waxing ? 'First quarter' : 'Last quarter')
      : `${waxing ? 'Waxing' : 'Waning'} ${illumination < 0.5 ? 'crescent' : 'gibbous'}`;
    return { age, phase, illumination, waxing, name };
  }

  // ---------- Mock randomness ----------
  // A random number generator (mulberry32) seeded by a string, so every mock "random" value is the same on
  // every load and in every direction.
  function seeded(str) {
    let seed = [...str].reduce((h, c) => Math.imul(h ^ c.charCodeAt(0), 2654435761), 7);
    return () => { seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  }

  // ---------- Voice notes ----------
  // Bar heights from 0.18 to 1, the same every time for the same note.
  function waveform(id, n = 48) {
    const rnd = seeded(id);
    return Array.from({ length: n }, (_, i) => +(0.18 + 0.82 * Math.abs(Math.sin(i / 3.1)) * (0.45 + 0.55 * rnd())).toFixed(2));
  }

  // ---------- An Era week by week (for charts) ----------
  // The listed weeks plus a stand-in for the weeks the mock doesn't list: the right number of weeks, with
  // entries that add up to the not-listed total, busier inside chapters such as a trip. Oldest first.
  const addDays = (d, n) => { const x = asDate(d); x.setUTCDate(x.getUTCDate() + n); return x.toISOString().slice(0, 10); };
  const mondayOf = (d) => addDays(d, -weekdayIndex(d));
  function eraSeries(id) {
    const page = eraPage(id);
    const listed = page.rows.map((r) => ({ startDate: r.startDate, entryCount: r.entryCount, title: r.title, listed: true })).reverse();
    const rest = C.eraPages[id]?.notListed;
    if (!rest || !rest.weekCount) return listed;
    const e = era(id);
    const mondays = [];
    for (let d = mondayOf(e.startDate); d < listed[0].startDate; d = addDays(d, 7)) mondays.push(d);
    const inner = (m) => C.eras.find((x) => x.id !== id && x.startDate <= addDays(m, 6) && (x.endDate || '9999') >= m && inEra(e, x.startDate));
    const rnd = seeded(id);
    const weighted = mondays.map((m) => ({ m, w: inner(m) ? 8 : 0.4 + rnd() * 2.6, era: inner(m) }));
    const picked = [...weighted].sort((a, b) => b.w - a.w).slice(0, rest.weekCount).sort((a, b) => (a.m < b.m ? -1 : 1));
    const spare = rest.entryCount - picked.length;
    const totalW = picked.reduce((n, x) => n + x.w, 0);
    picked.forEach((x) => { x.entryCount = 1 + Math.floor((x.w / totalW) * spare); });
    let left = rest.entryCount - picked.reduce((n, x) => n + x.entryCount, 0);
    [...picked].sort((a, b) => b.w - a.w).forEach((x) => { if (left > 0) { x.entryCount += 1; left -= 1; } });
    return [...picked.map((x) => ({ startDate: x.m, entryCount: x.entryCount, title: x.era ? x.era.title : null, listed: false })), ...listed];
  }

  // ---------- Icons (24px grid, stroked with currentColor) ----------
  const ICONS = {
    lock: '<rect x="4.5" y="10.5" width="15" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>',
    play: '<path d="M8 5.5v13l10.5-6.5z" fill="currentColor"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/>',
    image: '<rect x="3.5" y="4.5" width="17" height="15" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="m20.5 16-4.5-4.5-8 8"/>',
    page: '<path d="M6 3.5h8.5l3.5 3.5v13.5H6z"/><path d="M14.5 3.5V7h3.5M9 11h6M9 14h6M9 17h4"/>',
    tag: '<path d="M3.5 12.2V4.5a1 1 0 0 1 1-1h7.7l8.3 8.3a1.4 1.4 0 0 1 0 2l-6.2 6.2a1.4 1.4 0 0 1-2 0z"/><circle cx="8.2" cy="8.2" r="1.3"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    home: '<path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"/>',
    list: '<path d="M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"/>',
    book: '<path d="M2.5 4.5h6a3.5 3.5 0 0 1 3.5 3.5v12.5a2.5 2.5 0 0 0-2.5-2.5h-7zM21.5 4.5h-6A3.5 3.5 0 0 0 12 8v12.5a2.5 2.5 0 0 1 2.5-2.5h7z"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    music: '<path d="M9 18V5.5l11-2V16"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="17.5" cy="16" r="2.5"/>',
    chevronRight: '<path d="m9 6 6 6-6 6"/>',
    chevronLeft: '<path d="m15 6-6 6 6 6"/>',
    arrowLeft: '<path d="M19 12H5m6-6-6 6 6 6"/>',
    more: '<circle cx="5" cy="12" r="1.1"/><circle cx="12" cy="12" r="1.1"/><circle cx="19" cy="12" r="1.1"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
    sliders: '<path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    photos: '<rect x="7" y="7" width="13.5" height="13.5" rx="2"/><path d="M4 16.5V5a1.5 1.5 0 0 1 1.5-1.5H17"/>',
    pen: '<path d="M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17z"/>',
    trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13M9 7V4h6v3"/>',
    layers: '<path d="m12 3 9 5-9 5-9-5zM3 13l9 5 9-5"/>',
  };
  // Spotify's mark (Simple Icons), filled.
  const SPOTIFY = '<path fill="currentColor" stroke="none" d="M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z"/>';
  const icon = (name, { size = 20, stroke = 1.6, label } = {}) => raw(
    `<svg class="icon" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round" ${label ? `role="img" aria-label="${esc(label)}"` : 'aria-hidden="true"'}>${name === 'spotify' ? SPOTIFY : ICONS[name]}</svg>`);

  // ---------- Write ----------
  // One Write button that opens a small sheet: New entry, Photo dump, Diary page (review A4). The pencil is kept
  // for Edit, so Write uses a plus.
  let writeCount = 0;
  function writeMenu({ label = 'Write', cls = '' } = {}) {
    const id = `write-menu-${++writeCount}`;
    return html`<div class="write ${cls}">
      <button class="write-btn" type="button" aria-haspopup="menu" aria-expanded="false" aria-controls="${id}">${icon('plus', { size: 20, stroke: 2.2 })}<span>${label}</span></button>
      <div class="write-menu" id="${id}" role="menu" hidden>
        <button type="button" role="menuitem">${icon('pen', { size: 18 })}New entry</button>
        <button type="button" role="menuitem">${icon('image', { size: 18 })}Photo dump</button>
        <button type="button" role="menuitem">${icon('page', { size: 18 })}Diary page</button>
      </div></div>`;
  }
  document.addEventListener('click', (ev) => {
    const btn = ev.target.closest('.write-btn');
    document.querySelectorAll('.write-btn').forEach((b) => {
      const menu = document.getElementById(b.getAttribute('aria-controls'));
      const open = b === btn && menu.hidden;
      menu.hidden = !open;
      b.setAttribute('aria-expanded', String(open));
    });
  });
  document.addEventListener('keydown', (ev) => {
    if (ev.key !== 'Escape') return;
    document.querySelectorAll('.write-menu').forEach((m) => { m.hidden = true; });
    document.querySelectorAll('.write-btn').forEach((b) => b.setAttribute('aria-expanded', 'false'));
  });

  // ---------- Screens ----------
  // The hash picks the screen: #feed (default), #entry, #era, #loot.
  const SCREENS = ['feed', 'entry', 'era', 'loot'];
  const screen = () => (SCREENS.includes(location.hash.slice(1)) ? location.hash.slice(1) : 'feed');
  function mount(render) {
    const go = () => { document.documentElement.dataset.screen = screen(); render(screen()); window.scrollTo(0, 0); };
    window.addEventListener('hashchange', go);
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', go); else go();
  }

  window.M = {
    data: C, now: NOW, timeZone: TZ,
    html, raw, esc,
    date, instant, time, today, range, span, yearsAgo, duration, weekdayIndex,
    describeUnlock, opening,
    isSealed, previewLine, displayTitle, images, voiceNotes, src, caption, entry, weekOf, allEntries,
    weekLabel, weekStats, isCurrentWeek, feedGroups,
    era, cover, eraPage, eraSeries,
    moon, waveform, seeded, addDays, icon,
    screen, mount, writeMenu,
    featured: {
      entry: () => entry(C.featured.entryId),
      era: () => era(C.featured.eraId),
      lootDrop: () => entry(C.featured.lootDropId),
    },
  };

  // Figures that must agree wherever they appear (the screenshot tool reports failed asserts).
  const trip = weekStats(C.weeks[1]);
  console.assert(trip.entryCount === 5 && trip.photoCount === 10 && trip.voiceNoteCount === 1 && trip.sealedCount === 1, 'Grampians Trip Week counts', trip);
  const brunswick = eraPage('era-brunswick');
  console.assert(brunswick.totals.entryCount === brunswick.rows.reduce((n, r) => n + r.entryCount, 0) + C.eraPages['era-brunswick'].notListed.entryCount, 'Era totals add up');
  console.assert(today() === '2026-10-28', 'mock today is Wednesday 28 October 2026');
  const series = eraSeries('era-brunswick');
  console.assert(series.length === brunswick.totals.weekCount && series.reduce((n, w) => n + w.entryCount, 0) === brunswick.totals.entryCount, 'Era series matches the Era totals');
})();
