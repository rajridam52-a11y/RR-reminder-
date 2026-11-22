package com.example.ui.clock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DigitalClockWidget(
    modifier: Modifier = Modifier,
    configOverride: ClockAppearanceConfig? = null
) {
    val context = LocalContext.current
    val observedConfig by ClockAppearanceStore.observe(context).collectAsState()
    val config = configOverride ?: observedConfig ?: ClockAppearanceStore.load(context)

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val cal = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }
    val dateText = remember(currentTime / 60_000L) {
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(currentTime))
    }

    AnimatedContent(
        targetState = config.clockStyle,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "clock_style_transition",
        modifier = modifier.fillMaxWidth()
    ) { style ->
        when (style) {
            ClockDesignStyle.NEON_DIGITAL -> NeonDigitalClockCard(cal, dateText, config)
            ClockDesignStyle.CLASSIC_ANALOG -> ClassicAnalogClockCard(cal, dateText, config)
            ClockDesignStyle.RETRO_FLIP -> RetroFlipClockCard(cal, dateText, config)
            ClockDesignStyle.ORBITAL_RING -> OrbitalRingClockCard(cal, dateText, config)
            ClockDesignStyle.MINIMAL_CLEAN -> MinimalStudioClockCard(cal, dateText, config)
            ClockDesignStyle.DUAL_HORIZON -> DualHorizonClockCard(cal, dateText, config)
        }
    }
}

private fun formatTimeParts(cal: Calendar, use24Hour: Boolean): Triple<String, String, String> {
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val displayHour = if (use24Hour) {
        hour24
    } else {
        val h12 = hour24 % 12
        if (h12 == 0) 12 else h12
    }
    return Triple(
        String.format(Locale.getDefault(), "%02d", displayHour),
        String.format(Locale.getDefault(), "%02d", minute),
        String.format(Locale.getDefault(), "%02d", second)
    )
}

private fun amPmLabel(cal: Calendar): String =
    if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"

