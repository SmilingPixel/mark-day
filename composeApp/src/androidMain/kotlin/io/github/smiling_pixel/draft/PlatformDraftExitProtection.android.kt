package io.github.smiling_pixel.draft

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformDraftExitProtection(guard: EditorExitGuard?) {
    BackHandler(enabled = guard != null) {
        guard?.requestClose?.invoke()
    }
}
