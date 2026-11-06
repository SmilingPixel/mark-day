package io.github.smiling_pixel.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generates a UUID v4 string used as the stable cross-device synchronization identifier.
 */
@OptIn(ExperimentalUuidApi::class)
fun generateSyncId(): String = Uuid.random().toString()
