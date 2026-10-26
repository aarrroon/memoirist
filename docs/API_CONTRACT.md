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
| Docs | Swagger UI at `/swagger-ui.html`; one OpenAPI tag per resource (Users, Eras, Templates, Feed, Weekly Logs, Entries, Media) |

### Who is the current user?
- **Never** send a user ID in a body, query string or path.
- **Phase 1 (`local` and `test` profiles only):** an optional `X-Dev-User-Id: <uuid>` header. Without it the server uses the seeded dev user `00000000-0000-0000-0000-000000000001`.
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
| `malformed-request` | 400 | Unreadable JSON, bad UUID, bad `X-Dev-User-Id` |
| `not-found` | 404 | Missing **or not owned** |
| `entry-sealed` | 409 | Changing a sealed entry's content, date, template or media; or moving its unlock date earlier |
| `conflict` | 409 | Client-supplied `id` already used by another user |
| `payload-too-large` | 413 | File over 25 MB, content over 200 KB |
| `unsupported-media-type` | 415 | File type not in the allowed list |
| `storage-unavailable` | 503 | Object storage failed |
| `internal-error` | 500 | Anything unexpected (no stack traces in the body) |

### Pagination
Every paged endpoint takes `?page=0&size=10` (`size` max 50) and returns:
```json
{ "items": [ ... ], "page": 0, "size": 10, "totalItems": 42, "totalPages": 5 }
```

### Client-supplied IDs (D-12)
`POST` for eras, templates and entries accepts an optional `"id"` (UUID).
- The `id` is new: `201 Created`.
- The `id` already exists **for the same user**: `200 OK` with the existing resource (a safe retry; the body is not re-applied).
- The `id` exists for another user: `409 conflict`.

---

## Shared response shapes

### `Entry`
```json
{
  "id": "99887766-5544-3322-1100-aabbccddeeff",
  "weeklyLogId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "entryDate": "2026-10-20",
  "templateId": null,
  "contentJson": { "type": "doc", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "Just arrived at the campsite." } ] } ] },
  "contentText": "Just arrived at the campsite.",
  "createdAt": "2026-10-20T03:30:00Z",
  "updatedAt": "2026-10-20T03:30:00Z",
  "unlockDate": null,
  "isLocked": false,
  "media": [ { "...": "Media" } ]
}
```

**Sealed entry** (`unlockDate` is in the future): the same shape, with the private fields removed.
```json
{
  "id": "77776666-5555-4444-3333-222211110000",
  "weeklyLogId": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "entryDate": "2026-10-21",
  "templateId": null,
  "contentJson": null,
  "contentText": null,
  "createdAt": "2026-10-21T09:00:00Z",
  "updatedAt": "2026-10-21T09:05:00Z",
  "unlockDate": "2027-10-21T09:00:00Z",
  "isLocked": true,
  "media": []
}
```

### `Media`
`fileUrl` is a presigned GET URL that is valid for 60 minutes. Its host is the storage public endpoint (SeaweedFS locally, R2 in production).
```json
{
  "id": "55554444-3333-2222-1111-000099998888",
  "entryId": "99887766-5544-3322-1100-aabbccddeeff",
  "fileUrl": "http://localhost:8333/memoirist-media/users/00000000-0000-0000-0000-000000000001/entries/99887766-5544-3322-1100-aabbccddeeff/55554444-3333-2222-1111-000099998888.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=3600&X-Amz-Signature=…",
  "mediaType": "image/jpeg",
  "displayOrder": 0,
  "fileSizeBytes": 2483011,
  "uploadedAt": "2026-10-20T03:31:00Z"
}
```

### `WeeklyLog`
Used by the feed and by week detail. Entries are ordered by `entryDate`, then `createdAt`, ascending, so each week reads as that week's story.
```json
{
  "id": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "title": "Grampians Trip Week",
  "startDate": "2026-10-19",
  "endDate": "2026-10-25",
  "isClosed": false,
  "era": { "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "title": "Monash Double Degree Era" },
  "entries": [ { "...": "Entry" } ]
}
```

