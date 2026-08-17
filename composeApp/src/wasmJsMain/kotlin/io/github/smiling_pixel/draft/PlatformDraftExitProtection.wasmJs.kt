package io.github.smiling_pixel.draft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.window
import org.w3c.dom.events.Event

@Composable
actual fun PlatformDraftExitProtection(
    guard: EditorExitGuard?,
    onBackRequest: (() -> Unit)?,
) {
    DisposableEffect(guard?.hasUnpersistedChanges) {
        if (guard?.hasUnpersistedChanges != true) {
            return@DisposableEffect onDispose {}
        }
        // Browsers do not allow custom unload text. preventDefault requests the browser-provided confirmation and the
        // listener is removed as soon as the latest snapshot becomes durable, avoiding prompts for saved drafts.
        val listener: (Event) -> Unit = { event -> event.preventDefault() }
        window.addEventListener("beforeunload", listener)
        onDispose { window.removeEventListener("beforeunload", listener) }
    }
}
