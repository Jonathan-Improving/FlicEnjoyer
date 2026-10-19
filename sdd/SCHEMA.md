# Schema Reference

Exact layouts for every boundary named in [TECH.md](TECH.md#integration-boundaries). IDs are UUID strings throughout, except the synthetic prefixes used by demo data (see [Demo data](#demo-data)).

## PostgreSQL

Applied from `src/main/resources/schema.sql` on every startup with `CREATE TABLE IF NOT EXISTS`. There are no migrations, so a column change needs a reset or a manual `ALTER`.

### `catalog`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `VARCHAR(36)` | PRIMARY KEY | Assigned by the app (`UUID.randomUUID()`) |
| `title` | `VARCHAR(255)` | NOT NULL | |
| `genre` | `VARCHAR(50)` | NOT NULL | Display name, e.g. `Sci-Fi` (see [Genres](#genres)) |
| `description` | `TEXT` | | |
| `tags` | `TEXT` | | Comma-separated |
| `release_year` | `INTEGER` | | |
| `rating` | `DOUBLE PRECISION` | DEFAULT 0 | 0–5 stars |
| `duration_minutes` | `DOUBLE PRECISION` | DEFAULT 0 | Overwritten with the media player's measured length whenever the title is played |
| `video_path` | `TEXT` | | Absolute path under `~/.flicenjoyer/media/videos/` |
| `thumbnail_path` | `TEXT` | | Absolute path under `~/.flicenjoyer/media/thumbnails/`, or empty |
| `created_at` | `TIMESTAMP` | DEFAULT now() | Not mirrored to Valkey |

### `watch_history`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `user_id` | `VARCHAR(36)` | NOT NULL, PK part 1 | Profile `userId` |
| `catalog_id` | `VARCHAR(36)` | NOT NULL, PK part 2 | No foreign key; deleting a title deletes its rows explicitly |
| `title` | `VARCHAR(255)` | | Copied from the catalog for display |
| `resume_timestamp` | `BIGINT` | DEFAULT 0 | Seconds into playback |
| `completed` | `BOOLEAN` | DEFAULT false | |
| `last_watched` | `BIGINT` | DEFAULT 0 | Unix epoch seconds |

## Valkey keys

All values are hash fields stored as strings. Numbers are written with `String.valueOf`, so doubles look like `8.0`, and readers accept either an integer or a decimal string. No key has a TTL.

### `catalog:{id}` (hash)

| Field | Encodes | Source column |
|-------|---------|---------------|
| `title` | string | `title` |
| `genre` | string | `genre` |
| `description` | string, `""` if null | `description` |
| `tags` | comma-separated string, `""` if null | `tags` |
| `releaseYear` | integer | `release_year` |
| `rating` | double | `rating` |
| `durationMinutes` | double | `duration_minutes` |
| `videoPath` | string, `""` if null | `video_path` |
| `thumbnailPath` | string, `""` if null | `thumbnail_path` |

### `watch:{userId}:{catalogId}` (hash)

| Field | Encodes | Source column |
|-------|---------|---------------|
| `userId` | string | `user_id` |
| `catalogId` | string | `catalog_id` |
| `title` | string | `title` |
| `resumeTimestamp` | long seconds | `resume_timestamp` |
| `completed` | `"true"` \| `"false"` | `completed` |
| `lastWatched` | long epoch seconds | `last_watched` |

## ValkeySearch indexes

Both indexes are `ON HASH`, created only if FT._LIST does not already show them. Changing a field definition has no effect on an existing index, and nothing in the app drops one, so run `FT.DROPINDEX idx:catalog` (or `idx:watch`) by hand and restart.

### `idx:catalog` (prefix `catalog:`, language `english`)

| Field | Type | Options |
|-------|------|---------|
| `title` | TEXT | weight 1.0, `WITHSUFFIXTRIE`, `SORTABLE` |
| `genre` | TAG | separator `,`, case-insensitive, `SORTABLE` |
| `description` | TEXT | defaults |
| `tags` | TAG | separator `,`, case-insensitive |
| `releaseYear` | NUMERIC | `SORTABLE` |
| `rating` | NUMERIC | `SORTABLE` |
| `durationMinutes` | NUMERIC | |
| `videoPath` | TAG | |
| `thumbnailPath` | TAG | |

ValkeySearch 1.2 rejects any TEXT weight other than 1.0.

### `idx:watch` (prefix `watch:`)

| Field | Type | Options |
|-------|------|---------|
| `userId` | TAG | |
| `catalogId` | TAG | |
| `title` | TEXT | |
| `resumeTimestamp` | NUMERIC | |
| `completed` | TAG | |
| `lastWatched` | NUMERIC | `SORTABLE` |

### Queries issued

`{p}` is the user's input after `QueryEscaper.escapeQuery`, `{t}` after `escapeTag`, and `{p*}` means `*` appended to the last word.

| Purpose | Index | Query | Options |
|---------|-------|-------|---------|
| Search, prefix half | catalog | `(@title:{p*})\|(@description:{p*})\|(@genre:{{t}})` | `LIMIT 0 n` |
| Search, fuzzy half | catalog | same shape, each word wrapped as `%%word%%` | `LIMIT 0 n` |
| Benchmark FTS search | catalog | `@title:{p}*` | `LIMIT 0 n` |
| Browse a genre | catalog | `@genre:{{t}}` | `SORTBY field ASC\|DESC`, `LIMIT 0 1000` |
| Browse all / Other | catalog | `@releaseYear:[0 inf]` | `SORTBY` (sorted in Java for Other) |
| Profile history | watch | `@userId:{{t}}` | `SORTBY lastWatched DESC`, `LIMIT 0 1000` |
| Delete a title's history | watch | `@catalogId:{{t}}` | `LIMIT 0 1000` |
| Top titles report | watch | `@lastWatched:[0 9999999999]` | `LOAD @catalogId`, `GROUPBY @catalogId REDUCE COUNT AS viewerCount`, `SORTBY @viewerCount DESC`, `LIMIT 0 n` |
| Genre summary report | catalog | `@releaseYear:[0 9999]` | `LOAD @genre @rating`, `GROUPBY @genre REDUCE COUNT AS titleCount, AVG @rating AS avgRating`, `SORTBY @titleCount DESC`, `LIMIT 0 100` |

Search returns the prefix results first, then any fuzzy-only results, de-duplicated by ID and capped at `n`. The top titles report then resolves each `catalogId` to its title with a single pipelined HGETALL batch.

### Escaping rules

| Function | Rule |
|----------|------|
| `escapeQuery` | Replace `-` with a space, then backslash-escape every character outside `[A-Za-z0-9 ]` |
| `escapeTag` | Backslash-escape every character outside `[A-Za-z0-9 ]` |

Every operator ValkeySearch recognises (`@ { } ( ) \| * % ~ = > [ ] ,`) is therefore inert, as is any non-ASCII character.

## GLIDE ValkeySearch 1.2 API

valkey-glide 2.4.1 (Maven Central). Source files under `java/client/src/main/java/glide/api/` in [valkey-glide](https://github.com/valkey-io/valkey-glide): `commands/servermodules/FT.java` and `models/commands/FT/{FTCreateOptions,FTSearchOptions,FTAggregateOptions,FTInfoOptions}.java`. Where valkey-glide documentation or skills disagree with this section on 1.2 syntax, this section reflects the pinned version.

### Field constructors

| Type | Constructor |
|------|-------------|
| `TextField` | `new TextField(boolean noStem, Double weight, boolean withSuffixTrie, boolean noSuffixTrie, boolean sortable)` (`withSuffixTrie` and `noSuffixTrie` are mutually exclusive) |
| `NumericField` | `new NumericField(boolean sortable)` |
| `TagField` | `new TagField(char separator, boolean caseSensitive, boolean sortable)`, `new TagField(boolean caseSensitive, boolean sortable)` |

### `FTCreateOptions.builder()`

| Method | Meaning |
|--------|---------|
| `dataType(DataType)`, `prefixes(String[])` | Key type and prefixes bound to the index |
| `score(double)` | Default document score (1.0) |
| `language(String)` | Default stemming language |
| `skipInitialScan(true)` | Do not index keys that already exist |
| `minStemSize(int)` | Minimum word length for stemming |
| `withOffsets(true)` / `noOffsets(true)` | Store or omit term offsets (mutually exclusive) |
| `noStopWords(true)` / `stopWords(String[])` | Stop-word handling (mutually exclusive) |
| `punctuation(String)` | Tokenizer punctuation characters |

### `FTSearchOptions.builder()`

| Method | Meaning |
|--------|---------|
| `limit(int offset, int count)` | Page window |
| `sortBy(field, SortOrder)`, `withSortKeys()` | Sort by a `SORTABLE` field; include sort keys in the results |
| `nocontent()` | Return IDs only |
| `verbatim()`, `inorder()`, `slop(int)` | Turn off stemming; require terms in order; allow up to N words between terms |
| `shardScope(ShardScope)`, `consistency(ConsistencyMode)` | Cluster mode only |
| `dialect(int)` | Query dialect |

### `FTAggregateOptions.builder()`

| Method | Meaning |
|--------|---------|
| `loadFields(String[])` | Fields to load (`@name`) |
| `addClause(GroupBy \| SortBy \| Limit \| Apply \| Filter)` | Pipeline stages in order |
| `verbatim()`, `inorder()`, `slop(int)`, `dialect(int)` | Same as for search |

`GroupBy(String[] properties, Reducer[] reducers)`, `Reducer(String fn, String[] args, String alias)`, `SortBy(SortProperty[])`, `SortProperty(String property, SortOrder)`.

### `FT.info` overload

`FT.info(client, index, new FTInfoOptions(InfoScope, ShardScope, ConsistencyMode))`, where `InfoScope` ∈ {`LOCAL`, `PRIMARY`, `CLUSTER`}, `ShardScope` ∈ {`ALLSHARDS`, `SOMESHARDS`}, and `ConsistencyMode` ∈ {`CONSISTENT`, `INCONSISTENT`}.

## Local files

### `config.yaml` (working directory)

Every key is optional. If the file is missing, all defaults apply.

| Key | Type | Default |
|-----|------|---------|
| `valkey.host` | string | `localhost` |
| `valkey.port` | int | `6379` |
| `database.host` | string | `localhost` |
| `database.port` | int | `5432` |
| `database.name` | string | `flicenjoyer` |
| `database.user` | string | `flicenjoyer` |
| `database.password` | string | `flicenjoyer` |
| `admin.enabled` | bool | `false`: hides Administration and Edit Video (the shipped `config.yaml` sets `true`) |

### `~/.flicenjoyer/` layout

| Path | Content |
|------|---------|
| `profile.yaml` | `userId: <uuid>`, `displayName: <string>` (`Anonymous` if the name is left blank) |
| `media/videos/{catalogId}.{ext}` | Copied upload |
| `media/thumbnails/{catalogId}.{ext}` | Copied or extracted thumbnail |
| `flicenjoyer.log` | JUL file log, all levels, 5 MB, single file, appended |

Log lines use the format `yyyy-MM-dd HH:mm:ss.SSS LEVEL [logger] message`. Messages carry a bracketed operation tag, e.g. `[upload]`, `[search]`, `[catalog]`, `[player]`, `[admin]`, `[bench]`, `[aggregate]`.

## Genres

`Genre` enum value → display name stored in `genre`: `sci-fi` → `Sci-Fi`, `action` → `Action`, `drama` → `Drama`, `crime` → `Crime`, `thriller` → `Thriller`, `comedy` → `Comedy`, `fantasy` → `Fantasy`, `gaming` → `Gaming`, `other` → `Other`. Browse's **Other** filter matches any stored genre that is not a known value, compared case-insensitively.

## Demo data

| Item | Pattern |
|------|---------|
| Catalog IDs | `bench-…` |
| Viewer user IDs | `s` + 35 characters of a UUID |

`unseedData` deletes rows and keys that match these patterns. `resetData` deletes all rows in both tables, all `catalog:*` and `watch:*` keys (found with SCAN), and the whole media directory. It leaves the indexes in place.
