// PreToolUse hook for the spec-reviewer subagent (wired in .claude/agents/spec-reviewer.md):
// only read-only shell commands may run. Exit 0 = allow; exit 2 = block (stderr is shown to the agent).
// Unlike the reminder hooks, this guard blocks when in doubt.

let raw = '';
for await (const chunk of process.stdin) raw += chunk;

function block(reason) {
  process.stderr.write(
    `Blocked: spec-reviewer is read-only. ${reason}\n` +
      'Allowed: git status, diff, log, show, ls-files, grep, blame and rev-parse; grep, rg, ls, cat, ' +
      'head, tail, wc, sort, cut, find (without -delete or -exec), cd, pwd and echo.\n',
  );
  process.exit(2);
}

let command;
try {
  command = JSON.parse(raw)?.tool_input?.command;
} catch {
  block('The hook could not read the command.');
}
if (typeof command !== 'string' || command.trim() === '') block('The command is empty.');

// Redirecting to /dev/null or merging streams (2>&1) is harmless; drop those before checking.
const cmd = command.replace(/\d*>>?\s*\/dev\/null/g, ' ').replace(/\d*>&\d/g, ' ');

// Split into simple commands on unquoted ; & | and newlines. Refuse anything that can write or
// hide a command: an unquoted >, backticks, $( ), <( ) and here-documents.
const segments = [];
let current = '';
let quote = null;
for (let i = 0; i < cmd.length; i++) {
  const c = cmd[i];
  if (quote === "'") {
    if (c === "'") quote = null;
    current += c;
  } else if (quote === '"') {
    if (c === '\\') {
      current += c + (cmd[++i] ?? '');
      continue;
    }
    if (c === '`' || (c === '$' && cmd[i + 1] === '(')) block('Command substitution is not allowed.');
    if (c === '"') quote = null;
    current += c;
  } else if (c === "'" || c === '"') {
    quote = c;
    current += c;
  } else if (c === '\\') {
    current += c + (cmd[++i] ?? '');
  } else if (c === '`' || (c === '$' && cmd[i + 1] === '(')) {
    block('Command substitution is not allowed.');
  } else if (c === '>') {
    block('Writing to files is not allowed.');
  } else if (c === '<' && (cmd[i + 1] === '<' || cmd[i + 1] === '(')) {
    block('Here-documents and process substitution are not allowed.');
  } else if (c === ';' || c === '&' || c === '|' || c === '\n') {
    segments.push(current);
    current = '';
  } else {
    current += c;
  }
}
if (quote) block('The command has an unclosed quote.');
segments.push(current);

const GIT_READ = new Set(['status', 'diff', 'log', 'show', 'ls-files', 'grep', 'blame', 'rev-parse']);
const READ_ONLY = new Set(['grep', 'rg', 'ls', 'cat', 'head', 'tail', 'wc', 'sort', 'cut', 'find', 'cd', 'pwd', 'echo']);
const FIND_WRITES = /^-(delete|exec|execdir|ok|okdir|fprint|fprint0|fprintf|fls)$/;

for (const segment of segments) {
  const tokens = segment.trim().split(/\s+/).filter(Boolean);
  while (tokens.length > 0 && /^[A-Za-z_][A-Za-z0-9_]*=/.test(tokens[0])) tokens.shift(); // NAME=value prefixes
  if (tokens.length === 0) continue;
  const name = tokens[0].replace(/^["']|["']$/g, '').split(/[\\/]/).pop();
  const args = tokens.slice(1);
  if (name === 'git') {
    let sub;
    for (let i = 0; i < args.length; i++) {
      if (args[i] === '-C' || args[i] === '-c') {
        i++;
        continue;
      }
      if (!args[i].startsWith('-')) {
        sub = args[i];
        break;
      }
    }
    if (!GIT_READ.has(sub)) block(`"git ${sub ?? ''}" is not a read-only git command.`);
    if (args.some((a) => a.startsWith('--output'))) block('git --output writes a file.');
  } else if (name === 'find') {
    if (args.some((a) => FIND_WRITES.test(a))) block('find with -delete or -exec is not allowed.');
  } else if (name === 'sort') {
    if (args.some((a) => a === '-o' || a.startsWith('--output'))) block('sort -o writes a file.');
  } else if (!READ_ONLY.has(name)) {
    block(`"${name}" is not on the read-only list.`);
  }
}
process.exit(0);
