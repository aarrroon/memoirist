---
paths:
  - "backend/src/main/**"
  - "frontend/src/**"
---

# Security and privacy rules

## Ownership: every request, every resource
- The current user comes only from `CurrentUserProvider`.
- Load user data only through user-scoped finders (`findByIdAndUserId`, or through the owning entry for media).
- A resource that is missing or not owned throws `NotFoundException`, which becomes `404`. Never `403`, which would reveal that it exists.
- When a request references another resource (the `tagIds` or `templateId` on an entry), check that the referenced one is also owned. Otherwise return `404`.
- An entry in Recently deleted (D-30) counts as missing everywhere except its own list, restore and erase: `404`, never presigned, and never in counts, filters, share links, group views or AI input.

## Dev user header
- `DevHeaderCurrentUserProvider` is annotated `@Profile({"local","test"})`. No other profile may resolve `X-Dev-User-Id`.
- The dev-user seed lives in `db/seed-local`, which only the `local` and `test` profiles load.

## Loot Drops (strict seal)
"Sealed" means `unlockDate != null && unlockDate.isAfter(Instant.now(clock))`.
- **Redaction:** responses go through `EntryMapper`. It nulls `title`, `contentJson`, `contentText` and `templateId`, empties `media` and `tags`, and sets `isLocked`. In `EntrySummary` it also nulls `previewLine`, `firstMediaKind` and the media counts.
- **Counts and filters:** sealed entries never appear in tag filters, tag counts, search results or any other count or filter; leave them out in the query itself (D-10). Unfiltered lists still show them as locked rows.
- **Writes:** reject changes to title, content, date, template, tags and media with `EntrySealedException` (`409`). The unlock date may only move later. Delete is allowed; the entry stays sealed in Recently deleted (D-30).
- **Never** presign media of a sealed entry, log its content, include it in AI input or summaries (Phase 4), or expose it through share links (Phase 2).

## Content (TipTap JSON)
- **Backend:** validate against the allow-list and the 200 KB cap. Reject unknown node types, unknown marks and link `href`s that aren't `http:`, `https:` or `mailto:`. Derive `content_text` on the server.
- **Frontend:** render captions from JSON only. No `dangerouslySetInnerHTML`, no `innerHTML`, no HTML strings from the API.

## Secrets and logs
- Never read, print or commit `.env`. Read secrets from configuration and environment variables. New variables go in `.env.example` with a placeholder value.
- Never log entry content, search terms, presigned URLs, tokens or passwords. Log IDs instead.
- Keep presigned URLs short-lived (60 minutes) and generate them only for media the caller may see.
- The Spotify client secret stays on the server. The browser never calls Spotify's API, and Spotify's embedded player loads only when the user taps it (D-35).
- Emails never contain entry content, titles, captions, photos or summary text: dates, counts and a link only (D-32).
- Product events hold a user, a kind and a time, never content (D-38).

## Network
- Locally, the Vite proxy means no CORS configuration is needed. Never add a wildcard CORS origin.
- Phase 2 prefers a same-origin `/api` rewrite on Vercel; any CORS allow-list must name exact origins.
