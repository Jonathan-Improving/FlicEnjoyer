# Development Policy

## Build

- Build with the Gradle wrapper only: `./gradlew <task>`. Do not use a system `gradle`.
- Toolchain: Java 26 (`java.toolchain.languageVersion = 26`) and JavaFX 26. Install a JDK 26 locally. Homebrew does not ship one, so on macOS extract the Temurin 26 archive from Adoptium into `~/Library/Java/JavaVirtualMachines/`, where both `/usr/libexec/java_home` and Gradle's toolchain detection find it. Gradle itself also runs on that JDK.
- google-java-format is pinned to 1.36.1 for this Spotless release. Upgrade the two together, and run `spotlessCheck` on JDK 26 after either changes.
- The valkey-glide dependency uses a native OS classifier picked by `osdetector`. Do not hard-code the classifier.
- Run the app with `./gradlew run`. The working directory must be the project root, because `config.yaml` is read from there.

## Build pipeline

A change is valid only when `./gradlew check` passes. `check` runs these in order:

1. `compileJava`, `compileTestJava`
2. `test`: unit tests on JUnit Platform
3. `jacocoTestCoverageVerification`: at least 70% instruction coverage, excluding `ui/`, `db/`, and the entry points (`FlicEnjoyerApp`, `ResetData`, `SeedData`, `UnseedData`, `BenchmarkCli`)
4. `spotlessCheck`: google-java-format

Run `./gradlew spotlessApply` before `check`. Do not edit the formatter's output by hand.

## Testing

### Unit tests

- Every class in `service/` and `valkey/` has unit tests under `src/test/java`, mirroring the main package path.
- Mock Valkey through the `ValkeyClient` interface and mock the repositories or JDBC objects with Mockito. Unit tests must not need a running Valkey, PostgreSQL, Docker, or a display.
- `ui/` is excluded from coverage. Its thread-handoff helpers (`DataLoader`, `BackgroundTask`) still have unit tests that run without a display; keep them testable that way.
- Add a new CLI entry point to the JaCoCo exclusion list when it is created. Do not lower the 70% threshold.

### Integration tests

