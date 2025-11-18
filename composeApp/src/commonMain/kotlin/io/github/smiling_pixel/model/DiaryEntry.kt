package io.github.smiling_pixel.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Simple data model for a diary entry used for testing (no Room annotations in commonMain)
data class DiaryEntry(
    val id: Int,
    val title: String,
    val content: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
)
