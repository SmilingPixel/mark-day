package io.github.smiling_pixel.draft

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Identifies the durable draft associated with an entry editor. */
sealed interface EntryDraftKey {
    /** Identifies the single draft used while creating a new diary entry. */
    data object NewEntry : EntryDraftKey

    /**
     * Identifies a draft for an existing diary entry.
     *
     * @property syncId Stable synchronization identifier of the source entry.
     */
    data class ExistingEntry(
        val syncId: String,
    ) : EntryDraftKey
}

/**
 * Durable snapshot of every editable diary-entry field.
 *
 * Dates are stored as ISO-8601 strings and instants as epoch milliseconds so the serialized format is stable on every
 * supported platform.
 *
 * @property targetSyncId Stable identifier used by the diary entry created or updated from this draft.
 * @property sourceEntrySyncId Identifier of the existing entry being edited, or null for a new entry.
 * @property sourceUpdatedAtEpochMilliseconds Revision of the existing entry on which this draft is based.
 * @property title Draft title.
 * @property content Draft Markdown content.
 * @property entryDate ISO-8601 diary date.
 * @property weatherCondition Optional weather condition.
 * @property minTemperature Optional minimum temperature.
 * @property maxTemperature Optional maximum temperature.
 * @property createdAtEpochMilliseconds Creation time retained when the draft becomes a diary entry.
 * @property draftUpdatedAtEpochMilliseconds Time at which this snapshot was created.
 */
@Serializable
data class EntryDraft(
    val targetSyncId: String,
    val sourceEntrySyncId: String? = null,
    val sourceUpdatedAtEpochMilliseconds: Long? = null,
    val title: String,
    val content: String,
    val entryDate: String,
    val weatherCondition: String? = null,
    val minTemperature: Double? = null,
    val maxTemperature: Double? = null,
    val createdAtEpochMilliseconds: Long,
    val draftUpdatedAtEpochMilliseconds: Long,
) {
    init {
        require(targetSyncId.isNotBlank()) { "Draft target sync ID must not be blank." }
        require(sourceEntrySyncId == null || sourceEntrySyncId.isNotBlank()) {
            "Draft source sync ID must not be blank."
        }
        LocalDate.parse(entryDate)
    }

    /** Repository key under which this draft is stored. */
    val key: EntryDraftKey
        get() = sourceEntrySyncId?.let(EntryDraftKey::ExistingEntry) ?: EntryDraftKey.NewEntry
}

/** Current persistence state shown by the entry editor. */
enum class DraftSaveState {
    /** The editor has no meaningful changes that require persistence. */
    IDLE,

    /** The latest editor values are waiting to be or are currently being persisted. */
    SAVING,

    /** The latest editor values have been persisted. */
    SAVED,

    /** The latest editor values could not be persisted. */
    FAILED,
}
