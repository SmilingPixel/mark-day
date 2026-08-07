package io.github.smiling_pixel.draft

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Provides device-local persistence for entry drafts.
 *
 * Drafts are recovery snapshots only: implementations must not expose them as committed diary entries or include them
 * in synchronization. Implementations may throw when storage cannot be read or written; callers use those failures to
 * keep the editor open and avoid falsely reporting that the latest values are safe.
 */
interface EntryDraftRepository {
    /**
     * Returns the draft stored for [key], or null when no draft exists.
     *
     * @throws EntryDraftCorruptionException when persisted data is not safe to decode or overwrite.
     */
    suspend fun load(key: EntryDraftKey): EntryDraft?

    /**
     * Inserts or replaces [draft] without changing drafts stored under other keys.
     *
     * @throws EntryDraftCorruptionException when the existing document cannot be decoded safely.
     */
    suspend fun upsert(draft: EntryDraft)

    /**
     * Deletes the draft stored for [key] without changing drafts stored under other keys.
     *
     * @throws EntryDraftCorruptionException when the existing document cannot be decoded safely.
     */
    suspend fun delete(key: EntryDraftKey)
}

/**
 * Indicates that persisted draft data cannot be decoded safely.
 *
 * @param cause Serialization problem that made the stored document unsafe to overwrite.
 */
class EntryDraftCorruptionException(
    cause: Throwable,
) : Exception("Persisted entry drafts could not be decoded.", cause)

/**
 * Minimal whole-document storage shared by the DataStore and browser adapters.
 *
 * A null write removes the platform key. The repository owns serialization and read-modify-write coordination so both
 * platform implementations have identical behavior.
 */
internal interface EntryDraftStorage {
    suspend fun read(): String?

    suspend fun write(value: String?)
}

/**
 * JSON-backed implementation of [EntryDraftRepository].
 *
 * @param storage Platform storage used for the versioned JSON document.
 */
internal class PersistentEntryDraftRepository(
    private val storage: EntryDraftStorage,
) : EntryDraftRepository {
    // Production exposes this repository as a singleton. The mutex makes each JSON read-modify-write atomic within that
    // process so autosaves for different entry keys cannot replace one another.
    private val mutex = Mutex()

    override suspend fun load(key: EntryDraftKey): EntryDraft? =
        mutex.withLock {
            readEnvelope().drafts.firstOrNull { it.key == key }
        }

    override suspend fun upsert(draft: EntryDraft) {
        mutex.withLock {
            val envelope = readEnvelope()
            val drafts = envelope.drafts.filterNot { it.key == draft.key } + draft
            storage.write(json.encodeToString(DraftEnvelope(drafts = drafts)))
        }
    }

    override suspend fun delete(key: EntryDraftKey) {
        mutex.withLock {
            val envelope = readEnvelope()
            val drafts = envelope.drafts.filterNot { it.key == key }
            storage.write(if (drafts.isEmpty()) null else json.encodeToString(DraftEnvelope(drafts = drafts)))
        }
    }

    private suspend fun readEnvelope(): DraftEnvelope {
        val value = storage.read() ?: return DraftEnvelope()
        val envelope = decodeEnvelope(value)
        // Unknown fields are forward compatible, but accepting a newer structural version could rewrite data this app
        // does not understand. Treat it as corruption so callers preserve the original document.
        if (envelope.schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw EntryDraftCorruptionException(
                SerializationException("Unsupported draft schema ${envelope.schemaVersion}"),
            )
        }
        return envelope
    }

    private fun decodeEnvelope(value: String): DraftEnvelope =
        try {
            json.decodeFromString<DraftEnvelope>(value)
        } catch (e: SerializationException) {
            throw EntryDraftCorruptionException(e)
        } catch (e: IllegalArgumentException) {
            throw EntryDraftCorruptionException(e)
        }
}

/** Simple volatile draft repository intended for tests and previews. */
class InMemoryEntryDraftRepository : EntryDraftRepository {
    private val mutex = Mutex()
    private val drafts = mutableMapOf<EntryDraftKey, EntryDraft>()

    override suspend fun load(key: EntryDraftKey): EntryDraft? = mutex.withLock { drafts[key] }

    override suspend fun upsert(draft: EntryDraft) {
        mutex.withLock { drafts[draft.key] = draft }
    }

    override suspend fun delete(key: EntryDraftKey) {
        mutex.withLock { drafts.remove(key) }
    }
}

/** Returns the platform's persistent entry-draft repository. */
expect fun getEntryDraftRepository(): EntryDraftRepository

@Serializable
private data class DraftEnvelope(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val drafts: List<EntryDraft> = emptyList(),
)

private const val CURRENT_SCHEMA_VERSION = 1

private val json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
