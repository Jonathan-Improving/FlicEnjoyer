# Product Definition

## What is FlicEnjoyer?

FlicEnjoyer is a desktop sample app that shows Valkey working as a cache and search layer in front of a PostgreSQL database. It presents itself as a fictitious movie and TV tracker that a "flic" enjoyer uses to upload videos, find something to watch, pick up where they left off, and see what everyone else is watching. Every screen is backed by a real Valkey feature: cache-aside reads, ValkeySearch 1.2 full-text search, and FT.AGGREGATE reports. A built-in benchmark puts numbers on the difference Valkey makes.

## What problem does it solve?

Developers weighing Valkey for an existing database-backed app need more than a "hello world" to judge it.

- **Proof of speedup**: the benchmark runs the same workload against the database alone and against Valkey, under concurrent load, and reports the speedup side by side.
- **Search without a search cluster**: typeahead and misspelling-tolerant search come from ValkeySearch instead of a separate Elasticsearch or OpenSearch deployment.
- **Realistic patterns**: the database stays the source of truth while Valkey serves reads, which is the shape most production adopters have.
- **Working reference code**: every pattern uses the valkey-glide Java client, so the code can be lifted into real projects.

## Who is it for?

- Developers evaluating Valkey as a cache in front of an existing relational database
- Teams considering ValkeySearch full-text search as an alternative to a dedicated search engine
- Developers learning cache-aside, search, and aggregation patterns with valkey-glide
- Presenters who need a visual, interactive Valkey demo with live benchmark numbers

## Why does it exist?

- **Valkey and valkey-glide documentation** explains each command well, but does not show how they combine in a full application with a database behind them.
- **Synthetic benchmarks** such as `valkey-benchmark` measure raw server throughput, not the end-to-end difference a cache makes for an application.
- **Elasticsearch and OpenSearch** offer rich search, but add a second cluster to run and keep in sync.

FlicEnjoyer fills the gap: one small, runnable app that shows cache, search, and analytics on Valkey together, with a database behind it and a benchmark that measures the result.

## How is it used?

1. On first launch the user enters their name, which becomes their local **profile**.
2. The user uploads videos to the **catalog**, each with a title, genre, description, tags, and an optional thumbnail.
3. The user finds titles through **search** as they type, or **browses** the catalog by genre.
4. The user plays a title in the **player**; their **resume point** is saved as they watch, so **My History** can resume it later.
5. The user opens **reports** to see the most-watched titles and a catalog summary by genre.
6. The user runs a **benchmark** to compare database-only and Valkey-cached throughput.

An optional **administration** view, turned on in the app config, lets the user edit or delete catalog entries.

## Scope

- PostgreSQL holds all persistent data; Valkey is a cache and search layer that can be rebuilt from it
- Single desktop user per profile, with no login or authentication
- Local playback of uploaded files only; no streaming or transcoding
- Demo data tools seed and remove a synthetic catalog and viewing history for reports and benchmarks

## Success criteria

- Search results appear within 10 ms of a keystroke on the demo dataset
- Misspelled search terms still find the intended title
- Resume points load in under a millisecond when cached
- The benchmark shows Valkey well ahead of the database under concurrent load (target 10× or better)

## Vocabulary

| Term | Definition |
|------|-----------|
| **Catalog** | The library of uploaded titles and their metadata. |
| **Title** | One uploaded video in the catalog, with its metadata, video file, and optional thumbnail. |
| **Profile** | The local identity (name plus generated ID) the app creates on first launch. |
| **Search** | Find-as-you-type lookup over titles, descriptions, and genres that tolerates misspellings. |
| **Browse** | Listing the catalog filtered by genre and sorted by title, rating, or year. |
| **Watch history** | The per-profile record of titles started, how far each got, and whether it was finished. |
| **Resume point** | The playback position saved for a title so it can continue from where the user stopped. |
| **Reports** | Aggregate views across all profiles: top titles by viewer count and catalog summary by genre. |
| **Benchmark** | A timed run of the same workload against the database alone and through Valkey, reporting throughput and speedup. |
| **Administration** | The optional view for editing and deleting titles, enabled in the app config. |
| **Demo data** | Synthetic catalog and watch history added and removed by the seed and unseed tools. |
