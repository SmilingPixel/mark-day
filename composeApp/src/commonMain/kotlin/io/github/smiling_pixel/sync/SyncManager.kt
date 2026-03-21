package io.github.smiling_pixel.sync

import io.github.smiling_pixel.client.CloudDriveClient
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.preference.getSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant as KotlinTimeInstant

data class SyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val unchanged: Int
)

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
    val remoteFileMap = mutableMapOf<Int, Pair<io.github.smiling_pixel.client.DriveFile, Long>>()
    for (f in remoteFiles) {
        try {
            // format: markday_entry_<id>_<timestamp>.json
            val parts = f.name.removeSuffix(".json").split("_")
            val id = parts[2].toInt()
            val timestamp = parts[3].toLong()
            val existing = remoteFileMap[id]
            if (existing == null || existing.second < timestamp) {
                remoteFileMap[id] = Pair(f, timestamp)
            }
        } catch (e: Exception) {
            // ignore malformed
        }
    }

    var uploaded = 0
    var downloaded = 0
    var unchanged = 0

    val emptyEntryLocal = DiaryEntry(-1, "", "")

    for (local in localEntries) {
        val remote = remoteFileMap[local.id]
        val localTime = local.updatedAt.toEpochMilliseconds()
        
        if (remote != null) {
            val (driveFile, remoteTime) = remote
            if (localTime > remoteTime) {
                client.deleteFile(driveFile.id)
                val name = "markday_entry_${local.id}_${localTime}.json"
                client.createFile(name, encodeEntryForSync(local), "application/json", parentId)
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
            remoteFileMap.remove(local.id)
        } else {
            val name = "markday_entry_${local.id}_${localTime}.json"
            client.createFile(name, encodeEntryForSync(local), "application/json", parentId)
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

// TODO: better encode/decode solution in the future (e.g., JSON or protobuf) to handle edge cases and be more robust.@SmilingPixel
@OptIn(ExperimentalTime::class)
internal fun encodeEntryForSync(entry: DiaryEntry): ByteArray {
    val builder = StringBuilder()
    builder.appendLine(entry.title)
    builder.appendLine(entry.createdAt.toEpochMilliseconds().toString())
    builder.appendLine(entry.updatedAt.toEpochMilliseconds().toString())
    builder.appendLine(entry.entryDate.toString())
    builder.appendLine(entry.weatherCondition ?: "")
    builder.appendLine(entry.minTemperature?.toString() ?: "")
    builder.appendLine(entry.maxTemperature?.toString() ?: "")
    builder.appendLine(entry.content)
    return builder.toString().encodeToByteArray()
}

@OptIn(ExperimentalTime::class)
internal fun decodeEntryForSync(bytes: ByteArray, original: DiaryEntry): DiaryEntry? {
    try {
        val text = bytes.decodeToString()
        val lines = text.lines()
        if (lines.size < 8) return null
        val title = lines[0]
        val createdAt = KotlinTimeInstant.fromEpochMilliseconds(lines[1].toLong())
        val updatedAt = KotlinTimeInstant.fromEpochMilliseconds(lines[2].toLong())
        val entryDate = LocalDate.parse(lines[3])
        val weatherCondition = lines[4].takeIf { it.isNotEmpty() }
        val minTemperature = lines[5].toDoubleOrNull()
        val maxTemperature = lines[6].toDoubleOrNull()
        var contentLines = lines.drop(7)
        if (contentLines.isNotEmpty() && contentLines.last() == "") {
            contentLines = contentLines.dropLast(1)
        }
        val content = contentLines.joinToString("\n")
        return original.copy(
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            entryDate = entryDate,
            weatherCondition = weatherCondition,
            minTemperature = minTemperature,
            maxTemperature = maxTemperature,
            content = content
        )
    } catch(e: Exception) {
        return null
    }
}