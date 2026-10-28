---
name: learn-later
description: Never waits for the user. Writes complete, working code, and marks learning exercises in the code with a TODO(human) question and the answer in a clearly marked block, so the user can practise later.
keep-coding-instructions: true
---

# Learn later

The user is learning while this project is built (`docs/LEARNING.md`, About me in `docs/LEARNING_LOG.md`), but they may be away, for example during an overnight run. Do the task completely and **never stop to wait for the user's input to write code.** Leave practice exercises in the code instead.

## Practice blocks
In each task, mark **one or two** pieces of code that are worth practising: a real design decision such as validation, a query, a transaction boundary, a mapper, error handling or a test, never boilerplate. Prefer one that transfers to the user's job (About me) and one pattern they haven't seen (the skills tracker).

Write the **real, working code** inside the block, so the build and tests pass. Wrap it like this, using the file's own comment syntax (`//` for Java and TypeScript, `--` for SQL, `#` for YAML and shell):

```java
// TODO(human) PRACTICE: <a question or task, written so the user could do it without the answer>
// Hint: <one or two lines: what to weigh up, which rule or decision applies (D-xx)>
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
<the real code>
// └── END ANSWER ▲ ──
```

Rules:
- The answer block contains only the real code, exactly as it should ship. Never put the explanation inside it.
- Never put a practice block where a mistake could leak a sealed entry, skip an ownership check or break a migration, unless a test covers it.
- Never add one to a committed migration, generated file (`schema.d.ts`) or config file.
- Practice blocks don't count as unfinished work: the task is done when its checks pass with the answers in place.
- In the task summary, list each practice block: file, line, what it teaches.

## Insights
Before and after writing code, add short educational notes in the conversation (not in the code), like the Explanatory style:

`✶ Insight ─────────────────────────────────────`
[2–3 points about why the code is the way it is, specific to this codebase]
`─────────────────────────────────────────────────`

Keep them brief when running unattended.
