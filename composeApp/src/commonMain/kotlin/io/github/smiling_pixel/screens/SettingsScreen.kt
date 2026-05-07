package io.github.smiling_pixel.screens

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import io.github.smiling_pixel.client.UserInfo
import io.github.smiling_pixel.client.getCloudDriveClient
import io.github.smiling_pixel.preference.getSettingsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val scope = rememberCoroutineScope()
    // Remember the settings repository so recomposition does not recreate a new DataStore-backed
    // repository instance and resubscribe all mapped flows unnecessarily.
    val settingsRepository = remember { getSettingsRepository() }
    val themeMode by settingsRepository.themeMode.collectAsState(initial = io.github.smiling_pixel.theme.ThemeMode.SYSTEM)
    val isPureBlackEnabled by settingsRepository.isPureBlackEnabled.collectAsState(initial = false)
    val apiKey by settingsRepository.googleWeatherApiKey.collectAsState(initial = null)
    val uriHandler = LocalUriHandler.current

    val isCloudSyncEnabled by settingsRepository.isCloudSyncEnabled.collectAsState(initial = false)
    val isAutoSyncEnabled by settingsRepository.isAutoSyncEnabled.collectAsState(initial = false)
    val cloudSyncPath by settingsRepository.cloudSyncPath.collectAsState(initial = "/MarkDay")

    val cloudDriveClient = remember { getCloudDriveClient() }
    var userInfo by remember { mutableStateOf<UserInfo?>(null) }
    var isAuthorized by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingAuth by remember { mutableStateOf(false) }

    val checkAuthStatus by rememberUpdatedState {
        // use isCheckingAuth to prevent concurrent execution of the authentication status check
        if (!isCheckingAuth) {
            isCheckingAuth = true
            scope.launch {
                try {
                    isAuthorized = cloudDriveClient.isAuthorized()
                    if (isAuthorized) {
                        userInfo = cloudDriveClient.getUserInfo()
                    } else {
                        userInfo = null
                    }
                } catch (e: CancellationException) {
                    // Don't catch structured concurrency cancellation exceptions
                    throw e
                } catch (e: Exception) {
                    isAuthorized = false
                    userInfo = null
                    // Fail silently on background check or set error if critical
                    // errorMessage = "Failed to refresh status: ${e.message}"
                } finally {
                    isCheckingAuth = false
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkAuthStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        /*
         * A section to allow the user to select their preferred Theme Mode.
         * The user can select from SYSTEM, LIGHT, or DARK modes using a group of rounded rectangles.
         */
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            io.github.smiling_pixel.theme.ThemeMode.entries.forEach { mode ->
                val isSelected = themeMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                scope.launch {
                                    settingsRepository.setThemeMode(mode)
                                }
                            }
                        )
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) 
                            else Modifier
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = "Pure Black Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Use pure black backgrounds in dark mode instead of dark gray, optimizing for OLED screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isPureBlackEnabled,
                onCheckedChange = { isChecked ->
                    scope.launch {
                        settingsRepository.setPureBlackEnabled(isChecked)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Third-party Services",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
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

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Cloud Drive Sync",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        if (isAuthorized) {
            Text(
                text = "Connected to Google Drive",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Signed in as: ${userInfo?.name ?: "Loading..."}")
            Text("Email: ${userInfo?.email ?: ""}")
            
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = cloudSyncPath,
                onValueChange = { 
                    scope.launch { 
                        settingsRepository.setCloudSyncPath(it) 
                    }
                },
                label = { Text("Cloud Sync Save Path") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            ) // TODO: better user experience for selecting folder in Google Drive @SmilingPixel
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { 
                    scope.launch {
                        settingsRepository.setCloudSyncEnabled(!isCloudSyncEnabled)
                    }
                }
            ) {
                Text(if (isCloudSyncEnabled) "Disable Cloud Sync" else "Enable Cloud Sync")
            }
            if (isCloudSyncEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { 
                        scope.launch {
                            settingsRepository.setAutoSyncEnabled(!isAutoSyncEnabled)
                        }
                    }
                ) {
                    Text(if (isAutoSyncEnabled) "Disable Auto Sync" else "Enable Auto Sync")
                }
            }
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
                            settingsRepository.setCloudSyncEnabled(false)
                            settingsRepository.setAutoSyncEnabled(false)
                        } catch (e: CancellationException) {
                            // Don't catch structured concurrency cancellation exceptions
                            throw e
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
                        } catch (e: CancellationException) {
                            // Don't catch structured concurrency cancellation exceptions
                            throw e
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
                Text("Connect to Google Drive")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "MarkDay Diary App v1.0.0",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "A cross-platform diary application built with Kotlin Multiplatform and Compose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
