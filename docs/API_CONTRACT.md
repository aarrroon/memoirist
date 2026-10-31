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
  - Reading the header doesn't check that the user exists. A well-formed id that names nobody is `404 not-found` wherever the request needs that user: `GET` and `PATCH /users/me`, and every create. A create starts by loading the current user (`UserService.currentUser()`, task 3.2, D-11), so it never reaches the foreign key and never answers `500`. A list for such a user is an empty list, as for a user with no data. The `404` never repeats the id.
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
| `malformed-request` | 400 | Unreadable JSON, bad UUID in a path or query, a required parameter missing, bad `X-Dev-User-Id` (not a UUID, empty, or sent twice) |
| `not-found` | 404 | Missing **or not owned**; also a path that doesn't exist |
| `method-not-allowed` | 405 | The path exists but not for this HTTP method; the `Allow` header lists the right ones |
| `not-acceptable` | 406 | The `Accept` header names no format the API can send |
| `entry-sealed` | 409 | Changing a sealed entry's content, date, template or media; or moving its unlock date earlier |
| `entry-stale` | 409 | `PUT /entries/{id}` sent a `version` that is not the stored one, older or newer: the entry changed elsewhere (D-23) |
| `conflict` | 409 | Client-supplied `id` already used by another user, or by an entry in Recently deleted (D-30) |
| `payload-too-large` | 413 | File over 25 MB (reliably up to about 2 MB over; see the last note below), content over 200 KB |
| `unsupported-media-type` | 415 | File type not in the allowed list; or a request body sent with a `Content-Type` the endpoint doesn't read |
| `storage-unavailable` | 503 | Object storage failed |
| `music-search-unavailable` | 503 | Spotify search isn't set up, or Spotify refused the request (D-35) |
| `internal-error` | 500 | Anything unexpected (no stack traces in the body) |

What every error response promises (task 2.6, `common/GlobalExceptionHandler`):
- **Same fields everywhere:** `type`, `title`, `status`, `detail`, `instance` (the request path) and `timestamp`, as `application/problem+json`. `fieldErrors` appears only on `validation-failed`. The `type` suffix and `title` are fixed for each row above; `detail` is a sentence for the caller.
- **`fieldErrors`** is a flat object, `field name: message`. A nested field uses its path (`tags[1].name`). A field that failed several rules gets its messages joined with `; `, in alphabetical order. A rule that isn't about one field (such as one that compares two) appears in `detail` only. The field order is stable.
- **Messages are English** whatever the browser's `Accept-Language` says, so a client can match on them.
- **Nothing the client sent comes back.** `detail` and `fieldErrors` never repeat a rejected value, a header, a path variable or a file name. They name the field or parameter (our own identifiers) and say what is wrong.
- **Nothing from the server's insides comes out.** No stack trace, class name, SQL, constraint name, storage host or message from a system exception (Spring, Hibernate, JDBC, the AWS SDK) in any response. A `500` and a `storage-unavailable` always have the same `detail`; the other types' `detail` is a sentence our own code wrote for the caller (so `music-search-unavailable` says which of its two causes it was, and `entry-sealed` may say the entry is sealed). This holds for Spring's own errors (an unknown path, a wrong method) as well, which get a fixed `detail` per status.
- **Nothing the user wrote goes to the log either.** The server logs a failed request with the exception classes (the causes too), the SQLState of a database error and the stack frames, but never an exception's message, because a database message can hold a whole row of an entry (D-10). Hibernate's own SQL-error logger is switched off for the same reason (`logging.level.org.hibernate.orm.jdbc.error`).
- **Spring's own errors keep their status.** An unknown path is `404 not-found`, a wrong method `405 method-not-allowed`, never `500`. A status that isn't in the table (none is expected) keeps Spring's `about:blank` type, which RFC 9457 defines for "no more to say than the status".
- **`entry-stale` has two causes with one answer:** the service saw a `version` that differs from the stored one (older or newer), or the versions matched but another request committed first and the database refused the update.
- **A file far over 25 MB may end in a dropped connection instead of a `413`.** The server rejects the upload as soon as the limit is passed, but it won't read more than about 2 MB of a rejected body just to answer politely (`server.tomcat.max-swallow-size`). A file a little over the limit gets the `413`. Clients check the size first (`.claude/rules/frontend.md`), so this is only a safety net.

### Pagination
Every paged endpoint takes `?page=0&size=10` (`size` max 50, unless the endpoint says otherwise) and returns:
```json
{ "items": [ ... ], "page": 0, "size": 10, "totalItems": 42, "totalPages": 5 }
```
A `page` below 0, or a `size` below 1 or above the endpoint's maximum, is `400 validation-failed` with `fieldErrors.page` or `fieldErrors.size`; it is never clamped silently (task 3.7, first built for the feed). A page past the end is `200` with empty `items`.

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
- `version` goes up on every change to the entry (an edit, a seal, a delete into Recently deleted; not `mark-seen`, which only records a look); send it back on `PUT` (D-23). It isn't private, so sealed entries keep it.
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

How `/users/me` behaves (task 3.2):
- **`birthday` arrives in task 3.13.** Until then the response has the other four fields, and a `birthday` in the body is ignored like any other field the endpoint doesn't know.
- **A field that is missing or `null` is left alone**, and `{}` is a valid request that changes nothing. Only these fields can change: `email`, `id` and the dates are not in the body, so a body that names them has no effect.
- **`displayName`** is stripped of spaces at both ends, then must be 1 to 100 characters (`fieldErrors.displayName`: `size must be between 1 and 100`). A name of only spaces is empty.
- **`timezone`** must be an IANA region name from Java's list, such as `Australia/Melbourne`, `America/New_York` or `UTC` (`fieldErrors.timezone`: `must be an IANA time zone, such as Australia/Melbourne`). Offsets and abbreviations (`+10:00`, `UTC+10`, `Z`, `EST`) are refused even though Java can read some of them, because the browser's `Intl` can't use them. Names are case-sensitive and not trimmed.
- **Both fields are checked before anything is saved**, and every wrong field is named in one `400`. A good name with a bad zone saves neither.
- **`404 not-found`** when `X-Dev-User-Id` names a user who isn't there (see *Who is the current user?*). `415` when the body is not `application/json`. There is no `PUT` or `DELETE` on `/users/me` in Phase 1 (`405`).

