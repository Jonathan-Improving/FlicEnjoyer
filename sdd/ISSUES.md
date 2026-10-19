# Known Issues

| ID | Issue | Severity |
|----|-------|----------|
| A | Opening a title can wipe its saved resume point and completion | Medium |
| B | Integration tests are skipped unless explicitly enabled | Low |
| C | Integration tests write media into the real `~/.flicenjoyer` | Low |
| D | Running the benchmark CLI first skips the name prompt | Low |

## Closed issues

Intractable: no reachable fix. Do not reopen without a new constraint.

| ID | Issue |
|----|-------|
| CLSD-01 | Volume and mute changes lag by about half a second |

## A. Opening a title can wipe its saved resume point and completion

**Severity**: Medium (race; intermittent)

When the player opens a title, it starts two background jobs at the same moment: one reads the resume point, and the other records that the title is being watched. The second job writes a full watch entry with position 0 and "not completed", overwriting what was saved. If that write lands first, the read sees 0 and playback starts from the beginning. Even if the read wins, a completed title is reset to in progress every time it is opened, and the stored position stays 0 until the next autosave. This breaks TDD 6.3 and 6.4 intermittently.

**Mitigation options**:
- Only create the watch entry when none exists (insert-if-absent in the database, and HSETNX or an existence check in Valkey). This is the smallest change.
- Chain the two jobs so the entry is written after the resume point is read, carrying over the existing position and completion. This keeps one round trip but still rewrites the row.
- Fold "start watching" into the first resume-point save, and remove the separate write.

## B. Integration tests are skipped unless explicitly enabled

**Severity**: Low (coverage gap)

The integration tests only run when `FLICENJOYER_INTEGRATION_TESTS=true` is set; the reason for the gate is not recorded. CI sets the variable, but a local `./gradlew integrationTest` silently runs nothing, so search and aggregation regressions can pass locally unnoticed. With the variable set, all 33 tests pass against the current `valkey-bundle:unstable` image.

**Mitigation options**:
- Drop the gate and let `integrationTest` always run, skipping cleanly when Docker is unavailable.
- Keep the gate, but print a visible "skipped" notice when the variable is unset.

## C. Integration tests write media into the real `~/.flicenjoyer`

**Severity**: Low (test hygiene)

Upload always copies files into the fixed media folder under the user's home, so the upload and catalog integration tests leave small fake video and thumbnail files in the developer's real `~/.flicenjoyer/media`. They then show up as orphans next to genuine uploads, and `resetData` deletes them along with everything else.

**Mitigation options**:
- Let the media root be overridden (a system property or a constructor argument) and point the tests at a JUnit temporary directory.
- Delete each uploaded title's files in the tests' teardown. This is cheaper, but a test that crashes still leaves its files behind.

## D. Running the benchmark CLI first skips the name prompt

**Severity**: Low (first-run surprise)

The benchmark CLI needs a profile, so when none exists it quietly creates one named "BenchmarkUser" in `~/.flicenjoyer/profile.yaml`. If a user runs `./gradlew benchmark` before ever launching the app, the app finds that profile and never asks for their name, and all their watch history is filed under "BenchmarkUser".

**Mitigation options**:
- Give the CLI its own in-memory profile and never save it.
- Keep the saved profile, but mark it as generated so the app still asks for a name on first launch.

## CLSD-01. Volume and mute changes lag by about half a second

**Status**: Closed (platform limitation)

JavaFX `MediaPlayer` applies volume and mute changes to its internal audio buffer asynchronously, so adjustments are heard about 500 ms late. The lag is most noticeable when changing volume while paused and then resuming.

**Why it's closed**: The buffer belongs to JavaFX's native media stack and has no API for flushing it or changing its size. The only escape is replacing JavaFX media with a different playback engine (VLCJ, a GStreamer binding), which adds a second native media stack: well beyond what a small Valkey sample should carry for a cosmetic delay.
