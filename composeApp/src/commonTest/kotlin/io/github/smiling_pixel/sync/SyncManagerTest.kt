package io.github.smiling_pixel.sync

import io.github.smiling_pixel.client.CloudDriveClient
import io.github.smiling_pixel.client.DriveFile
import io.github.smiling_pixel.client.UserInfo
import io.github.smiling_pixel.database.DiaryRepository
import io.github.smiling_pixel.database.InMemoryDiaryDao
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.preference.SettingsRepository
import io.github.smiling_pixel.util.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SyncManagerTest {
    @Test
    fun localNewer_uploadsAndDeletesOldFileAfterCreate() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val syncId = "123e4567-e89b-12d3-a456-426614174000"
            val localEntry =
                diaryEntry(
                    syncId = syncId,
                    title = "Local",
                    content = "Local content",
                    createdAtMs = 1_000,
                    updatedAtMs = 2_000,
                )

            val dao = InMemoryDiaryDao(initial = listOf(localEntry))
            val repo = DiaryRepository(dao, settings = settings)

            val client = FakeCloudDriveClient(authorized = true)
            val oldRemote =
                diaryEntry(
                    syncId = syncId,
                    title = "Remote old",
                    content = "Old",
                    createdAtMs = 1_000,
                    updatedAtMs = 1_000,
                )
            val oldRemoteName = remoteEntryFileName(syncId, 1_000)
            client.seedFile(name = oldRemoteName, content = encodeEntryForSync(oldRemote))

            val result = performCloudSync(client, repo, dao.getAll(), settings)

            assertEquals(SyncResult(uploaded = 1, downloaded = 0, unchanged = 0), result)

            val remainingFiles = client.listFiles(null).filter { it.name.startsWith("markday_entry_") }
            assertEquals(1, remainingFiles.size)
            assertEquals(remoteEntryFileName(syncId, 2_000), remainingFiles.first().name)

            val createCallIndex = client.calls.indexOfFirst { it.startsWith("create:") }
            val deleteCallIndex = client.calls.indexOfFirst { it.startsWith("delete:") }
            assertTrue(createCallIndex >= 0)
            assertTrue(deleteCallIndex >= 0)
            assertTrue(createCallIndex < deleteCallIndex)
        }

    @Test
    fun localNewer_prunesAllOlderRemoteVersionsAfterCreate() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val syncId = "133e4567-e89b-12d3-a456-426614174000"
            val localEntry =
                diaryEntry(
                    syncId = syncId,
                    title = "Local",
                    content = "Local content",
                    createdAtMs = 1_000,
                    updatedAtMs = 3_000,
                )

            val dao = InMemoryDiaryDao(initial = listOf(localEntry))
            val repo = DiaryRepository(dao, settings = settings)

            val client = FakeCloudDriveClient(authorized = true)
            client.seedFile(
                name = remoteEntryFileName(syncId, 1_000),
                content = encodeEntryForSync(localEntry.copy(updatedAt = Instant.fromEpochMilliseconds(1_000))),
            )
            client.seedFile(
                name = remoteEntryFileName(syncId, 2_000),
                content = encodeEntryForSync(localEntry.copy(updatedAt = Instant.fromEpochMilliseconds(2_000))),
            )

            val result = performCloudSync(client, repo, dao.getAll(), settings)

            assertEquals(SyncResult(uploaded = 1, downloaded = 0, unchanged = 0), result)

            val entryFiles = client.listFiles(null).filter { it.name.startsWith("markday_entry_${syncId}_") }
            assertEquals(1, entryFiles.size)
            assertEquals(remoteEntryFileName(syncId, 3_000), entryFiles.first().name)
        }

    @Test
    fun remoteNewer_downloadsAndUpdatesLocalEntry() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val syncId = "223e4567-e89b-12d3-a456-426614174000"
            val localEntry =
                diaryEntry(
                    syncId = syncId,
                    title = "Local old",
                    content = "Local",
                    createdAtMs = 1_000,
                    updatedAtMs = 1_000,
                )
            val dao = InMemoryDiaryDao(initial = listOf(localEntry))
            val repo = DiaryRepository(dao, settings = settings)

            val remoteNew =
                diaryEntry(
                    syncId = syncId,
                    title = "Remote new",
                    content = "Remote",
                    createdAtMs = 1_000,
                    updatedAtMs = 3_000,
                )
            val client = FakeCloudDriveClient(authorized = true)
            client.seedFile(
                name = remoteEntryFileName(syncId, 3_000),
                content = encodeEntryForSync(remoteNew),
            )

            val result = performCloudSync(client, repo, dao.getAll(), settings)

            assertEquals(SyncResult(uploaded = 0, downloaded = 1, unchanged = 0), result)
            val saved = dao.getAll().first()
            assertEquals("Remote new", saved.title)
            assertEquals(3_000, saved.updatedAt.toEpochMilliseconds())
        }

    @Test
    fun remoteOnlyFile_downloadsAndInsertsLocally() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val syncId = "323e4567-e89b-12d3-a456-426614174000"
            val remoteEntry =
                diaryEntry(
                    syncId = syncId,
                    title = "Remote only",
                    content = "From cloud",
                    createdAtMs = 1_000,
                    updatedAtMs = 4_000,
                )

            val dao = InMemoryDiaryDao()
            val repo = DiaryRepository(dao, settings = settings)
            val client = FakeCloudDriveClient(authorized = true)
            client.seedFile(
                name = remoteEntryFileName(syncId, 4_000),
                content = encodeEntryForSync(remoteEntry),
            )

            val result = performCloudSync(client, repo, emptyList(), settings)

            assertEquals(SyncResult(uploaded = 0, downloaded = 1, unchanged = 0), result)
            val all = dao.getAll()
            assertEquals(1, all.size)
            assertEquals(syncId, all.first().syncId)
            assertEquals("Remote only", all.first().title)
        }

    @Test
    fun localDelete_uploadsTombstoneAndDeletesRemoteEntry() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val syncId = "333e4567-e89b-12d3-a456-426614174000"
            val localEntry =
                diaryEntry(
                    syncId = syncId,
                    title = "Local",
                    content = "Content",
                    createdAtMs = 1_000,
                    updatedAtMs = 2_000,
                )

            val dao = InMemoryDiaryDao(initial = listOf(localEntry))
            val repo = DiaryRepository(dao, settings = settings)
            repo.delete(localEntry)
            recordLocalDeletionTombstone(syncId = syncId, deletedAtEpochMillis = 2_000, settings = settings)

            val client = FakeCloudDriveClient(authorized = true)
            client.seedFile(
                name = remoteEntryFileName(syncId, 2_000),
                content = encodeEntryForSync(localEntry),
            )

            val result = performCloudSync(client, repo, dao.getAll(), settings)

            assertEquals(SyncResult(uploaded = 1, downloaded = 0, unchanged = 0), result)
            val entryFiles = client.listFiles(null).filter { it.name.startsWith("markday_entry_") }
            assertTrue(entryFiles.isEmpty())

            val tombstoneFiles = client.listFiles(null).filter { it.name.startsWith("markday_tombstone_") }
            assertEquals(1, tombstoneFiles.size)
            assertTrue(tombstoneFiles.first().name.startsWith("markday_tombstone_${syncId}_"))
        }

    @Test
    fun remoteTombstone_deletesLocalAndDoesNotRestore() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val syncId = "343e4567-e89b-12d3-a456-426614174000"
            val localEntry =
                diaryEntry(
                    syncId = syncId,
                    title = "Existing",
                    content = "Will be deleted",
                    createdAtMs = 1_000,
                    updatedAtMs = 2_000,
                )
            val dao = InMemoryDiaryDao(initial = listOf(localEntry))
            val repo = DiaryRepository(dao, settings = settings)

            val client = FakeCloudDriveClient(authorized = true)
            client.seedFile(
                name = remoteEntryFileName(syncId, 2_000),
                content = encodeEntryForSync(localEntry),
            )
            client.seedFile(
                name = remoteTombstoneFileName(syncId, 3_000),
                content = "{}".encodeToByteArray(),
            )

            val result = performCloudSync(client, repo, dao.getAll(), settings)

            assertEquals(SyncResult(uploaded = 0, downloaded = 1, unchanged = 0), result)
            assertTrue(dao.getAll().isEmpty())

            val remainingEntryFiles = client.listFiles(null).filter { it.name.startsWith("markday_entry_") }
            assertTrue(remainingEntryFiles.isEmpty())
        }

    @Test
    fun localNewer_whenCreateFails_keepsOldRemoteFile() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val syncId = "423e4567-e89b-12d3-a456-426614174000"
            val localEntry =
                diaryEntry(
                    syncId = syncId,
                    title = "Local",
                    content = "Local content",
                    createdAtMs = 1_000,
                    updatedAtMs = 2_000,
                )

            val dao = InMemoryDiaryDao(initial = listOf(localEntry))
            val repo = DiaryRepository(dao, settings = settings)

            val client = FakeCloudDriveClient(authorized = true)
            val oldName = remoteEntryFileName(syncId, 1_000)
            client.seedFile(
                name = oldName,
                content = encodeEntryForSync(localEntry.copy(updatedAt = Instant.fromEpochMilliseconds(1_000))),
            )
            client.failNextCreate = true

            assertFailsWith<IllegalStateException> {
                performCloudSync(client, repo, dao.getAll(), settings)
            }

            val remainingNames = client.listFiles(null).map { it.name }
            assertTrue(oldName in remainingNames)
        }

    @Test
    fun unauthorized_throwsBeforeAnySyncWork() =
        runTest {
            val settings = createTestSettingsRepository()
            configureSyncSettings(settings, enabled = true, path = "/")

            val dao = InMemoryDiaryDao()
            val repo = DiaryRepository(dao, settings = settings)
            val client = FakeCloudDriveClient(authorized = false)

            assertFailsWith<Exception> {
                performCloudSync(client, repo, emptyList(), settings)
            }

            assertTrue(client.calls.isEmpty())
        }

    private suspend fun configureSyncSettings(
        settings: SettingsRepository,
        enabled: Boolean,
        path: String,
    ) {
        settings.setCloudSyncEnabled(enabled)
        settings.setCloudSyncPath(path)
        settings.setCloudSyncDeletionTombstonesJson(null)
    }

    private fun createTestSettingsRepository(): SettingsRepository = InMemorySettingsRepository()

    private fun diaryEntry(
        syncId: String,
        title: String,
        content: String,
        createdAtMs: Long,
        updatedAtMs: Long,
    ): DiaryEntry =
        DiaryEntry(
            id = 1,
            syncId = syncId,
            title = title,
            content = content,
            createdAt = Instant.fromEpochMilliseconds(createdAtMs),
            updatedAt = Instant.fromEpochMilliseconds(updatedAtMs),
        )

    private fun remoteEntryFileName(
        syncId: String,
        timestampMillis: Long,
    ): String = "markday_entry_${syncId}_$timestampMillis.txt"

    private fun remoteTombstoneFileName(
        syncId: String,
        timestampMillis: Long,
    ): String = "markday_tombstone_${syncId}_$timestampMillis.txt"
}

