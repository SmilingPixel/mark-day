package io.github.smiling_pixel.sync

import io.github.smiling_pixel.model.DiaryEntry
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SyncManagerCodecTest {

    @Test
    fun encodeDecodeRoundTripPreservesSyncId() {
        val entry = DiaryEntry(
            id = 7,
            syncId = "123e4567-e89b-12d3-a456-426614174000",
            title = "Trip",
            content = "Line 1\nLine 2",
            entryDate = LocalDate.parse("2026-03-31"),
            weatherCondition = "Sunny",
            minTemperature = 10.5,
            maxTemperature = 20.5
        )

        val decoded = decodeEntryForSync(encodeEntryForSync(entry), entry.copy(id = 0))

        assertNotNull(decoded)
        assertEquals(entry.syncId, decoded.syncId)
        assertEquals(entry.title, decoded.title)
        assertEquals(entry.content, decoded.content)
        assertEquals(entry.entryDate, decoded.entryDate)
        assertEquals(entry.weatherCondition, decoded.weatherCondition)
        assertEquals(entry.minTemperature, decoded.minTemperature)
        assertEquals(entry.maxTemperature, decoded.maxTemperature)
    }

    @Test
    fun decodeReturnsNullWhenSyncIdIsNotUuid() {
        val payload = """
            {
              "syncId": "legacy-id-42",
              "title": "Title",
              "createdAtEpochMillis": 1711843200000,
              "updatedAtEpochMillis": 1711843200000,
              "entryDateIso": "2026-03-31",
              "content": "Body"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = decodeEntryForSync(
            bytes = payload,
            original = DiaryEntry(id = 0, syncId = "123e4567-e89b-12d3-a456-426614174000", title = "", content = "")
        )

        assertNull(decoded)
    }
}
