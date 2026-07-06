package io.github.smiling_pixel.sync

import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.model.DiaryEntry

/**
 * A local file selected for diary entry import.
 *
 * @property name User-visible file name for reporting invalid or skipped files.
 * @property content Raw file content.
 */
data class DiaryEntryImportFile(
    val name: String,
    val content: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiaryEntryImportFile) return false
        if (name != other.name) return false
        return content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

/**
 * A diary entry import conflict with an existing local entry.
 *
 * @property importedEntry Entry decoded from the selected import files.
 * @property existingEntry Existing local entry with the same sync identifier.
 * @property sourceFileName File name that produced the imported entry.
 */
data class DiaryEntryImportConflict(
    val importedEntry: DiaryEntry,
    val existingEntry: DiaryEntry,
    val sourceFileName: String,
)

/**
 * Preview of decoded diary entries before they are written to local storage.
 *
 * @property newEntries Entries that do not conflict with local entries.
 * @property conflicts Imported entries whose sync identifiers already exist locally.
 * @property invalidFileNames Files that could not be decoded as diary entry payloads.
 * @property duplicateFileNames Import files skipped because a newer file with the same sync identifier was selected.
 */
data class DiaryEntryImportPreview(
    val newEntries: List<DiaryEntry>,
    val conflicts: List<DiaryEntryImportConflict>,
    val invalidFileNames: List<String>,
    val duplicateFileNames: List<String>,
) {
    /**
     * Returns whether this preview contains any entry that can be imported.
     */
    val hasImportableEntries: Boolean
        get() = newEntries.isNotEmpty() || conflicts.isNotEmpty()
}

/**
 * Result of applying a diary entry import preview to local storage.
 *
 * @property inserted Number of new entries inserted.
 * @property updated Number of conflicting entries updated.
 * @property skippedConflicts Number of conflicting entries left unchanged.
 * @property ignoredInvalidFiles Number of selected files that could not be imported.
 * @property skippedDuplicateFiles Number of selected files skipped because a newer duplicate was selected.
 */
data class DiaryEntryImportResult(
    val inserted: Int,
    val updated: Int,
    val skippedConflicts: Int,
    val ignoredInvalidFiles: Int,
    val skippedDuplicateFiles: Int,
) {
    /**
     * Returns whether local storage changed during this import.
     */
    val changedEntries: Int
        get() = inserted + updated
}

/**
 * Builds a diary entry import preview from selected local files and current local entries.
 *
 * @param files Selected local files.
 * @param localEntries Current local diary entries.
 * @return A preview containing importable entries, conflicts, and skipped file details.
 */
fun previewDiaryEntryImport(
    files: List<DiaryEntryImportFile>,
    localEntries: List<DiaryEntry>,
): DiaryEntryImportPreview {
    val invalidFileNames = mutableListOf<String>()
    val duplicateFileNames = mutableListOf<String>()
    val newestBySyncId = linkedMapOf<String, Pair<DiaryEntry, String>>()

    for (file in files) {
        val decoded = decodeEntryForSync(file.content, emptyImportEntry())
        if (decoded == null) {
            invalidFileNames += file.name
            continue
        }

        val existing = newestBySyncId[decoded.syncId]
        if (existing == null) {
            newestBySyncId[decoded.syncId] = decoded to file.name
        } else if (decoded.updatedAt > existing.first.updatedAt) {
            duplicateFileNames += existing.second
            newestBySyncId[decoded.syncId] = decoded to file.name
        } else {
            duplicateFileNames += file.name
        }
    }

    val localBySyncId = localEntries.associateBy { it.syncId }
    val newEntries = mutableListOf<DiaryEntry>()
    val conflicts = mutableListOf<DiaryEntryImportConflict>()

    for ((syncId, imported) in newestBySyncId) {
        val existing = localBySyncId[syncId]
        if (existing == null) {
            newEntries += imported.first
        } else {
            conflicts +=
                DiaryEntryImportConflict(
                    importedEntry = imported.first,
                    existingEntry = existing,
                    sourceFileName = imported.second,
                )
        }
    }

    return DiaryEntryImportPreview(
        newEntries = newEntries,
        conflicts = conflicts,
        invalidFileNames = invalidFileNames,
        duplicateFileNames = duplicateFileNames,
    )
}

/**
 * Applies a diary entry import preview to local storage.
 *
 * @param preview Import preview to apply.
 * @param repo Diary repository to update.
 * @param overrideConflicts Whether conflicting local entries should be overwritten.
 * @return Counts describing the completed import.
 */
suspend fun applyDiaryEntryImport(
    preview: DiaryEntryImportPreview,
    repo: DiaryRepository,
    overrideConflicts: Boolean,
): DiaryEntryImportResult {
    var inserted = 0
    var updated = 0

    for (entry in preview.newEntries) {
        repo.insert(entry.copy(id = 0))
        inserted++
    }

    if (overrideConflicts) {
        for (conflict in preview.conflicts) {
            repo.update(conflict.importedEntry.copy(id = conflict.existingEntry.id))
            updated++
        }
    }

    val skippedConflicts =
        if (overrideConflicts) {
            0
        } else {
            preview.conflicts.size
        }

    return DiaryEntryImportResult(
        inserted = inserted,
        updated = updated,
        skippedConflicts = skippedConflicts,
        ignoredInvalidFiles = preview.invalidFileNames.size,
        skippedDuplicateFiles = preview.duplicateFileNames.size,
    )
}

private fun emptyImportEntry(): DiaryEntry =
    DiaryEntry(
        id = 0,
        title = "",
        content = "",
    )
