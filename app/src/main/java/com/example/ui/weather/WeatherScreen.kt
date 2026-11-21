package com.example.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.PrimaryNeon
import com.example.weather.WeatherRepository
import com.example.weather.model.IndianLocations
import com.example.weather.model.UIWeather
import kotlinx.coroutines.launch

@Composable
fun WeatherScreen() {
    val repo = remember { WeatherRepository() }
    val scope = rememberCoroutineScope()

    var selectedState by remember { mutableStateOf("Delhi") }
    val stateCities = remember(selectedState) { IndianLocations.getCitiesForState(selectedState) }
    var selectedCity by remember(selectedState) { mutableStateOf(stateCities.firstOrNull() ?: IndianLocations.citiesList.first()) }
    var stateMenuExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var weather by remember { mutableStateOf<UIWeather?>(null) }

    fun loadForecast() {
        scope.launch {
            isLoading = true
            errorMessage = null
            repo.fetchWeather(selectedCity)
                .onSuccess {
                    weather = it
                    isLoading = false
                }
                .onFailure {
                    errorMessage = it.localizedMessage ?: "Failed to load live weather"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(selectedCity) {
        loadForecast()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { stateMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("weather_state_picker")
                    ) {
                        Icon(Icons.Default.LocationCity, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedState, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = stateMenuExpanded,
                        onDismissRequest = { stateMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        IndianLocations.statesAndUTs.forEach { state ->
                            DropdownMenuItem(
                                text = { Text(state) },
                                onClick = {
                                    selectedState = state
                                    stateMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { loadForecast() },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("weather_refresh_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Weather", tint = PrimaryNeon)
                }
            }
        }

        if (stateCities.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stateCities) { city ->
                        FilterChip(
                            selected = city.name == selectedCity.name,
                            onClick = { selectedCity = city },
                            label = { Text(city.name) }
                        )
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryNeon)
                }
            }
        } else if (errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Weather Unavailable", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorMessage ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { loadForecast() }) { Text("Retry") }
                    }
                }
            }
        } else {
            val w = weather
            if (w != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${w.cityName}, ${w.stateName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(44.dp))
                            Text(w.currentTemp, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = PrimaryNeon)
                            Text("${w.conditionDescription} • Feels like ${w.feelsLikeTemp}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                WeatherMetric("Humidity", w.humidityDisplay)
                                WeatherMetric("Wind", "${w.windSpeedDisplay} ${w.windDirection}")
                                WeatherMetric("Rain", w.rainProbability)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Sunrise ${w.sunriseTime} • Sunset ${w.sunsetTime}", fontSize = 12.sp, color = AccentCyan)
                        }
                    }
                }

                item {
                    Text("Next 24 Hours", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(w.hourlyList) { h ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(h.timeDisplay, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(h.tempDisplay, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNeon)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(12.dp))
                                        Text(" ${h.rainChance}%", fontSize = 11.sp, color = AccentCyan)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("7-Day Forecast", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                items(w.dailyList) { d ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${d.dayName} (${d.dateDisplay})", fontWeight = FontWeight.SemiBold)
                                Text("${d.conditionDescription} • Rain ${d.rainChance}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${d.maxTemp} / ${d.minTemp}", fontWeight = FontWeight.Bold, color = AccentCyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
