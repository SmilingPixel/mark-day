package io.github.smiling_pixel.draft

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach

/** Default quiet period after the latest form change before durable persistence starts. */
internal const val DEFAULT_DRAFT_AUTOSAVE_DEBOUNCE_MILLIS = 750L

/**
 * Coalesces changing form snapshots while reporting that the latest value is pending persistence.
 *
 * The initial emission is deliberately dropped. The editor hydrates its form before collecting this flow, so that
 * emission describes already-classified initial or restored state rather than a user edit. Calling [onPending] before
 * the debounce lets exit protection know immediately that the latest values are not yet durable.
 */
@OptIn(FlowPreview::class)
internal fun <T> Flow<T>.debounceDraftChanges(onPending: (T) -> Unit): Flow<T> =
    distinctUntilChanged()
        .drop(1)
        .onEach(onPending)
        .debounce(DEFAULT_DRAFT_AUTOSAVE_DEBOUNCE_MILLIS)
