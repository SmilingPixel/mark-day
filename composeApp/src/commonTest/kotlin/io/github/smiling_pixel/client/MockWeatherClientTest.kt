package io.github.smiling_pixel.client

import io.github.smiling_pixel.model.Location
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class MockWeatherClientTest {

    @Test
    fun testGetWeather() = runTest {
        val client = MockWeatherClient()
        val location = Location(latitude = 0.0, longitude = 0.0)
        
        val weatherInfo = client.getWeather(location)
        
        assertEquals(20.0, weatherInfo.temperature)
        assertEquals("Sunny", weatherInfo.condition)
        assertEquals(50, weatherInfo.humidity)
        assertEquals(10.0, weatherInfo.windSpeed)
        assertEquals("Mock Location", weatherInfo.locationName)
    }

    @Test
    fun testGetHourlyForecast() = runTest {
        val client = MockWeatherClient()
        val location = Location(latitude = 0.0, longitude = 0.0)
        
        val forecast = client.getHourlyForecast(location)
        assertTrue(forecast.isEmpty())
    }

    @Test
    fun testGetHourlyHistory() = runTest {
        val client = MockWeatherClient()
        val location = Location(latitude = 0.0, longitude = 0.0)
        
        val start = Clock.System.now()
        val end = Clock.System.now()
        
        val history = client.getHourlyHistory(location, start, end)
        assertTrue(history.isEmpty())
    }
}