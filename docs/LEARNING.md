# Learning while building memoirist

> How this project is set up to help you grow as an engineer, not just ship features. Your notes go in [LEARNING_LOG.md](LEARNING_LOG.md).

## How it fits together

| Piece | What it does | When |
|---|---|---|
| **Learning output style** | Claude explains its choices in short `Insight` blocks and leaves small `TODO(human)` pieces for you to write | Every session, automatically (section 1) |
| **Hands-on offer** in `/implement-task` | Before each task, Claude offers one bigger piece for you to write: something you'll use at work, or a pattern you haven't seen. You can say no | Start of every coding task |
| `/review-with-me` | Walks you through the change, then asks you review questions and grades your answers | After a task, before committing |
| `/teach [topic]` | A short lesson on something from this codebase, with a tiny exercise | Whenever something confuses you |
| `/log-learning` | Adds an entry to your learning log from the session (or from your own notes) | End of a task, a review or a lesson |
| **[Build journal](build-journal/README.md)** | A step-by-step record of how each task was built: decisions and alternatives, tests, what went wrong, concepts, check-yourself questions. One overview per sprint | Written during overnight runs (or when you ask); study it afterwards |

## 1. Output styles: what the values mean

The style lives in `.claude/settings.local.json` as `"outputStyle": "Learning"`. That file is yours only (gitignored), and JSON can't hold comments, so the options are explained here.

| Value | What Claude does | Use it when |
|---|---|---|
| *(no value)* or `"default"` | Standard Claude Code: does the task, no extra teaching | You just want to ship |
| `"Concise"` | Leads with the result; no narration or recap | Answers feel too long |
| `"Proactive"` | Starts work straight away and makes reasonable assumptions instead of asking | Routine work you'll correct if needed |
| `"Explanatory"` | Does the task and adds short `★ Insight` blocks explaining *why* | You want the reasoning but not exercises today |
| **`"Learning"`** *(current)* | Insights **plus** `TODO(human)` pieces: at real design decisions (error handling, data structures, business logic) it leaves a few lines for you, explains what to write and waits | Hands-on practice while the task still gets done |
| `"learn-later"` *(this project's own style)* | **Never waits.** Writes complete, working code and marks one or two pieces per task as practice blocks: a `TODO(human) PRACTICE` question and hint, with the answer in a marked block (section 1b) | Overnight runs, or busy days when you want to practise afterwards |

**Notes:**
- Values are **case-sensitive** in the settings file: `"Learning"` works, `"learning"` silently falls back to the default.
- **To switch for one session**, type `/output-style explanatory` (the command ignores case). It saves to the same settings file, so switch back when you're done.
- The style affects your main conversation only. Subagents such as `spec-reviewer` keep their own instructions.
- Learning and Explanatory make answers longer, so they use more of your usage limit. On busy days, switch to `Explanatory` or the default.

### How `TODO(human)` works
1. Claude writes the routine parts, marks a spot with a `TODO(human)` comment, and sends a **Learn by Doing** note: what's already built, what to write, and what to weigh up.
2. You write the code at that spot (in your editor), then tell Claude "done".
3. Claude reviews it in an `Insight`, fixes anything it must with your agreement, and carries on.

If you decline the hands-on offer at the start of a task, Claude keeps the `TODO(human)` pieces to an occasional small one (rule in CLAUDE.md).

### 1b. Practice blocks (learn-later)
With `/output-style learn-later` (defined in `.claude/output-styles/learn-later.md`), Claude doesn't wait. It writes the real code and wraps the parts worth practising like this:

```java
// TODO(human) PRACTICE: Reject a memory date after today in the user's timezone.
// Hint: "today" comes from the injected Clock in the user's zone, not the server's (D-07, D-09).
// ┌── ANSWER ▼ (delete or fold this block, write your own, then compare) ──
LocalDate today = LocalDate.now(clock.withZone(zone));
if (entryDate.isAfter(today)) {
    throw new ValidationException("entryDate", "must not be after today");
}
// └── END ANSWER ▲ ──
```

**How to practise one:**
1. Find them: `git grep -n "TODO(human) PRACTICE"`, or the list in each task summary and PR description.
2. Fold or delete the lines between `┌── ANSWER ▼` and `└── END ANSWER ▲` (or cover them in your editor), and write your own version.
3. Run the tests, then compare with the answer (`git diff` shows the difference if you deleted it).
4. Restore the answer if yours was worse (`git checkout -- <file>`), or keep yours if the tests pass and it's better. Then `/log-learning`.

The code always works with the answers in place, so nothing is blocked while you haven't practised yet. Once you've done a block, you can delete its marker lines and keep the code.

## 2. Choosing what to practise
The hands-on offer picks from the task in front of you, guided by the **About me** section of [LEARNING_LOG.md](LEARNING_LOG.md). Fill that in once: your job, the languages and tools you use there, and what you want to get better at. Claude reads it to suggest pieces that transfer to your work, and reads past entries to avoid repeating topics and to revisit gaps.

## 3. A typical task
1. `/implement-task 3.4`: Claude reads the task and offers you a piece to write (or "not this time").
2. The plan names that piece; Claude builds the rest and leaves `TODO(human)` for yours.
3. You write it; Claude reviews it.
4. `/review-with-me`: you answer a few questions about the whole change.
5. `/log-learning`: one entry goes into your log. Commit as usual.

## 4. Getting the most out of it
- **Do the backend sprints (2–3) hands-on.** That's where the engineering ideas are densest: transactions, queries, testing, security.
- **Ask "why" freely.** "Why a record and not a class?" is a good question at any point.
- **Review before you commit.** The review questions are where reading someone else's code turns into a skill.
- **Look back monthly.** Ask: *"Read my learning log and tell me my strongest areas, my gaps, and what to practise next."*
