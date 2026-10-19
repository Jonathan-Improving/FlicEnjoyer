# AGENTS.md

FlicEnjoyer: a JavaFX desktop sample app showing Valkey as a cache-aside layer and ValkeySearch 1.2 full-text search engine in front of PostgreSQL, using the valkey-glide Java client.

## SDD skill

This project uses Spec-Driven Development (SDD). If you have access to the SDD skill, load it before taking any action on this project. It governs how to read, write, and maintain all project documentation. If the skill is unavailable, read the files in `sdd/` directly.

## Documentation

Project documentation lives in the `sdd/` directory.

### If you are exploring this project

- `sdd/PRODUCT.md`: what the app is, who it is for, and the shared vocabulary
- `sdd/TDD.md`: behavioural contract (Given/When/Then rubrics). Read its table of contents first.
- `sdd/TECH.md`: architecture map. Read it before opening source when asked how something works.

### If you are building, running, or testing this project

- `sdd/POLICY.md`: build commands, the Java 26 toolchain, the `check` pipeline, the coverage and formatting gates, and prohibited actions. Read it in full before running anything. Do not infer the build from `build.gradle.kts`.

### If you are changing code in this project (fixing, adding, refactoring)

Read all of the above, plus:
- `sdd/SCHEMA.md`: PostgreSQL tables, Valkey hash fields, FTS index definitions and queries, the GLIDE FT API, and the layout of the config and local files. Check it before touching any key, field, index, or query.

### Additional documents (read when relevant)

- `sdd/ISSUES.md`: known unresolved issues. This is a register. The moment you hit a bug or unexpected behaviour, scan its table of contents before you search or troubleshoot, then read only the entries that match. A scan that matches nothing is a complete result.
- `sdd/mockups.html`: early HTML design prototype. The app has since diverged from it, so treat [TECH.md](sdd/TECH.md) and the code as authoritative.
