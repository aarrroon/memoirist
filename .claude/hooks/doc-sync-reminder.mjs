// Stop hook: before Claude finishes a turn, check that the docs kept up with the work.
//   1. Schema code (migrations, @Entity classes) changed after docs/DATABASE_SCHEMA.md, or API code
//      (controllers, requests, responses, mappers, dto/) changed after docs/API_CONTRACT.md -> /sync-docs.
//   2. README.md changed after the product docs -> /sync-readme (D-27).
//   3. CLAUDE.md's "Next task" doesn't match the first unticked task in docs/SPRINT_PLAN.md.
//   4. API code changed after frontend/src/api/schema.d.ts was generated -> npm run gen:api.
// Each reminder is sent at most once per session for the same files (state in the OS temp dir).
// Prints {"decision":"block","reason":...} to keep Claude going; prints nothing to allow the stop.
// Any error allows the stop: this hook must never get in the way.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

try {
  let raw = '';
  for await (const chunk of process.stdin) raw += chunk;
  const input = JSON.parse(raw);
  if (input.stop_hook_active) process.exit(0);

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
  const newest = (files) => Math.max(0, ...files.map(mtime));
  const list = (files) => files.slice(0, 6).join(', ') + (files.length > 6 ? ', …' : '');

  const changed = execFileSync('git', ['status', '--porcelain', '--untracked-files=all'], {
    cwd: projectDir,
    encoding: 'utf8',
  })
    .split('\n')
    .filter(Boolean)
    .map((line) => line.slice(3).replace(/^.* -> /, '').replace(/^"|"$/g, ''));
  const isChanged = (rel) => changed.includes(rel);

  const reminders = [];

  // 1. Code -> its own spec doc. A doc counts as synced only if it changed after the code did.
  const JAVA = /^backend\/src\/main\/java\/.+\.java$/;
  const schemaCode = changed.filter(
    (f) =>
      /^backend\/src\/main\/resources\/db\/(migration|seed-local)\/.+\.sql$/.test(f) ||
      (JAVA.test(f) && /@Entity\b/.test(read(f))),
  );
  const apiCode = changed.filter(
    (f) => JAVA.test(f) && (/(Controller|Request|Response|Mapper|Dto)\.java$/.test(f) || /\/dto\//.test(f)),
  );
  for (const [files, doc] of [
    [schemaCode, 'docs/DATABASE_SCHEMA.md'],
    [apiCode, 'docs/API_CONTRACT.md'],
  ]) {
    if (files.length > 0 && (!isChanged(doc) || mtime(doc) < newest(files))) {
      reminders.push({
        key: `${doc}|${[...files].sort().join(',')}`,
        text: `${list(files)} changed after ${doc} was last updated. Run the sync-docs skill and update ${doc}, or tell the user briefly why no change is needed.`,
      });
    }
  }

  // 2. README -> product docs (D-27).
  const PRODUCT_DOCS = ['docs/PRD.md', 'docs/SPRINT_PLAN.md', 'docs/DECISIONS.md', 'docs/USER_FLOWS.md'];
  if (isChanged('README.md') && mtime('README.md') > newest(PRODUCT_DOCS)) {
    reminders.push({
      key: 'README.md',
      text: 'README.md changed after the product docs (PRD, SPRINT_PLAN, DECISIONS, USER_FLOWS). Run the sync-readme skill, or tell the user briefly why the docs need no change.',
    });
  }

  // 3. CLAUDE.md "Current focus" -> the first unticked task.
  const firstOpen = read('docs/SPRINT_PLAN.md').match(/^- \[ \] \*\*(\d+\.\d+)\b/m)?.[1];
  const focus = read('CLAUDE.md').match(/Next task:\s*(\d+\.\d+)/)?.[1];
  if (firstOpen && focus && firstOpen !== focus) {
    reminders.push({
      key: `focus|${focus}|${firstOpen}`,
      text: `CLAUDE.md says the next task is ${focus}, but the first unticked task in docs/SPRINT_PLAN.md is ${firstOpen}. Update the "Current focus" line in CLAUDE.md.`,
    });
  }

  // 4. Generated frontend API types (from Sprint 4).
  const types = 'frontend/src/api/schema.d.ts';
  if (apiCode.length > 0 && existsSync(path.join(projectDir, types)) && mtime(types) < newest(apiCode)) {
    reminders.push({
      key: `${types}|${[...apiCode].sort().join(',')}`,
      text: `Backend API code changed after ${types} was generated. With the backend running, run \`cd frontend && npm run gen:api\` and fix any type errors.`,
    });
  }

  if (reminders.length === 0) process.exit(0);

  // Remind once per session for the same set of files.
  const session = String(input.session_id || 'unknown').replace(/[^A-Za-z0-9_-]/g, '');
  const stateFile = path.join(tmpdir(), `memoirist-doc-sync-${session}.json`);
  let sent = [];
  try {
    sent = JSON.parse(readFileSync(stateFile, 'utf8'));
  } catch {
    sent = [];
  }
  if (!Array.isArray(sent)) sent = [];
  const hash = (text) => createHash('sha1').update(text).digest('hex').slice(0, 16);
  const fresh = reminders.filter((r) => !sent.includes(hash(r.key)));
  if (fresh.length === 0) process.exit(0);
  try {
    writeFileSync(stateFile, JSON.stringify([...sent, ...fresh.map((r) => hash(r.key))]));
  } catch {
    // Can't remember this one; still remind now.
  }

  process.stdout.write(
    JSON.stringify({
      decision: 'block',
      reason: `Before finishing: ${fresh.map((r, i) => `(${i + 1}) ${r.text}`).join(' ')} Then finish.`,
    }),
  );
} catch {
  process.exit(0);
}