### Eras (tag: Eras)
| Method and path | Body | Success |
|---|---|---|
| `POST /eras` | `{ "id"?, "title": string(1–150), "description"?: string, "startDate": date, "endDate"?: date ≥ startDate, "soundtrack"?: Spotify URI or null, "coverMediaId"?: uuid or null, "pinnedInFeed"?: boolean (default `false`) }` | `201` era. It holds every week its dates overlap, existing weeks included (D-22). A cover must be an image from one of the user's own entries, otherwise `400`; a photo from a sealed entry is `400`; another user's media, or a photo from an entry in Recently deleted, is `404` (D-36) |
| `GET /eras` | — | `200` array of eras, `startDate` descending |
| `GET /eras/{eraId}` | — | `200` era plus `"weeklyLogs": [{ "id", "title", "startDate", "endDate", "isClosed", "entryCount" }]`, newest first: the weeks its dates overlap. `entryCount` counts only the week's entries inside the Era's dates (D-22), leaving out sealed entries and entries in Recently deleted (D-10, D-30) |
| `PUT /eras/{eraId}` | Same as POST without `id` (full replace) | `200` era. New dates apply at once: it holds the weeks they overlap (D-22) |
| `DELETE /eras/{eraId}` | — | `204`. Only the Era goes; its weeks, their entries and their other Eras stay |

Era shape: `{ "id", "title", "description", "startDate", "endDate", "soundtrack", "cover", "pinnedInFeed", "createdAt", "updatedAt" }`. `pinnedInFeed` makes the Era win in the feed for weeks it shares (D-22). `cover` is a presigned `Media`, or `null` when there's none or its entry is sealed or in Recently deleted (D-36). `soundtrack` is a Spotify URI or `null` (D-35).

How `/eras` behaves (task 3.3):
- **`soundtrack` and `cover` arrive in tasks 3.14 and 3.15.** Until then the Era has the other eight fields, and a `soundtrack` or `coverMediaId` in the body is ignored like any other field the endpoint doesn't know. Nothing can set either yet, so they are always empty. After 3.14 and 3.15, a `PUT` that leaves one out clears it (a `PUT` is a full replace).
- **The body.**
  - `title` is stripped of spaces at both ends, then must be 1 to 150 characters (`fieldErrors.title`: `must not be null` when missing, `size must be between 1 and 150` when empty or too long).
  - `description` is optional, up to 5000 characters (`size must be between 0 and 5000`). It is stripped, and one that is empty afterwards is saved as none (`null`).
  - `startDate` is required. Both dates must fall between 1900-01-01 and 9999-12-31 (`must be between 1900-01-01 and 9999-12-31`), and `endDate` must not be before `startDate` (`fieldErrors.endDate`: `must not be before startDate`). A one-day Era has the same start and end. A missing `endDate` means the Era is ongoing.
  - `pinnedInFeed` missing or `null` means `false`. On `PUT`, every optional field that is left out is reset (no description, ongoing, not pinned).
  - Every wrong field is named in one `400`. An `id` in a `PUT` body is ignored: the path names the Era.
