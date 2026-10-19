# Test-Driven Development Rubrics

| # | Functional area | Rubrics |
|---|----------------|---------|
| 1 | Profile and startup | 1.1 – 1.5 |
| 2 | Upload | 2.1 – 2.4 |
| 3 | Search | 3.1 – 3.5 |
| 4 | Browse | 4.1 – 4.3 |
| 5 | Cache and source of truth | 5.1 – 5.5 |
| 6 | Watch history and resume | 6.1 – 6.7 |
| 7 | Reports | 7.1 – 7.3 |
| 8 | Benchmark | 8.1 – 8.4 |
| 9 | Administration | 9.1 – 9.4 |
| 10 | Responsiveness | 10.1 – 10.3 |
| 11 | Data tools | 11.1 – 11.2 |

## 1. Profile and startup

### 1.1 First launch creates a profile
- **Given** no saved profile exists
- **When** the app starts and the user enters a name
- **Then** a profile with that name and a newly generated unique ID is saved and used for the session

### 1.2 Blank name still yields a profile
- **Given** no saved profile exists
- **When** the user submits the first-launch prompt with an empty name
- **Then** a profile named "Anonymous" is created

### 1.3 Later launches reuse the profile
- **Given** a saved profile exists
- **When** the app starts
- **Then** no name prompt is shown, and the saved ID and name are used

### 1.4 Missing config falls back to local defaults
- **Given** an app config that is missing or omits any setting
- **When** the app starts
- **Then** each omitted setting takes its local default (localhost Valkey and PostgreSQL on their standard ports, administration off)

### 1.5 Startup makes the existing catalog searchable
- **Given** a database that holds catalog titles and a Valkey instance with none of them cached (e.g. freshly flushed)
- **When** the app starts
- **Then** the search indexes exist and every database title can be found through search without first being opened

## 2. Upload

### 2.1 Successful upload adds a title
- **Given** an upload with a title, a genre, and an existing video file
- **When** the user submits it
- **Then** the title is stored in the database, can be found through search, and its media is copied into the app's media folder

### 2.2 Required fields are enforced
- **Given** an upload missing its title, its genre, or a readable video file
- **When** the user submits it
- **Then** the upload is rejected and nothing is stored or copied

### 2.3 Thumbnail is optional
- **Given** an otherwise valid upload with no thumbnail
- **When** the user submits it
- **Then** the title is added and shows a placeholder image wherever it appears

### 2.4 Uploaded media keeps its file type
- **Given** an uploaded video or thumbnail file, whether its name has one extension, several dots, or none
- **When** it is copied into the media folder
- **Then** the copy is named by the title's ID and keeps the original's final extension, or has none if the original had none

## 3. Search

### 3.1 Typing a prefix finds titles
- **Given** a catalog with titles whose title or description contains words that start with the typed text
- **When** the user types that text into search
- **Then** those titles appear in the results

### 3.2 Misspellings still find titles
- **Given** a catalog title with a word in its title or description
- **When** the user searches for that word with a small typo
- **Then** the title still appears in the results

### 3.3 Genre names match
- **Given** a catalog with titles in a genre
- **When** the user searches for that genre's name
- **Then** titles in that genre appear in the results

### 3.4 Results are unique and bounded
- **Given** a search term that matches the same title both by prefix and by fuzzy match
- **When** results are shown
- **Then** the title appears once, prefix matches come before fuzzy-only matches, and no more than the result limit is shown

### 3.5 Search syntax in user input is inert
- **Given** search text containing query operators, field selectors, wildcards, fuzzy markers, tag braces, filter or vector-query syntax, or non-ASCII characters
- **When** the search runs
- **Then** the input is treated as literal text and cannot widen, narrow, or otherwise change the query beyond the matching rules above

## 4. Browse

### 4.1 Browse lists the catalog
- **Given** a catalog with titles
- **When** the user opens Browse with the "All" filter
- **Then** every title is listed

### 4.2 Genre filter narrows and sorts
- **Given** a catalog with titles across several genres
- **When** the user picks a genre and a sort order (title, rating, or year; ascending or descending)
- **Then** only titles in that genre are listed, in that order

### 4.3 "Other" collects unknown genres
- **Given** a catalog containing titles whose genre is not one of the built-in genres
- **When** the user picks the "Other" filter
- **Then** exactly those titles are listed, in the chosen sort order, and no title with a built-in genre appears

## 5. Cache and source of truth

### 5.1 Cached reads skip the database
- **Given** a title or resume point already in the cache
- **When** it is read
- **Then** the value comes from Valkey and the database is not queried

### 5.2 Cache misses fall back and backfill
- **Given** a title or resume point that exists in the database but not in the cache
- **When** it is read
- **Then** the database value is returned and the cache now holds it

### 5.3 Missing data is empty, not an error
- **Given** a title or resume point that exists in neither store
- **When** it is read
- **Then** the result is empty (no title, or a resume point of zero) and no error is raised

