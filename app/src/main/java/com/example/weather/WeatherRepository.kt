package com.example.weather

import com.example.weather.api.WeatherApiService
import com.example.weather.model.CityLocation
import com.example.weather.model.IndianLocations
import com.example.weather.model.UIDailyForecast
import com.example.weather.model.UIHourlyForecast
import com.example.weather.model.UIWeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherRepository(
    private val apiService: WeatherApiService = WeatherApiService.create()
) {

    suspend fun fetchWeather(city: CityLocation): Result<UIWeather> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getForecast(
                latitude = city.latitude,
                longitude = city.longitude
            )
            val current = response.currentWeather
                ?: return@withContext Result.failure(Exception("Current weather unavailable"))

            val desc = getWeatherDescription(current.weathercode)
            val isDay = current.isDay == 1
            val windDir = getWindDirectionText(current.winddirection)
            val humidity = response.hourly?.relativeHumidity2m?.firstOrNull() ?: 55
            val rainProb = response.hourly?.precipitationProbability?.firstOrNull() ?: 10

            val sunriseRaw = response.daily?.sunrise?.firstOrNull() ?: ""
            val sunsetRaw = response.daily?.sunset?.firstOrNull() ?: ""
            val sunriseTime = formatIsoTime(sunriseRaw)
            val sunsetTime = formatIsoTime(sunsetRaw)

            val hourlyList = mutableListOf<UIHourlyForecast>()
            val hourlyDto = response.hourly
            if (hourlyDto != null) {
                val count = minOf(hourlyDto.time.size, hourlyDto.temperature2m.size, 24)
                for (i in 0 until count) {
                    val rawTime = hourlyDto.time[i]
                    val timeDisp = formatIsoHour(rawTime)
                    val tempDisp = "${hourlyDto.temperature2m[i].toInt()}°C"
                    val code = hourlyDto.weathercode?.getOrNull(i) ?: current.weathercode
                    val rain = hourlyDto.precipitationProbability?.getOrNull(i) ?: 0
                    hourlyList.add(
                        UIHourlyForecast(
                            timeDisplay = timeDisp,
                            tempDisplay = tempDisp,
                            conditionDescription = getWeatherDescription(code),
                            rainChance = rain,
                            weatherCode = code
                        )
                    )
                }
            }

            val dailyList = mutableListOf<UIDailyForecast>()
            val dailyDto = response.daily
            if (dailyDto != null) {
                val daysCount = minOf(
                    dailyDto.time.size,
                    dailyDto.temperature2mMax.size,
                    dailyDto.temperature2mMin.size,
                    7
                )
                for (i in 0 until daysCount) {
                    val rawDate = dailyDto.time[i]
                    val dayName = formatIsoDayName(rawDate, i)
                    val dateDisp = formatIsoDate(rawDate)
                    val maxT = "${dailyDto.temperature2mMax[i].toInt()}°C"
                    val minT = "${dailyDto.temperature2mMin[i].toInt()}°C"
                    val code = dailyDto.weathercode.getOrNull(i) ?: current.weathercode
                    val rain = dailyDto.precipitationProbabilityMax?.getOrNull(i) ?: 0
                    val sRise = formatIsoTime(dailyDto.sunrise?.getOrNull(i) ?: "")
                    val sSet = formatIsoTime(dailyDto.sunset?.getOrNull(i) ?: "")

                    dailyList.add(
                        UIDailyForecast(
                            dayName = dayName,
                            dateDisplay = dateDisp,
                            maxTemp = maxT,
                            minTemp = minT,
                            conditionDescription = getWeatherDescription(code),
                            rainChance = rain,
                            weatherCode = code,
                            sunrise = sRise,
                            sunset = sSet
                        )
                    )
                }
            }

            val nowFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val lastUpdated = nowFormat.format(Date())
            val feelsLike = "${(current.temperature + 1.5).toInt()}°C"

            val uiWeather = UIWeather(
                cityName = city.name,
                stateName = city.state,
                currentTemp = "${current.temperature.toInt()}°C",
                feelsLikeTemp = feelsLike,
                conditionDescription = desc,
                humidityDisplay = "$humidity%",
                windSpeedDisplay = "${current.windspeed.toInt()} km/h",
                windDirection = windDir,
                rainProbability = "$rainProb%",
                sunriseTime = sunriseTime,
                sunsetTime = sunsetTime,
                lastUpdated = lastUpdated,
                isDay = isDay,
                weatherCode = current.weathercode,
                hourlyList = hourlyList,
                dailyList = dailyList
            )

            Result.success(uiWeather)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63 -> "Moderate Rain"
            65 -> "Heavy Rain"
            71, 73, 75 -> "Snowfall"
            80, 81, 82 -> "Rain Showers"
            95 -> "Thunderstorm"
            96, 99 -> "Severe Thunderstorm"
            else -> "Partly Cloudy"
        }
    }

    private fun getWindDirectionText(degrees: Double): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = (((degrees + 22.5) % 360) / 45).toInt()
        return directions[index.coerceIn(0, 7)]
    }

    private fun formatIsoTime(iso: String): String {
        if (iso.isBlank()) return "06:00 AM"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val out = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = parser.parse(iso)
            if (date != null) out.format(date) else "06:00 AM"
        } catch (e: Exception) {
            if (iso.contains("T")) iso.substringAfter("T") else iso
        }
    }

    private fun formatIsoHour(iso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val out = SimpleDateFormat("hh a", Locale.getDefault())
            val date = parser.parse(iso)
            if (date != null) out.format(date) else iso
        } catch (e: Exception) {
            iso
        }
    }

    private fun formatIsoDayName(iso: String, index: Int): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val out = SimpleDateFormat("EEEE", Locale.getDefault())
            val date = parser.parse(iso)
            if (date != null) out.format(date) else "Day $index"
        } catch (e: Exception) {
            "Day $index"
        }
    }

    private fun formatIsoDate(iso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val out = SimpleDateFormat("MMM d", Locale.getDefault())
            val date = parser.parse(iso)
            if (date != null) out.format(date) else iso
        } catch (e: Exception) {
            iso
        }
    }
}
