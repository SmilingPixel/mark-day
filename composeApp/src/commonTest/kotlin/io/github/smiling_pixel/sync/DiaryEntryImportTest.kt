package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.database.InMemoryDiaryDao
import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class DiaryEntryImportTest {
    @Test
    fun emptySelectionProducesNoImportableEntries() {
        val preview = previewDiaryEntryImport(emptyList(), emptyList())

        assertFalse(preview.hasImportableEntries)
        assertEquals(emptyList(), preview.newEntries)
        assertEquals(emptyList(), preview.conflicts)
        assertEquals(emptyList(), preview.invalidFileNames)
    }

    @Test
    fun validExportedPayloadImportsAsNewEntry() =
        runTest {
            val entry = diaryEntry(syncId = "123e4567-e89b-12d3-a456-426614174000", title = "Imported")
            val preview = previewDiaryEntryImport(listOf(importFile(entry)), emptyList())
            val dao = InMemoryDiaryDao()
            val repo = DiaryRepository(dao)

            val result = applyDiaryEntryImport(preview, repo, overrideConflicts = false)

            assertEquals(1, result.inserted)
            assertEquals(0, result.updated)
            assertEquals(entry.syncId, dao.getAll().single().syncId)
            assertEquals("Imported", dao.getAll().single().title)
        }

    @Test
    fun invalidPayloadsAreIgnoredAndCounted() {
        val preview =
            previewDiaryEntryImport(
                listOf(DiaryEntryImportFile("not-an-entry.txt", "not json".encodeToByteArray())),
                emptyList(),
            )

        assertFalse(preview.hasImportableEntries)
        assertEquals(listOf("not-an-entry.txt"), preview.invalidFileNames)
    }

    @Test
    fun conflictIsSkippedWhenOverrideIsFalse() =
        runTest {
            val existing = diaryEntry(id = 9, syncId = "123e4567-e89b-12d3-a456-426614174000", title = "Local")
            val imported = existing.copy(id = 0, title = "Imported", updatedAt = Instant.fromEpochMilliseconds(2_000))
            val preview = previewDiaryEntryImport(listOf(importFile(imported)), listOf(existing))
            val dao = InMemoryDiaryDao(listOf(existing))
            val repo = DiaryRepository(dao)

            val result = applyDiaryEntryImport(preview, repo, overrideConflicts = false)

            assertEquals(0, result.inserted)
            assertEquals(0, result.updated)
            assertEquals(1, result.skippedConflicts)
            assertEquals("Local", dao.getAll().single().title)
        }

    @Test
    fun conflictIsUpdatedWhenOverrideIsTrueAndPreservesLocalId() =
        runTest {
            val existing = diaryEntry(id = 9, syncId = "123e4567-e89b-12d3-a456-426614174000", title = "Local")
            val imported = existing.copy(id = 0, title = "Imported", updatedAt = Instant.fromEpochMilliseconds(2_000))
            val preview = previewDiaryEntryImport(listOf(importFile(imported)), listOf(existing))
            val dao = InMemoryDiaryDao(listOf(existing))
            val repo = DiaryRepository(dao)

            val result = applyDiaryEntryImport(preview, repo, overrideConflicts = true)
            val saved = dao.getAll().single()

            assertEquals(0, result.inserted)
            assertEquals(1, result.updated)
            assertEquals(0, result.skippedConflicts)
            assertEquals(9, saved.id)
            assertEquals("Imported", saved.title)
        }

    @Test
    fun duplicateImportedSyncIdsKeepNewestUpdatedAt() {
        val syncId = "123e4567-e89b-12d3-a456-426614174000"
        val older = diaryEntry(syncId = syncId, title = "Older", updatedAtMillis = 1_000)
        val newer = diaryEntry(syncId = syncId, title = "Newer", updatedAtMillis = 2_000)

        val preview = previewDiaryEntryImport(listOf(importFile(older), importFile(newer)), emptyList())

        assertEquals(1, preview.newEntries.size)
        assertEquals("Newer", preview.newEntries.single().title)
        assertEquals(listOf("markday_entry_123e4567-e89b-12d3-a456-426614174000_1000.txt"), preview.duplicateFileNames)
    }

    @Test
    fun mixedBatchImportsNewEntriesAndSkipsConflictsWhenOverrideDeclined() =
        runTest {
            val existing = diaryEntry(id = 5, syncId = "123e4567-e89b-12d3-a456-426614174000", title = "Local")
            val conflict = existing.copy(id = 0, title = "Imported")
            val newEntry = diaryEntry(syncId = "223e4567-e89b-12d3-a456-426614174000", title = "New")
            val preview = previewDiaryEntryImport(listOf(importFile(conflict), importFile(newEntry)), listOf(existing))
            val dao = InMemoryDiaryDao(listOf(existing))
            val repo = DiaryRepository(dao)

            val result = applyDiaryEntryImport(preview, repo, overrideConflicts = false)
            val saved = dao.getAll().sortedBy { it.syncId }

            assertEquals(1, result.inserted)
            assertEquals(1, result.skippedConflicts)
            assertEquals(listOf("Local", "New"), saved.map { it.title })
        }

    private fun importFile(entry: DiaryEntry): DiaryEntryImportFile {
        val file = buildDiaryEntryExportFiles(listOf(entry)).single()
        return DiaryEntryImportFile(file.fileName, file.content)
    }

    private fun diaryEntry(
        id: Int = 0,
        syncId: String,
        title: String = "Trip",
        updatedAtMillis: Long = 1_000,
    ): DiaryEntry =
        DiaryEntry(
            id = id,
            syncId = syncId,
            title = title,
            content = "Line 1\nLine 2",
            createdAt = Instant.fromEpochMilliseconds(500),
            updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
            entryDate = LocalDate.parse("2026-03-31"),
            weatherCondition = "Sunny",
            minTemperature = 10.5,
            maxTemperature = 20.5,
        )
}
