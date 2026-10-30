# API Contract: memoirist

> The intended HTTP contract. Change this file **in the same change** as the code (contract first, then implementation). Once the backend runs, `/v3/api-docs` is the machine-readable version, and `/sync-docs` keeps the two aligned.

## Global rules

| Rule | Value |
|---|---|
| Base URL | `/api/v1` |
| Format | JSON, camelCase. Media upload uses `multipart/form-data` |
| Instants | ISO-8601 UTC with `Z`, e.g. `2026-10-25T10:20:00Z` |
| Calendar dates | `YYYY-MM-DD`, meaning the user's local calendar day |
| IDs | UUID strings |
| Docs | Swagger UI at `/swagger-ui.html`; one OpenAPI tag per resource (Users, Eras, Templates, Tags, Feed, Weekly Logs, Entries, Media, Music) |

### Who is the current user?
- **Never** send a user ID in a body, query string or path.
- **Phase 1 (`local` and `test` profiles only):** an optional `X-Dev-User-Id: <uuid>` header. Without it the server uses the seeded dev user `00000000-0000-0000-0000-000000000001`.
  - The value must be a UUID in its usual form (8-4-4-4-12 hexadecimal digits, capitals allowed), and the header must be sent once. Anything else, including an empty value, is `400 malformed-request`, never a fall-back to the dev user.
  - Reading the header doesn't check that the user exists. A read for a user who isn't there is `404`, like any resource that isn't found. What a create does for such a user (a foreign key error or a `404`) is settled in task 3.2.
  - In any other profile there is no provider that reads the header, so it is ignored.
- **Phase 2:** `Authorization: Bearer <jwt>` (or a cookie; see open question Q2). Controller signatures do not change.
- A resource that belongs to someone else behaves exactly like one that doesn't exist: `404`.

### Errors (RFC 9457 `ProblemDetail`)
```json
{
  "type": "urn:memoirist:problem:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "entryDate must not be after today",
  "instance": "/api/v1/entries",
  "timestamp": "2026-10-25T02:30:00Z",
  "fieldErrors": { "entryDate": "must not be after today" }
}
```

| `type` suffix | Status | When |
|---|---|---|
| `validation-failed` | 400 | Bean validation or a business rule on input; `fieldErrors` when field-specific |
| `malformed-request` | 400 | Unreadable JSON, bad UUID, bad `X-Dev-User-Id` (not a UUID, empty, or sent twice) |
| `not-found` | 404 | Missing **or not owned** |
| `entry-sealed` | 409 | Changing a sealed entry's content, date, template or media; or moving its unlock date earlier |
| `entry-stale` | 409 | `PUT /entries/{id}` sent a `version` older than the stored one: the entry changed elsewhere (D-23) |
| `conflict` | 409 | Client-supplied `id` already used by another user, or by an entry in Recently deleted (D-30) |
| `payload-too-large` | 413 | File over 25 MB, content over 200 KB |
| `unsupported-media-type` | 415 | File type not in the allowed list |
| `storage-unavailable` | 503 | Object storage failed |
| `music-search-unavailable` | 503 | Spotify search isn't set up, or Spotify refused the request (D-35) |
| `internal-error` | 500 | Anything unexpected (no stack traces in the body) |

### Pagination
Every paged endpoint takes `?page=0&size=10` (`size` max 50, unless the endpoint says otherwise) and returns:
```json
{ "items": [ ... ], "page": 0, "size": 10, "totalItems": 42, "totalPages": 5 }
```

### Client-supplied IDs (D-12)
`POST` for eras, templates and entries accepts an optional `"id"` (UUID).
- The `id` is new: `201 Created`.
- The `id` already exists **for the same user**: `200 OK` with the existing resource (a safe retry; the body is not re-applied).
- The `id` exists for another user: `409 conflict`.
- The `id` belongs to one of the user's entries in Recently deleted: `409 conflict`; restore it instead (D-30).

---

## Shared response shapes

### `Entry`
```json
{
  "id": "99887766-5544-3322-1100-aabbccddeeff",
  "weeklyLogId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "entryDate": "2026-10-20",
  "templateId": null,
  "title": "Arrived at the Grampians",
  "contentJson": { "type": "doc", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "Just arrived at the campsite." } ] } ] },
  "contentText": "Just arrived at the campsite.",
  "tags": [ { "id": "12341234-1234-1234-1234-123412341234", "name": "Trips", "source": "USER" } ],
  "soundtrack": "spotify:track:1a2B3c4D5e6F7g8H9i0J1k",
  "replyToEntryId": null,
  "replyIds": [],
  "createdAt": "2026-10-20T03:30:00Z",
  "updatedAt": "2026-10-20T03:30:00Z",
  "unlockDate": null,
  "isLocked": false,
  "dateUnknown": false,
  "version": 3,
  "media": [ { "...": "Media" } ]
}
```
- `title` is optional (D-24). `tags[].source` is `USER`, or from Phase 4 `AI` for an accepted suggestion (D-26).
- `soundtrack` is a Spotify URI or `null` (D-35).
- `dateUnknown` is `true` when the memory date is a guess (a photo-dump photo with no date of its own). Changing `entryDate` clears it. It isn't private, so sealed entries keep it (D-34).
- `version` goes up on every change to the entry; send it back on `PUT` (D-23). It isn't private, so sealed entries keep it.
- `replyToEntryId` names the opened Loot Drop this entry answers. `replyIds` lists the replies to an opened Loot Drop (D-33).

