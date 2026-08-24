package io.github.smiling_pixel.screens

/**
 * A user-facing result emitted by a screen operation.
 *
 * @property message Plain-language message shown in the snackbar.
 * @property technicalDetails Optional diagnostic text shown from Details.
 * @property retry Optional action that repeats the failed operation.
 */
data class OperationEvent(
    val message: String,
    val technicalDetails: String? = null,
    val retry: (() -> Unit)? = null,
)
