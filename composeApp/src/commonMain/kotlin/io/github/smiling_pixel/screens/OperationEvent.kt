package io.github.smiling_pixel.screens

/**
 * A user-facing result emitted by a screen operation.
 *
 * @property message Plain-language message shown in the snackbar.
 * @property technicalDetails Optional diagnostic text shown from Details.
 * @property retry Optional action that repeats the failed operation.
 * @property actionLabel Optional snackbar action label for non-retry operations.
 * @property action Optional action invoked when [actionLabel] is selected.
 * @property onDismiss Optional callback invoked only when the snackbar is dismissed without its action.
 */
data class OperationEvent(
    val message: String,
    val technicalDetails: String? = null,
    val retry: (() -> Unit)? = null,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null,
)