**Sealed entry** (`unlockDate` is in the future): the same shape, with the private fields removed, including `soundtrack`, `replyToEntryId` and `replyIds`.
```json
{
  "id": "77776666-5555-4444-3333-222211110000",
  "weeklyLogId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "entryDate": "2026-10-21",
  "templateId": null,
  "title": null,
  "contentJson": null,
  "contentText": null,
  "tags": [],
  "soundtrack": null,
  "replyToEntryId": null,
  "replyIds": [],
  "createdAt": "2026-10-21T09:00:00Z",
  "updatedAt": "2026-10-21T09:05:00Z",
  "unlockDate": "2027-10-21T09:00:00Z",
  "isLocked": true,
  "dateUnknown": false,
  "version": 1,
  "media": []
}
```

### `EntrySummary`
A lightweight row for lists such as the all-entries timeline (D-25). No caption, no media URLs.
```json
{
  "id": "99887766-5544-3322-1100-aabbccddeeff",
  "weeklyLogId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "entryDate": "2026-10-20",
  "title": "Arrived at the Grampians",
  "previewLine": "Just arrived at the campsite.",
  "photoCount": 3,
  "voiceNoteCount": 0,
  "firstMediaKind": "PHOTO",
  "tags": [ { "id": "12341234-1234-1234-1234-123412341234", "name": "Trips", "source": "USER" } ],
  "createdAt": "2026-10-20T03:30:00Z",
  "unlockDate": null,
  "isLocked": false,
  "dateUnknown": false,
  "deletedAt": null
}
```
- `previewLine` is the first line of the caption that isn't a heading, up to 120 characters; the first heading if the caption has nothing else; `null` if there is no caption (D-24).
- `firstMediaKind` is `PHOTO`, `PAGE_SCAN` or `VOICE_NOTE` for the first media item, or `null` when there is none. The client shows `title`, else `previewLine`, else *Photo* / *Diary page* / *Voice note* (D-24).
- `photoCount` counts every image, diary pages included.
- Sealed: `title`, `previewLine`, `photoCount`, `voiceNoteCount` and `firstMediaKind` are `null` and `tags` is empty.
- `dateUnknown` is as on `Entry` (D-34).
- `deletedAt` is set only in the Recently deleted list; the entry is erased 30 days after it (D-30).

### `Media`
`fileUrl` is a presigned GET URL that is valid for 60 minutes. Its host is the storage public endpoint (SeaweedFS locally, R2 in production). `purpose` is `PHOTO`, or `PAGE_SCAN` for a photo of a paper diary page (D-20).
```json
{
  "id": "55554444-3333-2222-1111-000099998888",
  "entryId": "99887766-5544-3322-1100-aabbccddeeff",
  "fileUrl": "http://localhost:8333/memoirist-media/users/00000000-0000-0000-0000-000000000001/entries/99887766-5544-3322-1100-aabbccddeeff/55554444-3333-2222-1111-000099998888.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=3600&X-Amz-Signature=…",
  "mediaType": "image/jpeg",
  "purpose": "PHOTO",
  "displayOrder": 0,
  "fileSizeBytes": 2483011,
  "uploadedAt": "2026-10-20T03:31:00Z"
}
```

### `WeeklyLog`
Used by the feed and by week detail. `eras` lists every Era whose dates overlap the week: pinned Eras first, then latest start first. The feed groups the week under `eras[0]`, nested inside a bigger Era of the week when `eras[0]` sits wholly inside it (D-22). It is empty for a week in no Era, and is worked out from dates rather than stored (D-22). Entries are ordered by `entryDate`, then `createdAt`, ascending, so each week reads as that week's story. Entries in Recently deleted are left out (D-30).
```json
{
  "id": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "title": "Grampians Trip Week",
  "startDate": "2026-10-19",
  "endDate": "2026-10-25",
  "isClosed": false,
  "soundtrack": null,
  "eras": [ { "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "title": "Monash Double Degree Era", "startDate": "2025-02-24", "endDate": null, "pinnedInFeed": false } ],
  "entries": [ { "...": "Entry" } ]
}
```

---

## Phase 1 endpoints

### Users (tag: Users)
| Method and path | Body | Success |
|---|---|---|
| `GET /users/me` | — | `200` `{ "id", "email", "displayName", "timezone", "birthday" }` |
| `PATCH /users/me` | `{ "displayName"?: string(1–100), "timezone"?: IANA zone, "birthday"?: date or null }` | `200` user. Unknown zone, or a birthday after today: `400`. The birthday powers the *next birthday* unlock suggestion (D-33) |

### Eras (tag: Eras)
| Method and path | Body | Success |
|---|---|---|
| `POST /eras` | `{ "id"?, "title": string(1–150), "description"?: string, "startDate": date, "endDate"?: date ≥ startDate, "soundtrack"?: Spotify URI or null, "coverMediaId"?: uuid or null, "pinnedInFeed"?: boolean (default `false`) }` | `201` era. It holds every week its dates overlap, existing weeks included (D-22). A cover must be an image from one of the user's own entries, otherwise `400`; a photo from a sealed entry is `400`; another user's media, or a photo from an entry in Recently deleted, is `404` (D-36) |
| `GET /eras` | — | `200` array of eras, `startDate` descending |
| `GET /eras/{eraId}` | — | `200` era plus `"weeklyLogs": [{ "id", "title", "startDate", "endDate", "isClosed", "entryCount" }]`, newest first: the weeks its dates overlap. `entryCount` counts only the week's entries inside the Era's dates (D-22), leaving out sealed entries and entries in Recently deleted (D-10, D-30) |
| `PUT /eras/{eraId}` | Same as POST without `id` (full replace) | `200` era. New dates apply at once: it holds the weeks they overlap (D-22) |
| `DELETE /eras/{eraId}` | — | `204`. Only the Era goes; its weeks, their entries and their other Eras stay |

