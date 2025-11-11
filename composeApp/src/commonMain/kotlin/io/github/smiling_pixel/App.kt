package io.github.smiling_pixel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        var selected by remember { mutableStateOf(Screen.Entries) }

        Scaffold(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selected == Screen.Entries,
                        onClick = { selected = Screen.Entries },
                        icon = { Text("E") },
                        label = { Text("Entries") }
                    )
                    NavigationBarItem(
                        selected = selected == Screen.Insights,
                        onClick = { selected = Screen.Insights },
                        icon = { Text("I") },
                        label = { Text("Insights") }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                when (selected) {
                    Screen.Entries -> EntriesScreen()
                    Screen.Insights -> InsightsScreen()
                }
            }
        }
    }
}

private enum class Screen { Entries, Insights }