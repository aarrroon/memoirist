// SessionStart hook: print a short status so every new session starts oriented.
// Claude Code adds a SessionStart hook's plain-text output to Claude's context.
// It never fails a session: any problem just means a shorter status.
import { execFileSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import path from 'node:path';

let input = {};
try {
  let raw = '';
  for await (const chunk of process.stdin) raw += chunk;
  input = JSON.parse(raw || '{}');
} catch {
  input = {};
}

const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
const read = (rel) => {
  try {
    return readFileSync(path.join(projectDir, rel), 'utf8');
  } catch {
    return '';
  }
};
const mtime = (rel) => {
  try {
    return statSync(path.join(projectDir, rel)).mtimeMs;
  } catch {
    return 0;
  }
};

const lines = [];

try {
  const plan = read('docs/SPRINT_PLAN.md');
  const next = plan.match(/^- \[ \] \*\*(\d+\.\d+) ([^*\n]+)\*\*/m);
  if (next) {
    lines.push(`Next task: ${next[1]} ${next[2].trim()}. Start it with /implement-task ${next[1]}.`);

    // Open questions named on the "Decide first" line of that task's sprint.
    const start = plan.lastIndexOf('\n### ', next.index);
    const end = plan.indexOf('\n### ', next.index);
    const sprint = plan.slice(start + 1, end === -1 ? undefined : end);
    const decide = sprint.match(/\*\*Decide first:\*\*([^\n]*)/)?.[1] ?? '';
    const named = new Set();
    for (const [, from, to] of decide.matchAll(/Q(\d+)[–-]Q(\d+)/g)) {
      for (let n = Number(from); n <= Number(to); n++) named.add(`Q${n}`);
    }
    for (const [q] of decide.matchAll(/Q\d+/g)) named.add(q);
    const open = new Set([...read('docs/DECISIONS.md').matchAll(/^\| (Q\d+) \|/gm)].map((m) => m[1]));
    const blocking = [...named].filter((q) => open.has(q));
    if (blocking.length > 0) {
      const sprintName = sprint.split('\n')[0].replace(/^#+\s*/, '');
      lines.push(`Still open for ${sprintName}: ${blocking.join(', ')}. Ask the user to decide them (docs/DECISIONS.md) before building.`);
    }

    const focus = read('CLAUDE.md').match(/Next task:\s*(\d+\.\d+)/)?.[1];
    if (focus && focus !== next[1]) {
      lines.push(`CLAUDE.md's Current focus says ${focus}, but the first unticked task is ${next[1]}.`);
    }
  }
} catch {
  // Print what we have.
}

try {
  const status = execFileSync('git', ['status', '--porcelain'], { cwd: projectDir, encoding: 'utf8' })
    .split('\n')
    .filter(Boolean);
  if (status.length > 0) {
    lines.push(`${status.length} uncommitted file(s). Check \`git status\` before starting new work.`);
  }
  const PRODUCT_DOCS = ['docs/PRD.md', 'docs/SPRINT_PLAN.md', 'docs/DECISIONS.md', 'docs/USER_FLOWS.md'];
  const readmeChanged = status.some((line) => line.slice(3) === 'README.md');
  if (readmeChanged && mtime('README.md') > Math.max(...PRODUCT_DOCS.map(mtime))) {
    lines.push('README.md changed after the product docs. Suggest /sync-readme before relying on PRD or SPRINT_PLAN.');
  }
} catch {
  // Not a git repo, or git is missing.
}

if (lines.length > 0) {
  process.stdout.write(['memoirist session status:', ...lines.map((line) => `- ${line}`)].join('\n') + '\n');
}