Era shape: `{ "id", "title", "description", "startDate", "endDate", "soundtrack", "cover", "pinnedInFeed", "createdAt", "updatedAt" }`. `pinnedInFeed` makes the Era win in the feed for weeks it shares (D-22). `cover` is a presigned `Media`, or `null` when there's none or its entry is sealed or in Recently deleted (D-36). `soundtrack` is a Spotify URI or `null` (D-35).

### Templates (tag: Templates)
| Method and path | Body | Success |
|---|---|---|
| `POST /templates` | `{ "id"?, "name": string(1–100), "subheadings": string[] (0–20 items, each 1–200 chars) }` | `201` template |
| `GET /templates` | — | `200` array, by name |
| `GET /templates/{templateId}` | — | `200` template |
| `PUT /templates/{templateId}` | Same as POST without `id` | `200`. Existing entries are unaffected |
| `DELETE /templates/{templateId}` | — | `204`. Entries keep their content; `templateId` becomes null |

Template shape: `{ "id", "name", "subheadings": ["What triggered the spiral?", "..."], "createdAt", "updatedAt" }`.

### Tags (tag: Tags)
Manual tags are Phase 1 (D-26). `Tag`: `{ "id", "name", "entryCount" }`. `entryCount` never counts sealed entries (D-10).

| Method and path | Body | Success |
|---|---|---|
| `POST /tags` | `{ "name": string(1–50) }` | `201` `Tag`. A name that already exists, ignoring case, returns the existing tag with `200` |
| `GET /tags` | — | `200` `Tag[]`, by name |
| `PUT /tags/{tagId}` | `{ "name": string(1–50) }` | `200` `Tag`. Renaming to a name that exists: `409 conflict` |
| `DELETE /tags/{tagId}` | — | `204`. Entries keep their content and lose the tag |

Tags are set on an entry with `tagIds` in `POST` and `PUT /entries`. Browse by tag with `GET /entries?tagId=…`.

### Feed (tag: Feed)
| Method and path | Success |
|---|---|
| `GET /feed?page=0&size=10` | `200` `PageResponse<WeeklyLog>`. Only weeks with at least one entry that isn't in Recently deleted (D-30), `startDate` descending. Each week's entries are included, redacted and presigned. A week in several Eras is listed once. One request renders a page of the feed |

### Weekly logs (tag: Weekly Logs)
Weeks are created automatically when an entry is created (D-08). There is no create or delete endpoint.

| Method and path | Body | Success |
|---|---|---|
| `GET /weekly-logs/{logId}` | — | `200` `WeeklyLog` |
| `PUT /weekly-logs/{logId}` | `{ "title": string(0–200) or null, "soundtrack": Spotify URI or null }` (full replace) | `200` `WeeklyLog`. A week's Eras follow from their dates and can't be set here (D-22) |
| `POST /weekly-logs/{logId}/close` | — | `200` `WeeklyLog` with `isClosed: true` (idempotent). Phase 4: writes the week's summary through `WeeklyLogClosedEvent` (D-28) |
| `POST /weekly-logs/{logId}/reopen` | — | `200` `WeeklyLog` with `isClosed: false` (idempotent) |

### Entries (tag: Entries)
| Method and path | Body | Success |
|---|---|---|
| `POST /entries` | `{ "id"?, "entryDate"?: date (default today in the user's zone; not after today; not before 1900-01-01), "templateId"?: uuid, "title"?: string(1–150) or null, "contentJson"?: TipTap doc or null, "tagIds"?: [uuid] (at most 20, the user's own), "soundtrack"?: Spotify URI or null, "replyToEntryId"?: uuid or null, "dateUnknown"?: boolean (default `false`; the photo dump sets it, D-34) }` | `201` `Entry`, filed into the right week (created if needed). Replay of an existing own `id`: `200`. An entry may be created empty because media are uploaded afterwards (D-23). `replyToEntryId` must be one of the user's own Loot Drops that has opened, otherwise `400`; another user's is `404` (D-33) |
| `GET /entries?page=0&size=50&tagId={uuid}` | — | `200` `PageResponse<EntrySummary>` of every entry, `entryDate` then `createdAt`, newest first. `tagId` is optional and filters by tag; sealed entries are left out while filtering (D-10). `size` max 100 here. Backs the all-entries timeline (D-25) |
| `GET /entries/opened` | — | `200` `EntrySummary[]`: Loot Drops whose unlock time has passed and that haven't been marked seen, most recently opened first, at most 20 (D-10). Backs the *Opened* strip |
| `GET /entries/deleted?page=0&size=10` | — | `200` `PageResponse<EntrySummary>` of the entries in Recently deleted, most recently deleted first, each with `deletedAt` (D-30). Sealed ones are redacted (D-10). Backs the *Recently deleted* screen |
| `GET /entries/on-this-day` | — | `200` `EntrySummary[]`: entries from the same day and month in earlier years, newest year first, at most 20 (D-32). Sealed and deleted entries never appear. In a year that isn't a leap year, 29 February entries appear on 28 February. Backs the *On this day* strip |
| `GET /entries/{entryId}` | — | `200` `Entry` (redacted if sealed) |
| `PUT /entries/{entryId}` | `{ "version": integer (the one last read), "entryDate": date, "templateId": uuid or null, "title": string or null, "contentJson": doc or null, "tagIds": [uuid], "soundtrack": Spotify URI or null, "replyToEntryId": uuid or null }` (full replace; a date change moves the entry to the matching week and clears `dateUnknown`, D-34) | `200` `Entry`. Sealed: `409 entry-sealed`. Stale `version`: `409 entry-stale`, nothing saved (D-23). A tag ID that isn't the user's: `404` |
| `DELETE /entries/{entryId}` | — | `204`. Moves the entry to Recently deleted for 30 days; its media files stay until it is erased. Allowed even when sealed. An entry with no title, caption or media is erased at once (D-30) |
| `POST /entries/{entryId}/restore` | — | `200` `Entry`, back in its week with its media, tags and seal as they were (D-30). Not in Recently deleted: `404` |
| `POST /entries/{entryId}/erase` | — | `204`. Erases an entry in Recently deleted, with its media files, at once. Not in Recently deleted: `404` |
| `POST /entries/{entryId}/seal` | `{ "unlockDate": instant }`: after now, at most 100 years ahead; if already sealed, must be later than the current `unlockDate` | `200` `Entry` (now redacted). Earlier date on a sealed entry: `409 entry-sealed`. Sealing clears the entry's seen mark, so it shows in the Opened strip again when it opens |
| `POST /entries/{entryId}/mark-seen` | — | `204`. Records that the user has seen an opened Loot Drop; safe to repeat. Still sealed: `409 entry-sealed` |

