# Build journal

> A step-by-step record of **how** memoirist was built: what each task needed, what was planned and why, the alternatives that were rejected, how it was tested, what went wrong and how it was fixed. Written as the work happens, for studying the process afterwards. The code shows *what* was built; this shows *how and why*.

## How to study it
1. **Start with a sprint overview** (`sprint-N/README.md`): what the sprint set out to do, the architecture at the end of it, and how the tasks fit together.
2. **Then read the tasks in order** (`sprint-N/N.M-short-name.md`). Each one walks through the decisions in the order they were made.
3. **Open the code beside it.** Every entry links to the files and the commit, and the sprint's PR shows the whole diff.
4. **Try the practice blocks** it lists (`TODO(human) PRACTICE`, see [LEARNING.md §1b](../LEARNING.md)), then answer its *Check yourself* questions without looking.
5. **Log what you learned** with `/log-learning`, naming the journal entry.

## What's here

| Sprint | Overview | Tasks |
|---|---|---|
| 1. Setup and local infrastructure | [Overview](sprint-1/README.md) | [1.2 Machine prerequisites](sprint-1/1.2-machine-prerequisites.md), [1.3 Docker Compose: Postgres and SeaweedFS](sprint-1/1.3-docker-compose.md) (1.1 came before the journal) |
| 2. Backend foundation | | |
| 3. Phase 1 API | | |

## Writing an entry
Every task entry follows [TEMPLATE.md](TEMPLATE.md). Rules for whoever writes it, usually the agent building the task:
- **Write as you go**, not from memory at the end. Note each decision, error and fix when it happens.
- **Record what really happened**, including dead ends, failed attempts and test failures. The mistakes are the most useful part to study.
- **Explain at the reader's level:** a full-stack engineer who knows Java, Spring Boot, React and PostgreSQL, and wants the *why* behind each choice and better recall of the syntax (About me in [LEARNING_LOG.md](../LEARNING_LOG.md)).
- **Link, don't copy:** link to decisions (D-xx), docs sections and code (`file:line`). Quote code only when the snippet is the point.
- Commit the entry in the same commit as the task, so the journal and the code always match.
