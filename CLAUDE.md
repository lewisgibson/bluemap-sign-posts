# BlueMapSignPosts

Instructions for anyone, person or agent, working in this repository. User instructions take precedence. Claude Code loads this file and `.claude/rules/` by itself; other agents start from `AGENTS.md`, which points here.

**If `AGENTS.local.md` exists, read it now and follow it.** It is gitignored and holds one machine's specifics (where a test server lives, what not to touch). Local notes go there, never in a tracked file.

## What this is

A Paper plugin that allows players to add BlueMap markers using signs and banners: a sign with `[map]` on its first line, or a banner renamed in an anvil, becomes a marker on the BlueMap web map at that block, and the marker goes when the block does. It is one jar that loads from `plugins/` beside BlueMap on Paper, Purpur and Folia. [README.md](README.md) is the user-facing page, the detail is in [docs/](docs/), and [docs/architecture.md](docs/architecture.md) explains how the code fits together.

The code is split three ways. `core` is the pure domain (sign parsing, the signpost model, registry and service, the store interface, limits and the create policy, durations, marker content, settings and messages) with no `org.bukkit.*` or `de.bluecolored.*` imports. `storage` is the JDBC store (SQLite by default, or PostgreSQL or MySQL through a HikariCP pool) with no server imports. `bukkit` is the thin adapter that binds both to the server and to BlueMapAPI.

## Build and test

| Command                     | Description                                                            |
| --------------------------- | ---------------------------------------------------------------------- |
| `./gradlew build`           | The full gate: compile, Spotless, unit and integration tests, coverage |
| `./gradlew test`            | Unit tests only                                                        |
| `./gradlew integrationTest` | MockBukkit tests, with a fake BlueMapAPI registered                    |
| `./gradlew serverTest`      | Real Paper, BlueMap and databases in Docker, observed over RCON        |
| `./gradlew spotlessApply`   | Format Java (google-java-format, AOSP style, 4 spaces)                 |
| `make test-scripts`         | The release scripts' tests, at 100% line and branch coverage           |
| `make lint`                 | Prettier, shellcheck, actionlint, gitleaks and the changelog format    |
| `make hooks`                | Install the pre-commit gate (run once after cloning)                   |

The Java 25 toolchain is downloaded automatically. `hooks/pre-commit` runs the checks that what is staged needs, and the Build, Lint and Security workflows run them all on every push to `main` and every pull request.

## Layout

| Path                    | What is there                                                                                       |
| ----------------------- | --------------------------------------------------------------------------------------------------- |
| `src/main`              | The plugin: `core`, `storage`, `bukkit`, `plugin.yml` and the default `config.yml`                  |
| `src/test`              | Unit tests for `core` and `storage`                                                                 |
| `src/integrationTest`   | MockBukkit tests for the `bukkit` adapter                                                           |
| `src/serverTest`        | The Docker harness: real Paper, BlueMap, PostgreSQL, MySQL and MariaDB                              |
| `src/serverTestFixture` | A test-only second plugin the server tests use to drive and observe the real one                    |
| `src/jmh`               | JMH benchmarks of the hot paths (`docs/performance.md`)                                             |
| `docs/`                 | User and contributor documentation, `docs/images/`, and the store listings in `docs/listing/`       |
| `scripts/`              | The release scripts: changelog check, release notes, Modrinth and Hangar uploads; tests in `tests/` |
| `.github/workflows/`    | Build, Lint, Security, Upgrade Watch, Release, and Dependabot Auto-Merge                            |
| `.claude/skills/`       | Agent skills (`release`); Codex finds them through the `.agents/skills` link                        |

## Rules

The binding standards are the four files in `.claude/rules/`. Read them before changing code:

- `java-architecture.md`: the seam between `core`, `storage` and `bukkit`, value types, platform safety
- `java-consistency.md`: formatting, version pins, naming, documentation, tests, prose
- `java-review-checklist.md`: what every change is reviewed against
- `operational-awareness.md`: what the plugin can count on from the server, and what it promises

The ones broken most often:

- Decision logic stays in `core`. `storage` imports JDBC and nothing of the server. The adapter stays thin. `SignpostService` is the only writer of the registry, the store and the map, and no block event handler calls the store.
- Admin limit overrides live in the store, never in `config.yml`.
- Every dependency and plugin version is pinned in `gradle/libs.versions.toml`, and nowhere else.
- The whole plugin is held to 100% line and branch coverage by `./gradlew check`, with nothing excluded. Code only a real server can reach goes behind a seam the tests can drive.
- Only stable API: no NMS and no reflection into server or BlueMap internals (the Folia class-name check is the one exception).
- The plugin does one job. A feature that is not about signs and banners on the BlueMap map belongs in another plugin.
- A change to anything a player reads (the messages, the marker popup, the layer name) is called out in the commit message.
- Prose is never hard-wrapped.
- No personal paths (home folders, user names, machine names) in tracked files.

## Versions and releases

Versions are `MAJOR.MINOR.PATCH+mc<minecraft version>`, for example `1.0.0+mc26.3`, set once in `build.gradle.kts`. Every version after the first adds a `## <version>` entry to `CHANGELOG.md` in the format in [docs/releasing.md](docs/releasing.md#the-changelog). Work is committed straight to `main`. A push whose version has no GitHub release yet publishes it, so bump the version only when a release is meant. The `release` skill walks through it.
