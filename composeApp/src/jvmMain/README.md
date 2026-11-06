# Desktop Target (jvmMain)

## Introduction
The `jvmMain` directory is dedicated to the Desktop (Windows, macOS, Linux) target for the MarkDay application. It uses Compose for Desktop running on the JVM.

## Structure
Key components typically found here include:
- **Application Entry Point:** Desktop `main()` function defining window state, size, and icons.
- **Swing Interop:** Any integration with underlying AWT/Swing capabilities if needed.
- **Networking:** Desktop-specific HTTP client engines for Ktor (e.g., `java` engine).
- **File System:** Logic relying on `java.io` or desktop-specific file systems for exports, caching, and IO matching expected interfaces in `commonMain`.