**Recently deleted (D-30):** an entry in Recently deleted is left out of every list, count and filter, the feed, its week and the Opened strip included. Every other request about it, such as `GET`, `PUT`, `seal`, `mark-seen` and the media calls, returns `404`.

**Content rules (D-05):**
- Root `type` is `"doc"`.
- Allowed nodes: `paragraph`, `text`, `heading` (levels 1–3), `bulletList`, `orderedList`, `listItem`, `blockquote`, `hardBreak`, `horizontalRule`.
- Allowed marks: `bold`, `italic`, `strike`, `code`, `link` (`http`, `https` or `mailto` only).
- At most 200 KB serialised.
- Anything else: `400 validation-failed`. `contentText` is always computed by the server; clients never send it.

### Media (tag: Media)
Every media endpoint returns `409 entry-sealed` when the entry is sealed, and `404` when it is in Recently deleted (D-30).

| Method and path | Body | Success |
|---|---|---|
| `POST /entries/{entryId}/media` | multipart: `file` (required), `displayOrder` (optional int; default appends), `purpose` (optional: `PHOTO`, the default, or `PAGE_SCAN`) | `201` `Media`. `PAGE_SCAN` must be an image, otherwise `415`; an unknown purpose is `400` (D-20) |
| `PUT /entries/{entryId}/media/order` | `{ "mediaIds": [uuid, …] }`: exactly the entry's current media, in the new order | `200` `Media[]` in the new order. Missing, extra or duplicate IDs: `400` |
| `DELETE /media/{mediaId}` | — | `204`. Removes the stored file and the row; later items move up to close the gap |

**Upload rules (D-17):**
- Allowed types: `image/jpeg`, `image/png`, `image/webp`, `audio/webm`, `audio/mp4`, `audio/mpeg`.
- At most 25 MB per file (`413` above that) and 20 items per entry (`400` above that).
- Wrong type: `415`.

### Music (tag: Music)
D-35. Soundtracks are stored as Spotify URIs: `spotify:track:`, `spotify:album:` or `spotify:playlist:` followed by a 22-character id of letters and digits; anything else is `400`. Search terms go in the request body, never in the URL, and are never logged.

| Method and path | Body | Success |
|---|---|---|
| `POST /music/search` | `{ "query": string(1–200) }` | `200` `{ "items": [{ "uri", "kind", "title", "subtitle", "imageUrl" }] }`: at most 10 Spotify results. `kind` is `TRACK`, `ALBUM` or `PLAYLIST`, and `subtitle` is the artist or owner. Search isn't set up, or Spotify refuses: `503 music-search-unavailable`, and the client offers paste-a-link instead |

---

## Phase 2 endpoints (draft; tag: Auth & Sharing)
Finalise after open questions Q2–Q5 and Q19–Q21.

| Method and path | Notes |
|---|---|
| `POST /auth/register` | `{ "email", "password", "displayName" }` → token and user |
| `POST /auth/login` | `{ "email", "password" }` → token and user |
| `POST /shares` | `{ "entryId" }` or `{ "weeklyLogId" }`, plus `expiresAt` → `{ "id", "url", "expiresAt" }`. The raw token appears only in this response |
| `DELETE /shares/{shareId}` | Revoke |
| `GET /public/shares/{token}` | No auth. Read-only `Entry` or `WeeklyLog`, with Loot Drop redaction applied. Expired or revoked: `404` |
| Week PDF export | Rendered in the browser or on the server, depending on the library chosen in Q4. If the server renders it: `GET /weekly-logs/{logId}/pdf`. Sealed entries are redacted |
| `POST /users/me/export` | Builds a download of everything the user owns (Q3). Sealed entries appear only as locked placeholders |
| `DELETE /users/me` | Deletes the account and its stored files, after a confirmation that says how many sealed entries will be lost unseen (Q3) |
| `PATCH /users/me` | Gains `"emailOnUnlock": boolean` (default `true`): an email when a Loot Drop opens (task 5.7). Also gains `"yearlyLetterOn": "MM-DD"` or `null`: the yearly letter's day, the birthday's day by default; `null` turns it off (D-32, task 5.10) |
| Installable app | No endpoint: a web app manifest and service worker served with the frontend (D-31, task 5.8) |
| App lock | Endpoints depend on how Q2 settles the lock (D-37, task 5.9); a lock that lives only on the device needs none |
| Product metrics | No endpoint: the server writes `product_events` and they're read with SQL queries (D-38, task 5.11) |

