# MarkDay Diary App

MarkDay is a cross-platform diary and journaling application built with **Kotlin** and **Compose Multiplatform**. The application seamlessly targets **Android**, **Desktop (JVM)**, and **Web (Wasm)** from a unified codebase, maintaining consistency across platforms using Material Design 3. 

## Features

- **Cross-Platform:** Write your diary on Android, Desktop, or directly in the browser.
- **Unified UI:** Beautiful and consistent Material 3 interface across all devices, powered by Compose Multiplatform.
- **Local Storage:** Fast and reliable text storage utilizing Room Multiplatform (SQLite).
- **Rich Content:** Support for rich text and image rendering (via multiplatform-markdown-renderer and Coil).
- **Sync & Backup (Android/Desktop JVM only):** Google Drive integration for seamless data backups and remote sync. Web (Wasm) support is not implemented yet.

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

### Running Tests
To run all multiplatform tests across configured targets:
```sh
./gradlew allTests
```
Or to run tests for a specific platform (e.g., Desktop/JVM):
```sh
./gradlew composeApp:jvmTest
```
