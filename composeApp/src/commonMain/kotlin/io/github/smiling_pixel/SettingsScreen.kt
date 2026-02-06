package io.github.smiling_pixel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import io.github.smiling_pixel.client.UserInfo
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.preference.getSettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    val settingsRepository = getSettingsRepository()
    val apiKey by settingsRepository.googleWeatherApiKey.collectAsState(initial = null)
    val uriHandler = LocalUriHandler.current

    val cloudDriveClient = remember { getCloudDriveClient() }
    var userInfo by remember { mutableStateOf<UserInfo?>(null) }
    var isAuthorized by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val checkAuthStatus by rememberUpdatedState {
        scope.launch {
            try {
                isAuthorized = cloudDriveClient.isAuthorized()
                if (isAuthorized) {
                    userInfo = cloudDriveClient.getUserInfo()
                } else {
                    userInfo = null
                }
            } catch (e: Exception) {
                isAuthorized = false
                userInfo = null
                // Fail silently on background check or set error if critical
                // errorMessage = "Failed to refresh status: ${e.message}"
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkAuthStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Third-party Services",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey ?: "",
            onValueChange = { newKey ->
                scope.launch {
                    settingsRepository.setGoogleWeatherApiKey(newKey)
                }
            },
            label = { Text("Google Weather API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Cloud Drive Sync",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        if (isAuthorized) {
            Text("Signed in as: ${userInfo?.name ?: "Loading..."}")
            Text("Email: ${userInfo?.email ?: ""}")
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            cloudDriveClient.signOut()
                            isAuthorized = false
                            userInfo = null
                        } catch (e: Exception) {
                            errorMessage = "Sign out failed: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Revoke Authorization")
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            if (cloudDriveClient.authorize()) {
                                isAuthorized = true
                                userInfo = cloudDriveClient.getUserInfo()
                            } else {
                                errorMessage = "Authorization was cancelled or failed."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Authorization error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Authorize Google Drive")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "About",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "MarkDay Diary App v1.0.0",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "A cross-platform diary application built with Kotlin Multiplatform and Compose.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "View on GitHub",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://github.com/SmilingPixel/mark-day")
            }
        )
    }
}
