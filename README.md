# MarkDay Diary App

MarkDay is a cross-platform diary and journaling application built with **Kotlin** and **Compose Multiplatform**. The application seamlessly targets **Android**, **Desktop (JVM)**, and **Web (Wasm)** from a unified codebase, maintaining consistency across platforms using Material Design 3. 

## Features

- **Cross-Platform:** Write your diary on Android, Desktop, or directly in the browser.
- **Unified UI:** Beautiful and consistent Material 3 interface across all devices, powered by Compose Multiplatform.
- **Local Storage:** Fast and reliable text storage utilizing Room Multiplatform (SQLite) on Android, Desktop, and iOS. Web (Wasm) uses in-memory storage with browser localStorage for preferences.
- **Rich Content:** Support for rich text and image rendering (via multiplatform-markdown-renderer and Coil).
- **Sync & Backup (Android/Desktop JVM only):** Google Drive integration for seamless data backups and remote sync. Web (Wasm) support is not implemented yet.
- **Diary Import/Export:** Export diary entries as sync-compatible local text files and import them later to restore backed-up entries.

## Diary Import and Export

Diary entry export and import are available from **Settings > Diagnostics**.

- **Export Diary Entries** writes one sync-compatible `.txt` file per diary entry.
- **Import Diary Entries** reads MarkDay diary entry files from local storage and restores them into the app.
- Import is available on **Android** and **Desktop (JVM)**. Web (Wasm) and iOS currently report diary import as unavailable.
- If an imported entry has the same sync identifier as an existing local entry, MarkDay asks whether to override all conflicts or skip conflicting imports.
- Import follows the same local-first behavior as normal in-app saving: imported entries are written locally, and regular manual or auto cloud sync can run afterward. Import does not force an immediate cloud sync.

Imported files use the same JSON payload format and file naming convention produced by diary export and Google Drive sync, such as:

```text
markday_entry_<syncId>_<updatedAtEpochMillis>.txt
```

## Project Structure

The codebase is organized into Gradle/Kotlin Multiplatform source sets under `composeApp/src`:
- `commonMain/` – Core business logic, UI layer (Compose), Navigation, API, and DB interfaces.
- `androidMain/` – Android-specific implementations (e.g., Ktor OkHttp client, Play Services Auth).
- `jvmMain/` – Desktop entry point, Swing interop, and JVM implementations.
- `wasmJsMain/` – WebAssembly implementations for browser deployment.
- `nonWebMain/` – Shared implementations for non-web platforms (Android, Desktop), primarily dealing with local DB interactions using Room.
- `iosMain/` – iOS-specific logic (currently suspended).

## Prerequisites

To build the MarkDay application, ensure you have the following installed and configured:

- **Java Development Kit (JDK):** JDK 21+ recommended.
- **Kotlin:** 2.0+ 
- **Android SDK:** Required for Android builds.

## Configuration

### Android Setup
To build the Android application, you must configure the path to your Android SDK. If you haven't set the `ANDROID_HOME` environment variable, you can create a `local.properties` file in the root of the project.

**Example `local.properties`:**
```properties
# Add this file to the project root
sdk.dir=/path/to/your/android-sdk
# On Windows, this might look like: sdk.dir=C\:\\Users\\Username\\AppData\\Local\\Android\\Sdk
# On Linux/Codespaces: sdk.dir=/usr/lib/android-sdk
```

### iOS Development
> This setup ensures that iOS development remains suspended by default (saving build time and avoiding configuration issues on non-Mac environments) but can be easily re-enabled by setting `enableIos=true` in `gradle.properties` or passing `-PenableIos=true` as a command-line argument.

## Development

### Running the Application

- **Android:** 
  ```sh
  ./gradlew installDebug
  ```
- **Desktop (JVM):** 
  ```sh
  ./gradlew run
  ```
- **Web (Wasm):** 
  ```sh
  ./gradlew wasmJsBrowserRun
  ```

### Building for Android (Debug)
To build a debug APK for Android from the command line, run:
```sh
./gradlew assembleDebug
```
Once the build completes successfully, the generated APK will be located at:
`composeApp/build/outputs/apk/debug/composeApp-debug.apk`

### Code Style
Kotlin formatting is enforced with the Gradle ktlint plugin. Run the check before submitting Kotlin changes:
```sh
./gradlew ktlintCheck
```

Most formatting issues can be fixed automatically with:
```sh
./gradlew ktlintFormat
```

The ktlint setup lives in the Gradle version catalog, the root and `composeApp` Gradle scripts, and `.editorconfig`.
Generated Kotlin files under `build/generated` are skipped, and a few existing project conventions are configured there:
the `smiling_pixel` package segment, Compose-style function names, existing interface file names, and existing preference
key naming.

### Running Tests

To run all multiplatform tests across configured targets:

```sh
./gradlew allTests
```

Or to run tests for a specific platform (e.g., Desktop/JVM):

```sh
./gradlew composeApp:jvmTest
```

The broader Gradle verification command is:

```sh
./gradlew check
```

The Wasm browser test task links a complete test executable and can require substantially more memory than the JVM and
Android test tasks. On a resource-constrained development machine, run the remaining checks without the Wasm browser
tests and linker with:

```sh
./gradlew check -x wasmJsBrowserTest
```

This is a local verification fallback, not an equivalent replacement for the complete check: it does not execute the Web
tests. Run `./gradlew check` in an environment with enough memory before releasing changes that can affect the Wasm target.

### Updating the Wasm Yarn Lockfile

The Kotlin/Wasm Gradle plugin generates and validates `kotlin-js-store/wasm/yarn.lock`. Do not edit this lockfile by hand.
Dependency upgrades and Kotlin or Compose tooling upgrades can change the generated JavaScript dependency graph. When
Gradle reports that the lockfile changed and asks for `kotlinWasmUpgradeYarnLock`, use this procedure:

1. Regenerate the lockfile:

   ```sh
   ./gradlew kotlinWasmUpgradeYarnLock
   ```

2. Review the dependency changes:

   ```sh
   git diff -- kotlin-js-store/wasm/yarn.lock
   ```

3. Verify that the regenerated lockfile matches the configured dependency graph:

   ```sh
   ./gradlew kotlinWasmStoreYarnLock
   ```

4. Run `./gradlew check`. If local memory is insufficient for the Wasm test linker, use the documented exclusion above
   and ensure the complete check runs in CI or another suitably provisioned environment.

Commit the lockfile together with the dependency or tooling change that required the regeneration.
