---
paths:
  - ".claude/**"
  - "CLAUDE.md"
  - ".mcp.json"
---

# Rules for Claude Code configuration

- **CLAUDE.md** loads in every session: keep it under 200 lines, with rules concrete enough to check ("run `./gradlew test` before saying a task is done", not "test your changes"). Instructions for part of the code go in `.claude/rules/` with `paths`; step-by-step recipes go in skills.
- **Skills** (`.claude/skills/<name>/SKILL.md`): the `description` is always in context, so say what the skill does and when to use it. Use only documented frontmatter keys; unknown keys are silently ignored. A `` !`command` `` line runs before the skill loads and aborts it if the command fails, so use commands that exit 0 (such as `sed -n`) and list them in `allowed-tools`.
- **Subagents** (`.claude/agents/`): give the smallest tool list that works, and enforce read-only with a `PreToolUse` hook in the frontmatter, not with instructions alone.
- **Hooks** (`.claude/hooks/*.mjs`, wired in `.claude/settings.json` or in frontmatter): Node ES modules with LF line endings that read the event JSON from stdin. Reminders and context hooks exit 0 on any error, so a broken hook never blocks work; guards block when in doubt. Remind at most once per session. Test a hook by piping sample JSON into it before relying on it.
- **Check the current docs** at https://code.claude.com/docs before using a Claude Code feature this repo hasn't used yet; features change quickly.
- **Keep the lists in step:** when a skill, subagent, hook, rule or prompt changes, update `docs/CURRENT_SKILLS.md` and CLAUDE.md's "Project tools" in the same change.
- **Never weaken a guard to get past it:** the migration guard, the read-only reviewer guard and the `.env` deny rules stay as they are unless the user asks.
