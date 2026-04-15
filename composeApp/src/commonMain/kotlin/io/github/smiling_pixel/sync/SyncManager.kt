package io.github.smiling_pixel.sync

import io.github.smiling_pixel.client.CloudDriveClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.preference.SettingsRepository
import io.github.smiling_pixel.preference.getSettingsRepository
import io.github.smiling_pixel.util.generateSyncId
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
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

    val remoteFiles = client.listFiles(parentId).filter {
        it.name.startsWith(SYNC_ENTRY_FILE_PREFIX) || it.name.startsWith(SYNC_TOMBSTONE_FILE_PREFIX)
    }
    val remoteFileMap = mutableMapOf<String, Pair<io.github.smiling_pixel.client.DriveFile, Long>>()
    val remoteTombstoneMap = mutableMapOf<String, Pair<io.github.smiling_pixel.client.DriveFile, Long>>()
    for (f in remoteFiles) {
        val parsedEntry = parseRemoteEntryFileName(f.name)
        if (parsedEntry != null) {
            val (syncId, timestamp) = parsedEntry
            val existing = remoteFileMap[syncId]
            if (existing == null || existing.second < timestamp) {
                remoteFileMap[syncId] = Pair(f, timestamp)
            }
            continue
        }

        val parsedTombstone = parseRemoteTombstoneFileName(f.name)
        if (parsedTombstone != null) {
            val (syncId, deletedAtEpochMillis) = parsedTombstone
            val existing = remoteTombstoneMap[syncId]
            if (existing == null || existing.second < deletedAtEpochMillis) {
                remoteTombstoneMap[syncId] = Pair(f, deletedAtEpochMillis)
            }
        }
    }

    var uploaded = 0
    var downloaded = 0
    var unchanged = 0

    val localEntriesBySyncId = localEntries.associateBy { it.syncId }.toMutableMap()
    val localTombstones = loadLocalDeletionTombstones(settings).toMutableMap()

    // Apply remote tombstones before reconciling entries to avoid resurrecting deleted notes.
    for ((syncId, tombstoneData) in remoteTombstoneMap) {
        val remoteDeletedAt = tombstoneData.second
        val localEntry = localEntriesBySyncId[syncId]
        if (localEntry != null && localEntry.updatedAt.toEpochMilliseconds() <= remoteDeletedAt) {
            repo.delete(localEntry, recordSyncTombstone = false)
            localEntriesBySyncId.remove(syncId)
            downloaded++
        }

        val localDeletedAt = localTombstones[syncId]
        if (localDeletedAt != null && localDeletedAt <= remoteDeletedAt) {
            localTombstones.remove(syncId)
        }

        val remoteEntry = remoteFileMap[syncId]
        if (remoteEntry != null && remoteEntry.second <= remoteDeletedAt) {
            runCatching { client.deleteFile(remoteEntry.first.id) }
            remoteFileMap.remove(syncId)
        }
    }

    val emptyEntryLocal = DiaryEntry(id = 0, syncId = generateSyncId(), title = "", content = "")

    for (local in localEntriesBySyncId.values) {
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

    for ((syncId, deletedAtEpochMillis) in localTombstones.toMap()) {
        val remoteEntry = remoteFileMap[syncId]
        if (remoteEntry != null) {
            if (remoteEntry.second <= deletedAtEpochMillis) {
                runCatching { client.deleteFile(remoteEntry.first.id) }
                remoteFileMap.remove(syncId)
            } else {
                localTombstones.remove(syncId)
                continue
            }
        }

        val remoteTombstone = remoteTombstoneMap[syncId]
        if (remoteTombstone == null || remoteTombstone.second < deletedAtEpochMillis) {
            val name = buildRemoteTombstoneFileName(syncId, deletedAtEpochMillis)
            val created = client.createFile(
                name,
                encodeTombstoneForSync(syncId, deletedAtEpochMillis),
                SYNC_ENTRY_MIME_TYPE,
                parentId
            )
            if (remoteTombstone != null) {
                runCatching { client.deleteFile(remoteTombstone.first.id) }
            }
            remoteTombstoneMap[syncId] = Pair(created, deletedAtEpochMillis)
            uploaded++
        }

        localTombstones.remove(syncId)
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

    saveLocalDeletionTombstones(settings, localTombstones)

    // TODO: Optimise the synchronization process in the future (e.g., batch operations, or more efficient incremental sync).
    return SyncResult(uploaded, downloaded, unchanged)
}

@OptIn(ExperimentalTime::class)
suspend fun recordLocalDeletionTombstone(syncId: String, deletedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds()) {
    if (!looksLikeUuid(syncId)) return

    val settings = getSettingsRepository()
    val tombstones = loadLocalDeletionTombstones(settings).toMutableMap()
    val existing = tombstones[syncId]
    if (existing == null || existing < deletedAtEpochMillis) {
        tombstones[syncId] = deletedAtEpochMillis
        saveLocalDeletionTombstones(settings, tombstones)
    }
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
    return "${SYNC_ENTRY_FILE_PREFIX}${syncId}_${timestampMillis}${SYNC_ENTRY_FILE_EXTENSION}"
}

private fun buildRemoteTombstoneFileName(syncId: String, timestampMillis: Long): String {
    return "${SYNC_TOMBSTONE_FILE_PREFIX}${syncId}_${timestampMillis}${SYNC_ENTRY_FILE_EXTENSION}"
}

private fun parseRemoteEntryFileName(fileName: String): Pair<String, Long>? {
    if (!fileName.startsWith(SYNC_ENTRY_FILE_PREFIX) || !fileName.endsWith(SYNC_ENTRY_FILE_EXTENSION)) return null

    val core = fileName.removePrefix(SYNC_ENTRY_FILE_PREFIX).removeSuffix(SYNC_ENTRY_FILE_EXTENSION)

    val separatorIndex = core.lastIndexOf('_')
    if (separatorIndex <= 0 || separatorIndex == core.lastIndex) return null

    val syncId = core.substring(0, separatorIndex)
    val timestamp = core.substring(separatorIndex + 1).toLongOrNull() ?: return null

    // Legacy numeric-ID files are intentionally ignored in this migration phase.
    if (!looksLikeUuid(syncId)) return null

    return syncId to timestamp
}

private fun parseRemoteTombstoneFileName(fileName: String): Pair<String, Long>? {
    if (!fileName.startsWith(SYNC_TOMBSTONE_FILE_PREFIX) || !fileName.endsWith(SYNC_ENTRY_FILE_EXTENSION)) return null

    val core = fileName.removePrefix(SYNC_TOMBSTONE_FILE_PREFIX).removeSuffix(SYNC_ENTRY_FILE_EXTENSION)

    val separatorIndex = core.lastIndexOf('_')
    if (separatorIndex <= 0 || separatorIndex == core.lastIndex) return null

    val syncId = core.substring(0, separatorIndex)
    val timestamp = core.substring(separatorIndex + 1).toLongOrNull() ?: return null
    if (!looksLikeUuid(syncId)) return null

    return syncId to timestamp
}

private fun looksLikeUuid(value: String): Boolean {
    val pattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    return pattern.matches(value)
}

private const val SYNC_ENTRY_FILE_EXTENSION = ".txt"
private const val SYNC_ENTRY_FILE_PREFIX = "markday_entry_"
private const val SYNC_TOMBSTONE_FILE_PREFIX = "markday_tombstone_"
private const val SYNC_ENTRY_MIME_TYPE = "text/plain"

private val syncPayloadJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
private data class SyncDeletionTombstonePayload(
    val syncId: String,
    val deletedAtEpochMillis: Long,
)

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

private suspend fun loadLocalDeletionTombstones(settings: SettingsRepository): Map<String, Long> {
    val raw = settings.cloudSyncDeletionTombstonesJson.first() ?: return emptyMap()
    return try {
        val payloads = syncPayloadJson.decodeFromString(
            ListSerializer(SyncDeletionTombstonePayload.serializer()),
            raw
        )
        payloads
            .filter { looksLikeUuid(it.syncId) }
            .associate { it.syncId to it.deletedAtEpochMillis }
    } catch (_: Exception) {
        emptyMap()
    }
}

private suspend fun saveLocalDeletionTombstones(settings: SettingsRepository, tombstones: Map<String, Long>) {
    if (tombstones.isEmpty()) {
        settings.setCloudSyncDeletionTombstonesJson(null)
        return
    }

    val payloads = tombstones
        .entries
        .sortedBy { it.key }
        .map { SyncDeletionTombstonePayload(syncId = it.key, deletedAtEpochMillis = it.value) }
    val json = syncPayloadJson.encodeToString(ListSerializer(SyncDeletionTombstonePayload.serializer()), payloads)
    settings.setCloudSyncDeletionTombstonesJson(json)
}

private fun encodeTombstoneForSync(syncId: String, deletedAtEpochMillis: Long): ByteArray {
    val payload = SyncDeletionTombstonePayload(syncId = syncId, deletedAtEpochMillis = deletedAtEpochMillis)
    return syncPayloadJson.encodeToString(SyncDeletionTombstonePayload.serializer(), payload).encodeToByteArray()
}