package io.github.smiling_pixel.screens

import io.github.smiling_pixel.client.MockWeatherClient
import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.model.DiaryEntry
import io.github.smiling_pixel.model.IntervalWeatherInfo
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.model.WeatherInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WeatherLookupTest {
    @Test
    fun parsesValidCoordinatesIncludingBoundaries() {
        assertEquals(
            Location(12.5, -45.25),
            assertIs<LocationInputResult.Valid>(parseWeatherLocation(" 12.5 ", " -45.25 ")).location,
        )
        assertEquals(
            Location(-90.0, 180.0),
            assertIs<LocationInputResult.Valid>(parseWeatherLocation("-90", "180")).location,
        )
    }

    @Test
    fun rejectsMissingNonFiniteAndOutOfRangeCoordinates() {
        listOf(
            "" to "",
            "north" to "east",
            "NaN" to "Infinity",
            "90.1" to "0",
            "0" to "-180.1",
        ).forEach { (latitude, longitude) ->
            assertIs<LocationInputResult.Invalid>(parseWeatherLocation(latitude, longitude))
        }
    }

    @Test
    fun backdatedEntryUsesEntryDateForHistoryBounds() =
        runTest {
            val entry =
                DiaryEntry(
                    id = 1,
                    title = "Backdated",
                    content = "",
                    createdAt = Instant.parse("2026-08-20T12:00:00Z"),
                    entryDate = LocalDate(2026, 8, 3),
                )
            val client = RecordingWeatherClient(history = intervalsForAugustThird())

            fetchWeatherForDate(
                weatherClient = client,
                location = Location(10.0, 20.0),
                targetDate = entry.entryDate,
                now = Instant.parse("2026-08-20T12:00:00Z"),
                timeZone = TimeZone.UTC,
            )

            assertEquals(Instant.parse("2026-08-03T00:00:00Z"), client.historyStart)
            assertEquals(Instant.parse("2026-08-04T00:00:00Z"), client.historyEnd)
            assertEquals(1, client.historyCalls)
            assertEquals(0, client.forecastCalls)
        }

    @Test
    fun currentAndFutureDatesUseForecast() =
        runTest {
            val client = RecordingWeatherClient(forecast = intervalsForAugustThird())
            fetchWeatherForDate(
                weatherClient = client,
                location = Location(10.0, 20.0),
                targetDate = LocalDate(2026, 8, 3),
                now = Instant.parse("2026-08-03T08:00:00Z"),
                timeZone = TimeZone.UTC,
            )
            fetchWeatherForDate(
                weatherClient = client,
                location = Location(10.0, 20.0),
                targetDate = LocalDate(2026, 8, 4),
                now = Instant.parse("2026-08-03T08:00:00Z"),
                timeZone = TimeZone.UTC,
            )

            assertEquals(0, client.historyCalls)
            assertEquals(2, client.forecastCalls)
        }

    @Test
    fun filtersAdjacentDatesAndAggregatesMatchingIntervals() =
        runTest {
            val client = RecordingWeatherClient(history = intervalsForAugustThird())

            val result =
                fetchWeatherForDate(
                    weatherClient = client,
                    location = Location(10.0, 20.0),
                    targetDate = LocalDate(2026, 8, 3),
                    now = Instant.parse("2026-08-20T12:00:00Z"),
                    timeZone = TimeZone.UTC,
                )

            assertEquals(WeatherValues("Cloudy", 8.0, 24.0), result)
        }

    @Test
    fun noMatchingIntervalsReturnsUnavailableWithoutCurrentWeatherFallback() =
        runTest {
            val client =
                RecordingWeatherClient(
                    history =
                        listOf(
                            interval("2026-08-02T10:00:00Z", "2026-08-02T11:00:00Z", 1.0, 2.0, "Rain"),
                        ),
                )

            val result =
                fetchWeatherForDate(
                    weatherClient = client,
                    location = Location(10.0, 20.0),
                    targetDate = LocalDate(2026, 8, 3),
                    now = Instant.parse("2026-08-20T12:00:00Z"),
                    timeZone = TimeZone.UTC,
                )

            assertNull(result)
            assertEquals(0, client.currentCalls)
        }

    @Test
    fun exposesMissingConfigurationAndClearsEveryWeatherField() =
        runTest {
            assertTrue(!MockWeatherClient(configured = false).isConfigured())
            assertEquals(WeatherValues(null, null, null), emptyWeatherValues())
        }

    private fun intervalsForAugustThird(): List<IntervalWeatherInfo> =
        listOf(
            interval("2026-08-02T23:00:00Z", "2026-08-03T00:00:00Z", -10.0, 40.0, "Wrong day"),
            interval("2026-08-03T06:00:00Z", "2026-08-03T07:00:00Z", 8.0, 12.0, "Clear"),
            interval("2026-08-03T12:00:00Z", "2026-08-03T13:00:00Z", 18.0, 24.0, "Cloudy"),
            interval("2026-08-04T00:00:00Z", "2026-08-04T01:00:00Z", -20.0, 50.0, "Wrong day"),
        )

    private fun interval(
        start: String,
        end: String,
        minimum: Double,
        maximum: Double,
        condition: String,
    ): IntervalWeatherInfo =
        IntervalWeatherInfo(
            startTime = Instant.parse(start),
            endTime = Instant.parse(end),
            minTemperature = minimum,
            maxTemperature = maximum,
            condition = condition,
            humidity = 50,
            windSpeed = 5.0,
        )
}

private class RecordingWeatherClient(
    private val history: List<IntervalWeatherInfo> = emptyList(),
    private val forecast: List<IntervalWeatherInfo> = emptyList(),
) : WeatherClient {
    var historyCalls = 0
    var forecastCalls = 0
    var currentCalls = 0
    var historyStart: Instant? = null
    var historyEnd: Instant? = null

    override suspend fun isConfigured(): Boolean = true

    override suspend fun getWeather(location: Location): WeatherInfo {
        currentCalls += 1
        return WeatherInfo(0.0, "Fallback", 0, 0.0, "Fallback")
    }

    override suspend fun getHourlyForecast(location: Location): List<IntervalWeatherInfo> {
        forecastCalls += 1
        return forecast
    }

    override suspend fun getHourlyHistory(
        location: Location,
        start: Instant,
        end: Instant,
    ): List<IntervalWeatherInfo> {
        historyCalls += 1
        historyStart = start
        historyEnd = end
        return history
    }
}
