# Non-Web Targets (nonWebMain)

## Introduction
The `nonWebMain` source set is an intermediate layer designed to share code for non-web targets while specifically excluding WebAssembly (Wasm). In the current build setup, it is shared by Android and Desktop (JVM), and iOS also participates when `enableIos=true` in `composeApp/build.gradle.kts`.

## Structure
This module solves the problem of sharing technologies that the browser environment does not support across the non-web targets configured in the build:
- **Database (Room SQL):** The Room database implementation, KSP-generated DAOs, and SQLite drivers operate across JVM and native targets where configured, but are not viable on Web targets.
- **Local Persistence (DataStore):** Storage APIs tailored for actual file systems.

Any shared logic relying on robust, file-backed local storage schemas should be located here rather than `commonMain` so it can be omitted from web browser compilations without causing errors.