## Phase 3 endpoints (draft; tag: Groups)
Finalise after open questions Q8, Q11 and Q26 (D-19). The rules:
- Personal endpoints are unchanged. Another member's entry is still `404` at `GET /entries/{entryId}`; members read contributions only through the endpoints below.
- A group the caller isn't a member of is `404`. A member without the role for an action gets `403`.
- Members are addressed by `memberId` (the `group_members` row), never by a user ID.
- Contributions are built with `EntryMapper`, so sealed ones are redacted as in D-10. Media URLs are presigned only after the membership check.
- An entry in Recently deleted is left out of group views. Restoring it brings the contribution back; erasing it removes the contribution (D-30).
- The shared story and each member's opt-in to it come with the AI, in Phase 4 (D-31).

New problem types:

| `type` suffix | Status | When |
|---|---|---|
| `forbidden` | 403 | A group member lacks the role for the action, for example a non-owner editing the group |
| `group-sealed` | 409 | Moving a group's unlock date earlier |
| `conflict` | 409 | Also used when the only owner tries to leave a group |

### Shapes
- `Group`: `{ "id", "title", "description", "startDate", "endDate", "unlockDate", "isLocked", "myRole", "memberCount", "contributionCount", "createdAt", "updatedAt" }`. `isLocked` is true while `unlockDate` is in the future.
- `GroupMember`: `{ "id", "displayName", "role", "contributionCount" }`, where `role` is `OWNER` or `MEMBER`.
- `GroupEntry`: `{ "entry": Entry, "author": { "memberId", "displayName" }, "contributedAt" }`. A sealed contribution shows its author, date and unlock date, but no content or media.
- `Era` (the Phase 1 shape) gains `"groupId"`: set on an Era created from a trip, otherwise `null`.

### Groups and members
| Method and path | Body | Success |
|---|---|---|
| `POST /groups` | `{ "title": string(1–150) (optional with entryId), "description"?: string, "startDate"?: date, "endDate"?: date ≥ startDate, "unlockDate"?: instant after now, "entryId"?: uuid, "confirmUnlockDate"?: instant }` | `201` `Group`. The caller becomes its `OWNER`. With `entryId` (*share with friends*, D-19), the title and dates default to that entry's, and the entry becomes the first contribution under the rules of `POST /groups/{groupId}/entries` |
| `GET /groups` | — | `200` array of the caller's groups, newest first |
| `GET /groups/{groupId}` | — | `200` `Group` plus `"members": [GroupMember]` |
| `PUT /groups/{groupId}` | Same as POST (full replace) | `200` `Group`. Owner only. Moving `unlockDate` earlier: `409 group-sealed` |
| `DELETE /groups/{groupId}` | — | `204`. Owner only. Contributions are unlinked; no entry is deleted |
| `POST /groups/{groupId}/invites` | `{ "expiresAt": instant }` | `201` `{ "id", "url", "expiresAt" }`. Owner only. The raw token appears only in this response |
| `DELETE /groups/{groupId}/invites/{inviteId}` | — | `204`. Owner only. Revokes the link |
| `POST /group-invites/{token}/accept` | — | `200` `Group`; the caller becomes a `MEMBER` (already a member: unchanged). Unknown, expired or revoked token: `404` |
| `DELETE /groups/{groupId}/members/me` | — | `204`. Leaves the group: the caller's contributions are unlinked, and their trip Era stays but is unlinked. The only owner can't leave: `409 conflict` |
| `DELETE /groups/{groupId}/members/{memberId}` | — | `204`. Owner only, and not for themself (they use `members/me`) |

### Contributions
| Method and path | Body | Success |
|---|---|---|
| `POST /groups/{groupId}/entries` | `{ "entryId": uuid, "confirmUnlockDate"?: instant }` | `201` `GroupEntry` (already added: `200`). The entry must be the caller's own, otherwise `404`. If the group is locked, `confirmUnlockDate` must equal the group's `unlockDate` (otherwise `400`) and the entry is sealed until at least that time; an entry already sealed for longer keeps its date |
| `GET /groups/{groupId}/entries?page=0&size=10` | — | `200` `PageResponse<GroupEntry>`, ordered by `entryDate` then `createdAt`, ascending |
| `DELETE /groups/{groupId}/entries/{entryId}` | — | `204`. Withdraws the caller's own contribution; the entry itself is untouched. Allowed even when sealed |

**Trips as Eras** has no endpoint of its own. When a member joins a group that has a `startDate`, the service creates an Era in their timeline from the group's title and dates. Like any Era, it holds the weeks those dates overlap and shows only the entries inside them (D-22). An entry contributed from outside the dates stays in the group but not in the trip Era.

## Phase 4 endpoints (draft; tag: AI Biographer)
Sealed entries are never analysed (`409 entry-sealed`) and never included in summaries.

| Method and path | Notes |
|---|---|
| `GET /weekly-logs/{logId}/ai-summary` | The summary written when the week was closed (D-28), or `404` if there isn't one yet (see Q23) |
| `GET /characters` | Cast of Characters plus the friendship map's graph data. Hidden people are left out. A person's record, profile and merging are below |

