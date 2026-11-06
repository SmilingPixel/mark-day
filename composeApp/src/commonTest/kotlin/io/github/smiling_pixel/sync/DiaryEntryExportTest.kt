package io.github.smiling_pixel.sync

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DiaryEntryExportTest {
    @Test
    fun exportDiaryEntriesReturnsNoEntriesForEmptyList() =
        runTest {
            val result = exportDiaryEntries(emptyList())

            assertEquals(DiaryEntryExportResult.NoEntries, result)
        }

    @Test
    fun buildDiaryEntryExportFilesUsesSyncFileNameAndPayload() {
        val entry =
            diaryEntry(
                syncId = "123e4567-e89b-12d3-a456-426614174000",
                updatedAtMillis = 2_000,
            )

        val files = buildDiaryEntryExportFiles(listOf(entry))

        assertEquals(1, files.size)
        assertEquals("markday_entry_123e4567-e89b-12d3-a456-426614174000_2000.txt", files.first().fileName)

        val decoded = decodeEntryForSync(files.first().content, entry.copy(id = 0, title = "", content = ""))
        assertNotNull(decoded)
        assertEquals(entry.syncId, decoded.syncId)
        assertEquals(entry.title, decoded.title)
        assertEquals(entry.content, decoded.content)
        assertEquals(entry.entryDate, decoded.entryDate)
    }

    @Test
    fun buildDiaryEntryExportFilesSortsDeterministically() {
        val secondBySyncId =
            diaryEntry(
                syncId = "223e4567-e89b-12d3-a456-426614174000",
                updatedAtMillis = 2_000,
                entryDate = LocalDate.parse("2026-03-31"),
            )
        val firstByDate =
            diaryEntry(
                syncId = "323e4567-e89b-12d3-a456-426614174000",
                updatedAtMillis = 4_000,
                entryDate = LocalDate.parse("2026-03-30"),
            )
        val firstBySyncId =
            diaryEntry(
                syncId = "123e4567-e89b-12d3-a456-426614174000",
                updatedAtMillis = 2_000,
                entryDate = LocalDate.parse("2026-03-31"),
            )
        val lastByUpdatedAt =
            diaryEntry(
                syncId = "023e4567-e89b-12d3-a456-426614174000",
                updatedAtMillis = 3_000,
                entryDate = LocalDate.parse("2026-03-31"),
            )

        val files =
            buildDiaryEntryExportFiles(
                listOf(secondBySyncId, firstByDate, lastByUpdatedAt, firstBySyncId),
            )

        assertEquals(
            listOf(
                "markday_entry_323e4567-e89b-12d3-a456-426614174000_4000.txt",
                "markday_entry_123e4567-e89b-12d3-a456-426614174000_2000.txt",
                "markday_entry_223e4567-e89b-12d3-a456-426614174000_2000.txt",
                "markday_entry_023e4567-e89b-12d3-a456-426614174000_3000.txt",
            ),
            files.map { it.fileName },
        )
    }

    @Test
    fun buildSyncEntryFileNameMatchesCloudSyncConvention() {
        val fileName = buildSyncEntryFileName("123e4567-e89b-12d3-a456-426614174000", 1_234)

        assertEquals("markday_entry_123e4567-e89b-12d3-a456-426614174000_1234.txt", fileName)
    }

    private fun diaryEntry(
        syncId: String,
        updatedAtMillis: Long,
        entryDate: LocalDate = LocalDate.parse("2026-03-31"),
    ): DiaryEntry =
        DiaryEntry(
            id = 7,
            syncId = syncId,
            title = "Trip",
            content = "Line 1\nLine 2",
            createdAt = Instant.fromEpochMilliseconds(1_000),
            updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
            entryDate = entryDate,
            weatherCondition = "Sunny",
            minTemperature = 10.5,
            maxTemperature = 20.5,
        )
}
