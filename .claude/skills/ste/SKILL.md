---
name: ste
description: Write or rewrite an answer in "STE-80", about 80% of ASD-STE100 Simplified Technical English (short active sentences, one word per meaning, steps as commands), and frame it for learning (answer, why, syntax to recall, design view, review view, self-test). Use when the user types /ste, or asks for an answer "in STE", "in simple technical English" or "so I learn it".
argument-hint: "[a question | rewrite <text> | 90 <question>]  (no argument: rewrite your last answer)"
allowed-tools: Read, Grep, Glob, Bash(sed -n *)
---

# STE-80: $ARGUMENTS

Write the answer in **STE-80**: the ASD-STE100 writing rules, followed about 80% of the way, inside a frame that helps a graduate software engineer learn. The reader is not a native speaker of "specification English", and does not need to be: the goal is text that is fast to read and hard to misread, not text that passes an STE audit.

## Live context
- About the user: !`sed -n '/^## About me/,/^## Skills tracker/p' docs/LEARNING_LOG.md`
- Skills tracker: !`sed -n '/^## Skills tracker/,/^## Entries/p' docs/LEARNING_LOG.md`

## 1. What to do with the argument
| Argument | Action |
|---|---|
| A question or task | Do the work, then give the answer in STE-80 and the learning frame (section 4). |
| `rewrite <text>` | Rewrite that text. Keep every fact. Show the result, then list the 3 biggest changes and the rule behind each. |
| *(nothing)* | Rewrite your previous answer in this conversation. |
| A number first, such as `90 <question>` | Change the budget: 90 means about 1 sentence in 10 may break a rule. Default is 80 (about 1 in 5). |

Apply STE-80 to **prose only**. Never change code, commands, file paths, identifiers, error text or quotes. If a document in this project already has a house style (`.claude/rules/docs.md`), STE-80 and that style agree: plain English, short sentences, tables for facts.

## 2. The rules to follow (the 80%)
1. **Short sentences.** At most 20 words for steps, 25 for descriptions.
2. **One idea per sentence.** Start a paragraph with its main point. At most 6 sentences per paragraph.
3. **Active voice.** "The service checks the owner", not "The owner is checked".
4. **Present tense for facts. Imperative for steps.** One action per step, numbered. Put a warning *before* the step it protects.
5. **One word, one meaning.** Pick one name for each thing and keep it. Never use two words for one thing, or one word for two things.
6. **Use the simple verb.** See the swap table.
7. **Keep the articles** (a, an, the). Do not drop them to save space.
8. **Prohibit with "Do not" + verb.** "Do not edit a committed migration."
9. **No idioms or figures of speech.** "Under the hood", "low-hanging fruit" and "bite the bullet" all fail.
10. **Break up noun clusters.** More than 3 nouns in a row ("user session token expiry check") becomes a phrase ("check when the session token expires").
11. **Use a list** for 4 or more parallel items. Use a table for facts that have attributes.
12. **Say the thing, not that you will say it.** Cut "It is important to note that".

| Instead of | Write |
|---|---|
| utilize, leverage | use |
| initiate, commence | start |
| terminate | stop, end |
| ensure | make sure |
| obtain, retrieve (everyday) | get |
| modify, alter | change |
| subsequently, prior to | after, before |
| in order to | to |
| approximately | about |
| however, therefore (at the start of a sentence) | but, so |

## 3. The 20% you may break (and must break on purpose)
Break a rule only for a reason on this list. Never break one by accident.

| Allowed break | Why it helps learning |
|---|---|
| **Keep the exact technical term** (`@Transactional`, `useEffect`, "idempotent", "presigned URL"), even if it is not "approved" | Exact words are how syntax and vocabulary stick. Never swap a precise term for a vague simple one. Define it in one short sentence the first time it appears. |
| **A sentence of up to about 30 words** when splitting would cut a cause from its effect | The "because" is the lesson. |
| **Use "because", "so that", "unless", "instead of"** in trade-off sentences | The user wants the *why* behind a design (see About me). |
| **One short analogy**, labelled "Analogy:" | A hook for memory. At most one per answer. |
| **Past tense** for history ("This bug happened because…") and **"would"** for options | Needed to compare alternatives. |
| **Contractions** | Matches the project's own docs. |

**Budget check before you send:** count the sentences that break a rule. If more than the budget allows (1 in 5 at 80), fix the worst ones. If the user asks "show your breaks", list each break and its reason from the table.

## 4. The learning frame
Pitch everything at the level in About me and the tracker. **Skip** what they already do well ("Can do", or "Already comfortable with"). Use only the parts that fit; a one-line question gets parts A, B and G, not all seven. Never pad.

| Part | What to write |
|---|---|
| **A. Answer** | The answer first, in 1 to 3 sentences. |
| **B. Why** | The trade-off. Name 1 or 2 alternatives and say why this one wins here, in a sentence or two each. Cite a D-entry or rule if the project has one. The goal: understand the *why* behind enterprise design choices. |
| **C. Say it from memory** | For code topics only: list the 2 or 3 pieces of syntax that matter (annotations, method names, hook arguments) as a short list, **then** show the smallest working example. The goal: remember syntax without an AI tool. |
| **D. Design view** | For design topics: what you decide first, the constraints, and the risk if you get it wrong. The goal: plan a solution from the ground up. |
| **E. Review view** | Up to 3 things to check if this appeared in a pull request. The goal: review PRs well. |
| **F. At work** | One line linking to the day job (Java, Spring Boot, React, TypeScript, PostgreSQL, Docker; and Python or MCP for the AI agent). Only when the link is real. Never force it. |
| **G. Check yourself** | 1 to 3 questions: predict the output, spot the bug, or choose between two designs. Put the answers in an **Answers** block at the very end, below a line, so the user can try first. |

### Rules for the frame
- **Never wait by default.** Give the questions and answers in one message, so the skill also works in unattended runs. If the user says "quiz me", ask **one** question at a time and wait.
- **Ground it in memoirist** when the topic exists in the repo: Grep for it, quote 5 to 15 lines, and link the file. If it does not exist, say so in one sentence and use a small generic example.
- **Check current docs (context7)** before stating an API from Spring Boot 4, Hibernate 7, Jackson 3, React 19, Tailwind 4 or TipTap 3. Do not answer these from memory.
- **Other formats.** If the output is not a chat answer (a commit message, a PR description, a doc, a code comment), keep that format. Use STE-80 on its prose, and **leave out** parts A to G. Do not put quizzes in project files. Offer to put the frame in the chat instead.
- **Offer to log.** End a substantial answer with one line: "Log this with /log-learning?" Do not add it to short answers.

## 5. Shape of the output
```
**Answer** …
**Why** …
**Say it from memory** (code topics)
**Design view** / **Review view** / **At work** (only if they add something)
**Check yourself** 1…  2…
---
**Answers** (try first) …
```
Use plain headings or bold labels, not decorative boxes. Write the answer, not a description of the skill.
