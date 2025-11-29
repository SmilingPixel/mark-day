package io.github.smiling_pixel.client

import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.model.WeatherInfo

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
}
