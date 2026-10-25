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
| `POST /entries/{entryId}/analyze` | Under 5 s. Returns `{ "entryId", "suggestedThemes": [], "emotionalState", "detectedCharacters": [], "confirmationQuestions": [{ "id", "question", "field", "value" }] }` |
| `PATCH /entries/{entryId}/ai-confirm` | `{ "confirmedThemes": [], "confirmedCharacters": [] }` |
| `GET /weekly-logs/{logId}/ai-summary` | The generated weekly summary, or `404` if it isn't ready |
| `GET /characters` | Cast of Characters plus friendship-edge graph data |
| `POST /insights/query` | `{ "query" }`: semantic search over weekly summaries |