## Phase 4 endpoints: search (draft; tag: Search)
D-29. Search terms go in the request body, never in the URL, and are never logged.

| Method and path | Body | Success |
|---|---|---|
| `POST /search/words` | `{ "query": string(1–200), "tagId"?: uuid, "page"?: int, "size"?: int (max 50) }` | `200` `PageResponse<EntrySummary>`: the caller's entries whose title or caption match, best match first. Sealed entries never match. Needs no AI |
| `POST /search/feeling` | `{ "query": string(1–500) }` | `200` up to 10 `{ "weeklyLogId", "startDate", "endDate", "title", "summaryExcerpt", "score" }`: closed weeks whose summaries are closest in meaning. Needs AI consent, otherwise `403 consent-required` |

## Phase 4 endpoints: paper diaries (draft; tag: Transcriptions)
Tasks 7.6–7.8 (D-20). Finalise after open question Q12. A photographed diary page is an ordinary photo, marked as a page, and works from Phase 1. This section adds the optional step of reading the handwriting.

Rules:
- Reading a page is always an explicit request. A sealed entry can't be read: every endpoint below returns `409 entry-sealed` for it.
- A transcript never changes an entry by itself. The client builds the caption from the text and saves it with `PUT /entries/{entryId}`, so the TipTap rules (D-05) are the only way in.
- Transcriptions belong to the entry's owner. No group endpoint returns them (D-19).
- Another user's entry, media item or transcription is `404`.
- Needs the user's AI consent: otherwise `403 consent-required` (D-21).

New problem type:

| `type` suffix | Status | When |
|---|---|---|
| `rate-limited` | 429 | The user has reached the daily limit for pages read (the limit is decided in Q12) |

### Changes to media
- `Media.purpose` and the upload field are Phase 1 (D-20). Group views return `purpose` too, so members see a page as a page.
- `PUT /media/{mediaId}` with `{ "purpose": "PHOTO" }` or `{ "purpose": "PAGE_SCAN" }` returns `200` `Media`, to fix a photo marked the wrong way. Sealed: `409 entry-sealed`.

### `Transcription`
```json
{
  "id": "44443333-2222-1111-0000-ffffeeeedddd",
  "mediaId": "55554444-3333-2222-1111-000099998888",
  "status": "READY",
  "text": "Tuesday 12 March. Got the keys to the flat today.",
  "language": "en",
  "detectedDate": "2019-03-12",
  "confidence": 0.87,
  "failureReason": null,
  "requestedAt": "2026-10-26T01:00:00Z",
  "completedAt": "2026-10-26T01:00:12Z"
}
```
- `status` is `PENDING`, `READY` or `FAILED`. `text`, `language`, `detectedDate` and `confidence` are `null` until `READY`.
- `detectedDate` is a date the provider read from the page, or `null`. It is only a suggestion: the client offers it as a chip, and `entryDate` changes only when the user accepts it with `PUT /entries/{entryId}`. A date after today is never offered.
- `confidence` is advisory (0 to 1) and `null` when the provider gives none.
- `failureReason` is a short code such as `timeout`, `unreadable` or `provider-error`, set only when `FAILED`. It never contains page text.

### Endpoints
| Method and path | Body | Success |
|---|---|---|
| `POST /entries/{entryId}/transcriptions` | `{ "mediaIds"?: [uuid] }`, images of this entry. Default: every `PAGE_SCAN` that has no `READY` transcription | `202` `Transcription[]` in page order. A page that is already `PENDING` is returned as it is. A `READY` or `FAILED` page named in `mediaIds` is read again and replaced. Nothing to read: `200` with an empty array. An ID that isn't a media item of this entry: `400`. A media item that isn't an image: `415`. Over the daily limit: `429 rate-limited` |
| `GET /entries/{entryId}/transcriptions` | — | `200` `Transcription[]` for the pages that have one, in `displayOrder`. The client polls this every couple of seconds while any item is `PENDING` |
| `DELETE /transcriptions/{transcriptionId}` | — | `204`. Discards the transcript; the photo and the entry are untouched |

## Phase 4 endpoints: AI settings, Help me think, tags, closing Eras, Wrap up and friend profiles (draft)
Tasks 7.2 and 7.9–7.16 (D-21, D-28). Finalise after open questions Q14, Q17, Q23 and Q28.

Rules for every AI endpoint, including reading pages (D-20):
- The user must have given AI consent, otherwise `403 consent-required`. A feature the user has switched off, or that isn't available to them, returns `403 feature-unavailable`.
- A sealed entry is never read: `409 entry-sealed`. Entry text reaches the AI only through `AiContentSource`.
- Text from an unsaved draft (`contentJson`) is checked against the TipTap rules (D-05), processed in memory and never stored.
- Nothing the AI suggests changes data until the user taps. The client sends only the option the user chose; the server holds what each option does.
- Another user's entry, suggestion, tag, Era or character is `404`.
- Calls are limited per user per day: `429 rate-limited`. The limits are configuration.
- Hard topics follow D-39: a gentle tone, and no patterns or cards about self-harm, abuse or grief.

New problem types:

| `type` suffix | Status | When |
|---|---|---|
| `consent-required` | 403 | The user hasn't accepted the AI notice, or has withdrawn it |
| `feature-unavailable` | 403 | The feature is switched off in the user's settings, or isn't available to them |
| `not-enough-content` | 409 | There is too little to work with yet, for example a person mentioned too rarely for a profile |

