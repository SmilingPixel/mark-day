package io.github.smiling_pixel.draft

import androidx.compose.runtime.Composable

/**
 * Installs platform-specific exit protection for the active editor.
 *
 * @param guard Active editor guard, which takes precedence over ordinary Back behavior.
 * @param onBackRequest Optional fallback invoked when the platform handles Back without an active editor guard.
 */
@Composable
expect fun PlatformDraftExitProtection(
    guard: EditorExitGuard?,
    onBackRequest: (() -> Unit)? = null,
)
