package io.github.smiling_pixel.client

import io.github.smiling_pixel.model.IntervalWeatherInfo
import io.github.smiling_pixel.model.Location
import io.github.smiling_pixel.model.WeatherInfo
import kotlin.time.Instant

class MockWeatherClient : WeatherClient {
    override suspend fun getWeather(location: Location): WeatherInfo {
        return WeatherInfo(
            temperature = 20.0,
            condition = "Sunny",
            humidity = 50,
            windSpeed = 10.0,
            locationName = "Mock Location"
        )
    }

    override suspend fun getHourlyForecast(location: Location): List<IntervalWeatherInfo> {
        return emptyList()
    }

    override suspend fun getHourlyHistory(location: Location, start: Instant, end: Instant): List<IntervalWeatherInfo> {
        return emptyList()
    }
}
