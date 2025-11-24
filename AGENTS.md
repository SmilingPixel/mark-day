# AGENT INSTRUCTIONS: MarkDay Diary App

This document provides essential guidelines and context for AI agents interacting with the MarkDay Diary App project.

## 1. Project Context

*   **Type**: Compose Multiplatform Diary Application.
*   **Goal**: Develop and maintain a cross-platform diary app for Android, iOS, Desktop (JVM), and Web (JS/Wasm).
*   **Primary Language**: Kotlin.
*   **UI Framework**: Jetpack Compose Multiplatform.
*   **Build System**: Gradle.

## 2. Project Structure Overview

*   **`composeApp/`**: Core Kotlin Multiplatform module.
    *   **`composeApp/src/commonMain/kotlin/`**: Shared logic, UI, models, and interfaces. **Focus here for core feature development.**
    *   **`composeApp/src/androidMain/`**: Android-specific implementations.
    *   **`composeApp/src/iosMain/`**: iOS-specific implementations.
    *   **`composeApp/src/jvmMain/`**: Desktop-specific implementations.
    *   **`composeApp/src/jsMain/`**: JavaScript-specific implementations.
    *   **`composeApp/src/wasmJsMain/`**: WebAssembly-specific implementations.
    *   **`composeApp/src/commonMain/composeResources/`**: Shared assets (drawables, strings).
*   **`iosApp/`**: Xcode project for iOS native app. Interacts with `composeApp` as a framework.

## 3. Core Development Directives for AI Agents

When modifying or generating code, adhere to the following:

### 3.1. Code Style & Quality

*   **Language**: Kotlin.
*   **Conventions**: Follow official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
    *   **Indentation**: 4 spaces.
    *   **Line Length**: Max 120 characters (soft limit).
    *   **Naming**: `PascalCase` for classes/objects, `camelCase` for functions/properties, `SCREAMING_SNAKE_CASE` for constants.
*   **Documentation**:
    *   **KDoc**: All public classes, functions, and properties **must** have KDoc comments.
    *   **Expressiveness**: KDoc should clearly explain purpose, parameters, and return values.
*   **Automated Checks**:
    *   Run `ktlintCheck` to verify formatting.
    *   Run `detekt` for static analysis and code smells.
    *   **Action**: Automatically fix `ktlint` issues if possible (`./gradlew ktlintFormat`). Report `detekt` findings.

### 3.2. UI Design Principles

*   **Framework**: Jetpack Compose Multiplatform.
*   **Design System**: **Material Design 3**.
    *   Prioritize `androidx.compose.material3` components.
    *   Ensure consistency in color, typography, and shape.
    *   Design for adaptability across various screen sizes and platforms.

### 3.3. Build & Test

*   **Build Tool**: Gradle.


## 4. Interaction Protocol

*   **Changes**: When proposing changes (e.g., via a Pull Request), include:
    *   A clear description of the change.
    *   Evidence of passing `ktlintCheck`, `detekt`, and relevant tests.
    *   Screenshots/GIFs for UI changes, if applicable.
*   **Queries**: When asked to perform a task, prioritize `commonMain` for shared logic unless platform-specific implementation is explicitly requested or required.
