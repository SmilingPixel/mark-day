package io.github.smiling_pixel.model

import kotlinx.serialization.Serializable

/**
 * Represents the weather information for a specific location.
 *
 * @property temperature The temperature in Celsius.
 * @property condition The weather condition (e.g., "Sunny", "Cloudy").
 * @property humidity The humidity percentage (0-100).
 * @property windSpeed The wind speed in km/h.
 * @property locationName The name of the location.
 * @property iconUrl The URL of the weather icon, if available.
 */
@Serializable
data class WeatherInfo(
    val temperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double,
    val locationName: String,
    val iconUrl: String? = null
)

/**
 * Represents a geographical location.
 *
 * @property latitude The latitude of the location.
 * @property longitude The longitude of the location.
 */
@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double
)