### Settings and consent (tag: Users)
`UserSettings` has one block per AI feature, because the README promises every feature its own switch, plus the Wrap up reminder (D-28) and the weekly email (D-32). `available` is set by the server (from configuration today, from a plan later) and is ignored if sent. `aiConsent` is read-only here and is `null` until the user accepts. `people` covers friend profiles and the friendship map; `pageReading` stays unavailable until Q12 picks a provider.
```json
{
  "helpMeThink": { "available": true, "enabled": true, "pauseSeconds": 10 },
  "weeklySummaries": { "available": true, "enabled": true },
  "eraSummaries": { "available": true, "enabled": true },
  "people": { "available": true, "enabled": true },
  "pageReading": { "available": false, "enabled": false },
  "writingCoach": { "available": false, "enabled": false },
  "proactiveInsights": { "available": true, "enabled": false, "maxPerWeek": 3 },
  "wrapUpReminder": { "enabled": true },
  "weeklyEmail": { "enabled": true },
  "aiConsent": { "acceptedAt": "2026-10-27T01:00:00Z", "version": 1 }
}
```

| Method and path | Body | Success |
|---|---|---|
| `GET /users/me/settings` | — | `200` `UserSettings`. Defaults apply until the user changes something |
| `PUT /users/me/settings` | `UserSettings` without the read-only fields (full replace) | `200` `UserSettings`. An unknown key or an out-of-range value: `400`. Turning on a feature that isn't `available`: `403 feature-unavailable` |
| `POST /users/me/ai-consent` | `{ "version": integer }`: the wording version the user was shown | `200` `{ "acceptedAt", "version" }`. Safe to repeat |
| `DELETE /users/me/ai-consent` | — | `204`. Stops all AI use at once. Existing results stay until the user deletes them (Q17) |
| `DELETE /users/me/ai-data` | — | `204`. Deletes everything the AI made: summaries, Era summaries, profiles, suggestion cards, embeddings and AI facts. Entries, tags and the user's own notes are untouched |

### Suggestions: Help me think (tag: Suggestions)
A `Suggestion` is one question or card. Its `kind` says which, and `QUESTION` and `TAG` always have exactly two options.
```json
{
  "id": "66665555-4444-3333-2222-111100009999",
  "kind": "QUESTION",
  "status": "OPEN",
  "entryId": null,
  "prompt": "Did that feel more like stress or excitement?",
  "options": [
    { "id": "a", "label": "Stress" },
    { "id": "b", "label": "Excitement" }
  ],
  "sources": [],
  "createdAt": "2026-10-27T01:00:00Z",
  "expiresAt": "2026-10-27T13:00:00Z"
}
```
- `kind` is `QUESTION`, or `TAG` (for example "Add the tag Work?" with the options "Add tag" and "No thanks"). Phase 6 adds `WRITING_INSIGHT`, `PATTERN`, `GROWTH_REMINDER`, `SIMILAR_PAST` and `HIGHLIGHT_REEL`. A client ignores kinds it doesn't know, so new kinds don't break old clients.
- `status` is `PENDING` (still being made), `OPEN`, `ANSWERED`, `DISMISSED`, `EXPIRED` or `FAILED`.
- `entryId` is `null` for a card made from an unsaved draft, and for journal-wide cards.
- `sources` lists what a card was built from, for example `[{ "type": "ENTRY", "id": "…" }]`. The types are `ENTRY`, `WEEKLY_SUMMARY` and `ERA_SUMMARY`.
- A card made from an unsaved draft expires within hours.

| Method and path | Body | Success |
|---|---|---|
| `POST /suggestions` | `{ "feature": "helpMeThink", "entryId"?: uuid, "contentJson"?: TipTap doc }`. Exactly one of `entryId` and `contentJson` | `201` `Suggestion[]` (1–3), or `200` with `[]` when there is nothing worth asking, for example too little text. Under 5 s. Sealed entry: `409 entry-sealed` |
| `GET /suggestions?entryId={uuid}` | — | `200` `Suggestion[]`: the entry's `OPEN` cards, so they survive a reload |
| `GET /suggestions/{suggestionId}` | — | `200` `Suggestion` |
| `POST /suggestions/{suggestionId}/answer` | `{ "optionId": string, "entryId"?: uuid }`. `entryId` is required for a card made from a draft, and names the saved entry | `200` `Suggestion` (`ANSWERED`). The server applies the option's effect once, for example adding a tag. Repeating the same answer is harmless; a different one is `409 conflict` |
| `POST /suggestions/{suggestionId}/dismiss` | — | `200` `Suggestion` (`DISMISSED`) |

### Tags
Tags and their endpoints are Phase 1 (D-26). Phase 4 adds only `TAG` suggestions: answering *Add tag* links the tag with `source: "AI"`.

### Closing an Era and its summary (tag: Eras)
D-28. The ritual's last word and letter are ordinary entries the client makes before closing, with `POST /entries` and, for the letter, `POST /entries/{entryId}/seal`; closing needs nothing extra. The `Era` shape gains `"isClosed"`. `EraSummary`: `{ "eraId", "narrative", "themes": [string], "weekCount", "isStale", "generatedAt" }`. A stale summary can still be read until it is rebuilt in the background.

