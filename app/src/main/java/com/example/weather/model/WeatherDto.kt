package com.example.weather.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "timezone") val timezone: String,
    @Json(name = "current_weather") val currentWeather: CurrentWeatherDto?,
    @Json(name = "hourly") val hourly: HourlyDto?,
    @Json(name = "daily") val daily: DailyDto?
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    @Json(name = "temperature") val temperature: Double,
    @Json(name = "windspeed") val windspeed: Double,
    @Json(name = "winddirection") val winddirection: Double,
    @Json(name = "weathercode") val weathercode: Int,
    @Json(name = "is_day") val isDay: Int,
    @Json(name = "time") val time: String
)

@JsonClass(generateAdapter = true)
data class HourlyDto(
    @Json(name = "time") val time: List<String>,
    @Json(name = "temperature_2m") val temperature2m: List<Double>,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: List<Int>?,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>?,
    @Json(name = "weathercode") val weathercode: List<Int>?
)

@JsonClass(generateAdapter = true)
data class DailyDto(
    @Json(name = "time") val time: List<String>,
    @Json(name = "weathercode") val weathercode: List<Int>,
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>,
    @Json(name = "sunrise") val sunrise: List<String>?,
    @Json(name = "sunset") val sunset: List<String>?,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>?
)

data class UIHourlyForecast(
    val timeDisplay: String,
    val tempDisplay: String,
    val conditionDescription: String,
    val rainChance: Int,
    val weatherCode: Int
)

data class UIDailyForecast(
    val dayName: String,
    val dateDisplay: String,
    val maxTemp: String,
    val minTemp: String,
    val conditionDescription: String,
    val rainChance: Int,
    val weatherCode: Int,
    val sunrise: String,
    val sunset: String
)

data class UIWeather(
    val cityName: String,
    val stateName: String,
    val currentTemp: String,
    val feelsLikeTemp: String,
    val conditionDescription: String,
    val humidityDisplay: String,
    val windSpeedDisplay: String,
    val windDirection: String,
    val rainProbability: String,
    val sunriseTime: String,
    val sunsetTime: String,
    val lastUpdated: String,
    val isDay: Boolean,
    val weatherCode: Int,
    val hourlyList: List<UIHourlyForecast>,
    val dailyList: List<UIDailyForecast>
)
