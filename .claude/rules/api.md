---
paths:
  - "backend/src/main/java/**"
  - "backend/src/test/java/**"
  - "docs/API_CONTRACT.md"
---

# HTTP API rules

- **Contract first.** Change `docs/API_CONTRACT.md` before (or with) the code. If code and contract disagree about behaviour, ask the user which one is right.
- **Paths:** `/api/v1/<plural-resource>`. Actions that aren't CRUD are `POST /…/{id}/<verb>` (`/close`, `/reopen`, `/seal`).
- **Methods:** `PUT` is a full replace; `PATCH` is only for `/users/me`.
- **Status codes:** `201` create, `200` read/update/action, `204` delete. Replaying a create with an existing own `id` returns `200` with the stored resource; an `id` owned by someone else returns `409 conflict`.
- **Controller parameters:** never a `userId`. Get the user from `CurrentUserProvider` inside the service.
- **Errors:** throw domain exceptions (`NotFoundException`, `EntrySealedException`, …). The single `@RestControllerAdvice` maps them to `ProblemDetail` with `type = urn:memoirist:problem:<suffix>`, plus `timestamp` and (for validation) `fieldErrors`. No stack traces or SQL in responses.
- **Pagination:** return `PageResponse<T>(items, page, size, totalItems, totalPages)`. Never serialise a Spring `Page` directly. Cap `size` at 50 unless the contract sets another limit (`GET /entries` allows 100).
- **Private input:** search terms (including music searches) and other free text the user types go in request bodies, never in URLs or logs (D-29, D-35).
- **Entries in responses:** always built with `EntryMapper.toResponse(entry, now)` or `EntryMapper.toSummary(entry, now)`. Never build an `EntryResponse` or `EntrySummaryResponse` anywhere else.
- **Media in responses:** `fileUrl` comes from `StorageService.presignGet(objectKey)` at mapping time. Never return or store the raw key or host.
- **Uploads:** check the declared type against the D-17 allow-list (`415`), `purpose` (`PHOTO` or `PAGE_SCAN`, where `PAGE_SCAN` must be an image or `415`, and an unknown purpose is `400`, D-20), size ≤ 25 MB (`413`), count ≤ 20 per entry (`400`), that the entry isn't in Recently deleted (`404`, D-30), and that the entry isn't sealed (`409`).
- **OpenAPI:** one `@Tag` per resource ("Entries", "Eras", …). Document error responses with `@ApiResponse`.
- **JSON:** camelCase. Instants are serialised as ISO-8601 `Z` strings and dates as `YYYY-MM-DD`. Never send timestamps as numbers.