---

## Phase 1 endpoints

### Users (tag: Users)
| Method and path | Body | Success |
|---|---|---|
| `GET /users/me` | — | `200` `{ "id", "email", "displayName", "timezone" }` |
| `PATCH /users/me` | `{ "displayName"?: string(1–100), "timezone"?: IANA zone }` | `200` user. Unknown zone: `400` |

### Eras (tag: Eras)
| Method and path | Body | Success |
|---|---|---|
| `POST /eras` | `{ "id"?, "title": string(1–150), "description"?: string, "startDate": date, "endDate"?: date ≥ startDate }` | `201` era |
| `GET /eras` | — | `200` array of eras, `startDate` descending |
| `GET /eras/{eraId}` | — | `200` era plus `"weeklyLogs": [{ "id", "title", "startDate", "endDate", "isClosed", "entryCount" }]`, newest first |
| `PUT /eras/{eraId}` | Same as POST without `id` (full replace) | `200` era |
| `DELETE /eras/{eraId}` | — | `204`. Its weeks keep their entries and become era-less |

Era shape: `{ "id", "title", "description", "startDate", "endDate", "createdAt", "updatedAt" }`.

### Templates (tag: Templates)
| Method and path | Body | Success |
|---|---|---|
| `POST /templates` | `{ "id"?, "name": string(1–100), "subheadings": string[] (0–20 items, each 1–200 chars) }` | `201` template |
| `GET /templates` | — | `200` array, by name |
| `GET /templates/{templateId}` | — | `200` template |
| `PUT /templates/{templateId}` | Same as POST without `id` | `200`. Existing entries are unaffected |
| `DELETE /templates/{templateId}` | — | `204`. Entries keep their content; `templateId` becomes null |

Template shape: `{ "id", "name", "subheadings": ["What triggered the spiral?", "..."], "createdAt", "updatedAt" }`.

### Feed (tag: Feed)
| Method and path | Success |
|---|---|
| `GET /feed?page=0&size=10` | `200` `PageResponse<WeeklyLog>`. Only weeks with at least one entry, `startDate` descending. Each week's entries are included, redacted and presigned. One request renders a page of the feed |

### Weekly logs (tag: Weekly Logs)
Weeks are created automatically when an entry is created (D-08). There is no create or delete endpoint.

