package io.github.smiling_pixel

// Simple data model for a diary entry used for testing
data class DiaryEntry(val id: Int, val title: String, val content: String)

// Hardcoded sample entries for development/testing
val sampleEntries = listOf(
    DiaryEntry(1, "Morning run", "Had a brisk 5km run. Weather was crisp and clear."),
    DiaryEntry(2, "Work notes", "Implemented the login flow and fixed two bugs."),
    DiaryEntry(3, "Dinner", "Tried a new pasta recipe — turned out great!"),
    DiaryEntry(4, "Reading", "Finished a short story collection about city life."),
    DiaryEntry(5, "Thoughts", "Planning a small weekend trip to recharge."),
)
