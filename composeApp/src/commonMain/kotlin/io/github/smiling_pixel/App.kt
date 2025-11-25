package io.github.smiling_pixel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Serializable
sealed interface AppRoute

@Serializable
object EntriesRoute : AppRoute

@Serializable
object InsightsRoute : AppRoute

@Serializable
object SettingsRoute : AppRoute

@Serializable
object ProfileRoute : AppRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(providedRepo: io.github.smiling_pixel.database.DiaryRepository? = null) {
    MaterialTheme {
        val navController = rememberNavController()
        var selected by remember { mutableStateOf<AppRoute>(EntriesRoute) }
        // remember previous to return from profile
        var previous by remember { mutableStateOf<AppRoute>(EntriesRoute) }

        Scaffold(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        val title = when (selected) {
                            EntriesRoute -> "Entries"
                            InsightsRoute -> "Insights"
                            SettingsRoute -> "Settings"
                            ProfileRoute -> "Profile"
                            else -> ""
                        }
                        Text(title)
                    },
                    actions = {
                        IconButton(onClick = {
                            previous = selected
                            selected = ProfileRoute
                            navController.navigate(ProfileRoute)
                        }) {
                            // Use a simple emoji avatar to avoid platform icon dependencies
                            Text("👤")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selected == EntriesRoute,
                        onClick = {
                            selected = EntriesRoute
                            navController.navigate(EntriesRoute)
                        },
                        icon = { Text("E") },
                        label = { Text("Entries") }
                    )
                    NavigationBarItem(
                        selected = selected == InsightsRoute,
                        onClick = {
                            selected = InsightsRoute
                            navController.navigate(InsightsRoute)
                        },
                        icon = { Text("I") },
                        label = { Text("Insights") }
                    )
                    NavigationBarItem(
                        selected = selected == SettingsRoute,
                        onClick = {
                            selected = SettingsRoute
                            navController.navigate(SettingsRoute)
                        },
                        icon = { Text("S") },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                NavHost(navController = navController, startDestination = EntriesRoute) {
                    composable<EntriesRoute> {
                        if (providedRepo != null) EntriesScreen(providedRepo) else EntriesScreen()
                    }
                    composable<InsightsRoute> {
                        InsightsScreen()
                    }
                    composable<SettingsRoute> {
                        SettingsScreen()
                    }
                    composable<ProfileRoute> { backStackEntry ->
                        ProfileScreen(onBack = {
                            // return to previous selection when profile is dismissed
                            selected = previous
                            navController.popBackStack()
                        })
                    }
                }
            }
        }
    }
}