| Method and path | Body | Success |
|---|---|---|
| `GET /weekly-logs/{logId}` | — | `200` `WeeklyLog` |
| `PUT /weekly-logs/{logId}` | `{ "title": string(0–200) or null, "eraId": uuid or null }` (full replace; the era must be the user's) | `200` `WeeklyLog` |
| `POST /weekly-logs/{logId}/close` | — | `200` `WeeklyLog` with `isClosed: true` (idempotent). Phase 3: publishes `WeeklyLogClosedEvent` |
| `POST /weekly-logs/{logId}/reopen` | — | `200` `WeeklyLog` with `isClosed: false` (idempotent) |

### Entries (tag: Entries)
| Method and path | Body | Success |
|---|---|---|
| `POST /entries` | `{ "id"?, "entryDate"?: date (default today in the user's zone; not after today), "templateId"?: uuid, "contentJson"?: TipTap doc or null }` | `201` `Entry`, filed into the right week (created if needed). Replay of an existing own `id`: `200` |
| `GET /entries/{entryId}` | — | `200` `Entry` (redacted if sealed) |
| `PUT /entries/{entryId}` | `{ "entryDate": date, "templateId": uuid or null, "contentJson": doc or null }` (full replace; a date change moves the entry to the matching week) | `200` `Entry`. Sealed: `409 entry-sealed` |
| `DELETE /entries/{entryId}` | — | `204`. Allowed even when sealed; deletes its stored media files too |
| `POST /entries/{entryId}/seal` | `{ "unlockDate": instant }`: after now, at most 100 years ahead; if already sealed, must be later than the current `unlockDate` | `200` `Entry` (now redacted). Earlier date on a sealed entry: `409 entry-sealed` |

**Content rules (D-05):**
- Root `type` is `"doc"`.
- Allowed nodes: `paragraph`, `text`, `heading` (levels 1–3), `bulletList`, `orderedList`, `listItem`, `blockquote`, `hardBreak`, `horizontalRule`.
- Allowed marks: `bold`, `italic`, `strike`, `code`, `link` (`http`, `https` or `mailto` only).
- At most 200 KB serialised.
- Anything else: `400 validation-failed`. `contentText` is always computed by the server; clients never send it.

### Media (tag: Media)
Every media endpoint returns `409 entry-sealed` when the entry is sealed.

| Method and path | Body | Success |
|---|---|---|
| `POST /entries/{entryId}/media` | multipart: `file` (required), `displayOrder` (optional int; default appends) | `201` `Media` |
| `PUT /entries/{entryId}/media/order` | `{ "mediaIds": [uuid, …] }`: exactly the entry's current media, in the new order | `200` `Media[]` in the new order. Missing, extra or duplicate IDs: `400` |
| `DELETE /media/{mediaId}` | — | `204`. Removes the stored file and the row; later items move up to close the gap |

**Upload rules (D-17):**
- Allowed types: `image/jpeg`, `image/png`, `image/webp`, `audio/webm`, `audio/mp4`, `audio/mpeg`.
- At most 25 MB per file (`413` above that) and 10 items per entry (`400` above that).
- Wrong type: `415`.

---

## Phase 2 endpoints (draft; tag: Auth & Sharing)
Finalise after open questions Q2–Q4.

| Method and path | Notes |
|---|---|
| `POST /auth/register` | `{ "email", "password", "displayName" }` → token and user |
| `POST /auth/login` | `{ "email", "password" }` → token and user |
| `POST /shares` | `{ "entryId" }` or `{ "weeklyLogId" }`, plus `expiresAt` → `{ "id", "url", "expiresAt" }`. The raw token appears only in this response |
| `DELETE /shares/{shareId}` | Revoke |
| `GET /public/shares/{token}` | No auth. Read-only `Entry` or `WeeklyLog`, with Loot Drop redaction applied. Expired or revoked: `404` |

## Phase 3 endpoints (draft; tag: AI Biographer)
Sealed entries are never analysed (`409 entry-sealed`) and never included in summaries.

| Method and path | Notes |
|---|---|
| `GET /weekly-logs/{logId}/ai-summary` | The generated weekly summary, or `404` if it isn't ready |
| `GET /characters` | Cast of Characters plus friendship-edge graph data. Hidden people are left out. A person's record, profile and merging are below |
| `POST /insights/query` | `{ "query" }`: semantic search over weekly summaries |

## Phase 3 endpoints: paper diaries (draft; tag: Transcriptions)
Tasks 6.6–6.8 (D-20). Finalise after open question Q12. A photographed diary page is an ordinary photo and works from Phase 1. These additions only label a photo as a page and add the optional step of reading the handwriting.

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
- `Media` gains `"purpose"`: `PHOTO` (the default) or `PAGE_SCAN`. Group views return it too, so members see a page as a page.
- `POST /entries/{entryId}/media` accepts an optional multipart field `purpose`. `PAGE_SCAN` must be an image, otherwise `415`. An unknown value is `400`.
- `PUT /media/{mediaId}` with `{ "purpose": "PHOTO" }` or `{ "purpose": "PAGE_SCAN" }` returns `200` `Media`. This lets a photo uploaded before this feature existed be marked as a page. Sealed: `409 entry-sealed`.

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

## Phase 3 endpoints: AI settings, Help me think, tags, Era summaries and friend profiles (draft)
Tasks 6.2 and 6.9–6.15 (D-21). Finalise after open questions Q14 and Q17. These replace the earlier drafts of `POST /entries/{entryId}/analyze` and `PATCH /entries/{entryId}/ai-confirm`.

Rules for every AI endpoint, including reading pages (D-20):
- The user must have given AI consent, otherwise `403 consent-required`. A feature the user has switched off, or that isn't available to them, returns `403 feature-unavailable`.
- A sealed entry is never read: `409 entry-sealed`. Entry text reaches the AI only through `AiContentSource`.
- Text from an unsaved draft (`contentJson`) is checked against the TipTap rules (D-05), processed in memory and never stored.
- Nothing the AI suggests changes data until the user taps. The client sends only the option the user chose; the server holds what each option does.
- Another user's entry, suggestion, tag, Era or character is `404`.
- Calls are limited per user per day: `429 rate-limited`. The limits are configuration.

New problem types:

| `type` suffix | Status | When |
|---|---|---|
| `consent-required` | 403 | The user hasn't accepted the AI notice, or has withdrawn it |
| `feature-unavailable` | 403 | The feature is switched off in the user's settings, or isn't available to them |
| `not-enough-content` | 409 | There is nothing to summarise yet, for example an Era with no weekly summaries |

### Settings and consent (tag: Users)
`UserSettings` has one block per AI feature. `available` is set by the server (from configuration today, from a plan later) and is ignored if sent. `aiConsent` is read-only here and is `null` until the user accepts.
```json
{
  "helpMeThink": { "available": true, "enabled": true, "nudgeSeconds": 10 },
  "writingCoach": { "available": false, "enabled": false },
  "proactiveInsights": { "available": true, "enabled": false, "maxPerWeek": 3 },
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

### Tags (tag: Tags)
`Tag`: `{ "id", "name", "entryCount" }`. `Entry` gains `"tags": [{ "id", "name", "source" }]`, where `source` is `USER` or `AI` (an AI suggestion the user accepted). The list is empty when the entry is sealed.

| Method and path | Body | Success |
|---|---|---|
| `POST /tags` | `{ "name": string(1–50) }` | `201` `Tag`. A name that already exists, ignoring case, returns the existing tag with `200` |
| `GET /tags` | — | `200` `Tag[]`, by name |
| `PUT /tags/{tagId}` | `{ "name": string(1–50) }` | `200` `Tag`. Renaming to a name that exists: `409 conflict` |
| `DELETE /tags/{tagId}` | — | `204`. Entries keep their content and lose the tag |
| `PUT /entries/{entryId}/tags` | `{ "tagIds": [uuid] }` (at most 20; full replace) | `200` `Entry`. Sealed: `409 entry-sealed` |
| `GET /tags/{tagId}/entries?page=0&size=10` | — | `200` `PageResponse<Entry>`, newest first, redacted |

### Era summaries (tag: Eras)
`EraSummary`: `{ "eraId", "narrative", "themes": [string], "weekCount", "isStale", "generatedAt" }`. One is made automatically when an Era ends. A stale summary can still be read until it is rebuilt.

| Method and path | Body | Success |
|---|---|---|
| `GET /eras/{eraId}/summary` | — | `200` `EraSummary`, or `404` if there isn't one yet |
| `POST /eras/{eraId}/summary` | — | `202`. Builds or rebuilds it in the background from the Era's weekly summaries. None yet: `409 not-enough-content` |

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
  "profile": { "summary": "…", "isStale": false, "generatedAt": "2026-10-27T01:00:00Z" },
  "linkedMember": null
}
```
- `stats` always come from the database, never from the AI. `profile` is `null` until one is made.
- A profile is made automatically once someone is mentioned in several entries, and rebuilt in the background when it goes stale. `userNotes` are the user's own words and are never overwritten.
- `linkedMember` is the Phase 5 link to a group member.

| Method and path | Body | Success |
|---|---|---|
| `GET /characters/{characterId}` | — | `200` the record above |
| `PUT /characters/{characterId}` | `{ "name": string(1–150), "userNotes": string or null, "isHidden": boolean }` | `200` the record. Renaming to another person's name: `409 conflict` (merge them instead) |
| `POST /characters/{characterId}/profile` | — | `202`. Rebuilds the AI profile. Too few mentions: `409 not-enough-content` |
| `POST /characters/{characterId}/merge` | `{ "intoCharacterId": uuid }` | `200` the surviving record. Mentions move across and the old name becomes an alias |
| `GET /characters/{characterId}/entries?page=0&size=10` | — | `200` `PageResponse<Entry>` of entries that mention them, newest first, redacted |

## Phase 5 endpoints (draft; tag: Groups)
Finalise after open questions Q8–Q11 (D-19). The rules:
- Personal endpoints are unchanged. Another member's entry is still `404` at `GET /entries/{entryId}`; members read contributions only through the endpoints below.
- A group the caller isn't a member of is `404`. A member without the role for an action gets `403`.
- Members are addressed by `memberId` (the `group_members` row), never by a user ID.
- Contributions are built with `EntryMapper`, so sealed ones are redacted as in D-10. Media URLs are presigned only after the membership check.

New problem types:

| `type` suffix | Status | When |
|---|---|---|
| `forbidden` | 403 | A group member lacks the role for the action, for example a non-owner editing the group |
| `group-sealed` | 409 | Moving a group's unlock date earlier |
| `not-enough-contributions` | 409 | Asking for a shared story when fewer than two members who opted in have contributions |
| `conflict` | 409 | Also used when the only owner tries to leave a group |

### Shapes
- `Group`: `{ "id", "title", "description", "startDate", "endDate", "unlockDate", "isLocked", "myRole", "memberCount", "contributionCount", "createdAt", "updatedAt" }`. `isLocked` is true while `unlockDate` is in the future.
- `GroupMember`: `{ "id", "displayName", "role", "contributionCount" }`, where `role` is `OWNER` or `MEMBER`.
- `GroupEntry`: `{ "entry": Entry, "author": { "memberId", "displayName" }, "contributedAt" }`. A sealed contribution shows its author, date and unlock date, but no content or media.
- `Era` (the Phase 1 shape) gains `"groupId"`: set on an Era created from a trip, otherwise `null`.

### Groups and members
| Method and path | Body | Success |
|---|---|---|
| `POST /groups` | `{ "title": string(1–150), "description"?: string, "startDate"?: date, "endDate"?: date ≥ startDate, "unlockDate"?: instant after now }` | `201` `Group`. The caller becomes its `OWNER` |
| `GET /groups` | — | `200` array of the caller's groups, newest first |
| `GET /groups/{groupId}` | — | `200` `Group` plus `"members": [GroupMember]` |
| `PUT /groups/{groupId}` | Same as POST (full replace) | `200` `Group`. Owner only. Moving `unlockDate` earlier: `409 group-sealed` |
| `DELETE /groups/{groupId}` | — | `204`. Owner only. Contributions are unlinked; no entry is deleted |
| `POST /groups/{groupId}/invites` | `{ "expiresAt": instant }` | `201` `{ "id", "url", "expiresAt" }`. Owner only. The raw token appears only in this response |
| `DELETE /groups/{groupId}/invites/{inviteId}` | — | `204`. Owner only. Revokes the link |
| `POST /group-invites/{token}/accept` | — | `200` `Group`; the caller becomes a `MEMBER` (already a member: unchanged). Unknown, expired or revoked token: `404` |
| `PUT /groups/{groupId}/members/me` | `{ "aiConsent": boolean }` | `200` `GroupMember`. Opting out deletes the stored shared story |
| `DELETE /groups/{groupId}/members/me` | — | `204`. Leaves the group: the caller's contributions are unlinked, and their trip Era stays but is unlinked. The only owner can't leave: `409 conflict` |
| `DELETE /groups/{groupId}/members/{memberId}` | — | `204`. Owner only, and not for themself (they use `members/me`) |

### Contributions
| Method and path | Body | Success |
|---|---|---|
| `POST /groups/{groupId}/entries` | `{ "entryId": uuid, "confirmUnlockDate"?: instant }` | `201` `GroupEntry` (already added: `200`). The entry must be the caller's own, otherwise `404`. If the group is locked, `confirmUnlockDate` must equal the group's `unlockDate` (otherwise `400`) and the entry is sealed until at least that time; an entry already sealed for longer keeps its date |
| `GET /groups/{groupId}/entries?page=0&size=10` | — | `200` `PageResponse<GroupEntry>`, ordered by `entryDate` then `createdAt`, ascending |
| `DELETE /groups/{groupId}/entries/{entryId}` | — | `204`. Withdraws the caller's own contribution; the entry itself is untouched. Allowed even when sealed |

**Trips as Eras** has no endpoint of its own. When a member joins a group that has a `startDate`, the service creates an Era in their timeline from the group's title and dates. When they contribute, it files that week under the Era if the week has none (see Q10).

### Shared story and friendship map (8b)
| Method and path | Body | Success |
|---|---|---|
| `POST /groups/{groupId}/story` | — | `202`. Builds or rebuilds the shared story in the background from contributions by members who opted in. Sealed entries are never included. Fewer than two such members: `409 not-enough-contributions` |
| `GET /groups/{groupId}/story` | — | `200` `{ "narrativeSummary", "contributorNames": [string], "generatedAt" }`, or `404` if there is none. It is deleted when anyone withdraws, opts out or leaves |
| `PUT /characters/{characterId}/member` | `{ "memberId": uuid or null }` | `200` `Character`. Links it to a member of a group the caller also belongs to (any other ID: `404`); `null` unlinks. Nothing of that member's journal is exposed |

`GET /characters` (Phase 3) gains `"linkedMember": { "displayName" }` on each character, or `null`.

## Phase 6 endpoints (draft; tag: Suggestions)
Tasks 9.1–9.5 (D-21). Finalise after open questions Q15 and Q16. Insight cards are `Suggestion`s with new kinds. They use only the caller's own entries (never other members' contributions) and never sealed ones, and every card that draws on past material lists its `sources`. The rules and problem types of the Phase 3 AI section apply.

