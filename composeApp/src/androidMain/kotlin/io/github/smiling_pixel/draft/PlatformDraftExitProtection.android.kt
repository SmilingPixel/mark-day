package io.github.smiling_pixel.draft

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformDraftExitProtection(
    guard: EditorExitGuard?,
    onBackRequest: (() -> Unit)?,
) {
    BackHandler(enabled = guard != null || onBackRequest != null) {
        guard?.requestClose?.invoke() ?: onBackRequest?.invoke()
    }
}
