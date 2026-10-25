---
name: new-endpoint
description: Recipe for adding or changing a memoirist backend REST endpoint — contract, DTO records, user-scoped service, mapper, controller, tests. Use when implementing any /api/v1 endpoint or changing a request/response shape.
argument-hint: "[resource or endpoint, e.g. eras CRUD]"
---

# Add or change an endpoint

Target: **$ARGUMENTS**

Work through these steps in order. The HTTP rules are in `.claude/rules/api.md` and the ownership and seal rules in `.claude/rules/security.md`.

1. **Contract.**
   - Find the endpoint in `docs/API_CONTRACT.md`. If it's missing or the shape changes, update the contract first and show the user the diff.
   - Note the status codes and error types the endpoint must return.

2. **DTOs** in `<feature>/dto/`, as records:
   - `XCreateRequest` / `XUpdateRequest`, with Jakarta validation matching the contract's limits.
   - `XResponse`, where `isX` booleans carry `@JsonProperty("isX")`.

3. **Repository.**
   - Add user-scoped finders only (`findByIdAndUserId`, …).
   - For lists, think about the queries: no N+1, and never fetch-join two collections in one query.

4. **Service** (`@Transactional`):
   - Get the user ID from `CurrentUserProvider`.
   - Load through scoped finders; a missing or unowned resource throws `NotFoundException`.
   - Check that every referenced ID (`eraId`, `templateId`, …) belongs to the user.
   - Optional client `id` on create: if it's new, insert. If it exists and is owned, return the existing resource (the controller sends `200`). If it exists and isn't owned, throw a conflict (`409`).
   - Entries and media: apply the seal rules (`EntrySealedException`). Take time from the injected `Clock`.

5. **Mapper.**
   - Entries always go through `EntryMapper.toResponse(entry, now)`.
   - Media URLs come from `StorageService.presignGet`.

6. **Controller.**
   - `@Tag` for the resource; `@ApiResponse` for each documented error.
   - No `userId` parameter and no business logic.
   - Return `201`, `200` or `204` as the contract says.

7. **Tests** (`@SpringBootTest` + Testcontainers + MockMvc or RestTestClient). At minimum:

   | Case | Expect |
   |---|---|
   | Happy path, JSON field names exactly as in the contract (`isLocked`, not `locked`) | 2xx |
   | Invalid input, with `fieldErrors` | 400 |
   | Another user's resource (send a different `X-Dev-User-Id`) | 404 |
   | Referenced ID owned by another user | 404 |
   | Entry or media on a sealed entry (where relevant) | 409 `entry-sealed`, and nothing leaked in any response |
   | Create replay with the same `id` (where relevant) | 200 |

8. **Verify and sync.**
   - Run `cd backend && ./gradlew test` and quote the summary.
   - Check the endpoint in `/swagger-ui.html`.
   - Run the `sync-docs` skill if the contract changed.
   - If the frontend exists, run `cd frontend && npm run gen:api` (backend running) and fix any type errors.
