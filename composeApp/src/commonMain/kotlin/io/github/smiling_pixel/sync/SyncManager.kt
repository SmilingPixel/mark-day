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
    val unchanged: Int,
    val warnings: List<String> = emptyList(),
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
    localEntries: List<DiaryEntry>,
    settings: SettingsRepository = getSettingsRepository(),
): SyncResult {
    val isEnabled = settings.isCloudSyncEnabled.first()
    if (!isEnabled) {
        throw Exception("Cloud Sync is disabled. Please enable it in Settings.")
    }

    if (!client.isAuthorized()) {
        throw Exception("Not connected to Google Drive. Please connect in Settings.")
    }

    val syncPath = settings.cloudSyncPath.first()
    val parentId = getOrCreateFolderByPath(client, syncPath)

    val remoteFiles =
        client.listFiles(parentId).filter {
            it.name.startsWith(SYNC_ENTRY_FILE_PREFIX) || it.name.startsWith(SYNC_TOMBSTONE_FILE_PREFIX)
        }
    val remoteFileMap = mutableMapOf<String, Pair<io.github.smiling_pixel.client.DriveFile, Long>>()
    val remoteFileVersions = mutableMapOf<String, MutableList<Pair<io.github.smiling_pixel.client.DriveFile, Long>>>()
    val remoteTombstoneMap = mutableMapOf<String, Pair<io.github.smiling_pixel.client.DriveFile, Long>>()
    for (f in remoteFiles) {
        val parsedEntry = parseRemoteEntryFileName(f.name)
        if (parsedEntry != null) {
            val (syncId, timestamp) = parsedEntry
            remoteFileVersions.getOrPut(syncId) { mutableListOf() }.add(Pair(f, timestamp))
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
    val warnings = mutableListOf<String>()

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

        val remoteEntries = remoteFileVersions[syncId].orEmpty()
        for ((driveFile, remoteUpdatedAt) in remoteEntries) {
            if (remoteUpdatedAt <= remoteDeletedAt) {
                runCatching { client.deleteFile(driveFile.id) }
            }
        }
        remoteFileVersions.remove(syncId)
        remoteFileMap.remove(syncId)
    }

    val emptyEntryLocal = DiaryEntry(id = 0, syncId = generateSyncId(), title = "", content = "")

    for (local in localEntriesBySyncId.values) {
        val remote = remoteFileMap[local.syncId]
        val localTime = local.updatedAt.toEpochMilliseconds()

        if (remote != null) {
            val (driveFile, remoteTime) = remote
            if (localTime > remoteTime) {
                val name = buildSyncEntryFileName(local.syncId, localTime)
                client.createFile(name, encodeEntryForSync(local), SYNC_ENTRY_MIME_TYPE, parentId)
                // Delete old files only after successful upload to avoid losing the only remote copy.
                val oldVersions = remoteFileVersions[local.syncId].orEmpty()
                for ((oldDriveFile, oldRemoteTime) in oldVersions) {
                    if (oldRemoteTime <= remoteTime) {
                        runCatching { client.deleteFile(oldDriveFile.id) }
                    }
                }
                uploaded++
            } else if (remoteTime > localTime) {
                val remoteContent = client.downloadFile(driveFile.id)
                val parsed = decodeEntryForSync(remoteContent, local)
                if (parsed != null) {
                    repo.update(parsed)
                    downloaded++
                } else {
                    val legacyName = quarantineLegacyFile(client, driveFile, remoteContent, parentId)
                    warnings += "Quarantined unrecognized remote file \"${driveFile.name}\" as \"$legacyName\""
                }
            } else {
                unchanged++
            }
            remoteFileMap.remove(local.syncId)
        } else {
            val name = buildSyncEntryFileName(local.syncId, localTime)
            client.createFile(name, encodeEntryForSync(local), SYNC_ENTRY_MIME_TYPE, parentId)
            uploaded++
        }
    }

    for ((syncId, deletedAtEpochMillis) in localTombstones.toMap()) {
        val remoteEntries = remoteFileVersions[syncId].orEmpty()
        val newestRemoteEntryTimestamp = remoteEntries.maxOfOrNull { it.second }
        if (newestRemoteEntryTimestamp != null && newestRemoteEntryTimestamp > deletedAtEpochMillis) {
            localTombstones.remove(syncId)
            continue
        }

        for ((driveFile, remoteTimestamp) in remoteEntries) {
            if (remoteTimestamp <= deletedAtEpochMillis) {
                runCatching { client.deleteFile(driveFile.id) }
            }
        }
        remoteFileVersions.remove(syncId)
        remoteFileMap.remove(syncId)

        val remoteTombstone = remoteTombstoneMap[syncId]
        if (remoteTombstone == null || remoteTombstone.second < deletedAtEpochMillis) {
            val name = buildRemoteTombstoneFileName(syncId, deletedAtEpochMillis)
            val created =
                client.createFile(
                    name,
                    encodeTombstoneForSync(syncId, deletedAtEpochMillis),
                    SYNC_ENTRY_MIME_TYPE,
                    parentId,
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
        } else {
            val legacyName = quarantineLegacyFile(client, driveFile, remoteContent, parentId)
            warnings += "Quarantined unrecognized remote file \"${driveFile.name}\" as \"$legacyName\""
        }
    }

    saveLocalDeletionTombstones(settings, localTombstones)

    // TODO: Optimise the synchronization process in the future (e.g., batch operations, or more efficient incremental sync).
    return SyncResult(uploaded, downloaded, unchanged, warnings.toList())
}

@OptIn(ExperimentalTime::class)
suspend fun recordLocalDeletionTombstone(
    syncId: String,
    deletedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    settings: SettingsRepository = getSettingsRepository(),
) {
    if (!looksLikeUuid(syncId)) return

    val tombstones = loadLocalDeletionTombstones(settings).toMutableMap()
    val existing = tombstones[syncId]
    if (existing == null || existing < deletedAtEpochMillis) {
        tombstones[syncId] = deletedAtEpochMillis
        saveLocalDeletionTombstones(settings, tombstones)
    }
}

/**
 * Clears a local deletion tombstone for an entry that has been restored.
 *
 * @param syncId Stable cross-device entry identifier.
 * @param settings Settings repository containing the persisted tombstones.
 */
suspend fun clearLocalDeletionTombstone(
    syncId: String,
    settings: SettingsRepository = getSettingsRepository(),
) {
    val tombstones = loadLocalDeletionTombstones(settings).toMutableMap()
    if (tombstones.remove(syncId) != null) {
        saveLocalDeletionTombstones(settings, tombstones)
    }
}

private suspend fun getOrCreateFolderByPath(
    client: CloudDriveClient,
    path: String,
): String? {
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

/**
 * Builds the cloud-sync file name used for a diary entry payload.
 *
 * @param syncId Stable cross-device entry identifier.
 * @param timestampMillis Entry update timestamp in epoch milliseconds.
 * @return The sync-compatible entry file name.
 */
internal fun buildSyncEntryFileName(
    syncId: String,
    timestampMillis: Long,
): String = "${SYNC_ENTRY_FILE_PREFIX}${syncId}_${timestampMillis}${SYNC_ENTRY_FILE_EXTENSION}"

private fun buildRemoteTombstoneFileName(
    syncId: String,
    timestampMillis: Long,
): String = "${SYNC_TOMBSTONE_FILE_PREFIX}${syncId}_${timestampMillis}${SYNC_ENTRY_FILE_EXTENSION}"

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
private const val SYNC_LEGACY_FILE_PREFIX = "markday_legacy_"
private const val SYNC_ENTRY_MIME_TYPE = "text/plain"

private val syncPayloadJson =
    Json {
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
    val payload =
        SyncEntryPayload(
            syncId = entry.syncId,
            title = entry.title,
            createdAtEpochMillis = entry.createdAt.toEpochMilliseconds(),
            updatedAtEpochMillis = entry.updatedAt.toEpochMilliseconds(),
            entryDateIso = entry.entryDate.toString(),
            weatherCondition = entry.weatherCondition,
            minTemperature = entry.minTemperature,
            maxTemperature = entry.maxTemperature,
            content = entry.content,
        )
    return syncPayloadJson.encodeToString(SyncEntryPayload.serializer(), payload).encodeToByteArray()
}

@OptIn(ExperimentalTime::class)
internal fun decodeEntryForSync(
    bytes: ByteArray,
    original: DiaryEntry,
): DiaryEntry? {
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
            content = content,
        )
    } catch (e: Exception) {
        return null
    }
}

private suspend fun loadLocalDeletionTombstones(settings: SettingsRepository): Map<String, Long> {
    val raw = settings.cloudSyncDeletionTombstonesJson.first() ?: return emptyMap()
    return try {
        val payloads =
            syncPayloadJson.decodeFromString(
                ListSerializer(SyncDeletionTombstonePayload.serializer()),
                raw,
            )
        payloads
            .filter { looksLikeUuid(it.syncId) }
            .associate { it.syncId to it.deletedAtEpochMillis }
    } catch (_: Exception) {
        emptyMap()
    }
}

private suspend fun saveLocalDeletionTombstones(
    settings: SettingsRepository,
    tombstones: Map<String, Long>,
) {
    if (tombstones.isEmpty()) {
        settings.setCloudSyncDeletionTombstonesJson(null)
        return
    }

    val payloads =
        tombstones
            .entries
            .sortedBy { it.key }
            .map { SyncDeletionTombstonePayload(syncId = it.key, deletedAtEpochMillis = it.value) }
    val json = syncPayloadJson.encodeToString(ListSerializer(SyncDeletionTombstonePayload.serializer()), payloads)
    settings.setCloudSyncDeletionTombstonesJson(json)
}

private fun encodeTombstoneForSync(
    syncId: String,
    deletedAtEpochMillis: Long,
): ByteArray {
    val payload = SyncDeletionTombstonePayload(syncId = syncId, deletedAtEpochMillis = deletedAtEpochMillis)
    return syncPayloadJson.encodeToString(SyncDeletionTombstonePayload.serializer(), payload).encodeToByteArray()
}

/**
 * Renames a remote file that cannot be decoded (e.g., legacy format) to a quarantine prefix
 * so it is not re-downloaded on every subsequent sync.
 *
 * @return the new quarantine file name (even if the operations fail).
 */
private suspend fun quarantineLegacyFile(
    client: CloudDriveClient,
    driveFile: io.github.smiling_pixel.client.DriveFile,
    content: ByteArray,
    parentId: String?,
): String {
    val legacyName = driveFile.name.replaceFirst(SYNC_ENTRY_FILE_PREFIX, SYNC_LEGACY_FILE_PREFIX)
    runCatching {
        client.createFile(legacyName, content, SYNC_ENTRY_MIME_TYPE, parentId)
        client.deleteFile(driveFile.id)
    }
    return legacyName
}
