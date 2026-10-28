---
name: record-decision
description: Record a settled choice in docs/DECISIONS.md — a new D-entry, or an update to the entry it changes — close the matching open question (Q-x) or flow gap (G-xx), and update every doc that assumed otherwise, including the README with the user's approval. Use when the user answers an open question, changes an earlier decision, or picks a library or approach.
argument-hint: "[the decision, e.g. \"Q14: show the Help me think button after an 8-second pause\"]"
allowed-tools: Read, Grep, Glob, Bash(date *), Bash(sed -n *)
---

# Record a decision

Decision: **$ARGUMENTS**

## Live context
- Today: !`date +%F`
- Decisions so far: !`sed -n '/^| D-[0-9]/p' docs/DECISIONS.md`

1. **Understand it.**
   - Read `docs/DECISIONS.md`: the header, the summary table, the D-entries and the open-questions table. Read the gaps table at the end of `docs/USER_FLOWS.md`.
   - Work out whether this answers an open Q-x or G-xx, changes an existing D-entry, or is a new topic.
   - Check the README (D-27): does this change what users are promised, or in which phase? If so, show the user the README edit you'd make and get their approval first.
   - If the decision is vague or its consequences are unclear, ask the user before writing: a short list of questions (AskUserQuestion), each option with its trade-offs and a recommended default.

2. **Write it.**
   - **It changes an existing topic:** update that D-entry in place so it states the decision as it stands now, and set the summary row's **Updated** date to today. Don't add "amended" or "superseded" notes; git keeps the old wording.
   - **It's a new topic:** use the next free number, `D-{n}`. Add a summary row with today's date, and a section in the existing style: `## D-{n} Title`, the decision, then **Why:** and, if useful, **Consequences:**.
   - **It drops an earlier decision:** keep the entry as one line saying it was dropped, when, and why, so links still work.

3. **Close the question or gap.**
   - Remove an answered Q-x row from the open-questions table. Numbers are never reused: a new question gets one more than the highest Q mentioned anywhere in the file.
   - For a G-xx, record it as settled in the USER_FLOWS gaps section, naming the D-entry.
   - Update SPRINT_PLAN's "Decide first" lines and any "Read:" line that names the Q-x.
   - If it settles a recommendation in `docs/product/FEATURE_REVIEW.md`, set that R-xx's status to **Accepted (D-xx)** or **Rejected**.
   - If the decision raises follow-up questions, add them as new Q-x rows with a "Needed by" sprint and a default.

4. **Update the other docs.**
   - Grep `README.md`, `docs/`, `CLAUDE.md` and `.claude/` for the old assumption, the Q-x or G-xx number and the topic's key terms.
   - Update each place to match, keeping the existing wording style, and point to the D-entry where a reference helps. Change the README only with the user's approval.

5. **Verify.** Grep again for the Q-x number and the old wording. Only historical mentions may remain (for example "answers Q1").

6. **Report** the D-entry and the files changed, with a one-line summary per file and README changes listed separately. Don't commit.
