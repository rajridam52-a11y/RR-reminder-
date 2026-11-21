package com.example.worldclock

import com.example.data.dao.WorldClockDao
import com.example.data.model.WorldClockEntity
import kotlinx.coroutines.flow.Flow

data class AvailableWorldCity(
    val cityName: String,
    val countryName: String,
    val timeZoneId: String
)

class WorldClockRepository(private val dao: WorldClockDao) {

    val allClocks: Flow<List<WorldClockEntity>> = dao.getAllClocksFlow()

    suspend fun addClock(city: AvailableWorldCity) {
        val count = dao.getClockCount()
        val entity = WorldClockEntity(
            id = city.timeZoneId,
            cityName = city.cityName,
            countryName = city.countryName,
            timeZoneId = city.timeZoneId,
            orderIndex = count
        )
        dao.insertClock(entity)
    }

    suspend fun removeClock(clockId: String) {
        dao.deleteClockById(clockId)
    }

    companion object {
        val predefinedCities: List<AvailableWorldCity> = listOf(
            AvailableWorldCity("New Delhi", "India", "Asia/Kolkata"),
            AvailableWorldCity("Mumbai", "India", "Asia/Kolkata"),
            AvailableWorldCity("Bengaluru", "India", "Asia/Kolkata"),
            AvailableWorldCity("London", "United Kingdom", "Europe/London"),
            AvailableWorldCity("New York", "United States", "America/New_York"),
            AvailableWorldCity("Los Angeles", "United States", "America/Los_Angeles"),
            AvailableWorldCity("San Francisco", "United States", "America/Los_Angeles"),
            AvailableWorldCity("Chicago", "United States", "America/Chicago"),
            AvailableWorldCity("Tokyo", "Japan", "Asia/Tokyo"),
            AvailableWorldCity("Paris", "France", "Europe/Paris"),
            AvailableWorldCity("Berlin", "Germany", "Europe/Berlin"),
            AvailableWorldCity("Rome", "Italy", "Europe/Rome"),
            AvailableWorldCity("Dubai", "United Arab Emirates", "Asia/Dubai"),
            AvailableWorldCity("Singapore", "Singapore", "Asia/Singapore"),
            AvailableWorldCity("Sydney", "Australia", "Australia/Sydney"),
            AvailableWorldCity("Melbourne", "Australia", "Australia/Melbourne"),
            AvailableWorldCity("Toronto", "Canada", "America/Toronto"),
            AvailableWorldCity("Vancouver", "Canada", "America/Vancouver"),
            AvailableWorldCity("Hong Kong", "Hong Kong", "Asia/Hong_Kong"),
            AvailableWorldCity("Bangkok", "Thailand", "Asia/Bangkok"),
            AvailableWorldCity("Seoul", "South Korea", "Asia/Seoul"),
            AvailableWorldCity("Moscow", "Russia", "Europe/Moscow"),
            AvailableWorldCity("Cairo", "Egypt", "Africa/Cairo"),
            AvailableWorldCity("Johannesburg", "South Africa", "Africa/Johannesburg"),
            AvailableWorldCity("Sao Paulo", "Brazil", "America/Sao_Paulo"),
            AvailableWorldCity("Auckland", "New Zealand", "Pacific/Auckland"),
            AvailableWorldCity("Honolulu", "United States", "Pacific/Honolulu")
        )
    }
}
