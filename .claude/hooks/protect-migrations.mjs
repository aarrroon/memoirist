// PreToolUse hook (Edit|Write): block changes to Flyway migrations that are already committed to git.
// A committed migration may have run on someone's database; changing it breaks Flyway's checksum
// validation. New or still-uncommitted migrations can be edited freely; repeatable R__ files are allowed.
// Exit 0 = allow, exit 2 = block (stderr is shown to Claude).
import { execFileSync } from 'node:child_process';
import path from 'node:path';

const MIGRATION = /^backend\/src\/main\/resources\/db\/(migration|seed-local)\/V[^/]+\.sql$/i;

let raw = '';
for await (const chunk of process.stdin) raw += chunk;

let input;
try {
  input = JSON.parse(raw);
} catch {
  process.exit(0);
}

const filePath = input?.tool_input?.file_path;
if (typeof filePath !== 'string' || filePath === '') process.exit(0);

const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
const relative = path
  .relative(projectDir, path.resolve(projectDir, filePath))
  .split(path.sep)
  .join('/');
if (!MIGRATION.test(relative)) process.exit(0);

try {
  execFileSync('git', ['ls-files', '--error-unmatch', '--', relative], {
    cwd: projectDir,
    stdio: 'ignore',
  });
} catch {
  process.exit(0); // not tracked yet, so it can still change
}

process.stderr.write(
  `Blocked: ${relative} is a committed Flyway migration and must not change.\n` +
    'Create a new migration with the next version number instead (see the new-migration skill).\n',
);
process.exit(2);
