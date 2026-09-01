package io.github.smiling_pixel.filesystem

import io.github.smiling_pixel.database.IFileMetadataDao
import io.github.smiling_pixel.model.FileMetadata
import io.github.smiling_pixel.model.LoadState
import io.github.smiling_pixel.model.MomentEntryLink
import io.github.smiling_pixel.util.Logger
import io.github.smiling_pixel.util.d
import io.github.smiling_pixel.util.generateSyncId
import io.github.smiling_pixel.util.i
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Maximum accepted size of an individual Moment. */
const val MAX_MOMENT_BYTES: Long = 500L * 1024L * 1024L

/** Size above which import requires explicit confirmation. */
const val LARGE_MOMENT_WARNING_BYTES: Long = 100L * 1024L * 1024L

/** Prefix that identifies raw files exclusively owned by the Moments repository. */
const val MOMENT_STORAGE_PREFIX = "moment_"

/**
 * Files staged for reversible deletion.
 *
 * Raw bytes remain untouched until [FileRepository.finalizeDelete] is called.
 *
 * @property token Opaque identifier for the staged operation.
 * @property files Metadata hidden while the operation can still be undone.
 * @property links Relationships removed if deletion is finalized.
 */
data class PendingMomentDeletion(
    val token: String,
    val files: List<FileMetadata>,
    val links: List<MomentEntryLink>,
)

/**
 * Owns local Moment bytes, metadata, entry links, maintenance, and reversible deletion.
 *
 * @param fileManager Platform storage for raw bytes.
 * @param metadataDao Metadata and relationship persistence.
 * @param scope Application-lifetime scope used to observe and reconcile state.
 */
