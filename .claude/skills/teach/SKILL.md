---
name: teach
description: A short, hands-on lesson on one engineering topic, grounded in memoirist's own code or specs — explanation, a real example from the repo, a tiny exercise, then feedback. Use when the user says "teach me…", "explain … properly", or is confused by a pattern, library or piece of syntax.
argument-hint: "[topic, e.g. \"JPA lazy loading\", \"presigned URLs\", \"React useEffect\"]"
disable-model-invocation: true
allowed-tools: Read, Grep, Glob, Bash(sed -n *)
---

# Teach me: $ARGUMENTS

## Live context
- About the user: !`sed -n '/^## About me/,/^## Skills tracker/p' docs/LEARNING_LOG.md`
- Skills tracker: !`sed -n '/^## Skills tracker/,/^## Entries/p' docs/LEARNING_LOG.md`

If no topic was given, suggest three that the current task or the next sprint will need (from `docs/SPRINT_PLAN.md`) and the tracker hasn't covered, and ask which one.

Keep the whole lesson to about **15 minutes**. Pitch it at the level in "About me" and the tracker: skip what they already know.

## 1. The idea (3–5 short paragraphs)
- What it is, in plain words, and the problem it solves.
- **How it works underneath**, at the level a working engineer needs (for example what Hibernate actually does when you touch a lazy collection).
- The common mistake, and what goes wrong when people make it.
- If the topic is about a library or framework version, check current docs with context7 first: this project uses Spring Boot 4, Hibernate 7, React 19, Tailwind 4 and TipTap 3.

## 2. In this project
- Show where it appears in memoirist: real code if it exists (Grep for it), otherwise the spec that will drive it (`docs/TECH_STACK.md`, `docs/API_CONTRACT.md`, `.claude/rules/`). Quote 5–20 lines and explain them.
- Say why the project chose this approach over the alternative, citing the D-entry or rule if there is one.

## 3. A tiny exercise
- One exercise that takes 5–10 minutes, related to this codebase: predict what some code does, write a few lines, fix a deliberate bug, or choose between two designs and justify it.
- Ask it, then **wait** for the answer. Don't write it to a project file unless the user asks; an exercise is answered in the chat.

## 4. Feedback and next step
- Grade the answer (right / partly / not quite) and explain.
- Give one "if you remember one thing" sentence.
- Suggest one follow-up topic.
- Offer: *"Log this with /log-learning?"* If yes, run the `log-learning` skill with the topic, how the exercise went and the follow-up.
