package io.github.smiling_pixel.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberCurrentLocationRequester(onResult: (CurrentLocationResult) -> Unit): CurrentLocationRequester {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        object : CurrentLocationRequester {
            override val isSupported: Boolean = false

            override fun request() {
                currentOnResult.value(CurrentLocationResult.Unsupported)
            }
        }
    }
}
