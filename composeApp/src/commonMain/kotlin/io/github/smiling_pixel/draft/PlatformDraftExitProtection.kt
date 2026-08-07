package io.github.smiling_pixel.draft

import androidx.compose.runtime.Composable

/** Installs platform-specific exit protection for the active [guard]. */
@Composable
expect fun PlatformDraftExitProtection(guard: EditorExitGuard?)
