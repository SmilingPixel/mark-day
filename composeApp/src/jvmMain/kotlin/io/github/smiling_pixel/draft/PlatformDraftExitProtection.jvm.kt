package io.github.smiling_pixel.draft

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformDraftExitProtection(
    guard: EditorExitGuard?,
    onBackRequest: (() -> Unit)?,
) = Unit
