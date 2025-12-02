package io.github.smiling_pixel.model

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

// Simple data model for a diary entry used for testing (no Room annotations in commonMain)
data class DiaryEntry(
    val id: Int,
    val title: String,
    val content: String,
    @OptIn(ExperimentalTime::class)
    val createdAt: Instant = Clock.System.now(),
    @OptIn(ExperimentalTime::class)
    val updatedAt: Instant = createdAt,
    val weatherCondition: String? = null,
    val minTemperature: Double? = null,
    val maxTemperature: Double? = null,
)
