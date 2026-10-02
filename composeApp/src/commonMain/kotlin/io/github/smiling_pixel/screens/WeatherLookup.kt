package io.github.smiling_pixel.screens

import io.github.smiling_pixel.client.WeatherClient
import io.github.smiling_pixel.model.Location
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal data class WeatherValues(
    val condition: String? = null,
    val minTemperature: Double? = null,
    val maxTemperature: Double? = null,
)

internal sealed interface LocationInputResult {
    data class Valid(
        val location: Location,
    ) : LocationInputResult

    data class Invalid(
        val latitudeError: String? = null,
        val longitudeError: String? = null,
    ) : LocationInputResult
}

internal fun parseWeatherLocation(
    latitudeText: String,
    longitudeText: String,
): LocationInputResult {
    val latitude = latitudeText.trim().toDoubleOrNull()
    val longitude = longitudeText.trim().toDoubleOrNull()
    val latitudeError =
        when {
            latitude == null -> "Enter a latitude."
            !latitude.isFinite() || latitude !in MIN_LATITUDE..MAX_LATITUDE ->
                "Latitude must be between -90 and 90."
            else -> null
        }
    val longitudeError =
        when {
            longitude == null -> "Enter a longitude."
            !longitude.isFinite() || longitude !in MIN_LONGITUDE..MAX_LONGITUDE ->
                "Longitude must be between -180 and 180."
            else -> null
        }
    return if (latitudeError == null && longitudeError == null) {
        LocationInputResult.Valid(Location(requireNotNull(latitude), requireNotNull(longitude)))
    } else {
        LocationInputResult.Invalid(latitudeError, longitudeError)
    }
}

internal suspend fun fetchWeatherForDate(
    weatherClient: WeatherClient,
    location: Location,
    targetDate: LocalDate,
    now: Instant,
    timeZone: TimeZone,
): WeatherValues? {
    val start = targetDate.atStartOfDayIn(timeZone)
    val end = targetDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
    val today = now.toLocalDateTime(timeZone).date
    val hourly =
        if (targetDate < today) {
            weatherClient.getHourlyHistory(location, start, end)
        } else {
            weatherClient.getHourlyForecast(location)
        }

    // Only exact-date intervals are eligible. An empty result intentionally has no current-weather or date fallback.
    val matching = hourly.filter { it.startTime < end && it.endTime > start }.sortedBy { it.startTime }
    if (matching.isEmpty()) return null

    return WeatherValues(
        condition = matching[matching.size / 2].condition,
        minTemperature = matching.minOf { it.minTemperature },
        maxTemperature = matching.maxOf { it.maxTemperature },
    )
}

internal fun emptyWeatherValues(): WeatherValues = WeatherValues()

private const val MIN_LATITUDE = -90.0
private const val MAX_LATITUDE = 90.0
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0
