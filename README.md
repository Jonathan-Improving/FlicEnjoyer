# FlicEnjoyer

A streaming-catalog desktop demo that puts [Valkey](https://valkey.io) in front of PostgreSQL as a cache and [ValkeySearch 1.2](https://github.com/valkey-io/valkey-search) full-text search engine, then benchmarks the difference.

Built with Java 26, JavaFX, and [valkey-glide](https://github.com/valkey-io/valkey-glide). Part of the [valkey-developer-resources](https://github.com/valkey-io/valkey-developer-resources) collection.

## What it does

FlicEnjoyer is a pretend movie tracker. Upload videos, find them with find-as-you-type search that tolerates typos, play them, and pick up where you left off. Behind every screen, PostgreSQL stores the data and Valkey serves it: cached reads, ValkeySearch queries, and FT.AGGREGATE reports. A built-in benchmark runs the same workload against the database alone and through Valkey, and shows the speedup.

## Features

- 🔍 **Search**: prefix and fuzzy matching over titles, descriptions, and genres as you type
- 📂 **Browse**: filter the catalog by genre and sort by title, rating, or year
- ▶️ **Watch history**: resume points saved every few seconds, per user profile
- 📊 **Reports**: FT.AGGREGATE rankings of top titles by viewers and a catalog summary by genre
- ⚡ **Benchmarks**: DB-direct vs Valkey-cached throughput under concurrent load
- 🛠️ **Administration** (opt-in): edit or delete titles

## Requirements

| Requirement | Details |
|-------------|---------|
| JDK | 26, e.g. Temurin 26 from [Adoptium](https://adoptium.net/temurin/releases/?version=26) (Homebrew does not ship 26) |
| Docker | Runs Valkey (`valkey/valkey-bundle:unstable`) and PostgreSQL 17 |
| Ports | 6379 and 5432 on localhost. Change `docker-compose.yml` and `config.yaml` together if they are taken |

ValkeySearch 1.2 or later is required. Among the bundle images, only `unstable` ships it (currently 1.3); the stable `9.0` tag carries 1.0.

## Quickstart

```bash
docker compose up -d     # Valkey + PostgreSQL
./gradlew run            # prompts for your name on first launch
```

Optional demo data for reports and benchmarks:

```bash
./gradlew seedData       # synthetic catalog + viewers (default 200 titles)
./gradlew unseedData     # remove only the demo data
```

The Administration view (edit and delete titles) is on in the shipped `config.yaml`. Set `admin.enabled: false` to hide it.

## Benchmarking

```bash
./gradlew benchmark --args="--threads=64 --ops=500 --operation=catalog"   # or resume, search
```

Each run uses a 7:1 read/write mix. Both paths do identical database work on writes, so the measured gap comes from serving reads out of Valkey.

`docker-compose.yml` deliberately gives PostgreSQL tiny buffers (`shared_buffers=128kB`, `work_mem=64kB`). The demo dataset would otherwise sit entirely in PostgreSQL's cache, which no production working set does. Constraining the buffers models the cache-miss rate a real database sees, while Valkey always serves from memory. That gap is the case for a cache.

## Development

```bash
./gradlew spotlessApply check    # format, unit tests, 70% coverage gate
FLICENJOYER_INTEGRATION_TESTS=true ./gradlew integrationTest   # Testcontainers, needs Docker
```

| Task | Effect |
|------|--------|
| `./gradlew resetData` | ⚠️ Deletes all catalog and watch-history rows, all cached keys, and `~/.flicenjoyer/media` |
| `./gradlew dbBackup` / `dbRestore` | Dump to or restore from `backups/flicenjoyer.sql` (git-ignored) |

The PostgreSQL container has no volume. Run `dbBackup` before `docker compose down` if you want to keep your data.

Local state lives in `config.yaml` (connection settings, admin flag) and `~/.flicenjoyer/` (profile, uploaded media, log file).

## Known limitations

- Volume and mute changes lag by about 500 ms, a JavaFX `MediaPlayer` limitation.
- Integration tests are skipped unless `FLICENJOYER_INTEGRATION_TESTS=true` is set.

## Documentation

Design documents live in [`sdd/`](sdd/):

- [PRODUCT.md](sdd/PRODUCT.md): what the app is, who it's for, vocabulary
- [TECH.md](sdd/TECH.md): architecture, system diagram, module map, threading
- [SCHEMA.md](sdd/SCHEMA.md): tables, Valkey keys, FTS indexes and queries, GLIDE FT API, config files
- [TDD.md](sdd/TDD.md): behavioural rubrics
- [POLICY.md](sdd/POLICY.md): build, test, and architecture rules
- [ISSUES.md](sdd/ISSUES.md): known issues
- [mockups.html](sdd/mockups.html): early HTML prototype (the app has since diverged)

## License

[MIT](LICENSE)
