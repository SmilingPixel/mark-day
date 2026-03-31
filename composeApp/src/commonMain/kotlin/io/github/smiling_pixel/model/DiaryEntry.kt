package io.github.smiling_pixel.model

import io.github.smiling_pixel.util.generateSyncId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Shared diary entry model used across all active platforms.
 *
 * `id` is a local persistence identifier and is not stable across devices.
 * `syncId` is a stable cross-device identifier used for cloud synchronization.
 */
data class DiaryEntry(
    val id: Int,
    val syncId: String = generateSyncId(),
    val title: String,
    val content: String,
    @OptIn(ExperimentalTime::class)
    val createdAt: Instant = Clock.System.now(),
    @OptIn(ExperimentalTime::class)
    val updatedAt: Instant = createdAt,
    val entryDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val weatherCondition: String? = null,
    val minTemperature: Double? = null,
    val maxTemperature: Double? = null,
)