| Method and path | Body | Success |
|---|---|---|
| `POST /eras/{eraId}/close` | — | `200` Era with `isClosed: true` (safe to repeat). Its open weeks are closed too; their summaries are written in the background within the daily limit, and then the Era summary. If the Era has no end date, it gets the end date of its latest week |
| `POST /eras/{eraId}/reopen` | — | `200` Era with `isClosed: false` (safe to repeat). The summary stays |
| `GET /eras/{eraId}/summary` | — | `200` `EraSummary`, or `404` if there isn't one yet (see Q23) |

### Wrap up (tag: Weekly Logs)
D-28. Backs the *Wrap up* card and the gentle reminder at the top of the feed.

| Method and path | Body | Success |
|---|---|---|
| `GET /wrap-up` | — | `200` `{ "week": { "id", "title", "startDate", "endDate", "entryCount" } or null, "endedEras": [Era], "openFinishedWeeks": integer }`. `week` is the most recent finished week (its Sunday has passed in the user's timezone) that has entries and is still open. `endedEras` are Eras whose end date has passed but that aren't closed. `openFinishedWeeks` drives the reminder |

### Friend profiles (tag: AI Biographer)
`GET /characters` lists people with their stats. A person's full record:
```json
{
  "id": "22223333-4444-5555-6666-777788889999",
  "name": "Amogh",
  "aliases": ["Mo"],
  "isHidden": false,
  "userNotes": "Met in orientation week.",
  "stats": {
    "mentionCount": 41,
    "firstMentionedOn": "2019-03-12",
    "lastMentionedOn": "2026-10-20",
    "eras": [ { "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "title": "Monash Double Degree Era", "mentionCount": 33 } ]
  },
  "profile": { "summary": "…", "isStale": false, "generatedAt": "2026-10-27T01:00:00Z" }
}
```
- `stats` always come from the database, never from the AI, and never count sealed entries (D-10). `profile` is `null` until one is made.
- A profile is made automatically once someone is mentioned in several entries, and rebuilt in the background when it goes stale. `userNotes` are the user's own words and are never overwritten.

| Method and path | Body | Success |
|---|---|---|
| `GET /characters/{characterId}` | — | `200` the record above |
| `PUT /characters/{characterId}` | `{ "name": string(1–150), "userNotes": string or null, "isHidden": boolean }` | `200` the record. Renaming to another person's name: `409 conflict` (merge them instead) |
| `POST /characters/{characterId}/profile` | — | `202`. Rebuilds the AI profile. Too few mentions: `409 not-enough-content` |
| `POST /characters/{characterId}/merge` | `{ "intoCharacterId": uuid }` | `200` the surviving record. Mentions move across and the old name becomes an alias |
| `GET /characters/{characterId}/entries?page=0&size=10` | — | `200` `PageResponse<Entry>` of entries that mention them, newest first, redacted |

## Phase 4 endpoints: shared story (draft; tag: Groups)
Task 7.17 (D-19, D-31). It needs Phase 3's groups and the AI layer, so it follows the group rules and the AI rules above. Finalise after open questions Q6 and Q9.

New problem type:

| `type` suffix | Status | When |
|---|---|---|
| `not-enough-contributions` | 409 | Asking for a shared story when fewer than two members who opted in have contributions |

| Method and path | Body | Success |
|---|---|---|
| `PUT /groups/{groupId}/members/me` | `{ "aiConsent": boolean }` | `200` `GroupMember`. Opting out deletes the stored shared story |
| `POST /groups/{groupId}/story` | — | `202`. Builds or rebuilds the shared story in the background from contributions by members who opted in. Sealed entries are never included. Fewer than two such members: `409 not-enough-contributions` |
| `GET /groups/{groupId}/story` | — | `200` `{ "narrativeSummary", "contributorNames": [string], "generatedAt" }`, or `404` if there is none. It is deleted when anyone withdraws, opts out or leaves |

## Phase 6 endpoints (draft; tag: Suggestions)
Tasks 9.1–9.5 (D-21). Finalise after open questions Q15 and Q16. Insight cards are `Suggestion`s with new kinds. They use only the caller's own entries (never other members' contributions) and never sealed ones, and every card that draws on past material lists its `sources`. The rules and problem types of the Phase 4 AI section apply. Predictions, A gift of memories and Year in review (tasks 9.6–9.8) are drafted when Sprint 9 is planned.

Insight cards add `title` and `body` (plain text). A `HIGHLIGHT_REEL` also has `reel`: `{ "entries": [Entry] }`, filled in when the card is read, so redaction always applies and an entry sealed after the reel was made simply drops out.

| Method and path | Body | Success |
|---|---|---|
| `GET /suggestions?inbox=true&page=0&size=10` | — | `200` `PageResponse<Suggestion>`: the caller's journal-wide cards, newest first. Give exactly one of `inbox` and `entryId`. Needs `proactiveInsights` on |
| `POST /suggestions/{suggestionId}/feedback` | `{ "value": "USEFUL" }` or `{ "value": "NOT_USEFUL" }` | `200` `Suggestion` (`ANSWERED`). Feedback tunes how often similar cards appear |
| `POST /highlight-reels` | Exactly one of `{ "eraId" }`, `{ "characterId" }` or `{ "from": date, "to": date }` | `202` `Suggestion` (kind `HIGHLIGHT_REEL`, status `PENDING`). Poll `GET /suggestions/{suggestionId}` |
| `POST /suggestions` | `{ "feature": "writingCoach", "entryId"?: uuid, "contentJson"?: TipTap doc }` | `201` with one `WRITING_INSIGHT`, or `200` with `[]`, which is the usual answer. Needs `writingCoach` on. Over the daily cap: `429 rate-limited` |
