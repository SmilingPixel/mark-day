# Non-Web Targets (nonWebMain)

## Introduction
The `nonWebMain` source set is an intermediate layer designed to share code between Android, Desktop (JVM), and iOS, while specifically excluding WebAssembly (Wasm).

## Structure
This module solves the problem of sharing technologies that the browser environment does not support:
- **Database (Room SQL):** The Room database implementation, KSP-generated DAOs, and SQLite drivers operate seamlessly across native and JVM targets natively, but are not viable on Web targets.
- **Local Persistence (DataStore):** Storage APIs tailored for actual file systems.

Any shared logic relying on robust, file-backed local storage schemas should be located here rather than `commonMain` so it can be omitted from web browser compilations without causing errors.