- Live in `src/integrationTest/java` and run with `FLICENJOYER_INTEGRATION_TESTS=true ./gradlew integrationTest`. They need Docker. Testcontainers starts `valkey/valkey-bundle:unstable` and `postgres:17`.
- `integrationTest` passes `DOCKER_HOST` through and falls back to `unix:///var/run/docker.sock`. On Colima or another non-default runtime, also export `DOCKER_HOST` (e.g. `unix://$HOME/.colima/default/docker.sock`), `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, and, if Ryuk cannot mount the socket, `TESTCONTAINERS_RYUK_DISABLED=true`.
- Before writing or changing one, read the matching rubrics in [TDD.md](TDD.md). Each integration test traces to a rubric; behaviour with no rubric is unit-test territory.

### Manual verification

After changes to search, cache-aside, playback, or the benchmark:

1. `docker compose up -d`, then `./gradlew run`.
2. Upload a title, find it through search (including a misspelling), and play it. Leave the player and resume from My History.
3. Optionally run `./gradlew seedData`, then check Reports and run a Benchmark. Clean up with `./gradlew unseedData`.

## Input and output sanitization

- Every user-supplied string placed in an FT.SEARCH or FT.AGGREGATE query goes through `QueryEscaper.escapeQuery` (free text) or `escapeTag` (TAG values). Never concatenate raw input into a query.
- All SQL with variable input uses `PreparedStatement` parameters. Only fixed statements (`schema.sql` and the CLI tools' `DELETE`s) may run without parameters.
- File deletion goes only through the path check that requires the resolved path to start with `AppPaths.MEDIA`. Never delete a path taken from the catalog without that check.
- Upload requires a non-blank title, a non-blank genre, and an existing video file. Reject the upload before any copy or write happens.

## Code style

- Java 21+ idioms: records for data, `switch` expressions, pattern matching for `instanceof`, `List.of`/`Map.of`, `.toList()`, `var` for locals.
- Build UI in Java code. There is no FXML. Styling lives in `src/main/resources/styles.css`.
- Logging is `java.util.logging` only, one logger per class, with messages prefixed by a bracketed operation tag (`[upload]`, `[search]`, …). Use `SEVERE` for failures, `WARNING` for recoverable problems, `INFO` for lifecycle events, and `FINE` for queries and timings.
- Wrap checked `SQLException`s at the service boundary in a `RuntimeException` with context (operation and ID). A cache miss is not an error.
- Add an icon as an SVG file under `src/main/resources/icons/` (one `<path>`, optional `data-filled="true"`) and register its name in `IconLoader.ICON_NAMES`.

## Dependencies

- Pin exact versions in `build.gradle.kts`. Justify any new dependency in the commit message.
- Use valkey-glide for every Valkey call. Do not add Jedis, Lettuce, or Redisson.
- Use ValkeySearch for every search. Do not add Elasticsearch, OpenSearch, Lucene, or PostgreSQL full-text search for app features.
- Use plain JDBC with HikariCP. Do not add Hibernate, JPA, jOOQ, or Spring.
- Use JUL only. Do not add SLF4J, Log4j, or Logback to the main source set. `slf4j-simple` exists only on the integration-test runtime classpath, for Testcontainers.
- Use SnakeYAML for local files and YAML for every project-owned local file. Do not add Jackson or JSON config files.
- Do not add JavaScript or Node tooling.

## Architecture rules

- PostgreSQL is the source of truth. Every write goes to PostgreSQL first and then updates Valkey. If the database write fails, Valkey is left untouched.
- Every Valkey key must be rebuildable from PostgreSQL. Never store data only in Valkey.
- Never set TTLs on cache keys. The app manages entries itself, which keeps benchmarks deterministic.
- Search, browse, history listing, and reports query ValkeySearch. PostgreSQL queries for these features exist only as the DB-direct path used by the benchmark.
- Keep the layers apart: `ui` calls only `service` and `model`; only `db/` imports JDBC or HikariCP; only `valkey/` imports valkey-glide; `model/` imports nothing internal.
- The DB-direct and cached benchmark tasks must do the same database work on writes, so the measured difference comes only from cached reads. Keep the 7:1 read/write mix in `BenchmarkService.createTasks`, the one place that defines both the CLI and UI workloads.
- Never call Valkey, JDBC, or the filesystem on the JavaFX Application Thread. Load images with `backgroundLoading=true`. Every view that starts async work registers a leave hook in `MainController` that cancels it.
- Administration and Edit Video exist only when `admin.enabled: true` is set in `config.yaml`. Keep them gated.
- Use the key and index names from `ValkeyKeys`. Do not hard-code prefixes anywhere else.

## Prohibited actions

- Do not run `docker compose down` or `./gradlew resetData` without asking. The PostgreSQL container has no volume, so both destroy data. Run `./gradlew dbBackup` first if data matters.
- Do not run `FLUSHALL`/`FLUSHDB` or `FT.DROPINDEX` against the shared local Valkey without asking.
- Do not commit `config.yaml` changes that carry real credentials or non-local hosts.
- Do not commit `backups/`, `build/`, `.gradle/`, or anything under `~/.flicenjoyer/`.
- Do not change the PostgreSQL buffer settings in `docker-compose.yml` to make the database look faster. They model a production cache-miss rate on purpose.

## Deployment

- Local only: `docker compose up -d` starts `flicenjoyer-valkey` on port 6379 and `flicenjoyer-postgres` on port 5432, on a fixed bridge subnet `172.22.0.0/24`. If those host ports are taken, change both `docker-compose.yml` and `config.yaml`.
- Back up and restore the database with `./gradlew dbBackup` and `./gradlew dbRestore`, which write `backups/flicenjoyer.sql`.
- CI (`.github/workflows/fts-ci.yml`) runs `./gradlew check` on Temurin 26, then `integrationTest`, for pushes and pull requests to `main`.

## Debug output

- Log file: `~/.flicenjoyer/flicenjoyer.log` (all levels). The console (stderr) shows `INFO` and above.
- To see FT.SEARCH queries and result counts, raise `com.flicenjoyer.level` to `FINE` in `src/main/resources/logging.properties` and do not commit the change.