class FileRepository(
    private val fileManager: FileManager,
    private val metadataDao: IFileMetadataDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    /** Observes all persisted Moments. */
    val files: Flow<List<FileMetadata>> = metadataDao.getAllFiles()

    /** Observes all device-local Moment relationships. */
    val links: Flow<List<MomentEntryLink>> = metadataDao.getAllLinks()

    private val _filesState = MutableStateFlow<LoadState<List<FileMetadata>>>(LoadState.Loading)
    private val _pendingDeletionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val mutationMutex = Mutex()
    private var pendingDeletion: PendingMomentDeletion? = null

    /** Emits loading, content, or error state for file metadata. */
    val filesState: StateFlow<LoadState<List<FileMetadata>>> = _filesState

    /** IDs hidden while a confirmed delete can still be undone. */
    val pendingDeletionIds: StateFlow<Set<Long>> = _pendingDeletionIds

    init {
        scope.launch {
            try {
                metadataDao.getAllFiles().collect { value ->
                    _filesState.value = LoadState.Content(value)
                }
            } catch (e: Exception) {
                Logger.e("MomentsRepository", "load_failed type=${e::class.simpleName}")
                _filesState.value = LoadState.Error("Moments could not be loaded.", "Storage read failed.")
            }
        }
        scope.launch {
            runCatching { reconcileStorage() }
                .onFailure { Logger.e("MomentsCleanup", "startup_failed type=${it::class.simpleName}") }
        }
    }

    /**
     * Saves [content] under a generated storage key and returns its persisted metadata.
     *
     * @param fileName Original user-visible filename.
     * @param content Raw file bytes.
     * @param tags Optional labels.
     * @param mimeType Best-effort source MIME type.
     * @return Persisted metadata containing its assigned local identifier.
     */
    suspend fun saveFile(
        fileName: String,
        content: ByteArray,
        tags: List<String> = emptyList(),
        mimeType: String = inferMimeType(fileName),
    ): FileMetadata =
        mutationMutex.withLock {
            require(content.size.toLong() <= MAX_MOMENT_BYTES) { "File exceeds the Moments size limit." }
            val storageKey = newStorageKey(fileName)
            fileManager.save(storageKey, content)
            val metadata =
                FileMetadata(
                    originalFileName = fileName.ifBlank { "Untitled file" },
                    filePath = storageKey,
                    tags = tags,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    mimeType = mimeType.ifBlank { inferMimeType(fileName) },
                    sizeBytes = content.size.toLong(),
                )
            try {
                val id = metadataDao.insertFile(metadata)
                metadata.copy(id = id)
            } catch (e: Exception) {
                runCatching { fileManager.delete(storageKey) }
                    .onFailure { Logger.w("MomentsImport", "rollback_failed type=${it::class.simpleName}") }
                Logger.e("MomentsImport", "metadata_write_failed type=${e::class.simpleName}")
                throw e
            }
        }

    /** Returns raw bytes for [fileMetadata], or null when they are missing. */
    suspend fun getFileContent(fileMetadata: FileMetadata): ByteArray? = fileManager.read(fileMetadata.filePath)

    /** Returns whether raw bytes for [fileMetadata] currently exist. */
    suspend fun fileExists(fileMetadata: FileMetadata): Boolean = fileManager.exists(fileMetadata.filePath)

    /** Returns Moment IDs currently linked to [entrySyncId]. */
    suspend fun getFileIdsForEntry(entrySyncId: String): Set<Long> = metadataDao.getFileIdsForEntry(entrySyncId)

    /** Returns entry IDs currently linked to [fileId]. */
    suspend fun getEntrySyncIdsForFile(fileId: Long): Set<String> = metadataDao.getEntrySyncIdsForFile(fileId)

    /** Replaces all Moment links for an existing entry. */
    suspend fun replaceLinksForEntry(
        entrySyncId: String,
        fileIds: Set<Long>,
    ) {
        metadataDao.replaceLinksForEntry(entrySyncId, fileIds)
        Logger.i("MomentLinks", "links_replaced count=${fileIds.size}")
    }

    /** Returns links that must be restored if entry deletion is undone. */
    suspend fun snapshotLinksForEntries(entrySyncIds: Set<String>): List<MomentEntryLink> =
        metadataDao.getAllLinks().first().filter { it.entrySyncId in entrySyncIds }

    /** Restores previously captured entry relationships. */
    suspend fun restoreLinks(links: List<MomentEntryLink>) {
        metadataDao.restoreLinks(links)
        Logger.i("MomentLinks", "links_restored count=${links.size}")
    }

    /** Removes relationships for a permanently deleted entry. */
    suspend fun deleteLinksForEntry(entrySyncId: String) = metadataDao.deleteLinksForEntry(entrySyncId)

    /**
     * Hides [fileIds] and returns a token that may be undone or finalized.
     *
     * Any older staged operation is finalized first so only one Undo window owns repository state.
     */
    suspend fun stageDelete(fileIds: Set<Long>): PendingMomentDeletion =
        mutationMutex.withLock {
            pendingDeletion?.let { finalizeDeleteLocked(it.token) }
            val selected = metadataDao.getAllFiles().first().filter { it.id in fileIds }
            val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
            val selectedLinks = metadataDao.getAllLinks().first().filter { it.fileId in selectedIds }
            PendingMomentDeletion(generateSyncId(), selected, selectedLinks).also {
                pendingDeletion = it
                _pendingDeletionIds.value = selectedIds
                Logger.d("MomentsRepository", "delete_staged count=${selected.size}")
            }
        }

    /** Cancels the staged deletion identified by [token]. */
    suspend fun undoDelete(token: String): Boolean =
        mutationMutex.withLock {
            if (pendingDeletion?.token != token) return@withLock false
            pendingDeletion = null
            _pendingDeletionIds.value = emptySet()
            Logger.d("MomentsRepository", "delete_undone")
            true
        }

    /** Requests Undo on the repository's application-lifetime scope. */
    fun requestUndoDelete(token: String) {
        scope.launch { undoDelete(token) }
    }

    /** Permanently deletes the staged operation identified by [token]. */
    suspend fun finalizeDelete(token: String): Boolean = mutationMutex.withLock { finalizeDeleteLocked(token) }

    /** Requests permanent deletion on the repository's application-lifetime scope. */
    fun requestFinalizeDelete(token: String) {
        scope.launch { finalizeDelete(token) }
    }

    /** Immediately deletes one Moment. Prefer staged deletion for user actions. */
    suspend fun deleteFile(fileMetadata: FileMetadata) {
        val staged = stageDelete(setOf(fileMetadata.id))
        finalizeDelete(staged.token)
    }

    /**
     * Repairs legacy metadata and removes only unreferenced repository-owned raw files.
     *
     * Missing metadata rows remain visible so users can see and delete the failed item.
     */
    suspend fun reconcileStorage(validEntrySyncIds: Set<String>? = null) {
        mutationMutex.withLock {
            var relocated = 0
            var backfilled = 0
            val current = metadataDao.getAllFiles().first()
            for (metadata in current) {
                var updated = metadata
                if (!metadata.filePath.startsWith(MOMENT_STORAGE_PREFIX) && fileManager.exists(metadata.filePath)) {
                    val destination = newStorageKey(metadata.originalFileName)
                    fileManager.move(metadata.filePath, destination)
                    updated = updated.copy(filePath = destination)
                    relocated++
                }
                if (updated.sizeBytes < 0 && fileManager.exists(updated.filePath)) {
                    updated =
                        updated.copy(
                            sizeBytes = fileManager.getSize(updated.filePath),
                            mimeType = inferMimeType(updated.originalFileName),
                        )
                    backfilled++
                }
                if (updated != metadata) metadataDao.updateFile(updated)
            }
            val referenced = metadataDao.getAllFiles().first().mapTo(mutableSetOf()) { it.filePath }
            val orphans = fileManager.list().filter { it.startsWith(MOMENT_STORAGE_PREFIX) && it !in referenced }
            orphans.forEach { fileManager.delete(it) }
            validEntrySyncIds?.let { metadataDao.deleteDanglingLinks(it) }
            Logger.i(
                "MomentsCleanup",
                "complete relocated=$relocated backfilled=$backfilled removed=${orphans.size}",
            )
        }
    }

    private suspend fun finalizeDeleteLocked(token: String): Boolean {
        val operation = pendingDeletion?.takeIf { it.token == token } ?: return false
        pendingDeletion = null
        _pendingDeletionIds.value = emptySet()
        operation.files.forEach { metadataDao.deleteFile(it) }
        var rawFailures = 0
        operation.files.forEach {
            runCatching { fileManager.delete(it.filePath) }
                .onFailure { rawFailures++ }
        }
        Logger.i(
            "MomentsRepository",
            "delete_finalized count=${operation.files.size} rawFailures=$rawFailures",
        )
        return true
    }
}

/** Returns a best-effort MIME type derived only from [fileName]. */
fun inferMimeType(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "txt", "md", "csv", "json" -> "text/plain"
        "mp3", "wav", "ogg", "m4a" -> "audio/*"
        "mp4", "webm", "mov", "mkv" -> "video/*"
        "zip", "rar", "7z", "tar", "gz" -> "application/zip"
        else -> "application/octet-stream"
    }

private fun newStorageKey(originalFileName: String): String {
    val extension =
        originalFileName
            .substringAfterLast('.', "")
            .lowercase()
            .filter(Char::isLetterOrDigit)
            .take(10)
    return buildString {
        append(MOMENT_STORAGE_PREFIX)
        append(generateSyncId().replace("-", ""))
        if (extension.isNotEmpty()) append('.').append(extension)
    }
}