@Composable
private fun NeonDigitalClockCard(
    cal: Calendar,
    dateText: String,
    config: ClockAppearanceConfig
) {
    val (hh, mm, ss) = formatTimeParts(cal, config.use24Hour)
    val accent = config.accent.primary
    val timeText = if (config.showSeconds) "$hh:$mm:$ss" else "$hh:$mm"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), config.accent.secondary.copy(alpha = 0.3f))),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = timeText,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = accent
                )
                if (!config.use24Hour) {
                    Text(
                        text = amPmLabel(cal),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = dateText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ClassicAnalogClockCard(
    cal: Calendar,
    dateText: String,
    config: ClockAppearanceConfig
) {
    val accent = config.accent.primary
    val dialStroke = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val hourHandColor = MaterialTheme.colorScheme.onSurface
    val minuteHandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val faceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    val hours = cal.get(Calendar.HOUR)
    val minutes = cal.get(Calendar.MINUTE)
    val seconds = cal.get(Calendar.SECOND)
    val (hh, mm, _) = formatTimeParts(cal, config.use24Hour)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(196.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f - 8.dp.toPx()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    drawCircle(color = faceBg, radius = radius, center = center)
                    drawCircle(color = accent.copy(alpha = 0.55f), radius = radius, center = center, style = Stroke(width = 3.dp.toPx()))

                    // 60 tick marks
                    for (i in 0 until 60) {
                        val angleRad = (i * 6.0 - 90.0) * PI / 180.0
                        val isMajor = i % 5 == 0
                        val innerR = radius - (if (isMajor) 14.dp.toPx() else 7.dp.toPx())
                        val outerR = radius - 3.dp.toPx()
                        val start = Offset(
                            center.x + (innerR * cos(angleRad)).toFloat(),
                            center.y + (innerR * sin(angleRad)).toFloat()
                        )
                        val end = Offset(
                            center.x + (outerR * cos(angleRad)).toFloat(),
                            center.y + (outerR * sin(angleRad)).toFloat()
                        )
                        drawLine(
                            color = if (isMajor) accent else dialStroke,
                            start = start,
                            end = end,
                            strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Hour hand
                    val hourAngle = (((hours % 12) + minutes / 60f) * 30f - 90f) * PI / 180.0
                    drawLine(
                        color = hourHandColor,
                        start = center,
                        end = Offset(
                            center.x + (radius * 0.52f * cos(hourAngle)).toFloat(),
                            center.y + (radius * 0.52f * sin(hourAngle)).toFloat()
                        ),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Minute hand
                    val minAngle = ((minutes + seconds / 60f) * 6f - 90f) * PI / 180.0
                    drawLine(
                        color = minuteHandColor,
                        start = center,
                        end = Offset(
                            center.x + (radius * 0.75f * cos(minAngle)).toFloat(),
                            center.y + (radius * 0.75f * sin(minAngle)).toFloat()
                        ),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Second hand
                    if (config.showSeconds) {
                        val secAngle = (seconds * 6f - 90f) * PI / 180.0
                        drawLine(
                            color = accent,
                            start = Offset(
                                center.x - (radius * 0.16f * cos(secAngle)).toFloat(),
                                center.y - (radius * 0.16f * sin(secAngle)).toFloat()
                            ),
                            end = Offset(
                                center.x + (radius * 0.84f * cos(secAngle)).toFloat(),
                                center.y + (radius * 0.84f * sin(secAngle)).toFloat()
                            ),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    drawCircle(color = accent, radius = 5.dp.toPx(), center = center)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (config.use24Hour) "$hh:$mm • $dateText" else "$hh:$mm ${amPmLabel(cal)} • $dateText",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RetroFlipClockCard(
    cal: Calendar,
    dateText: String,
    config: ClockAppearanceConfig
) {
    val (hh, mm, ss) = formatTimeParts(cal, config.use24Hour)
    val accent = config.accent.primary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlipTile(value = hh, accent = accent)
                Text(":", fontSize = 32.sp, fontWeight = FontWeight.Black, color = accent)
                FlipTile(value = mm, accent = accent)
                if (config.showSeconds) {
                    Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlipTile(value = ss, accent = config.accent.secondary, isCompact = true)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!config.use24Hour) {
                    Text(
                        text = amPmLabel(cal),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = config.accent.onAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = dateText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FlipTile(value: String, accent: Color, isCompact: Boolean = false) {
    val tileColor = MaterialTheme.colorScheme.surfaceVariant
    val seamColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f)
    Box(
        modifier = Modifier
            .width(if (isCompact) 62.dp else 82.dp)
            .height(if (isCompact) 72.dp else 88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tileColor)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            fontSize = if (isCompact) 28.sp else 40.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(seamColor)
        )
    }
}

@Composable
private fun OrbitalRingClockCard(
    cal: Calendar,
    dateText: String,
    config: ClockAppearanceConfig
) {
    val accent = config.accent.primary
    val accent2 = config.accent.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val (hh, mm, ss) = formatTimeParts(cal, config.use24Hour)

    val secFraction = cal.get(Calendar.SECOND) / 60f
    val minFraction = (cal.get(Calendar.MINUTE) + secFraction) / 60f
    val hourFraction = ((cal.get(Calendar.HOUR_OF_DAY) % 12) + minFraction) / 12f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(198.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeOuter = 8.dp.toPx()
                    val strokeMid = 7.dp.toPx()
                    val strokeInner = 6.dp.toPx()

                    fun drawOrbit(inset: Float, strokeW: Float, sweepFraction: Float, color: Color) {
                        val topLeft = Offset(inset, inset)
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW)
                        )
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = (sweepFraction.coerceIn(0.02f, 1f)) * 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }

                    if (config.showSeconds) {
                        drawOrbit(8.dp.toPx(), strokeOuter, secFraction, accent)
                    }
                    drawOrbit(22.dp.toPx(), strokeMid, minFraction, accent2)
                    drawOrbit(35.dp.toPx(), strokeInner, hourFraction, accent.copy(alpha = 0.7f))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (config.showSeconds) "$hh:$mm:$ss" else "$hh:$mm",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!config.use24Hour) {
                        Text(
                            text = amPmLabel(cal),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = dateText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MinimalStudioClockCard(
    cal: Calendar,
    dateText: String,
    config: ClockAppearanceConfig
) {
    val (hh, mm, ss) = formatTimeParts(cal, config.use24Hour)
    val accent = config.accent.primary
    val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time).uppercase()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dayOfWeek,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    color = accent
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$hh:$mm",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (config.showSeconds) {
                        Text(
                            text = ":$ss",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                        )
                    }
                    if (!config.use24Hour) {
                        Text(
                            text = " ${amPmLabel(cal)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                    }
                }
                Text(
                    text = dateText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DualHorizonClockCard(
    cal: Calendar,
    dateText: String,
    config: ClockAppearanceConfig
) {
    val (hh, mm, ss) = formatTimeParts(cal, config.use24Hour)
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val dayProgress = ((hour24 * 60 + cal.get(Calendar.MINUTE)) / 1440f).coerceIn(0.02f, 1f)
    val greeting = when (hour24) {
        in 5..11 -> "GOOD MORNING"
        in 12..16 -> "GOOD AFTERNOON"
        in 17..20 -> "GOOD EVENING"
        else -> "NIGHT MODE"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(config.accent.primary.copy(alpha = 0.20f), config.accent.secondary.copy(alpha = 0.30f))
                )
            )
            .border(1.dp, config.accent.primary.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = config.accent.primary
                )
                Text(
                    text = if (config.use24Hour) "24H" else amPmLabel(cal),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (config.showSeconds) "$hh:$mm:$ss" else "$hh:$mm",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(dayProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(listOf(config.accent.primary, config.accent.secondary))
                        )
                )
            }
        }
    }
}
