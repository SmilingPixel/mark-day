package io.github.smiling_pixel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies the serializable route values used by the app navigation graph. */
class AppRouteTest {
    @Test
    fun entryDetailsRouteRoundTripsStableSyncId() {
        val route = EntryDetailsRoute("sync-entry-42", AppTab.SEARCH)
        val encoded = Json.encodeToJsonElement(EntryDetailsRoute.serializer(), route)

        assertEquals(route, Json.decodeFromJsonElement(EntryDetailsRoute.serializer(), encoded))
    }

    @Test
    fun nestedRoutesHighlightTheirOriginTab() {
        assertEquals(AppTab.ENTRIES, EntryDetailsRoute("entry", AppTab.ENTRIES).toAppTab())
        assertEquals(AppTab.SEARCH, EntryDetailsRoute("entry", AppTab.SEARCH).toAppTab())
        assertEquals(AppTab.MOMENTS, NewEntryRoute(AppTab.MOMENTS).toAppTab())
        assertEquals(AppTab.SETTINGS, ProfileRoute(AppTab.SETTINGS).toAppTab())
    }
}
