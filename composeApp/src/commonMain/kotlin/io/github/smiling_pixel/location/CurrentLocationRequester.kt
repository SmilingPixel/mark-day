package io.github.smiling_pixel.location

import androidx.compose.runtime.Composable
import io.github.smiling_pixel.model.Location

/** Result of a user-initiated request for the device's current location. */
sealed interface CurrentLocationResult {
    /**
     * A current coordinate was obtained from the platform.
     *
     * @property location Coordinate reported by the platform location service.
     */
    data class Success(
        val location: Location,
    ) : CurrentLocationResult

    /** The user denied the location permission requested by the platform. */
    data object PermissionDenied : CurrentLocationResult

    /** Device location is not supported on this platform. */
    data object Unsupported : CurrentLocationResult

    /** Device location is supported, but no current coordinate could be obtained. */
    data object Unavailable : CurrentLocationResult
}

/**
 * Starts a platform current-location request.
 *
 * Calling [request] is the consent boundary: platform permission must not be requested before this method is called
 * in direct response to a user action.
 */
interface CurrentLocationRequester {
    /** Whether this platform supports requesting the device's current location. */
    val isSupported: Boolean

    /** Requests a current coordinate and reports the outcome to the callback supplied by the platform factory. */
    fun request()
}

/**
 * Remembers the platform current-location requester.
 *
 * @param onResult Callback invoked after a user-initiated request completes.
 * @return A requester for the current platform.
 */
@Composable
expect fun rememberCurrentLocationRequester(onResult: (CurrentLocationResult) -> Unit): CurrentLocationRequester
