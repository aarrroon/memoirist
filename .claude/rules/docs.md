---
paths:
  - "docs/**"
  - "README.md"
---

# Rules for editing the docs

- **Which doc wins (D-27):** `README.md` wins on what users get and in which phase it ships; `docs/DECISIONS.md` wins on how it works. If they disagree, stop and ask the user.
- **The README changes only with the user's approval.** After it changes, run `/sync-readme`.
- **Change every mention together.** When a decision, endpoint, table or term changes, grep `README.md`, `docs/`, `CLAUDE.md` and `.claude/` and update every mention in the same change.
- **Decisions:** record them with `/record-decision`. An entry states the decision as it stands now: update it in place and set its Updated date (git keeps the old wording). IDs (D-, Q-, G-, F-, R-) are never reused or renumbered. Task numbers can be renumbered, with every reference, until their sprint starts (its first task is ticked); after that, a new task takes the next free number.
- **Specs first:** `API_CONTRACT.md` and `DATABASE_SCHEMA.md` describe the intended behaviour; change them before or with the code. Never edit committed SQL; drafts for later phases can change freely.
- **Link, don't copy:** each fact has one home (`docs/WORKFLOW.md` §4); other docs link to it.
- **House style:** plain English, short sentences, tables for lists of facts, dates as YYYY-MM-DD, and a D- or Q- reference where a rule comes from. `docs/product/` is research and isn't kept in step with every decision.
