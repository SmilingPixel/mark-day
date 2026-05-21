# WebAssembly Target (wasmJsMain)

## Introduction
The `wasmJsMain` source set targets the browser using modern Kotlin WebAssembly (Wasm). This allows the application to run natively inside web browsers with excellent performance.

## Structure
WebAssembly implementation specifics:
- **Browser APIs:** JavaScript interoperability or Browser DOM capabilities bridging the `commonMain` UI to the browser.
- **Networking:** Fetch-API based engines for Ktor to work smoothly within browser CORS and networking models.
- **Storage limitations:** Alternative approaches for local persistence, handling capabilities missing from native SQL (since `nonWebMain` features are excluded here).