private class InMemorySettingsRepository : SettingsRepository {
    private val themeModeState = MutableStateFlow(io.github.smiling_pixel.theme.ThemeMode.SYSTEM)
    private val pureBlackEnabledState = MutableStateFlow(false)
    private val apiKeyState = MutableStateFlow<String?>(null)
    private val cloudSyncEnabledState = MutableStateFlow(false)
    private val autoSyncEnabledState = MutableStateFlow(false)
    private val cloudSyncPathState = MutableStateFlow("/MarkDay")
    private val cloudSyncDeletionTombstonesJsonState = MutableStateFlow<String?>(null)
    private val logLevelState = MutableStateFlow(LogLevel.ERROR)
    private val logPersistenceEnabledState = MutableStateFlow(false)

    override val themeMode: Flow<io.github.smiling_pixel.theme.ThemeMode> = themeModeState.asStateFlow()

    override suspend fun setThemeMode(mode: io.github.smiling_pixel.theme.ThemeMode) {
        themeModeState.value = mode
    }

    override val isPureBlackEnabled: Flow<Boolean> = pureBlackEnabledState.asStateFlow()

    override suspend fun setPureBlackEnabled(enabled: Boolean) {
        pureBlackEnabledState.value = enabled
    }

    override val googleWeatherApiKey: Flow<String?> = apiKeyState.asStateFlow()

