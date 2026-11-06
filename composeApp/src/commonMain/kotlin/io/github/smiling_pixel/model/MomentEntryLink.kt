package io.github.smiling_pixel.model

/**
 * Device-local relationship between a Moment and a diary entry.
 *
 * @property fileId Local identifier of the Moment.
 * @property entrySyncId Stable identifier of the related diary entry.
 */
data class MomentEntryLink(
    val fileId: Long,
    val entrySyncId: String,
)
