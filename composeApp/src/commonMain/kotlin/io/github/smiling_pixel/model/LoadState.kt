package io.github.smiling_pixel.model

/**
 * Represents the lifecycle of content loaded from a local repository.
 *
 * @param T Loaded value type.
 */
sealed interface LoadState<out T> {
    /** Content is being loaded for the first time. */
    data object Loading : LoadState<Nothing>

    /** Content was loaded successfully. */
    data class Content<T>(val value: T) : LoadState<T>

    /** Content could not be loaded. */
    data class Error(
        val message: String,
        val technicalDetails: String? = null,
    ) : LoadState<Nothing>
}
