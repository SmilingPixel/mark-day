package io.github.smiling_pixel.search

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.datetime.LocalDate

internal const val DEFAULT_SEARCH_PREVIEW_LENGTH = 160

internal enum class EntrySortField {
    DIARY_DATE,
    CREATED_AT,
    UPDATED_AT,
}

internal data class EntrySearchCriteria(
    val query: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val sortField: EntrySortField = EntrySortField.DIARY_DATE,
) {
    val normalizedQuery: String
        get() = query.trim()

    val hasValidDateRange: Boolean
        get() = startDate == null || endDate == null || startDate <= endDate
}

internal data class SearchTextRange(
    val start: Int,
    val endExclusive: Int,
)

internal data class SearchTextPreview(
    val text: String,
    val matches: List<SearchTextRange>,
)

internal fun searchEntries(
    entries: List<DiaryEntry>,
    criteria: EntrySearchCriteria,
): List<DiaryEntry> {
    if (!criteria.hasValidDateRange) return emptyList()

    val query = criteria.normalizedQuery
    val matchingEntries =
        entries.filter { entry ->
            val matchesQuery =
                query.isEmpty() ||
                    entry.title.contains(query, ignoreCase = true) ||
                    entry.content.contains(query, ignoreCase = true)
            val isOnOrAfterStart = criteria.startDate?.let { entry.entryDate >= it } ?: true
            val isOnOrBeforeEnd = criteria.endDate?.let { entry.entryDate <= it } ?: true
            matchesQuery && isOnOrAfterStart && isOnOrBeforeEnd
        }

    val comparator =
        when (criteria.sortField) {
            EntrySortField.DIARY_DATE -> compareByDescending<DiaryEntry> { it.entryDate }
            EntrySortField.CREATED_AT -> compareByDescending { it.createdAt }
            EntrySortField.UPDATED_AT -> compareByDescending { it.updatedAt }
        }.thenByDescending { it.updatedAt }
            .thenBy { it.syncId }

    return matchingEntries.sortedWith(comparator)
}

internal fun findCaseInsensitiveMatches(
    text: String,
    query: String,
): List<SearchTextRange> {
    val normalizedQuery = query.trim()
    if (text.isEmpty() || normalizedQuery.isEmpty()) return emptyList()

    val matches = mutableListOf<SearchTextRange>()
    var searchFrom = 0
    while (searchFrom <= text.length - normalizedQuery.length) {
        val matchStart = text.indexOf(normalizedQuery, startIndex = searchFrom, ignoreCase = true)
        if (matchStart < 0) break
        val matchEnd = matchStart + normalizedQuery.length
        matches += SearchTextRange(matchStart, matchEnd)
        searchFrom = matchStart + 1
    }
    return matches
}

internal fun buildSearchPreview(
    text: String,
    query: String,
    maxLength: Int = DEFAULT_SEARCH_PREVIEW_LENGTH,
): SearchTextPreview {
    require(maxLength > 0) { "Preview length must be positive" }
    if (text.isEmpty()) return SearchTextPreview("", emptyList())

    val normalizedQuery = query.trim()
    val firstMatch =
        if (normalizedQuery.isEmpty()) {
            -1
        } else {
            text.indexOf(normalizedQuery, ignoreCase = true)
        }
    val windowLength = maxOf(maxLength, normalizedQuery.length)
    val start =
        if (firstMatch < 0) {
            0
        } else {
            (firstMatch - (windowLength - normalizedQuery.length) / 2)
                .coerceIn(0, (text.length - windowLength).coerceAtLeast(0))
        }
    val end = (start + windowLength).coerceAtMost(text.length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < text.length) "..." else ""
    val visibleContent = text.substring(start, end)
    val preview = prefix + visibleContent + suffix

    return SearchTextPreview(
        text = preview,
        matches =
            findCaseInsensitiveMatches(visibleContent, normalizedQuery).map { match ->
                SearchTextRange(
                    start = match.start + prefix.length,
                    endExclusive = match.endExclusive + prefix.length,
                )
            },
    )
}
