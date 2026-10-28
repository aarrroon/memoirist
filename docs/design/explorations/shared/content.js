// The invented journal every direction renders, so the designs differ and the content never does.
// Shapes follow docs/API_CONTRACT.md: camelCase, dates as YYYY-MM-DD, instants as ISO-8601 in UTC.
// Fields marked "mock" exist only for these mockups (the real API returns a presigned fileUrl, a bare
// Spotify URI and so on). Photos are credited in photos/CREDITS.md.
(function () {
  // TipTap JSON builders, so captions are stored the way the real editor stores them (never as HTML).
  const text = (t, ...marks) => (marks.length ? { type: 'text', text: t, marks: marks.map((m) => ({ type: m })) } : { type: 'text', text: t });
  const p = (...parts) => ({ type: 'paragraph', content: parts.map((x) => (typeof x === 'string' ? text(x) : x)) });
  const heading = (t) => ({ type: 'heading', attrs: { level: 3 }, content: [text(t)] });
  const list = (...items) => ({ type: 'bulletList', content: items.map((t) => ({ type: 'listItem', content: [p(t)] })) });
  const doc = (...content) => ({ type: 'doc', content });

  // Media. `kind` (mock) mirrors the API's firstMediaKind: PHOTO, PAGE_SCAN or VOICE_NOTE.
  const photo = (file, alt) => ({ id: `m-${file}`, kind: 'PHOTO', mediaType: 'image/jpeg', file: `${file}.jpg`, alt });
  const pageScan = (file, alt) => ({ id: `m-${file}`, kind: 'PAGE_SCAN', mediaType: 'image/jpeg', file: `${file}.jpg`, alt });
  const voiceNote = (id, durationSeconds, alt) => ({ id, kind: 'VOICE_NOTE', mediaType: 'audio/webm', durationSeconds, alt });

  const tag = (id, name) => ({ id: `tag-${id}`, name, source: 'USER' });
  const TAGS = {
    work: tag('work', 'Work'), friends: tag('friends', 'Friends'), grampians: tag('grampians', 'Grampians'),
    outdoors: tag('outdoors', 'Outdoors'), walks: tag('walks', 'Walks'), home: tag('home', 'Home'), uni: tag('uni', 'Uni'),
  };

  // Soundtracks: the API stores only a Spotify URI; title and artist (mock) are what search returns.
  const HOLOCENE = { uri: 'spotify:track:0000000000000000000001', kind: 'TRACK', title: 'Holocene', artist: 'Bon Iver' };
  const DEPRESTON = { uri: 'spotify:track:0000000000000000000002', kind: 'TRACK', title: 'Depreston', artist: 'Courtney Barnett' };

  const entry = (e) => ({
    templateId: null, title: null, contentJson: null, tags: [], soundtrack: null, replyToEntryId: null, replyIds: [],
    unlockDate: null, isLocked: false, media: [], updatedAt: e.createdAt, ...e,
  });

  // Eras, latest start first. `colour` (mock) is taken from each cover, for directions that use it.
  const eras = [
    { id: 'era-grampians', title: 'Grampians Long Weekend', description: 'Four of us, one hire car, far too many snacks.',
      startDate: '2026-10-23', endDate: '2026-10-25', soundtrack: null, cover: 'pinnacle-sunrise.jpg', colour: '#F3C33F' },
    { id: 'era-japan', title: 'Japan, finally', description: 'Two weeks, eleven trains, one very small hotel room.',
      startDate: '2025-04-05', endDate: '2025-04-19', soundtrack: null, cover: null, colour: '#C9503A' },
    { id: 'era-brunswick', title: 'The Brunswick Years', description: 'Five of us, one bathroom, a lemon tree that never fruits.',
      startDate: '2024-03-02', endDate: null, soundtrack: DEPRESTON, cover: 'fitzroy-terrace.jpg', colour: '#1E5B48' },
    { id: 'era-uni', title: 'Uni Days', description: 'A double degree, three share houses and a lot of Lygon Street.',
      startDate: '2019-02-25', endDate: '2023-11-24', soundtrack: null, cover: 'lecture-theatre.jpg', colour: '#2F4F7A' },
  ];
  const eraRef = (id) => { const e = eras.find((x) => x.id === id); return { id: e.id, title: e.title }; };

  // The feed: weeks newest first; each week's entries by entryDate, then createdAt (API_CONTRACT §WeeklyLog).
  const weeks = [
    {
      id: 'wk-2026-10-26', title: null, startDate: '2026-10-26', endDate: '2026-11-01', isClosed: false, soundtrack: null,
      eras: [eraRef('era-brunswick')],
      entries: [
        entry({ id: 'e-1026-tram', entryDate: '2026-10-26', createdAt: '2026-10-25T22:14:00Z', title: 'Monday on the 19',
          contentJson: doc(p('Back on the 19 tram with everyone who looks like they spent the weekend outside. Three days ago I watched the sun come up over a valley. Now I am watching a man eat a pie at 8:10 am.')),
          tags: [TAGS.work], media: [photo('sydney-road-tram', 'A green and white tram on Sydney Road, Brunswick, on a bright morning')] }),
        entry({ id: 'e-1027-walk', entryDate: '2026-10-27', createdAt: '2026-10-27T07:31:00Z',
          contentJson: doc(p('Walked home the long way through Princes Park and talked to myself like a podcast. Kept the good bit.')),
          tags: [TAGS.walks], media: [
            photo('princes-park', 'Palm trees and a path through Princes Park in late afternoon light'),
            voiceNote('m-voice-1027', 48, 'Voice note, 48 seconds, recorded walking through Princes Park'),
          ] }),
        entry({ id: 'e-1028-today', entryDate: '2026-10-28', createdAt: '2026-10-28T08:20:00Z', title: 'Post-trip blues',
          contentJson: doc(
            p('Inbox at 214. Spent the morning pretending the weekend was a dream someone else had.'),
            p('Small wins: the café on Lygon remembered my order, and Mei sent the photos from the top. I am framing the one of Jonno holding the soup thermos like a trophy.'),
          ),
          tags: [TAGS.work] }),
      ],
    },
    {
      id: 'wk-2026-10-19', title: 'Grampians Trip Week', startDate: '2026-10-19', endDate: '2026-10-25', isClosed: false, soundtrack: null,
      eras: [eraRef('era-grampians'), eraRef('era-brunswick')],
      entries: [
        entry({ id: 'e-1019-packing', entryDate: '2026-10-19', createdAt: '2026-10-18T21:30:00Z', title: 'Packing list, round two',
          contentJson: doc(
            p('Mei says it is 6° at the top before sunrise. Mei is usually right.'),
            list('Thermals', 'Head torch (the good one)', 'Thermos', 'Snacks for four, which means snacks for six'),
          ),
          tags: [TAGS.grampians], media: [photo('flat-white', 'A flat white with a leaf pattern in the foam')] }),
        entry({ id: 'e-1023-halls-gap', entryDate: '2026-10-23', createdAt: '2026-10-23T11:05:00Z', title: 'Halls Gap, finally',
          contentJson: doc(p('Four hours from Brunswick with one wrong turn at Ararat. A kangaroo on the oval stared at Jonno like he owed it money. Pasta on the camp stove, then the fire until the ranger walked past.')),
          tags: [TAGS.grampians, TAGS.friends], media: [
            photo('halls-gap-ranges', 'The Grampians ranges above gum trees and a dry paddock at Halls Gap'),
            photo('halls-gap-kangaroo', 'An eastern grey kangaroo standing in long grass'),
            photo('campfire', 'Sparks rising from a campfire at night'),
          ] }),
        entry({ id: 'e-1024-pinnacle', entryDate: '2026-10-24', createdAt: '2026-10-23T22:40:00Z', title: 'Sunrise at the Pinnacle',
          templateId: 'tpl-trips',
          contentJson: doc(
            heading('Best moment'),
            p('The cloud sat in the valley like a lake, then burnt off all at once. Nobody said anything for about ten minutes, which for us is a record.'),
            heading('What I want to remember'),
            p('How cold my hands were around the thermos. Mei laughing at the top. Jonno pouring soup like it was champagne.'),
          ),
          tags: [TAGS.grampians, TAGS.friends, TAGS.outdoors], soundtrack: HOLOCENE, media: [
            photo('pinnacle-sunrise', 'The Pinnacle cliff above a sea of cloud at sunrise'),
            photo('pinnacle-lookout', 'The fenced Pinnacle lookout on a sandstone ledge above the valley'),
            photo('grampians-sandstone', 'A honeycombed sandstone overhang on the Wonderland walk'),
            photo('boroka-lookout', 'Lake Bellfield and the valley seen from Boroka Lookout'),
            voiceNote('m-voice-1024', 72, 'Voice note, 1 minute 12 seconds: wind, and Mei laughing at the top'),
          ] }),
        entry({ id: 'e-1024-page', entryDate: '2026-10-24', createdAt: '2026-10-25T08:10:00Z',
          media: [pageScan('diary-page', 'A handwritten diary page dated Saturday 24 October, Halls Gap')] }),
        entry({ id: 'e-1025-falls', entryDate: '2026-10-25', createdAt: '2026-10-25T02:30:00Z', title: 'Mackenzie Falls on the way out',
          contentJson: doc(p('Two hundred and sixty steps down and the same back up. Worth every one. Slept most of the drive home.')),
          tags: [TAGS.grampians, TAGS.outdoors], media: [photo('mackenzie-falls', 'Mackenzie Falls pouring into a dark pool')] }),
        // A Loot Drop as the API returns it while sealed: every private field is already gone (D-10).
        entry({ id: 'e-1025-sealed', entryDate: '2026-10-25', createdAt: '2026-10-25T09:48:00Z',
          unlockDate: '2027-10-24T22:00:00Z', isLocked: true }),
      ],
    },
  ];

  // On this day: the same day and month in earlier years (D-32). Never sealed or deleted entries.
  const onThisDay = [
    entry({ id: 'e-2025-1028', entryDate: '2025-10-28', createdAt: '2025-10-28T11:02:00Z', title: 'First night in the big room',
      contentJson: doc(p('Priya moved out, so I have taken the front room. No bed until Saturday. Slept on the floor with the window open and heard the 19 go past all night.')),
      tags: [TAGS.home], media: [photo('empty-bedroom', 'An empty bedroom with a pillow and a red blanket on the carpet')],
      eras: [eraRef('era-brunswick')] }),
    entry({ id: 'e-2023-1028', entryDate: '2023-10-28', createdAt: '2023-10-28T06:15:00Z', title: 'Last exam, ever',
      contentJson: doc(p('Three hours, two pens, one existential crisis about question four. Dumplings on Lygon Street after, then a very long nap.')),
      tags: [TAGS.uni], media: [photo('lecture-theatre', 'Rows of empty wooden desks in a lecture theatre')],
      eras: [eraRef('era-uni')] }),
  ];

  // The Era page for The Brunswick Years: the feed's weeks plus older weeks as summary rows
  // (GET /eras/{id} returns weeklyLogs with entryCount), and one total row for weeks not listed.
  const eraPages = {
    'era-brunswick': {
      olderWeeks: [
        { id: 'wk-2026-10-12', title: 'Deadline week', startDate: '2026-10-12', endDate: '2026-10-18', entryCount: 4, photoCount: 2, voiceNoteCount: 1 },
        { id: 'wk-2026-10-05', title: null, startDate: '2026-10-05', endDate: '2026-10-11', entryCount: 2, photoCount: 5, voiceNoteCount: 0 },
        { id: 'wk-2026-09-21', title: "Mum's 60th", startDate: '2026-09-21', endDate: '2026-09-27', entryCount: 6, photoCount: 38, voiceNoteCount: 2 },
        { id: 'wk-2026-09-07', title: 'Flu, mostly', startDate: '2026-09-07', endDate: '2026-09-13', entryCount: 1, photoCount: 0, voiceNoteCount: 0 },
        { id: 'wk-2026-08-24', title: 'First warm day', startDate: '2026-08-24', endDate: '2026-08-30', entryCount: 3, photoCount: 7, voiceNoteCount: 0 },
      ],
      notListed: { weekCount: 91, entryCount: 248, photoCount: 1037, voiceNoteCount: 36 },
      chaptersInside: ['era-grampians', 'era-japan'],
    },
  };

  window.MEMOIRIST_CONTENT = {
    // Wednesday 28 October 2026, 7:42 pm in Melbourne (AEDT, UTC+11). Every "today" and countdown uses it.
    now: '2026-10-28T08:42:00Z',
    user: { displayName: 'Sam', timezone: 'Australia/Melbourne', birthday: '1998-08-12' },
    tags: Object.values(TAGS),
    eras, weeks, onThisDay, eraPages,
    // Which items the four screens show.
    featured: { entryId: 'e-1024-pinnacle', eraId: 'era-brunswick', lootDropId: 'e-1025-sealed' },
  };
})();
