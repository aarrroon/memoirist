// Stop hook: if the working tree changes API or schema code but no spec doc, ask Claude
// to run /sync-docs before finishing. Fires at most once per stop (stop_hook_active guard).
// Prints {"decision":"block","reason":...} to keep Claude going; prints nothing to allow the stop.
import { execFileSync } from 'node:child_process';

let raw = '';
for await (const chunk of process.stdin) raw += chunk;

let input = {};
try {
  input = JSON.parse(raw);
} catch {
  process.exit(0);
}
if (input.stop_hook_active) process.exit(0);

const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();

let changed;
try {
  changed = execFileSync('git', ['status', '--porcelain', '--untracked-files=all'], {
    cwd: projectDir,
    encoding: 'utf8',
  })
    .split('\n')
    .filter(Boolean)
    .map((line) => line.slice(3).replace(/^.* -> /, '').replace(/^"|"$/g, ''));
} catch {
  process.exit(0); // not a git repo or git missing: never block
}

const CODE = [
  /^backend\/src\/main\/resources\/db\/(migration|seed-local)\/.+\.sql$/,
  /^backend\/src\/main\/java\/.+(Controller|Request|Response|Dto)\.java$/,
  /^backend\/src\/main\/java\/.+\/dto\/.+\.java$/,
  /^backend\/src\/main\/java\/.+\/[A-Z][A-Za-z]*Entity\.java$/,
];
const DOCS = /^docs\/(API_CONTRACT|DATABASE_SCHEMA|DECISIONS)\.md$/;

const codeChanged = changed.filter((f) => CODE.some((re) => re.test(f)));
if (codeChanged.length === 0 || changed.some((f) => DOCS.test(f))) process.exit(0);

const list = codeChanged.slice(0, 8).join(', ') + (codeChanged.length > 8 ? ', …' : '');
process.stdout.write(
  JSON.stringify({
    decision: 'block',
    reason:
      `API or schema code changed (${list}) but docs/API_CONTRACT.md, docs/DATABASE_SCHEMA.md ` +
      'and docs/DECISIONS.md are untouched. Run the sync-docs skill and update the docs, or tell ' +
      'the user briefly why no doc change is needed. Then finish.',
  }),
);