Insight cards add `title` and `body` (plain text). A `HIGHLIGHT_REEL` also has `reel`: `{ "entries": [Entry] }`, filled in when the card is read, so redaction always applies and an entry sealed after the reel was made simply drops out.

| Method and path | Body | Success |
|---|---|---|
| `GET /suggestions?inbox=true&page=0&size=10` | — | `200` `PageResponse<Suggestion>`: the caller's journal-wide cards, newest first. Give exactly one of `inbox` and `entryId`. Needs `proactiveInsights` on |
| `POST /suggestions/{suggestionId}/feedback` | `{ "value": "USEFUL" }` or `{ "value": "NOT_USEFUL" }` | `200` `Suggestion` (`ANSWERED`). Feedback tunes how often similar cards appear |
| `POST /highlight-reels` | Exactly one of `{ "eraId" }`, `{ "characterId" }` or `{ "from": date, "to": date }` | `202` `Suggestion` (kind `HIGHLIGHT_REEL`, status `PENDING`). Poll `GET /suggestions/{suggestionId}` |
| `POST /suggestions` | `{ "feature": "writingCoach", "entryId"?: uuid, "contentJson"?: TipTap doc }` | `201` with one `WRITING_INSIGHT`, or `200` with `[]`, which is the usual answer. Needs `writingCoach` on. Over the daily cap: `429 rate-limited` |
