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
| 2. Backend foundation | [Overview](sprint-2/README.md) | [2.1 Spring Boot project](sprint-2/2.1-spring-boot-project.md), [2.2 Configuration and profiles](sprint-2/2.2-configuration-and-profiles.md), [2.3 V1 migration and seed](sprint-2/2.3-v1-migration-and-seed.md), [2.4 Entities and repositories](sprint-2/2.4-entities-and-repositories.md), [2.5 Current user resolution](sprint-2/2.5-current-user-resolution.md), [2.6 Global error handling](sprint-2/2.6-global-error-handling.md), [2.7 CI: backend](sprint-2/2.7-ci-backend.md) |
| 3. Phase 1 API | [Overview](sprint-3/README.md) | [3.1 Storage service](sprint-3/3.1-storage-service.md), [3.2 /users/me](sprint-3/3.2-users-me.md), [3.3 Eras CRUD](sprint-3/3.3-eras-crud.md), [3.4 Templates CRUD](sprint-3/3.4-templates-crud.md), [3.5 Entries](sprint-3/3.5-entries.md), [3.6 Media](sprint-3/3.6-media.md), [3.7 Weekly logs and feed](sprint-3/3.7-weekly-logs-and-feed.md), [3.8 Tags](sprint-3/3.8-tags.md), [3.9 Entry list for the timeline](sprint-3/3.9-entry-list-timeline.md), [3.10 Opened Loot Drops](sprint-3/3.10-opened-loot-drops.md), [3.11 Recently deleted](sprint-3/3.11-recently-deleted.md), [3.12 On this day](sprint-3/3.12-on-this-day.md), [3.13 Loot Drop replies and the birthday](sprint-3/3.13-replies-and-birthday.md), [3.14 Soundtracks and Spotify search](sprint-3/3.14-soundtracks-and-spotify.md), [3.15 Era covers](sprint-3/3.15-era-covers.md), [3.16 Local sample content](sprint-3/3.16-local-sample-content.md), [3.17 API review](sprint-3/3.17-api-review.md) |

## Writing an entry
Every task entry follows [TEMPLATE.md](TEMPLATE.md). Rules for whoever writes it, usually the agent building the task:
- **Write as you go**, not from memory at the end. Note each decision, error and fix when it happens.
- **Record what really happened**, including dead ends, failed attempts and test failures. The mistakes are the most useful part to study.
- **Explain at the reader's level:** a full-stack engineer who knows Java, Spring Boot, React and PostgreSQL, and wants the *why* behind each choice and better recall of the syntax (About me in [LEARNING_LOG.md](../LEARNING_LOG.md)).
- **Link, don't copy:** link to decisions (D-xx), docs sections and code (`file:line`). Quote code only when the snippet is the point.
- Commit the entry in the same commit as the task, so the journal and the code always match.
