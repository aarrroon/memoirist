# Task N.M: <task title>

> **Sprint:** N · **Commit:** `<hash>` · **Status:** done / needs attention · **Time taken:** roughly …
> **Built:** one sentence on what exists now that didn't before.

## 1. The goal
What the task had to achieve, in plain words, and why it comes at this point in the build order (what it depends on, what later tasks need from it). Link the task block in [SPRINT_PLAN.md](../SPRINT_PLAN.md).

## 2. What was read first, and why
| Read | What it settled |
|---|---|
| D-xx … | e.g. "timestamps are `TIMESTAMPTZ`, never `LocalDateTime`" |
| API_CONTRACT §… | e.g. "the exact status codes to return" |

Note anything the docs **didn't** settle, and how that gap was handled (a documented default, an assumption to check).

## 3. The plan
The steps, in the order they were done, and **why that order** (for example: "migration first, because the entities are validated against it").

## 4. Decisions and alternatives
One row per real choice made while building. These are the heart of the entry.

| Decision | Options considered | Chosen | Why | Trade-off accepted |
|---|---|---|---|---|
| e.g. how to map JSONB | `@JdbcTypeCode`, a custom `AttributeConverter`, a String column | `@JdbcTypeCode(SqlTypes.JSON)` | Built into Hibernate 7; no extra code | Ties the entity to Hibernate |

## 5. How it was built
Walk through the change in the order a reviewer should read it (contract or migration → domain → service → controller → tests). For each file: its job, the key lines (`file:line`) and anything non-obvious. Short snippets only where the code is the lesson.

## 6. How it was tested
- **Strategy:** which kinds of test, and why (unit for pure logic, Testcontainers integration for anything touching Postgres or storage, …).
- **The tests written**, each with what it proves, as a table: `Test | Proves | Type`.
- **How to run them yourself:** the exact commands, and what passing output looks like.
- **Manual checks**, if any (curl, Swagger, `psql`), with the commands.

## 7. Proof: each "Done when" item
| Done when | Evidence |
|---|---|
| … | the command run and the decisive output line |

## 8. What went wrong
Every failure, in order: what happened (error message), the cause, and the fix. Include attempts that didn't work. "Nothing went wrong" is fine if true.

## 9. Review
What the `spec-reviewer` subagent found, and what was changed because of it. Any assumptions made (Q-x defaults) and docs updated.

## 10. Concepts to learn from this task
Each pattern, idea or piece of syntax that appears, explained in two or three sentences with where it's used:
- **Name** (`file:line`): what it is, why it's here, and the mistake people make with it.

## 11. Practice blocks
| File:line | What to write | What it teaches |
|---|---|---|

## 12. At work
How this transfers: where you'd meet the same problem in an enterprise Spring Boot / React codebase, and what a reviewer there would look for.

## 13. Check yourself
5–8 questions to answer without looking, mixing "why", "what happens if…" and "how would you…". Answers in a folded block:

<details><summary>Answers</summary>

1. …

</details>
