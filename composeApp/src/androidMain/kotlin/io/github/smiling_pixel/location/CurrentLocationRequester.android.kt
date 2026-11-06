package io.github.smiling_pixel.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.smiling_pixel.model.Location

@Composable
actual fun rememberCurrentLocationRequester(onResult: (CurrentLocationResult) -> Unit): CurrentLocationRequester {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)

    fun requestLocation() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val provider =
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { candidate -> locationManager?.isProviderEnabled(candidate) == true }
        if (locationManager == null || provider == null) {
            currentOnResult.value(CurrentLocationResult.Unavailable)
            return
        }

        try {
            // LocationManager completes through a platform callback; keep that bridge here so common UI remains small.
            locationManager.getCurrentLocation(provider, null, context.mainExecutor) { platformLocation ->
                currentOnResult.value(
                    platformLocation?.let {
                        CurrentLocationResult.Success(Location(it.latitude, it.longitude))
                    } ?: CurrentLocationResult.Unavailable,
                )
            }
        } catch (_: SecurityException) {
            currentOnResult.value(CurrentLocationResult.PermissionDenied)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestLocation()
            } else {
                currentOnResult.value(CurrentLocationResult.PermissionDenied)
            }
        }

    return remember(context, permissionLauncher) {
        object : CurrentLocationRequester {
            override val isSupported: Boolean = true

            override fun request() {
                if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestLocation()
                } else {
                    // This launch occurs only after the user presses "Use current location" in the explained dialog.
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
    }
}
