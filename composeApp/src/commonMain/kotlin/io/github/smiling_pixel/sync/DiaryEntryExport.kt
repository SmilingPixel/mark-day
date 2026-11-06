package io.github.smiling_pixel.sync

import io.github.smiling_pixel.model.DiaryEntry
import kotlin.time.ExperimentalTime

/**
 * Represents the result of exporting diary entries as sync-compatible files.
 */
sealed interface DiaryEntryExportResult {
    /**
     * Indicates that diary entries were exported successfully.
     *
     * @property fileCount Number of diary entry files exported.
     * @property destinationDescription User-readable destination description.
     */
    data class Success(
        val fileCount: Int,
        val destinationDescription: String,
    ) : DiaryEntryExportResult

    /**
     * Indicates that there are no diary entries available to export.
     */
    data object NoEntries : DiaryEntryExportResult

    /**
     * Indicates that diary entry export is not available on the current platform.
     */
    data object Unavailable : DiaryEntryExportResult

    /**
     * Indicates that diary entry export failed.
     *
     * @property message A user-readable failure message.
     */
    data class Failure(
        val message: String,
    ) : DiaryEntryExportResult
}

/**
 * Exports active diary entries as the same human-readable files used by cloud sync.
 *
 * @param entries Current local diary entries to export.
 * @return The export result.
 */
suspend fun exportDiaryEntries(entries: List<DiaryEntry>): DiaryEntryExportResult {
    val files = buildDiaryEntryExportFiles(entries)
    if (files.isEmpty()) {
        return DiaryEntryExportResult.NoEntries
    }
    return writeDiaryEntryExportFiles(files)
}

@OptIn(ExperimentalTime::class)
internal fun buildDiaryEntryExportFiles(entries: List<DiaryEntry>): List<DiaryEntryExportFile> =
    entries
        .sortedWith(
            compareBy<DiaryEntry> { it.entryDate }
                .thenBy { it.updatedAt }
                .thenBy { it.syncId },
        ).map { entry ->
            DiaryEntryExportFile(
                fileName = buildSyncEntryFileName(entry.syncId, entry.updatedAt.toEpochMilliseconds()),
                content = encodeEntryForSync(entry),
            )
        }

internal data class DiaryEntryExportFile(
    val fileName: String,
    val content: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiaryEntryExportFile) return false
        if (fileName != other.fileName) return false
        return content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

internal expect suspend fun writeDiaryEntryExportFiles(files: List<DiaryEntryExportFile>): DiaryEntryExportResult
