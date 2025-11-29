package io.github.smiling_pixel.client

import io.github.smiling_pixel.model.IntervalWeatherInfo
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.model.WeatherInfo
import kotlinx.datetime.Instant

/**
 * Interface for fetching weather information.
 */
interface WeatherClient {
    /**
     * Fetches the current weather for the given location.
     *
     * @param location The location to fetch weather for.
     * @return The weather information.
     * @throws Exception if the weather information cannot be fetched.
     */
    suspend fun getWeather(location: Location): WeatherInfo

    /**
     * Fetches the hourly weather forecast for the given location.
     *
     * @param location The location to fetch forecast for.
     * @return A list of hourly weather info.
     */
    suspend fun getHourlyForecast(location: Location): List<IntervalWeatherInfo>

    /**
     * Fetches the hourly weather history for the given location and time range.
     *
     * @param location The location to fetch history for.
     * @param start The start time of the history range.
     * @param end The end time of the history range.
     * @return A list of hourly weather info.
     */
    suspend fun getHourlyHistory(location: Location, start: Instant, end: Instant): List<IntervalWeatherInfo>
}
