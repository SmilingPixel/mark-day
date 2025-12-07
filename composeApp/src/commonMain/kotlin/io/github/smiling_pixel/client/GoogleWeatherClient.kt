package io.github.smiling_pixel.client

import io.github.smiling_pixel.model.IntervalWeatherInfo
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.model.WeatherInfo
import io.github.smiling_pixel.preference.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Implementation of [WeatherClient] using Google Weather API.
 *
 * Note: The endpoint and response structure are based on the Google Maps Platform Weather API documentation.
 * Please ensure the API key has the necessary permissions.
 *
 * @property settingsRepository The repository to fetch the API key.
 * @property httpClient The Ktor HttpClient to use for requests.
 */
class GoogleWeatherClient(
    private val settingsRepository: SettingsRepository,
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

    private suspend fun getApiKey(): String {
        return settingsRepository.googleWeatherApiKey.first()
            ?: throw IllegalStateException("Google Weather API Key not set")
    }

    override suspend fun getWeather(location: Location): WeatherInfo {
        val apiKey = getApiKey()
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

    override suspend fun getHourlyForecast(location: Location): List<IntervalWeatherInfo> {
        val apiKey = getApiKey()
        val url = "https://weather.googleapis.com/v1/forecast/hours:lookup"
        try {
            val response: GoogleHourlyForecastResponse = httpClient.get(url) {
                parameter("key", apiKey)
                parameter("location.latitude", location.latitude)
                parameter("location.longitude", location.longitude)
                parameter("units", "metric")
                // parameter("hours", 24) // Default is 24
            }.body()
            return response.forecastHours.map { it.toIntervalWeatherInfo() }
        } catch (e: Exception) {
            throw Exception("Failed to fetch hourly forecast: ${e.message}", e)
        }
    }

    override suspend fun getHourlyHistory(location: Location, start: Instant, end: Instant): List<IntervalWeatherInfo> {
        val apiKey = getApiKey()
        val url = "https://weather.googleapis.com/v1/history/hours:lookup"
        try {
            val response: GoogleHourlyHistoryResponse = httpClient.get(url) {
                parameter("key", apiKey)
                parameter("location.latitude", location.latitude)
                parameter("location.longitude", location.longitude)
                parameter("units", "metric")
            }.body()
            return response.historyHours.map { it.toIntervalWeatherInfo() }
        } catch (e: Exception) {
            throw Exception("Failed to fetch hourly history: ${e.message}", e)
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
private data class GoogleHourlyForecastResponse(
    @SerialName("forecastHours") val forecastHours: List<HourlyItem>
)

@Serializable
private data class GoogleHourlyHistoryResponse(
    @SerialName("historyHours") val historyHours: List<HourlyItem>
)

@Serializable
private data class HourlyItem(
    @SerialName("interval") val interval: Interval,
    @SerialName("temperature") val temperature: TemperatureData,
    @SerialName("weatherCondition") val weatherCondition: WeatherConditionData,
    @SerialName("relativeHumidity") val relativeHumidity: Int,
    @SerialName("wind") val wind: WindData
) {
    fun toIntervalWeatherInfo(): IntervalWeatherInfo {
        return IntervalWeatherInfo(
            startTime = Instant.parse(interval.startTime),
            endTime = Instant.parse(interval.endTime),
            minTemperature = temperature.degrees,
            maxTemperature = temperature.degrees,
            condition = weatherCondition.description.text,
            humidity = relativeHumidity,
            windSpeed = wind.speed.value,
            iconUrl = null
        )
    }
}

@Serializable
private data class Interval(
    @SerialName("startTime") val startTime: String,
    @SerialName("endTime") val endTime: String
)

@Serializable
private data class TemperatureData(
    @SerialName("degrees") val degrees: Double
)

@Serializable
private data class WeatherConditionData(
    @SerialName("description") val description: DescriptionData
)

@Serializable
private data class DescriptionData(
    @SerialName("text") val text: String
)

@Serializable
private data class WindData(
    @SerialName("speed") val speed: SpeedData
)

@Serializable
private data class SpeedData(
    @SerialName("value") val value: Double
)

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