### 5.4 Writes update both stores
- **Given** a change to a title's metadata, rating, duration, or thumbnail, or to a resume point or completion
- **When** the change is saved
- **Then** both the database and the cache hold the new value, and search and browse reflect it

### 5.5 A failed database write leaves the cache untouched
- **Given** any write whose database step fails
- **When** the write is attempted
- **Then** an error is raised and the cached value is unchanged

## 6. Watch history and resume

### 6.1 Playing a title records it in history
- **Given** a title the profile has never played
- **When** the user starts playing it
- **Then** it appears in My History as in progress

### 6.2 Progress is saved while watching
- **Given** a title that is playing
- **When** the user pauses, stops, seeks, leaves the player, or keeps watching for several seconds
- **Then** the current position is saved as the resume point

### 6.3 Resuming continues from the saved position
- **Given** a title with a saved resume point
- **When** the user opens it from My History, Search, or Browse
- **Then** playback starts from that position and the user is told where it resumed

### 6.4 Finishing marks the title completed
- **Given** a title that is playing
- **When** playback reaches the end
- **Then** the title is marked completed in My History

### 6.5 The play action reflects progress
- **Given** a title shown on a card or in the player
- **When** its action is shown
- **Then** it reads "Play" with no saved progress, "Start Over" when progress is within the last 5% of the title's length, and "Resume" otherwise

### 6.6 History is per profile and most-recent first
- **Given** several profiles with watch history
- **When** a user opens My History
- **Then** only their own titles are listed, most recently watched first

### 6.7 Ratings persist
- **Given** a title open in the player
- **When** the user sets a rating by clicking a star or pressing 0–5
- **Then** the rating is saved and shown wherever that title appears

## 7. Reports

### 7.1 Top titles by viewers
- **Given** watch history from several profiles
- **When** the user generates the top titles report
- **Then** titles are ranked by how many profiles have watched them, highest first, shown by title name, up to the report limit

### 7.2 Catalog summary by genre
- **Given** a catalog with titles across several genres
- **When** the user generates the genre summary
- **Then** each genre is listed with its title count and average rating, largest genre first

### 7.3 Empty data yields an empty report
- **Given** no watch history, or an empty catalog
- **When** the corresponding report is generated
- **Then** the report shows no rows and no error

## 8. Benchmark

### 8.1 Side-by-side comparison
- **Given** a chosen operation (catalog lookup, resume point, or search), thread count, and operations per thread
- **When** the user runs the benchmark
- **Then** throughput is reported for the database alone and for the Valkey path, together with the speedup between them

### 8.2 Warmup is excluded
- **Given** any benchmark run
- **When** throughput is measured
- **Then** the first tenth of each thread's operations runs untimed, and only the remaining operations count toward the result

### 8.3 Both paths do the same work
- **Given** the catalog lookup or resume point operation
- **When** the benchmark runs
- **Then** both paths use a 7:1 read/write mix and do the same database work on writes, so the measured difference comes from cached reads alone

### 8.4 Failures do not hang the run
- **Given** a benchmark whose operations throw errors
- **When** it runs
- **Then** the run still completes and reports throughput for the operations that succeeded

## 9. Administration

### 9.1 Administration is opt-in
- **Given** an app config without administration turned on
- **When** the app starts
- **Then** the Administration and Edit Video views are not reachable

### 9.2 Editing updates a title
- **Given** administration is turned on and a title exists
- **When** the user edits its metadata or thumbnail and saves
- **Then** the change appears in search, browse, and the player

### 9.3 Deleting removes a title everywhere
- **Given** administration is turned on and a title exists with watch history from one or more profiles
- **When** the user confirms deletion
- **Then** the title is gone from the database, the cache, search, browse, and reports, its watch history is gone for every profile, and its media files are removed

### 9.4 Deletion never touches files outside the media folder
- **Given** a title whose stored video or thumbnail path resolves outside the app's media folder
- **When** the title is deleted
- **Then** those files are left in place

## 10. Responsiveness

### 10.1 The UI never waits on storage
- **Given** any view that reads or writes Valkey, the database, or files
- **When** that work runs
- **Then** the window stays responsive and results appear when ready

### 10.2 Only the latest search is shown
- **Given** the user types quickly, triggering several searches
- **When** results come back out of order
- **Then** only the results for the most recent text are displayed

### 10.3 Leaving a view cancels its work
- **Given** a view with loading or running work
- **When** the user navigates away
- **Then** that work is cancelled and its results are not applied

## 11. Data tools

### 11.1 Unseed removes only demo data
- **Given** a catalog and history containing both demo data and user-uploaded titles
- **When** the unseed tool runs
- **Then** all demo data is removed from both stores, and user titles and history remain

### 11.2 Reset erases everything
- **Given** any stored catalog, history, cache, and media
- **When** the reset tool runs
- **Then** both tables are empty, no catalog or watch keys remain in Valkey, and the media folder is gone
