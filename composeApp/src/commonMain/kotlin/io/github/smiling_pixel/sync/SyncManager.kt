package io.github.smiling_pixel.sync

import io.github.smiling_pixel.client.CloudDriveClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.preference.getSettingsRepository
import io.github.smiling_pixel.util.generateSyncId
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant as KotlinTimeInstant

/**
 * Result counters returned by [performCloudSync].
 */
data class SyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val unchanged: Int
)

/**
 * Synchronizes local diary entries with cloud files.
 *
 * Matching is keyed by [DiaryEntry.syncId], a stable cross-device identifier.
 * Local database IDs are intentionally not used for cloud matching.
 */
@OptIn(ExperimentalTime::class)
suspend fun performCloudSync(
    client: CloudDriveClient,
    repo: DiaryRepository,
    localEntries: List<DiaryEntry>
): SyncResult {
    val settings = getSettingsRepository()
    val isEnabled = settings.isCloudSyncEnabled.first()
    if (!isEnabled) {
        throw Exception("Cloud Sync is disabled. Please enable it in Settings.")
    }

    if (!client.isAuthorized()) {
        throw Exception("Not connected to Google Drive. Please connect in Settings.")
    }

    val syncPath = settings.cloudSyncPath.first()
    val parentId = getOrCreateFolderByPath(client, syncPath)

    val remoteFiles = client.listFiles(parentId).filter { it.name.startsWith("markday_entry_") }
    val remoteFileMap = mutableMapOf<String, Pair<io.github.smiling_pixel.client.DriveFile, Long>>()
    for (f in remoteFiles) {
        val parsed = parseRemoteEntryFileName(f.name)
        if (parsed != null) {
            val (syncId, timestamp) = parsed
            val existing = remoteFileMap[syncId]
            if (existing == null || existing.second < timestamp) {
                remoteFileMap[syncId] = Pair(f, timestamp)
            }
        }
    }

    var uploaded = 0
    var downloaded = 0
    var unchanged = 0

    val emptyEntryLocal = DiaryEntry(id = 0, syncId = generateSyncId(), title = "", content = "")

    for (local in localEntries) {
        val remote = remoteFileMap[local.syncId]
        val localTime = local.updatedAt.toEpochMilliseconds()

        if (remote != null) {
            val (driveFile, remoteTime) = remote
            if (localTime > remoteTime) {
                val name = buildRemoteEntryFileName(local.syncId, localTime)
                client.createFile(name, encodeEntryForSync(local), SYNC_ENTRY_MIME_TYPE, parentId)
                // Delete old file only after successful upload to avoid losing the only remote copy.
                runCatching { client.deleteFile(driveFile.id) }
                uploaded++
            } else if (remoteTime > localTime) {
                val remoteContent = client.downloadFile(driveFile.id)
                val parsed = decodeEntryForSync(remoteContent, local)
                if (parsed != null) {
                    repo.update(parsed)
                    downloaded++
                }
            } else {
                unchanged++
            }
            remoteFileMap.remove(local.syncId)
        } else {
            val name = buildRemoteEntryFileName(local.syncId, localTime)
            client.createFile(name, encodeEntryForSync(local), SYNC_ENTRY_MIME_TYPE, parentId)
            uploaded++
        }
    }

    // For remote files that don't exist locally, download them
    for ((_, remoteData) in remoteFileMap) {
        val (driveFile, _) = remoteData
        val remoteContent = client.downloadFile(driveFile.id)
        val parsed = decodeEntryForSync(remoteContent, emptyEntryLocal)
        if (parsed != null) {
            repo.insert(parsed)
            downloaded++
        }
    }

    // TODO: Optimise the synchronization process in the future (e.g., batch operations, or more efficient incremental sync).
    return SyncResult(uploaded, downloaded, unchanged)
}

private suspend fun getOrCreateFolderByPath(client: CloudDriveClient, path: String): String? {
    val folders = path.split("/").filter { it.isNotBlank() }
    if (folders.isEmpty()) return null
    
    var currentParentId: String? = null
    for (folderName in folders) {
        val files = client.listFiles(currentParentId)
        var folder = files.firstOrNull { it.name == folderName && it.isFolder }
        if (folder == null) {
            folder = client.createFolder(folderName, currentParentId)
        }
        currentParentId = folder.id
    }
    return currentParentId
}

private fun buildRemoteEntryFileName(syncId: String, timestampMillis: Long): String {
    return "markday_entry_${syncId}_${timestampMillis}${SYNC_ENTRY_FILE_EXTENSION}"
}

