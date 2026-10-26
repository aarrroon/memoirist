---
name: record-decision
description: Record a settled choice as a new D-entry in docs/DECISIONS.md, close the matching open question, and update every doc that assumed otherwise. Use when the user answers an open question (Q-x), changes an earlier decision, or picks a library or approach.
argument-hint: "[the decision, e.g. \"Q1: new weeks auto-join the matching era\"]"
---

# Record a decision

Decision: **$ARGUMENTS**

1. **Understand it.**
   - Read `docs/DECISIONS.md`: the summary table, the D-entries and the open-questions table.
   - Work out whether this answers an open Q-x, replaces an existing D-entry, or is new.
   - If the decision is vague or its consequences are unclear, ask the user before writing. Offer a recommended default.

2. **Write the D-entry.**
   - Use the next free number, `D-{n}`.
   - Add a row to the summary table with today's date.
   - Add a section in the existing style: `## D-{n} Title`, then what was decided, then **Why:** and, if useful, **Consequences:**.
   - If it replaces an older entry, don't delete the old one. Add "Superseded by D-{n}" under its heading and strike its summary row's text.

3. **Close the question.** If it answers a Q-x, remove that row from the open-questions table.

4. **Update the other docs.**
   - Grep `docs/`, `CLAUDE.md` and `.claude/` for the old assumption, the Q-x number and the topic's key terms.
   - Update each place to match, keeping the existing wording style.
   - Point to the new D-entry where a reference helps.

5. **Report** the D-entry and a list of files changed, with a one-line summary per file. Don't commit.
