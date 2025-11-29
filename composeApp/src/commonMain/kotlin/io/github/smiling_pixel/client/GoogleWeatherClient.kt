package io.github.smiling_pixel.client

import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.model.WeatherInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Implementation of [WeatherClient] using Google Weather API.
 *
 * Note: The endpoint and response structure are based on the Google Maps Platform Weather API documentation.
 * Please ensure the API key has the necessary permissions.
 *
 * @property apiKey The API key for accessing Google Maps Platform.
 * @property httpClient The Ktor HttpClient to use for requests.
 */
class GoogleWeatherClient(
    private val apiKey: String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }
) : WeatherClient {

    override suspend fun getWeather(location: Location): WeatherInfo {
        // Hypothetical endpoint based on standard Google API patterns.
        // Adjust the URL if the specific API version or path differs.
        // Example: https://weather.googleapis.com/v1/currentConditions
        val url = "https://weather.googleapis.com/v1/currentConditions"
        
        try {
            val response: GoogleWeatherResponse = httpClient.get(url) {
                parameter("key", apiKey)
                parameter("location", "${location.latitude},${location.longitude}")
                // Requesting metric units by default
                parameter("units", "metric") 
            }.body()

            return response.toWeatherInfo()
        } catch (e: Exception) {
            // Wrap and rethrow or handle specific errors
            throw Exception("Failed to fetch weather data: ${e.message}", e)
        }
    }
}

@Serializable
private data class GoogleWeatherResponse(
    @SerialName("currentConditions") val currentConditions: CurrentConditions
) {
    fun toWeatherInfo(): WeatherInfo {
        return WeatherInfo(
            temperature = currentConditions.temperature.value,
            condition = currentConditions.conditionDescription,
            humidity = currentConditions.humidity,
            windSpeed = currentConditions.windSpeed.value,
            locationName = "Current Location", // API might not return name
            iconUrl = null // Map condition to icon URL if possible
        )
    }
}

@Serializable
private data class CurrentConditions(
    @SerialName("temperature") val temperature: Temperature,
    @SerialName("conditionDescription") val conditionDescription: String,
    @SerialName("humidity") val humidity: Int,
    @SerialName("windSpeed") val windSpeed: WindSpeed
)

@Serializable
private data class Temperature(
    @SerialName("value") val value: Double,
    @SerialName("units") val units: String
)

@Serializable
private data class WindSpeed(
    @SerialName("value") val value: Double,
    @SerialName("units") val units: String
)
