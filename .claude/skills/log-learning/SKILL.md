---
name: log-learning
description: Add an entry to docs/LEARNING_LOG.md from this session — a hands-on piece the user wrote, a /review-with-me quiz, a /teach lesson, or notes the user gives — and update its skills tracker. Use when the user says "log this", "add to my learning log", or accepts the offer at the end of /review-with-me, /teach or /implement-task.
argument-hint: "[optional notes in your own words, e.g. \"finally understood why lazy loading breaks outside a transaction\"]"
allowed-tools: Read, Edit, Bash(date *), Bash(sed -n *), Bash(git log *)
---

# Log what I learned

User's own notes: **$ARGUMENTS**

## Live context
- Today: !`date +%F`
- Last commit: !`git log -1 --format="%h %s"`
- Skills tracker: !`sed -n '/^## Skills tracker/,/^## Entries/p' docs/LEARNING_LOG.md`
- Latest entry: !`sed -n '/^### [0-9]/,/^### [0-9]/p' docs/LEARNING_LOG.md`

## 1. Gather
Collect from this session (don't invent anything that didn't happen):
- what was worked on: the task ID, the review scope or the lesson topic
- what the user **did** themselves: a `TODO(human)` piece, quiz answers, an exercise
- what they learned, what they got wrong or found hard, and the feedback they were given
- the user's own notes above, quoted in their words

If the session has nothing to log and no notes were given, ask what they'd like to record. If the user's notes say something different from the session, use their notes.

## 2. Write the entry
Insert it directly **below** the `<!-- /log-learning adds new entries … -->` line in `docs/LEARNING_LOG.md`, so the newest is first:

```markdown
### YYYY-MM-DD · <short title>
**Context:** task 3.4 / review of … / lesson on … (commit abc1234 if there is one)
**What I did:** <what the user wrote or answered, one or two lines>
**Learned:**
- <concrete point, with the pattern or syntax named>
- <…>
**Got wrong or found hard:** <honest, specific; "—" if nothing>
**Next time:** <one thing to practise or watch for>
**Skills:** <comma-separated tracker names>
```

Keep it to about 10 lines. Write in the user's voice ("I learned…"), plainly.

## 3. Update the skills tracker
For each skill in **Skills:**
- If it's new, add a row to the tracker: name it the way a job description would (for example "JPA lazy loading", "Bean validation", "Writing integration tests with Testcontainers").
- Set the level: **Seen** (explained or read), **Tried** (wrote it with help or got it partly right), **Can do** (wrote it alone, or answered correctly in review). Only ever raise a level from evidence in this session; never lower one without asking.
- Set "Last practised" to today and "Entry" to the entry's title.

## 4. Confirm
Show the entry and the tracker rows you changed. Don't commit; the log is committed with the task or on its own when the user asks.
