# Android Target (androidMain)

## Introduction
The `androidMain` source directory holds the Android-specific implementation details for the MarkDay application. These implementations fulfill the `expect` declarations found in `commonMain` and handle Android-native integrations.

## Structure
Key components found in this module:
- **Application Context & Entry Points:** `MainActivity` and OS-level lifecycle hooks.
- **Networking:** Android-native networking components, like the Ktor `okhttp` client engine.
- **System Integrations:** Access to Google Play Services for things like Google Drive Auth and Cloud Sync features.
- **Platform APIs:** Specific implementations requiring `Context` or Android application framework resources.
