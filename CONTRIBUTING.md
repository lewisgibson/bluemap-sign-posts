# Contributing

Thanks for helping improve BlueMapSignPosts. This is a small project, and issues and pull requests are welcome.

## Prerequisites

Use JDK 17 or newer to run Gradle, and Python 3.12 or newer for the release scripts. The Foojay resolver downloads the Java 25 toolchain used to compile the plugin.

Docker is required for `./gradlew serverTest`. You do not need to provide a Minecraft server for the build, unit tests or integration tests. A local server is only needed for manual testing; the server test task manages its own Docker servers.

## Build and test

| Command                     | Purpose                                                                                        |
| --------------------------- | ---------------------------------------------------------------------------------------------- |
| `./gradlew build`           | Compile, Spotless, unit and integration tests, coverage gate and jar                           |
| `./gradlew test`            | Unit tests                                                                                     |
| `./gradlew integrationTest` | MockBukkit adapter tests with a fake BlueMapAPI registered                                     |
| `./gradlew serverTest`      | Built jar on real Paper 26.3 with BlueMap in Docker, using each storage backend; takes minutes |
| `./gradlew spotlessApply`   | Format Java with google-java-format, AOSP style and 4-space indentation                        |
| `./gradlew runServer`       | Start a local Paper server with the plugin installed                                           |
| `./gradlew jmh`             | Run [hot-path benchmarks](docs/performance.md)                                                 |
| `make hooks`                | Install the pre-commit gate after cloning                                                      |
| `make lint`                 | Run Prettier, shellcheck, actionlint and gitleaks across the tree                              |
| `make test-scripts`         | Test the release scripts (changelog check, release notes, store uploads) at 100% coverage      |

The current jar is `build/libs/BlueMapSignPosts-1.0.0+mc26.3.jar`. Before using `./gradlew runServer`, put BlueMap's jar in `run/plugins/`; the plugin requires it.

Unit and integration tests together must cover every line and branch. `./gradlew check` enforces 100% coverage with no excluded classes. Reports are in `build/reports/jacoco/`. Put server-only behaviour behind a seam the tests can drive, then use real-server tests to verify the built jar as well.

The full behaviour suite uses Paper 26.3 build 174 and BlueMap 5.29. Every build also boots the jar with BlueMap on Paper and Purpur 26.3 and waits for the markers to be published. MockBukkit drives the Folia chunk-region scheduling path.

The store is tested against SQLite, PostgreSQL 16, MySQL 8.4 and MariaDB 11.8, with and without TLS. Real-server tests cover dimensions, runtime world loading, BlueMap reloads, explosions and pistons. BlueMap's saved marker state has also been checked in file, SQLite, PostgreSQL with TLS, and MariaDB storage.

`make hooks` installs a pre-commit check of what is staged: prettier, shellcheck, actionlint, gitleaks, the script tests, the changelog format and `./gradlew check`. The Build, Lint and Security workflows run the same checks on every pull request into `main`.

## Project layout

| Path                                         | Responsibility                                                                           |
| -------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `src/main/java/.../core`                     | Platform-free decisions, tested with plain JUnit and AssertJ                             |
| `src/main/java/.../storage`                  | JDBC store, including tests against a real SQLite file                                   |
| `src/main/java/.../bukkit`                   | Plugin, listener, command, configuration reader and BlueMap sink, tested with MockBukkit |
| `src/integrationTest`                        | Adapter integration tests                                                                |
| `src/serverTest` and `src/serverTestFixture` | Docker tests and their fixture plugin                                                    |
| `docs/`                                      | User and contributor documentation                                                       |

[docs/architecture.md](docs/architecture.md) explains the boundaries. Detailed standards live in [.claude/rules/](.claude/rules/).

## Coding standards

- Keep `org.bukkit.*` and `de.bluecolored.*` out of `core` and `storage`. Convert platform objects into plain values in the adapter.
- Use immutable records for domain values and validate their invariants in the constructor.
- Route mutations through `SignpostService`. Event handlers must not write to the store directly.
- Pin dependency and build-plugin versions in `gradle/libs.versions.toml`.
- Give public types and methods Javadoc explaining their purpose. Keep a `package-info.java` in every package.
- Keep features focused on player signs and banners on BlueMap. Unrelated features belong in another plugin.
- Call out changes to player-facing messages, marker popups or layer labels for review.

## Pull requests

1. Fork the repository and create a branch.
2. Make the change with tests and run `./gradlew build` until it passes.
3. Run `./gradlew serverTest` if you changed the store, `plugin.yml` or anything else only a real server can show.
4. Open a pull request saying what it changes and why, and calling out any change to what players read.

The owner manages releases separately. Release version bumps are made directly on `main`, without a release pull request. Pushing a version with no existing GitHub release runs the gate and publishes automatically. See [docs/releasing.md](docs/releasing.md) for the version, changelog and store setup.

By contributing, you agree to license your contributions under the project's [GPL-3.0 license](LICENSE).
