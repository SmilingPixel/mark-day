package io.github.smiling_pixel.draft

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EntryDraftRepositoryTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rapidChangesAreCoalescedForSevenHundredFiftyMilliseconds() =
        runTest {
            val values = MutableStateFlow("initial")
            val pending = mutableListOf<String>()
            val persisted = mutableListOf<String>()
            backgroundScope.launch {
                values.debounceDraftChanges(pending::add).collect { persisted += it }
            }
            runCurrent()

            values.value = "first"
            runCurrent()
            advanceTimeBy(500)
            values.value = "latest"
            runCurrent()
            advanceTimeBy(749)
            runCurrent()

            assertEquals(listOf("first", "latest"), pending)
            assertEquals(emptyList(), persisted)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("latest"), persisted)
        }

    @Test
    fun savesLoadsAndDeletesIndependentDrafts() =
        runTest {
            val storage = TestStorage()
            val repository = PersistentEntryDraftRepository(storage)
            val newDraft = draft(targetSyncId = "new")
            val existingDraft = draft(targetSyncId = "existing", sourceEntrySyncId = "existing")

            repository.upsert(newDraft)
            repository.upsert(existingDraft)

            assertEquals(newDraft, repository.load(EntryDraftKey.NewEntry))
            assertEquals(existingDraft, repository.load(EntryDraftKey.ExistingEntry("existing")))

            repository.delete(EntryDraftKey.NewEntry)

            assertNull(repository.load(EntryDraftKey.NewEntry))
            assertEquals(existingDraft, repository.load(EntryDraftKey.ExistingEntry("existing")))
        }

    @Test
    fun serializedDraftsCanBeRestoredByANewRepository() =
        runTest {
            val storage = TestStorage()
            val original = draft(targetSyncId = "restored", sourceEntrySyncId = "source")
            PersistentEntryDraftRepository(storage).upsert(original)

            val restored =
                PersistentEntryDraftRepository(storage)
                    .load(EntryDraftKey.ExistingEntry("source"))

            assertEquals(original, restored)
        }

    @Test
    fun malformedStorageIsNotOverwritten() =
        runTest {
            val malformed = "{not-json"
            val storage = TestStorage(malformed)
            val repository = PersistentEntryDraftRepository(storage)

            assertFailsWith<EntryDraftCorruptionException> {
                repository.upsert(draft(targetSyncId = "new"))
            }
            assertEquals(malformed, storage.value)
        }

    @Test
    fun storageFailuresArePropagated() =
        runTest {
            val storage = TestStorage(failWrites = true)
            val repository = PersistentEntryDraftRepository(storage)

            assertFailsWith<IllegalStateException> {
                repository.upsert(draft(targetSyncId = "new"))
            }
        }

    private fun draft(
        targetSyncId: String,
        sourceEntrySyncId: String? = null,
    ): EntryDraft =
        EntryDraft(
            targetSyncId = targetSyncId,
            sourceEntrySyncId = sourceEntrySyncId,
            sourceUpdatedAtEpochMilliseconds = sourceEntrySyncId?.let { 10L },
            title = "A title",
            content = "Draft content",
            entryDate = "2026-08-01",
            weatherCondition = "Clear",
            minTemperature = 12.5,
            maxTemperature = 24.0,
            createdAtEpochMilliseconds = 100L,
            draftUpdatedAtEpochMilliseconds = 200L,
        )
}

private class TestStorage(
    var value: String? = null,
    private val failWrites: Boolean = false,
) : EntryDraftStorage {
    override suspend fun read(): String? = value

    override suspend fun write(value: String?) {
        check(!failWrites) { "write failed" }
        this.value = value
    }
}