private fun parseRemoteEntryFileName(fileName: String): Pair<String, Long>? {
    if (!fileName.startsWith("markday_entry_") || !fileName.endsWith(SYNC_ENTRY_FILE_EXTENSION)) return null

    val core = fileName.removePrefix("markday_entry_").removeSuffix(SYNC_ENTRY_FILE_EXTENSION)

    val separatorIndex = core.lastIndexOf('_')
    if (separatorIndex <= 0 || separatorIndex == core.lastIndex) return null

    val syncId = core.substring(0, separatorIndex)
    val timestamp = core.substring(separatorIndex + 1).toLongOrNull() ?: return null

    // Legacy numeric-ID files are intentionally ignored in this migration phase.
    if (!looksLikeUuid(syncId)) return null

    return syncId to timestamp
}

private fun looksLikeUuid(value: String): Boolean {
    val pattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    return pattern.matches(value)
}

private const val SYNC_ENTRY_FILE_EXTENSION = ".txt"
private const val SYNC_ENTRY_MIME_TYPE = "text/plain"

private val syncPayloadJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
private data class SyncEntryPayload(
    val syncId: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val entryDateIso: String,
    val weatherCondition: String? = null,
    val minTemperature: Double? = null,
    val maxTemperature: Double? = null,
    val content: String,
)

@OptIn(ExperimentalTime::class)
internal fun encodeEntryForSync(entry: DiaryEntry): ByteArray {
    val payload = SyncEntryPayload(
        syncId = entry.syncId,
        title = entry.title,
        createdAtEpochMillis = entry.createdAt.toEpochMilliseconds(),
        updatedAtEpochMillis = entry.updatedAt.toEpochMilliseconds(),
        entryDateIso = entry.entryDate.toString(),
        weatherCondition = entry.weatherCondition,
        minTemperature = entry.minTemperature,
        maxTemperature = entry.maxTemperature,
        content = entry.content
    )
    return syncPayloadJson.encodeToString(SyncEntryPayload.serializer(), payload).encodeToByteArray()
}

@OptIn(ExperimentalTime::class)
internal fun decodeEntryForSync(bytes: ByteArray, original: DiaryEntry): DiaryEntry? {
    try {
        val text = bytes.decodeToString()
        val payload = syncPayloadJson.decodeFromString(SyncEntryPayload.serializer(), text)
        val syncId = payload.syncId
        if (!looksLikeUuid(syncId)) return null

        val title = payload.title
        val createdAt = KotlinTimeInstant.fromEpochMilliseconds(payload.createdAtEpochMillis)
        val updatedAt = KotlinTimeInstant.fromEpochMilliseconds(payload.updatedAtEpochMillis)
        val entryDate = LocalDate.parse(payload.entryDateIso)
        val weatherCondition = payload.weatherCondition
        val minTemperature = payload.minTemperature
        val maxTemperature = payload.maxTemperature
        val content = payload.content
        return original.copy(
            syncId = syncId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            entryDate = entryDate,
            weatherCondition = weatherCondition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature,
            content = content
        )
    } catch (e: Exception) {
        return decodeLegacyLineDelimitedEntryForSync(bytes, original)
    }
}

@OptIn(ExperimentalTime::class)
private fun decodeLegacyLineDelimitedEntryForSync(bytes: ByteArray, original: DiaryEntry): DiaryEntry? {
    return try {
        val text = bytes.decodeToString()
        val lines = text.lines()
        if (lines.size < 9) return null

        val syncId = lines[0]
        if (!looksLikeUuid(syncId)) return null

        val title = lines[1]
        val createdAt = KotlinTimeInstant.fromEpochMilliseconds(lines[2].toLong())
        val updatedAt = KotlinTimeInstant.fromEpochMilliseconds(lines[3].toLong())
        val entryDate = LocalDate.parse(lines[4])
        val weatherCondition = lines[5].takeIf { it.isNotEmpty() }
        val minTemperature = lines[6].toDoubleOrNull()
        val maxTemperature = lines[7].toDoubleOrNull()
        var contentLines = lines.drop(8)
        if (contentLines.isNotEmpty() && contentLines.last() == "") {
            contentLines = contentLines.dropLast(1)
        }
        val content = contentLines.joinToString("\n")
        original.copy(
            syncId = syncId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            entryDate = entryDate,
            weatherCondition = weatherCondition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature,
            content = content
        )
    } catch (e: Exception) {
        null
    }
}