package io.github.smiling_pixel.search

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class EntrySearchTest {
    @Test
    fun exactPhraseMatchesTitleOrContentIgnoringCase() {
        val titleMatch = entry(id = 1, title = "Summer Trip")
        val contentMatch = entry(id = 2, content = "Our SUMMER TRIP began early.")
        val separatedWords = entry(id = 3, content = "Summer was the best part of the trip.")
        val partialPhrase = entry(id = 4, content = "The summer began early.")

        val results =
            searchEntries(
                listOf(titleMatch, contentMatch, separatedWords, partialPhrase),
                EntrySearchCriteria(" summer trip "),
            )

        assertEquals(setOf(titleMatch.syncId, contentMatch.syncId), results.map { it.syncId }.toSet())
    }

    @Test
    fun emptyQueryMatchesAllEntries() {
        val entries = listOf(entry(id = 1), entry(id = 2))

        assertEquals(2, searchEntries(entries, EntrySearchCriteria(query = "   ")).size)
    }

    @Test
    fun dateBoundsAreInclusiveAndCanBeOpenEnded() {
        val early = entry(id = 1, entryDate = LocalDate(2025, 1, 1))
        val middle = entry(id = 2, entryDate = LocalDate(2025, 1, 15))
        val late = entry(id = 3, entryDate = LocalDate(2025, 1, 31))

        val bounded =
            searchEntries(
                listOf(early, middle, late),
                EntrySearchCriteria(startDate = early.entryDate, endDate = middle.entryDate),
            )
        val startOnly =
            searchEntries(
                listOf(early, middle, late),
                EntrySearchCriteria(startDate = late.entryDate),
            )
        val endOnly =
            searchEntries(
                listOf(early, middle, late),
                EntrySearchCriteria(endDate = early.entryDate),
            )

        assertEquals(setOf(early.syncId, middle.syncId), bounded.map { it.syncId }.toSet())
        assertEquals(listOf(late.syncId), startOnly.map { it.syncId })
        assertEquals(listOf(early.syncId), endOnly.map { it.syncId })
    }

    @Test
    fun invalidDateRangeIsReportedAndReturnsNoResults() {
        val criteria =
            EntrySearchCriteria(
                startDate = LocalDate(2025, 2, 1),
                endDate = LocalDate(2025, 1, 1),
            )

        assertFalse(criteria.hasValidDateRange)
        assertTrue(searchEntries(listOf(entry(id = 1)), criteria).isEmpty())
    }

    @Test
    fun eachSortFieldUsesNewestFirst() {
        val first =
            entry(
                id = 1,
                entryDate = LocalDate(2025, 3, 1),
                createdAtMillis = 100,
                updatedAtMillis = 300,
            )
        val second =
            entry(
                id = 2,
                entryDate = LocalDate(2025, 2, 1),
                createdAtMillis = 300,
                updatedAtMillis = 100,
            )
        val third =
            entry(
                id = 3,
                entryDate = LocalDate(2025, 1, 1),
                createdAtMillis = 200,
                updatedAtMillis = 200,
            )
        val entries = listOf(third, first, second)

        assertEquals(
            listOf(first.syncId, second.syncId, third.syncId),
            searchEntries(entries, EntrySearchCriteria(sortField = EntrySortField.DIARY_DATE)).map { it.syncId },
        )
        assertEquals(
            listOf(second.syncId, third.syncId, first.syncId),
            searchEntries(entries, EntrySearchCriteria(sortField = EntrySortField.CREATED_AT)).map { it.syncId },
        )
        assertEquals(
            listOf(first.syncId, third.syncId, second.syncId),
            searchEntries(entries, EntrySearchCriteria(sortField = EntrySortField.UPDATED_AT)).map { it.syncId },
        )
    }

    @Test
    fun tiesUseUpdatedTimeThenStableSyncId() {
        val olderUpdate = entry(id = 1, syncId = "z", updatedAtMillis = 100)
        val laterSyncId = entry(id = 2, syncId = "b", updatedAtMillis = 200)
        val earlierSyncId = entry(id = 3, syncId = "a", updatedAtMillis = 200)

        val results = searchEntries(listOf(olderUpdate, laterSyncId, earlierSyncId), EntrySearchCriteria())

        assertEquals(listOf("a", "b", "z"), results.map { it.syncId })
    }

    @Test
    fun combinedCriteriaApplyBeforeSorting() {
        val matchingNewer =
            entry(
                id = 1,
                title = "A quiet day",
                entryDate = LocalDate(2025, 1, 20),
                updatedAtMillis = 300,
            )
        val matchingOlder =
            entry(
                id = 2,
                content = "Notes from a quiet day",
                entryDate = LocalDate(2025, 1, 10),
                updatedAtMillis = 100,
            )
        val outsideRange =
            entry(
                id = 3,
                title = "A quiet day",
                entryDate = LocalDate(2024, 12, 31),
                updatedAtMillis = 500,
            )

        val results =
            searchEntries(
                listOf(matchingOlder, outsideRange, matchingNewer),
                EntrySearchCriteria(
                    query = "quiet day",
                    startDate = LocalDate(2025, 1, 1),
                    sortField = EntrySortField.UPDATED_AT,
                ),
            )

        assertEquals(listOf(matchingNewer.syncId, matchingOlder.syncId), results.map { it.syncId })
    }

    @Test
    fun matchRangesIncludeEveryVisibleMixedCaseOccurrence() {
        val matches = findCaseInsensitiveMatches("Day by DAY by day", "day")

        assertEquals(
            listOf(SearchTextRange(0, 3), SearchTextRange(7, 10), SearchTextRange(14, 17)),
            matches,
        )
    }

    @Test
    fun matchRangesIncludeOverlappingOccurrences() {
        assertEquals(
            listOf(SearchTextRange(0, 2), SearchTextRange(1, 3), SearchTextRange(2, 4)),
            findCaseInsensitiveMatches("aaaa", "aa"),
        )
    }

    @Test
    fun previewCentersDistantMatchAndMarksIt() {
        val content = "a".repeat(200) + "Needle" + "b".repeat(200)

        val preview = buildSearchPreview(content, "needle", maxLength = 40)

        assertTrue(preview.text.startsWith("..."))
        assertTrue(preview.text.endsWith("..."))
        assertEquals("Needle", preview.matches.single().let { preview.text.substring(it.start, it.endExclusive) })
    }

    @Test
    fun previewDoesNotHighlightSyntheticTruncationMarkers() {
        val preview = buildSearchPreview("x".repeat(50) + "." + "y".repeat(50), ".", maxLength = 24)

        assertTrue(preview.text.startsWith("..."))
        assertTrue(preview.matches.all { preview.text.substring(it.start, it.endExclusive) == "." })
        assertTrue(preview.matches.none { it.start < 3 })
    }

    private fun entry(
        id: Int,
        syncId: String = "sync-$id",
        title: String = "Title $id",
        content: String = "Content $id",
        entryDate: LocalDate = LocalDate(2025, 1, 1),
        createdAtMillis: Long = 100,
        updatedAtMillis: Long = 100,
    ): DiaryEntry =
        DiaryEntry(
            id = id,
            syncId = syncId,
            title = title,
            content = content,
            entryDate = entryDate,
            createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
            updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
        )
}
