package io.github.smiling_pixel

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** Base type for every destination in the MarkDay navigation graph. */
@Serializable
sealed interface AppRoute

/** Identifies the top-level tab that owns a nested route. */
@Serializable
enum class AppTab {
    ENTRIES,
    SEARCH,
    MOMENTS,
    INSIGHTS,
    SETTINGS,
}

/** The diary entries list destination. */
@Serializable
object EntriesRoute : AppRoute

/** Destination for searching and filtering diary entries. */
@Serializable
object SearchRoute : AppRoute

/** Destination for the Moments media library. */
@Serializable
object MomentsRoute : AppRoute

/** Destination for diary insights. */
@Serializable
object InsightsRoute : AppRoute

/** Destination for application settings. */
@Serializable
object SettingsRoute : AppRoute

/** Displays a committed diary entry resolved by its stable synchronization identifier. */
@Serializable
data class EntryDetailsRoute(
    val syncId: String,
    val origin: AppTab = AppTab.ENTRIES,
) : AppRoute

/** Displays the durable new-entry draft editor. */
@Serializable
data class NewEntryRoute(
    val origin: AppTab = AppTab.ENTRIES,
) : AppRoute

/** Displays the profile screen and records the tab to select when it is dismissed. */
@Serializable
data class ProfileRoute(
    val returnTab: AppTab = AppTab.ENTRIES,
) : AppRoute

/** Maps a route to the top-level tab that should be highlighted in bottom navigation. */
fun AppRoute.toAppTab(): AppTab =
    when (this) {
        EntriesRoute -> AppTab.ENTRIES
        SearchRoute -> AppTab.SEARCH
        MomentsRoute -> AppTab.MOMENTS
        InsightsRoute -> AppTab.INSIGHTS
        SettingsRoute -> AppTab.SETTINGS
        is EntryDetailsRoute -> origin
        is NewEntryRoute -> origin
        is ProfileRoute -> returnTab
    }

/** Decodes the typed route represented by this back-stack entry. */
fun NavBackStackEntry.toAppRoute(): AppRoute =
    when {
        destination.hasRoute<EntryDetailsRoute>() -> toRoute<EntryDetailsRoute>()
        destination.hasRoute<NewEntryRoute>() -> toRoute<NewEntryRoute>()
        destination.hasRoute<ProfileRoute>() -> toRoute<ProfileRoute>()
        destination.hasRoute<SearchRoute>() -> SearchRoute
        destination.hasRoute<MomentsRoute>() -> MomentsRoute
        destination.hasRoute<InsightsRoute>() -> InsightsRoute
        destination.hasRoute<SettingsRoute>() -> SettingsRoute
        else -> EntriesRoute
    }
