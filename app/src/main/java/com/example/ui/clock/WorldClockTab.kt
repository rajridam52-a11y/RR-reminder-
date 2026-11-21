package com.example.ui.clock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.worldclock.WorldClockRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorldClockTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { WorldClockRepository(AppDatabase.getDatabase(context).worldClockDao()) }
    val clocks by repo.allClocks.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (clocks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "No world clocks",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No world clocks added", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap + to compare time zones worldwide.", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(clocks, key = { it.id }) { clock ->
                    key(tick) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        clock.cityName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "${clock.countryName} • ${clock.formattedDate}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        clock.getTimeDifferenceFromLocal(),
                                        fontSize = 12.sp,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        clock.formattedTime,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { scope.launch { repo.removeClock(clock.id) } },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove City", tint = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_world_clock_fab"),
            containerColor = Color(0xFF00E5FF),
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add World City")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select World City") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(WorldClockRepository.predefinedCities) { city ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    repo.addClock(city)
                                    showDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${city.cityName}, ${city.countryName}", color = Color.White)
                                Text(city.timeZoneId.substringAfter("/"), color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}