    override suspend fun setGoogleWeatherApiKey(key: String?) {
        apiKeyState.value = key
    }

    override val isCloudSyncEnabled: Flow<Boolean> = cloudSyncEnabledState.asStateFlow()

    override suspend fun setCloudSyncEnabled(enabled: Boolean) {
        cloudSyncEnabledState.value = enabled
    }

    override val isAutoSyncEnabled: Flow<Boolean> = autoSyncEnabledState.asStateFlow()

    override suspend fun setAutoSyncEnabled(enabled: Boolean) {
        autoSyncEnabledState.value = enabled
    }

    override val cloudSyncPath: Flow<String> = cloudSyncPathState.asStateFlow()

    override suspend fun setCloudSyncPath(path: String) {
        cloudSyncPathState.value = path
    }

    override val cloudSyncDeletionTombstonesJson: Flow<String?> = cloudSyncDeletionTombstonesJsonState.asStateFlow()

    override suspend fun setCloudSyncDeletionTombstonesJson(value: String?) {
        cloudSyncDeletionTombstonesJsonState.value = value
    }

    override val logLevel: Flow<LogLevel> = logLevelState.asStateFlow()

    override suspend fun setLogLevel(level: LogLevel) {
        logLevelState.value = level
    }

    override val isLogPersistenceEnabled: Flow<Boolean> = logPersistenceEnabledState.asStateFlow()

