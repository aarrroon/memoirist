---
name: review-with-me
description: Teach the user to review code — walk them through a change file by file, then ask them review questions about it and grade their answers. Use after a sprint task, before committing, or when the user says "review this with me" or "quiz me on this change".
argument-hint: "[scope: task id, file, or commit range — omit for the uncommitted changes]"
disable-model-invocation: true
allowed-tools: Read, Grep, Glob, Bash(git status *), Bash(git diff *), Bash(git log *), Bash(git show *), Bash(sed -n *)
---

# Review this change with me

Scope: **$ARGUMENTS**. If empty, review the uncommitted changes (`git status --short`, `git diff HEAD`, and new files in full).

## Live context
- Changed files: !`git status --short`
- About the user: !`sed -n '/^## About me/,/^## Skills tracker/p' docs/LEARNING_LOG.md`

This is a **lesson, not a fix-up**: don't edit code while it runs. Pitch the explanations at the level in "About me" and the skills tracker.

## 1. The tour (keep it short)
- One paragraph: what the change does and why, in plain words.
- Then go file by file, **in the order a reviewer should read them** (contract or migration → domain and service → controller → tests), not alphabetically. For each file: its job in two sentences, and one thing worth noticing (a pattern, a trade-off, a rule from `.claude/rules/` it follows).
- Point out anything the user might not have seen before, by name, in one sentence each.

## 2. The quiz
Ask **3 to 5 questions**, one at a time, with AskUserQuestion where the answers fit options and as an open question otherwise. Wait for each answer before asking the next.
- Mix the kinds:
  - **"What happens if…"**: an edge case (the entry is sealed, the user doesn't own it, the date is in another timezone, a request is replayed)
  - **"Why…"**: a design choice (why this is in the service, not the controller; why `404` not `403`; why a record)
  - **"Spot it"**: show a short snippet with one deliberate mistake (based on the real code) and ask what's wrong
  - **"What's missing"**: a test, a doc update or a check the change should have
- At least one question should be about a project rule (sealed entries, ownership, UTC, object keys) and one about a general engineering idea they can use at work.
- Base every question on code in the scope; quote the lines you're asking about.

## 3. Grade and teach
After each answer:
- Say plainly whether it's **right**, **partly right** or **not quite**, and why, in two or three sentences.
- If it's not right, give the answer and the reasoning, then one follow-up hint they can apply next time.

At the end:
- A score (for example 3½ / 5) and **one strength** and **one thing to practise**, both specific.
- If the review surfaced a real problem in the code, list it under **Worth fixing** for the user to decide; don't fix it here.

## 4. Log it
Offer to record the session: *"Log this with /log-learning?"* If yes, run the `log-learning` skill with the scope, the score, the strength, the gap and the topics the questions covered.
