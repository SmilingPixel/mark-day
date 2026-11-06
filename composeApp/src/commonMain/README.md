# Shared Core (commonMain)

## Introduction
The `commonMain` directory is the heart of the MarkDay application. It contains the cross-platform, multiplatform codebase shared across all targets (Android, Desktop, Web, and potentially iOS).

## Structure
All UI presentation and core business logic reside here:
- **UI (Compose Multiplatform):** Screens, components, and Material 3 theming.
- **Navigation:** Core application routing logic using Compose Navigation.
- **Models & Logic:** Domain entities, view models, and state management.
- **Networking/Data Interfaces:** Shared expect/actual declarations and DB interfaces (Room).
- **Draft Recovery:** Shared draft models, repository behavior, autosave coordination, and editor exit protection. See
  [`docs/entry-drafts.md`](../../../docs/entry-drafts.md) for lifecycle and storage invariants.

Modify code here to implement features that will automatically propagate to all application targets.