    override suspend fun setLogPersistenceEnabled(enabled: Boolean) {
        logPersistenceEnabledState.value = enabled
    }
}

private class FakeCloudDriveClient(
    private val authorized: Boolean,
) : CloudDriveClient {
    private data class StoredFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val parentId: String?,
        val isFolder: Boolean,
        val content: ByteArray,
    )

    private val files = linkedMapOf<String, StoredFile>()
    private var idCounter = 1

    val calls = mutableListOf<String>()
    var failNextCreate: Boolean = false

    fun seedFile(
        name: String,
        content: ByteArray,
        parentId: String? = null,
    ): DriveFile {
        val id = "seed-${idCounter++}"
        val file =
            StoredFile(
                id = id,
                name = name,
                mimeType = "text/plain",
                parentId = parentId,
                isFolder = false,
                content = content,
            )
        files[id] = file
        return toDriveFile(file)
    }

    override suspend fun listFiles(parentId: String?): List<DriveFile> {
        calls += "list:$parentId"
        return files.values
            .filter { it.parentId == parentId }
            .map(::toDriveFile)
    }

    override suspend fun createFile(
        name: String,
        content: ByteArray,
        mimeType: String,
        parentId: String?,
    ): DriveFile {
        calls += "create:$name"
        if (failNextCreate) {
            failNextCreate = false
            throw IllegalStateException("create failed")
        }

        val id = "file-${idCounter++}"
        val file =
            StoredFile(
                id = id,
                name = name,
                mimeType = mimeType,
                parentId = parentId,
                isFolder = false,
                content = content,
            )
        files[id] = file
        return toDriveFile(file)
    }

    override suspend fun createFolder(
        name: String,
        parentId: String?,
    ): DriveFile {
        calls += "mkdir:$name"
        val id = "folder-${idCounter++}"
        val folder =
            StoredFile(
                id = id,
                name = name,
                mimeType = CloudDriveClient.MIME_TYPE_FOLDER,
                parentId = parentId,
                isFolder = true,
                content = ByteArray(0),
            )
        files[id] = folder
        return toDriveFile(folder)
    }

    override suspend fun deleteFile(fileId: String) {
        calls += "delete:$fileId"
        files.remove(fileId)
    }

    override suspend fun downloadFile(fileId: String): ByteArray {
        calls += "download:$fileId"
        val file = files[fileId] ?: throw IllegalStateException("missing file: $fileId")
        return file.content
    }

    override suspend fun updateFile(
        fileId: String,
        content: ByteArray,
    ): DriveFile {
        calls += "update:$fileId"
        val existing = files[fileId] ?: throw IllegalStateException("missing file: $fileId")
        val updated = existing.copy(content = content)
        files[fileId] = updated
        return toDriveFile(updated)
    }

    override suspend fun isAuthorized(): Boolean = authorized

    override suspend fun authorize(): Boolean = throw NotImplementedError("Not needed in test")

    override suspend fun signOut(): Unit = throw NotImplementedError("Not needed in test")

    override suspend fun getUserInfo(): UserInfo? = null

    private fun toDriveFile(file: StoredFile): DriveFile =
        DriveFile(
            id = file.id,
            name = file.name,
            mimeType = file.mimeType,
            isFolder = file.isFolder,
        )
}
