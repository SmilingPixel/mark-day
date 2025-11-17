package io.github.smiling_pixel.model

// Simple data model for a diary entry used for testing (no Room annotations in commonMain)
data class DiaryEntry(
    val id: Int,
    val title: String,
    val content: String
)
