package io.github.smiling_pixel.draft

/**
 * Allows app and platform navigation to protect the latest editor values before leaving.
 *
 * @property hasUnpersistedChanges Whether the current form differs from the last durable snapshot.
 * @property persistLatest Immediately persists the current form and reports whether it succeeded.
 * @property requestClose Requests the editor's normal protected Back behavior.
 */
class EditorExitGuard(
    val hasUnpersistedChanges: Boolean,
    val persistLatest: suspend () -> Boolean,
    val requestClose: () -> Unit,
)