- **`POST` and the `id`.** `201` for a new Era, `200` with the stored Era (body not re-applied) when the user already has one with that `id`, `409 conflict` when someone else has it (D-12). A user who isn't there is `404` before anything else (see *Who is the current user?*). `GET /eras` for such a user is an empty list.
- **Order.** `GET /eras` lists the latest `startDate` first; Eras that start on the same day come newest-created first. A week's `eras` (task 3.7) use the same order after the pinned ones: pinned Eras first, then latest start, then newest-created (D-22).
- **`GET /eras/{eraId}` lists every week row its dates overlap**, newest first, including a week with `entryCount: 0` (all its entries are outside the Era's dates, sealed or in Recently deleted). `entryCount` counts the entries whose memory date is inside the Era's dates, both ends included, that are not sealed (an opened Loot Drop counts) and not in Recently deleted (D-10, D-30).
- **`DELETE`** removes the Era row and nothing else: no week, entry or other Era changes. Another user's Era, and one that was already deleted, is `404`.
- **`404 not-found`** for an Era that doesn't exist or isn't the user's. `415` when the body is not `application/json`. A path id that isn't a UUID is `400 malformed-request`.

### Templates (tag: Templates)
| Method and path | Body | Success |
|---|---|---|
| `POST /templates` | `{ "id"?, "name": string(1–100), "subheadings": string[] (0–20 items, each 1–200 chars) }` | `201` template |
| `GET /templates` | — | `200` array, by name |
| `GET /templates/{templateId}` | — | `200` template |
| `PUT /templates/{templateId}` | Same as POST without `id` | `200`. Existing entries are unaffected |
| `DELETE /templates/{templateId}` | — | `204`. Entries keep their content; `templateId` becomes null |

Template shape: `{ "id", "name", "subheadings": ["What triggered the spiral?", "..."], "createdAt", "updatedAt" }`.

How `/templates` behaves (task 3.4):
- **The body.**
  - `name` is stripped of spaces at both ends, then must be 1 to 100 characters (`fieldErrors.name`: `must not be null` when missing, `size must be between 1 and 100` when empty or too long).
  - `subheadings` is required, because the table doesn't mark it optional: a missing or `null` list is `fieldErrors.subheadings`: `must not be null`. `[]` is the way to say no prompts. A `PUT` needs it too, so a body that forgets it can't quietly empty the list.
  - The list holds at most 20 items (`fieldErrors.subheadings`: `size must be between 0 and 20`). Each item is stripped, then must be 1 to 200 characters, so an item of only spaces is empty. A bad item is named by its position (`fieldErrors["subheadings[2]"]`: `size must be between 1 and 200`, or `must not be null` for a `null`).
  - Lengths count Java characters (UTF-16 units), which is never more than the database counts, so an accepted name always fits its column. An emoji counts as 2.
  - The order of the subheadings is kept, and repeats are allowed. Two templates may have the same name. Nothing limits how many templates a user has.
  - Every wrong field is named in one `400`. An `id` in a `PUT` body is ignored: the path names the template. A `userId` or a timestamp in a body is ignored too.
- **`POST` and the `id`.** `201` for a new template, `200` with the stored template (body not re-applied) when the user already has one with that `id`, `409 conflict` when someone else has it (D-12). A user who isn't there is `404` before anything else (see *Who is the current user?*). `GET /templates` for such a user is an empty list.
- **Order.** `GET /templates` lists by name, ignoring case and accents (`apple`, `Banana`, `Éclair`, `Zucchini`), with digits sorted as text (`10 days` before `2 days`). Templates whose names look the same come in their exact spelling's order (capitals first), then oldest first. The server sorts them, so the order doesn't depend on the database's collation.
- **`PUT`** replaces the name and the whole list. It changes no entry: an entry that started from the template keeps its `templateId` and every word it holds (D-06). A `PUT` that changes nothing leaves `updatedAt` alone.
- **`DELETE`** removes the template and nothing else. Every entry that started from it stays, with all its content, and its `templateId` becomes `null`; that includes sealed entries (their `templateId` is hidden anyway, D-10) and entries in Recently deleted (D-30). The database does this (`ON DELETE SET NULL`), so an entry's `version` and `updatedAt` don't move. Another user's template, and one that was already deleted, is `404`.
- **`404 not-found`** for a template that doesn't exist or isn't the user's. `415` when the body is not `application/json`. A path id that isn't a UUID is `400 malformed-request`.
- **The starter gallery** (*Trip day*, *Spiralling check-in*, …) lives in the app and adds a copy with this same `POST` (D-06), so the server has no gallery endpoint.

### Tags (tag: Tags)
Manual tags are Phase 1 (D-26). `Tag`: `{ "id", "name", "entryCount" }`. `entryCount` never counts sealed entries (D-10).

| Method and path | Body | Success |
|---|---|---|
| `POST /tags` | `{ "name": string(1–50) }` | `201` `Tag`. A name that already exists, ignoring case, returns the existing tag with `200` |
| `GET /tags` | — | `200` `Tag[]`, by name |
| `PUT /tags/{tagId}` | `{ "name": string(1–50) }` | `200` `Tag`. Renaming to a name that exists: `409 conflict` |
| `DELETE /tags/{tagId}` | — | `204`. Entries keep their content and lose the tag |

Tags are set on an entry with `tagIds` in `POST` and `PUT /entries`. Browse by tag with `GET /entries?tagId=…`.

How `/tags` behaves (task 3.8):
- **The body.** `name` is stripped of spaces at both ends, then must be 1 to 50 characters (`fieldErrors.name`: `must not be null` when missing, `size must be between 1 and 50` when empty, only spaces, or too long). Lengths count Java characters (an emoji counts as 2), which is never more than the database counts. `userId`, `entryCount` and every other field in a body are ignored. Nothing limits how many tags a user has; the 20 in D-26 is the most **one entry** can carry.
- **Names are unique per user, ignoring case** (`Work` and `work` are one tag), decided by the same `lower(name)` the database index uses. Other users' tags don't count: Bob may have his own `Work`.
- **`POST`.** `201` with the new tag. When the user already has a tag with that name, ignoring case, it is `200` with the **stored** tag (its own spelling, `Work`, even if the request said `work`), and nothing is written. Two requests for the same new name at the same moment give one `201`, one `200` and one tag. A user who isn't there is `404` before anything else (see *Who is the current user?*); `GET /tags` for such a user is an empty list.
- **`GET`** lists the user's tags by name, ignoring case and accents, with digits sorted as text, and names that look the same in their exact spelling's order. The server sorts them (the same order as `GET /templates`), so it doesn't depend on the database's collation. `tags` on an `Entry` use the same order.
- **`entryCount`** is the number of the user's entries that carry the tag, leaving out sealed entries and entries in Recently deleted (D-10, D-30). A Loot Drop counts from the moment it opens; a restored entry counts again. A tag that only sealed entries carry is still listed, with `0`: tags are the user's own vocabulary, and the count is what must not hint at a sealed entry.
- **`PUT`** renames the tag and nothing else. A name another one of the user's tags already has, ignoring case, is `409 conflict`; the tag's own name, even changed only in capitals (`work` to `Work`), is fine, and a `PUT` that changes nothing leaves `updatedAt` alone. Two renames to the same free name at the same moment give one `200` and one `409`. A rename changes no entry: they show the new name, but their `version` and `updatedAt` don't move.
- **`DELETE`** removes the tag and nothing else. Every entry that carried it stays with all its content and simply loses the tag; that includes sealed entries (their `tags` are hidden anyway, D-10) and entries in Recently deleted (D-30). The database does this (`ON DELETE CASCADE` on `entry_tags`), so an entry's `version` and `updatedAt` don't move.
- **`404 not-found`** for a tag that doesn't exist or isn't the user's. `415` when a body is not `application/json`. A path id that isn't a UUID is `400 malformed-request`. There is no `GET /tags/{tagId}`: that path is `405`.

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

How `/feed` and `/weekly-logs` behave (task 3.7):
- **Paging.** `page` is 0 or more and `size` is 1 to 50 (defaults 0 and 10). A value outside that is `400 validation-failed` with `fieldErrors.page` or `fieldErrors.size` (`must be greater than or equal to 0`, `must be greater than or equal to 1`, `must be less than or equal to 50`), never a silent clamp, so a client that asks for 51 learns it. A value that is not a whole number is `400 malformed-request`. A page past the end is `200` with empty `items` and the real `totalItems` and `totalPages`. A user with no entries gets `totalItems: 0` and `totalPages: 0`, and so does a header that names a user who isn't there (see *Who is the current user?*).
- **Which weeks.** A week is on the feed if it has at least one entry that is not in Recently deleted. A week whose entries are all sealed stays, as locked rows (D-10); a week whose only entries are in Recently deleted, or whose entries were erased, is left out and is not counted in `totalItems` (D-08, D-30). `GET /weekly-logs/{logId}` still finds a week with no entries left (`200`, `entries: []`), because an Era's page lists such weeks and a client may open one.
- **A week's entries** are the same `Entry` objects as everywhere else, built by the one mapper: a sealed Loot Drop is a locked row with nothing private in it and no signed photo URL, and it is whole again at the unlock instant (D-10). They run from the oldest `entryDate`, then the oldest `createdAt`, so the week reads as a story (D-25).
- **A week's `eras`** are every Era of the user whose dates overlap the week, pinned first, then the latest start, then the newest created (D-22; `WeekEras.of`, task 3.3). Each is `{ id, title, startDate, endDate, pinnedInFeed }` (`endDate` is `null` for an ongoing Era), and an Era of another user is never listed. They are worked out when the week is read, so a new Era, new dates, a pin or a deleted Era show on the very next read. Which Era a week is grouped under, and any nesting, is the client's job from this list (D-22).
- **`soundtrack`** is in every week and is `null` until task 3.14 can set it.
- **`PUT /weekly-logs/{logId}`** replaces what the user may set, which is the title for now. `title` is stripped of spaces at both ends and may be 0 to 200 characters (`fieldErrors.title`: `size must be between 0 and 200`); a title that is left out, `null`, empty or only spaces clears it (stored as `null`). Task 3.14 adds `soundtrack` to the body; until then a `soundtrack` in the body, and every other field the endpoint doesn't know (`isClosed`, `startDate`, `eras`, `userId`), is ignored. The answer is the whole `WeeklyLog`. A `PUT` that changes nothing writes nothing.
- **`close` and `reopen`** change only `isClosed`, answer with the whole `WeeklyLog`, and are safe to repeat: closing a closed week, or reopening an open one, is `200` and writes nothing. A closed week stays editable: its title can change and entries can still be added to it. Closing and renaming a week at the same moment from two devices can't undo each other, because each writes only its own column.
- **`404 not-found`** for a week that doesn't exist or isn't the user's, on all four calls (`detail` never repeats the id). `415` when a `PUT` body is not `application/json`. A path id that isn't a UUID is `400 malformed-request`. There is no `POST /weekly-logs` and no `DELETE`: `DELETE /weekly-logs/{logId}` is `405`, and `POST /weekly-logs` is `404` like any path that doesn't exist.

### Entries (tag: Entries)
| Method and path | Body | Success |
|---|---|---|
| `POST /entries` | `{ "id"?, "entryDate"?: date (default today in the user's zone; not after today; not before 1900-01-01), "templateId"?: uuid, "title"?: string(1–150) or null, "contentJson"?: TipTap doc or null, "tagIds"?: [uuid] (at most 20, the user's own), "soundtrack"?: Spotify URI or null, "replyToEntryId"?: uuid or null, "dateUnknown"?: boolean (default `false`; the photo dump sets it, D-34) }` | `201` `Entry`, filed into the right week (created if needed). Replay of an existing own `id`: `200`. An entry may be created empty because media are uploaded afterwards (D-23). `replyToEntryId` must be one of the user's own Loot Drops that has opened, otherwise `400`; another user's is `404` (D-33) |
| `GET /entries?page=0&size=50&tagId={uuid}` | — | `200` `PageResponse<EntrySummary>` of every entry, `entryDate` then `createdAt`, newest first. `tagId` is optional and filters by tag; sealed entries are left out of the list and of `totalItems` while filtering (D-10). `size` defaults to 50 and is at most 100 here. Backs the all-entries timeline (D-25). Details in *The list* below |
| `GET /entries/opened` | — | `200` `EntrySummary[]` (a plain array, not a page): Loot Drops whose unlock time has passed and that haven't been marked seen, most recently opened first, at most 20 (D-10). Backs the *Opened* strip. Details in *The Opened strip* below |
| `GET /entries/deleted?page=0&size=10` | — | `200` `PageResponse<EntrySummary>` of the entries in Recently deleted, most recently deleted first, each with `deletedAt` (D-30). Sealed ones are redacted (D-10). Backs the *Recently deleted* screen |
| `GET /entries/on-this-day` | — | `200` `EntrySummary[]`: entries from the same day and month in earlier years, newest year first, at most 20 (D-32). Sealed and deleted entries never appear. In a year that isn't a leap year, 29 February entries appear on 28 February. Backs the *On this day* strip |
| `GET /entries/{entryId}` | — | `200` `Entry` (redacted if sealed) |
| `PUT /entries/{entryId}` | `{ "version": integer (the one last read), "entryDate": date, "templateId": uuid or null, "title": string or null, "contentJson": doc or null, "tagIds": [uuid], "soundtrack": Spotify URI or null, "replyToEntryId": uuid or null }` (full replace; a date change moves the entry to the matching week and clears `dateUnknown`, D-34) | `200` `Entry`. Sealed: `409 entry-sealed`. Stale `version`: `409 entry-stale`, nothing saved (D-23). A tag ID that isn't the user's: `404` |
| `DELETE /entries/{entryId}` | — | `204`. Moves the entry to Recently deleted for 30 days; its media files stay until it is erased. Allowed even when sealed. An entry with no title, caption or media is erased at once (D-30) |
| `POST /entries/{entryId}/restore` | — | `200` `Entry`, back in its week with its media, tags and seal as they were (D-30). Not in Recently deleted: `404` |
| `POST /entries/{entryId}/erase` | — | `204`. Erases an entry in Recently deleted, with its media files, at once. Not in Recently deleted: `404` |
| `POST /entries/{entryId}/seal` | `{ "unlockDate": instant }`: after now, at most 100 years ahead; if already sealed, must be later than the current `unlockDate` | `200` `Entry` (now redacted). Earlier date on a sealed entry: `409 entry-sealed`. Sealing clears the entry's seen mark, so it shows in the Opened strip again when it opens |
| `POST /entries/{entryId}/mark-seen` | — | `204`. Records that the user has seen an opened Loot Drop, which takes it out of the Opened strip; safe to repeat. Still sealed: `409 entry-sealed`. It is a view mark, not an edit: `version` and `updatedAt` don't change. Details in *The Opened strip* below |

**Recently deleted (D-30):** an entry in Recently deleted is left out of every list, count and filter, the feed, its week and the Opened strip included. Every other request about it, such as `GET`, `PUT`, `seal`, `mark-seen` and the media calls, returns `404`.

**Content rules (D-05):**
- Root `type` is `"doc"`.
- Allowed nodes: `paragraph`, `text`, `heading` (levels 1–3), `bulletList`, `orderedList`, `listItem`, `blockquote`, `hardBreak`, `horizontalRule`.
- Allowed marks: `bold`, `italic`, `strike`, `code`, `link` (`http`, `https` or `mailto` only).
- At most 200 KB serialised: over it is `413 payload-too-large` (details in *How `/entries` behaves*, below).
- Anything else: `400 validation-failed`. `contentText` is always computed by the server; clients never send it.

How `/entries` behaves (task 3.5):
- **What exists after 3.10:** `POST /entries`, `GET /entries` (the list, task 3.9), `GET /entries/opened` and `POST /entries/{entryId}/mark-seen` (the Opened strip, task 3.10), `GET /entries/{entryId}`, `PUT /entries/{entryId}`, `DELETE /entries/{entryId}` and `POST /entries/{entryId}/seal`. The Recently deleted, On this day, `restore` and `erase` rows arrive in tasks 3.11 and 3.12. `tagIds` is read since task 3.8 (see *Tags on an entry*, below). `replyToEntryId` (3.13) and `soundtrack` (3.14) are not read yet: a body that names them is ignored, a `PUT` leaves the entry's reply target and soundtrack as they are, and an `Entry` shows `soundtrack: null`, `replyToEntryId: null` and `replyIds: []` until they can be set. `media` is filled by the uploads of task 3.6 and presigned here.
- **Size and shape of the caption.**
  - Over 200 KB (200 × 1024 bytes of compact UTF-8 JSON, counted in bytes, not characters) is `413 payload-too-large`; exactly 200 KB is accepted. The size is checked first, before the document is walked.
  - Every other broken rule is `400 validation-failed` with `fieldErrors.contentJson`, one sentence that says what is wrong and where (`has a node type that is not allowed (at content[1])`), and never repeats what the client sent. A caption that is not an object whose `type` is `doc` (a string, a list, `{}`) is `must be a TipTap document: an object whose type is doc`.
  - A node has only the fields `type`, `content`, `attrs`, `marks` and `text`; a field set to `null` counts as absent. `text` belongs to `text` nodes only and is required there. `text`, `hardBreak` and `horizontalRule` have no `content`. A `heading` needs `attrs.level` of 1, 2 or 3. Other `attrs` are kept as sent.
  - A `link` needs `attrs.href` to start (after spaces at both ends are removed) with `http://`, `https://` or `mailto:`, in any capitals, followed by something. Nothing else is accepted: not `javascript:`, `data:`, `file:`, `//host`, `/path` or a bare `example.com`. An address with a control character anywhere (a tab, a line break, a null) is refused, because a browser deletes those before it reads the scheme, which turns `java<TAB>script:` into `javascript:`.
  - A document nested more than 500 levels is refused while the request is read: `400 malformed-request`. A very large body is read in full before the 200 KB check can run; a request-size limit belongs to the deployment (Phase 2).
  - `contentText` is the words of the caption, one line per paragraph, heading, list item and `hardBreak`, with the formatting gone and leading and trailing blank lines removed. `previewLine` (stored, not in `Entry`) is the first line that isn't a heading, or the first heading if there is nothing else, cut to 120 characters (D-24).
- **The body of `POST`.**
  - Every field is optional; `{}` creates an empty entry for today (D-23). `null` and a missing field mean the same.
  - `title` is stripped, then must be 1 to 150 characters (`fieldErrors.title`: `size must be between 1 and 150`), so `""` and `"   "` are `400`; no title is `null`. Lengths count Java characters (an emoji counts as 2), which is never more than the database counts.
  - `entryDate` defaults to today **in the user's time zone** (D-07). It may not be after that today (`fieldErrors.entryDate`: `must not be after today`) or before 1900-01-01 (`must not be before 1900-01-01`), both `400`. A date that isn't a calendar date (`2026-13-45`) is `400 malformed-request`.
  - `templateId` must be one of the user's own templates; someone else's, and one that doesn't exist, are the same `404`, and nothing is created (no week either). `tagIds` is checked the same way, after it (see *Tags on an entry*).
  - `dateUnknown` defaults to `false`. `userId`, `weeklyLogId`, `unlockDate`, `version`, `contentText` and every other field the endpoint doesn't know are ignored. Every wrong field is named in one `400` where bean validation can see it; the checks that need the clock, the document or the database (date, caption, template) come after it and stop at the first failure.
  - The entry is filed in the week that contains its memory date, Monday to Sunday (D-08); the week is created if this is its first entry. Which day it is, for "today" and for the week, is decided in the user's zone, so 23:30 on a Sunday in Melbourne is that week even though it is already Monday morning somewhere else.
  - `201` for a new entry, `200` with the stored entry (body not re-applied, even a body that would now be invalid; redacted if it is sealed) when the user already has an entry with that `id`, `409 conflict` when someone else has it or when it is one of the user's own entries in Recently deleted (D-12, D-30). A user who isn't there is `404` before anything else (see *Who is the current user?*).
- **Tags on an entry (task 3.8, D-26).**
  - `tagIds` is a list of the user's own tag ids. On `POST` it is optional (missing, `null` and `[]` mean no tags); on `PUT` it is required and replaces the whole set, so `[]` removes every tag.
  - At most 20 items, counted as sent (`fieldErrors.tagIds`: `size must be between 0 and 20`), so 21 is `400` however many are the same. A `null` item is named by its position (`fieldErrors["tagIds[1]"]`: `must not be null`). An id repeated in the list is counted once. An item that isn't a UUID is `400 malformed-request`.
  - A tag that doesn't exist and one that is another user's are the same `404 not-found`, and the error doesn't say which ids were wrong. If any id fails, nothing is created or changed (a `POST` makes no week either). The tags are checked after the date, the caption and the template. A tag that is deleted at the same moment the entry is being saved with it is also `404` (the delete and the save take turns), never a `500`.
  - A sealed entry has no tags to show or change (D-10). Its `PUT` is `409 entry-sealed` before `tagIds` is looked at (a body that is malformed on its own, such as a missing `tagIds` or more than 20 ids, is `400` first, which says nothing about the entry), so even an id that doesn't exist can't be told apart from a good one. A Loot Drop that has opened is an ordinary entry again.
  - A change to an entry's tags is a change to the entry: it raises `version` by one and moves `updatedAt`. A `PUT` that names the tags it already has writes nothing. A replayed `POST` (an existing own `id`) does not apply its `tagIds`.
  - `tags` on an `Entry` are in the order of `GET /tags`, and each one has `source: "USER"` until Phase 4.
- **The list (task 3.9, D-24, D-25).** `GET /entries` is the all-entries timeline: one `EntrySummary` row per entry, built by the one mapper, `EntryMapper.toSummary`. It reads and writes nothing else.
  - **Paging.** `page` is 0 or more (default 0) and `size` is 1 to 100 (default 50). A value outside that is `400 validation-failed` with `fieldErrors.page` or `fieldErrors.size` (`must be greater than or equal to 0`, `must be greater than or equal to 1`, `must be less than or equal to 100`), never a silent clamp; a value that is not a whole number is `400 malformed-request`. An empty value (`size=`, `page=`, `tagId=`) is read as not sent, so it takes the default (or means no filter). A page past the end is `200` with empty `items` and the real `totalItems` and `totalPages`. A user with no entries, and a header that names a user who isn't there, get `totalItems: 0` and `totalPages: 0`.
  - **Order.** Newest `entryDate` first, then newest `createdAt`, then the entry's id, so two entries made in the same instant on the same day (a photo dump) always come in the same order and a client paging through never sees one twice or misses one.
  - **Which entries.** All of the user's entries except those in Recently deleted (D-30), which are left out of the rows and of `totalItems`. A sealed Loot Drop is listed as a locked row (D-10): `isLocked: true`, its `unlockDate`, and none of `title`, `previewLine`, `photoCount`, `voiceNoteCount` and `firstMediaKind`, with empty `tags`. A Loot Drop is whole again at the very instant of its `unlockDate`.
  - **`tagId`.** Lists only the user's entries that carry the tag, in the same order and with the same paging. **Sealed entries are not listed and not counted** while filtering, so `totalItems` doesn't move when an entry is sealed and nothing shows that a Loot Drop carries the tag; a tag that only sealed entries carry answers exactly like a tag nobody has used (`200`, `items: []`, `totalItems: 0`). An entry that has opened is an ordinary entry again and is listed. Each row still lists all the entry's tags, not only the one filtered by. A `tagId` that is not the user's tag, and one that doesn't exist, are the same `404 not-found` (and `detail` never repeats the id); one that is not a UUID is `400 malformed-request`.
  - **The row.** `title` is the entry's title or `null`. `previewLine` is the stored one (D-24): the first line of the caption that isn't a heading, or the first heading if the caption has nothing else, cut to 120 characters, or `null` when there is no caption. It follows the caption whenever the caption is saved. `photoCount` counts every image, diary pages included, `voiceNoteCount` every audio file, and `firstMediaKind` is the kind of the item with the lowest `displayOrder`: `PAGE_SCAN` for a diary page, `PHOTO` for any other image, `VOICE_NOTE` for audio, or `null` with counts of `0` when there is no media. There is no caption, no media list and no file URL in a row; `GET /entries/{entryId}` has those. `tags` are in the order of `GET /tags` with `source: "USER"`. `deletedAt` is always `null` here (it is filled in the Recently deleted list, task 3.11).
  - **Cost.** A page runs the same few statements whatever it holds: the page, the media of its open entries and their tags, plus the count when the page is full, plus one check of the tag when filtering. A sealed entry's media and tags are never loaded.
- **The Opened strip (task 3.10, D-10).** `GET /entries/opened` and `POST /entries/{entryId}/mark-seen` are the two halves of one idea: a Loot Drop that has opened is listed until the user has seen it.
  - **Which Loot Drops.** The user's entries with an unlock date that is not after now (the very instant of the unlock date counts as open) and no seen mark. A sealed Loot Drop is never listed, so nothing in the answer hints that one is waiting; an entry that was never sealed has no unlock date and is never listed; an entry in Recently deleted is left out (D-30) and comes back to the strip when it is restored, if it was not marked seen. Another user's Loot Drops are never listed. A user the header names who isn't there gets `[]`, like the lists (see *Who is the current user?*).
  - **The answer.** A plain JSON array of `EntrySummary` rows, not a `PageResponse`: the strip has no page two. It is the Loot Drop that opened most recently first (`unlockDate` newest first, then the entry's id, so two that open in the same instant always come in the same order), at most 20; any beyond that wait their turn and show once the others are marked seen. Every row is open, so every row is whole: `isLocked: false`, with its `title`, `previewLine`, media counts, `firstMediaKind` and tags, built by the one mapper (`EntryMapper.toSummary`). `unlockDate` is the instant it opened. There is no query string. `[]` when there is nothing to show.
  - **`mark-seen`.** Writes the time of the request to `unlock_seen_at` and answers `204` with no body. It is safe to repeat: the second call changes nothing, and the time of the first look stays. The checks, in order: the entry is the user's and not in Recently deleted (`404`; another user's, a missing one and a deleted one look the same, and `detail` never repeats the id), then it is not sealed (`409 entry-sealed`, nothing is written). An entry that was never sealed is not a Loot Drop and has nothing to mark: it answers `204` and writes nothing. A path id that isn't a UUID is `400 malformed-request`.
  - **A view mark is not an edit.** `mark-seen` leaves the entry's `version` and `updatedAt` exactly as they were, so a client that reads an opened Loot Drop, marks it seen and then saves the caption it was editing is not told its copy is stale (`PUT` with that `version` is `200`). `unlock_seen_at` is never in an `Entry` or an `EntrySummary`; the strip is how a client learns what is unseen.
  - **Coming back.** `seal` on an opened Loot Drop clears the seen mark (task 3.5), so a Loot Drop that is sealed again leaves the strip at once, whether or not it had been marked seen, and is listed again when its new unlock date passes. `seal` always wins over a `mark-seen` that happens at the same moment: a sealed Loot Drop never keeps a mark from before its seal. A `PUT` that read the entry just before a `mark-seen` and saves just after it writes the old, empty mark back, so the Opened strip shows that Loot Drop once more; nothing is lost and the next `mark-seen` clears it.
  - **Cost.** The strip runs 3 statements for any number of Loot Drops, photos and tags (the query, then the media and the tags of its rows), and 1 when there is nothing to show; the query is served by the partial index `idx_entries_opened_unseen`. `mark-seen` runs 2 (the lookup and one conditional `UPDATE`).
- **`PUT`** replaces the date, template, title, caption and tags (`dateUnknown`, `soundtrack` and `replyToEntryId` are not part of it yet; see the first bullet). `version`, `entryDate` and `tagIds` are required; `title`, `templateId` and `contentJson` that are left out are cleared. `tagIds` is required, like `subheadings` on a template, because the table doesn't mark it "or null": a body that forgets it must not quietly strip every tag (`fieldErrors.tagIds`: `must not be null`), and `[]` is the way to say no tags. The checks run in this order and the first to fail answers: the entry is the user's and not in Recently deleted (`404`), it isn't sealed (`409 entry-sealed`, whatever `version` was sent), `version` is the stored one (`409 entry-stale`), the new values are valid (`400`; `404` for a template or a tag that isn't the user's). Nothing is written unless all pass.
  - A changed `entryDate` moves the entry to the week that contains it (the week is created if needed; the week it left stays, and the feed hides empty weeks, D-08) and clears `dateUnknown`. The date limits apply only to a date the client is changing, so an entry whose date is "in the future" because the user changed time zone can still be edited. `templateId` is checked only when it differs from the entry's current one.
  - **`version`.** A new entry is at `0`. A request that changes the entry raises it by exactly one (an edit, a seal, a delete into Recently deleted). `mark-seen` is not one of them: it records that the user looked, not that the entry changed (see *The Opened strip*). A `PUT` that changes nothing writes nothing and leaves it. A `version` that is not the stored one, older **or** newer, is `409 entry-stale` and nothing is saved; the client's number is only compared, never stored. Two requests with the same `version` at the same moment: one saves, the other is `409 entry-stale`.
  - A sealed entry has no successful `PUT`, so there is no redacted `PUT` answer: the `409` says only that the entry is sealed and carries nothing from it. A Loot Drop that has opened is editable, and its `PUT` answer is complete.
- **`DELETE`** sets the entry aside in Recently deleted, with its media files, tags and seal as they are (D-30). An entry with **no title, no words in its caption and no media** is erased at once instead, its id is free again, and it never appears in Recently deleted; a caption of only empty paragraphs or a divider has no words. Deleting a sealed entry is always allowed. It is `204` with no body; a second `DELETE` is `404`.
- **`seal`.**
  - `unlockDate` is required (`must not be null`), must be after now (`fieldErrors.unlockDate`: `must be in the future`) and at most 100 years from now, that exact instant included (`must be at most 100 years ahead`), both `400`. The server keeps whole microseconds, as the database does, and the answer shows what is stored. An offset in the request is fine; the answer is always UTC.
  - On an entry that is sealed now, the date must be **later** than the current one; the same or an earlier date is `409 entry-sealed`. A date that is not in the future is `400` first, sealed or not.
  - A Loot Drop that has opened is not sealed any more: it can be sealed again to any future date. Sealing clears `unlock_seen_at` and leaves the background job's `unlock_notified` alone.
  - The answer is the entry as the mapper shows it at that moment: sealed, so redacted.
- **Redaction.** Every answer that carries an `Entry` is built by the mapper: `GET`, a replayed `POST`, a new `POST`, `PUT` and `seal`. While sealed it shows only `id`, `weeklyLogId`, `entryDate`, `createdAt`, `updatedAt`, `unlockDate`, `isLocked: true`, `dateUnknown` and `version` (D-10, D-34); every other field is `null` or empty, and no photo URL is signed. The unlock instant itself opens it: at `unlockDate` the entry is whole again.
- **`404 not-found`** for an entry that doesn't exist, isn't the user's or is in Recently deleted; the three look the same, and `detail` never repeats the id. `415` when the body is not `application/json`. A path id that isn't a UUID is `400 malformed-request`.

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

How `/media` behaves (task 3.6):
- **What exists after 3.6:** the three calls above. `PUT /media/{mediaId}` (fixing a photo's `purpose`) is a Phase 4 draft and isn't built. The files of an entry that is **erased** go with it, after the commit (D-30); the endpoint that erases is task 3.11, and `EntryErasure` is the one place that does it.
- **Who may:** the entry (or the media item's entry) is the user's and not in Recently deleted, otherwise `404 not-found`; another user's, a missing one and a deleted one look the same, and `detail` never repeats an id. Then it is not sealed, otherwise `409 entry-sealed` whatever else is wrong with the request, except that a reorder body that fails bean validation (`mediaIds` missing, over 20 ids, or a null id) is `400` before the entry is looked up. A Loot Drop that has opened is not sealed and takes changes (D-10). A path id that isn't a UUID is `400 malformed-request`.
- **The order of the checks on an upload**, and the first to fail answers: entry (`404`), seal (`409`), then the form: `file` present and not empty (`400`, `fieldErrors.file`: `must not be null`, `must not be empty`), `purpose` (`400`), `displayOrder` not negative (`400`), the file's type (`415`), a `PAGE_SCAN` being an image (`415`), the size (`413`), then the room: fewer than 20 items already (`400`, `fieldErrors.file`: `an entry can hold at most 20 photos and voice notes`) and `displayOrder` not past the end (`400`). A refused upload stores nothing.
- **The type** is the `Content-Type` the client gave the `file` part, with its parameters dropped and capitals ignored: Chrome labels a voice recording `audio/webm;codecs=opus`, and it is accepted and stored as `audio/webm`. `Media.mediaType` and the `Content-Type` the file is later served with are that plain type. The bytes are not inspected and the client's file name is never read, stored or used in the object key; the key is `users/{userId}/entries/{entryId}/{mediaId}.{ext}` with `jpg`, `png`, `webp`, `webm`, `m4a` (for `audio/mp4`) or `mp3`. A missing or unreadable type is `415`. The `415` detail lists the allowed types and never repeats the one sent.
- **The size** is 25 MB as Spring counts it, 25 × 1024 × 1024 = 26,214,400 bytes, and a file of exactly that is accepted. Spring's multipart limit stops a bigger one while it is read, before the entry is looked up, so a file over the limit is `413` even for an entry that is someone else's or sealed; that tells the caller nothing about the entry. The service checks the same number again. See *Errors* for a file far over the limit.
- **`purpose`** is exactly `PHOTO` or `PAGE_SCAN` (capitals matter, an empty value is unknown): left out it is `PHOTO`, anything else is `400` with `fieldErrors.purpose`: `must be PHOTO or PAGE_SCAN`. `PAGE_SCAN` needs an image type (`415` for audio).
- **The order.** An entry's `displayOrder` values are always `0, 1, 2, …` with no gap and no repeat. An upload goes last, or at the `displayOrder` asked for, which moves the items from that place on down by one; a value between 0 and the number of items now is accepted, and anything past that is `400` (`fieldErrors.displayOrder`: `must be between 0 and 3` for an entry that holds 3). A `displayOrder` that isn't a whole number is `400 malformed-request`. Deleting an item moves the later ones up. Calls that change the same entry's media take turns (the entry's row is locked), so several uploads sent at once each get their own place.
- **Media changes never touch the entry's `version`**: a photo added while the user types a caption must not make the caption's `PUT` stale (D-23). The entry shows the new item at once (`media` on `GET /entries/{id}`, in `displayOrder`).
- **Files and rows.** An upload stores the file first and writes the row second; if anything fails after the file is stored (the entry was sealed, deleted or filled up in that moment, or the row could not be written) the file is deleted again, and the error the user sees is the first one. Storage being down is `503 storage-unavailable` with no row. Deleting an item deletes the row first and the file after the commit; a file that can't be deleted is logged and left (wasted space), and the request is still `204`, because the change was saved.
- **`PUT …/media/order`** takes `mediaIds`: not null, at most 20, no null element (`fieldErrors.mediaIds`, `fieldErrors.mediaIds[1]`), and exactly the ids of the entry's media. Three `400` answers: `must not list the same id twice`, `must only list media that belong to this entry` (an id from another entry, from another user or from nowhere gets the same sentence, so it confirms nothing) and `must list every photo and voice note of the entry`. An entry with no media takes `[]` and answers `[]`. The answer lists the whole carousel with fresh presigned URLs; an order that is already the current one is accepted and writes nothing.
- **`DELETE /media/{mediaId}`** is `204` and a second one is `404`. If the item was an Era's cover, the cover is cleared by the database (`ON DELETE SET NULL`, D-36).

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
