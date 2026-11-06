package io.github.smiling_pixel.client

import io.github.smiling_pixel.model.IntervalWeatherInfo
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.model.WeatherInfo
import kotlin.time.Instant

/**
 * Simple configurable weather client used by tests and previews.
 *
 * @param configured Whether this client should report that its required configuration is present.
 */
class MockWeatherClient(
    private val configured: Boolean = true,
) : WeatherClient {
    override suspend fun isConfigured(): Boolean = configured

    override suspend fun getWeather(location: Location): WeatherInfo =
        WeatherInfo(
            temperature = 20.0,
            condition = "Sunny",
            humidity = 50,
            windSpeed = 10.0,
            locationName = "Mock Location",
        )

    override suspend fun getHourlyForecast(location: Location): List<IntervalWeatherInfo> = emptyList()

    override suspend fun getHourlyHistory(
        location: Location,
        start: Instant,
        end: Instant,
    ): List<IntervalWeatherInfo> = emptyList()
}
