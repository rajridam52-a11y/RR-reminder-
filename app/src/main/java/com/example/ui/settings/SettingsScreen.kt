package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.SettingsRepository
import com.example.integration.firebase.GoogleAuthHelper
import com.example.integration.firebase.authStateFlow
import com.example.integration.protection.DeviceRegistrationManager
import com.example.security.crypto.PasswordHasher
import com.example.ui.navigation.Screen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.PrimaryNeon
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onSettingsChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var theme by remember { mutableStateOf(repo.getTheme()) }
    var language by remember { mutableStateOf(repo.getLanguage()) }
    var soundEnabled by remember { mutableStateOf(repo.isSoundEnabled()) }
    var vibrationEnabled by remember { mutableStateOf(repo.isVibrationEnabled()) }
    var ttsEnabled by remember { mutableStateOf(repo.isAssistantTtsEnabled()) }
    var wakeWordEnabled by remember { mutableStateOf(repo.isWakeWordEnabled()) }
    var snoozeMins by remember { mutableStateOf(repo.getDefaultSnooze().toFloat()) }
    var biometricEnabled by remember { mutableStateOf(repo.isBiometricEnabled()) }
    var screenshotProtection by remember { mutableStateOf(repo.isScreenshotProtectionEnabled()) }
    var hasPinConfigured by remember { mutableStateOf(!repo.getAppLockPinHash().isNullOrBlank()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var isAccountBusy by remember { mutableStateOf(false) }
    var accountStatus by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PrimaryNeon)
                        Text("Google Account", fontWeight = FontWeight.Bold, color = PrimaryNeon)
                    }
                    val user = currentUser
                    if (user == null) {
                        Text(
                            "Sign in with Google to keep your account connected.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    isAccountBusy = true
                                    GoogleAuthHelper.signInWithGoogle(context)
                                        .onSuccess {
                                            DeviceRegistrationManager.registerOrSyncDevice(context)
                                            accountStatus = "Signed in (${it.email ?: "Google Account"})"
                                        }
                                        .onFailure { err ->
                                            accountStatus = err.message ?: "Sign-in unavailable"
                                        }
                                    isAccountBusy = false
                                }
                            },
                            enabled = !isAccountBusy,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_google_signin_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                        ) {
                            Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            user.email ?: "Connected Google Account",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isAccountBusy = true
                                    GoogleAuthHelper.signOut(context)
                                    accountStatus = "Signed out"
                                    isAccountBusy = false
                                }
                            },
                            enabled = !isAccountBusy,
                            modifier = Modifier.testTag("settings_sign_out_btn")
                        ) {
                            Text("Sign Out")
                        }
                    }
                    if (!accountStatus.isNullOrBlank()) {
                        Text(accountStatus ?: "", fontSize = 12.sp, color = AccentCyan)
                    }
                }
            }
        }

        item {
            SettingsActionCard(
                title = "Dashboard Studio",
                subtitle = "Customize which summary widgets appear on Home",
                icon = Icons.Default.DashboardCustomize,
                accent = AccentCyan,
                tag = "settings_studio_btn",
                onClick = { onNavigate(Screen.DashboardStudio.route) }
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance & Language", fontWeight = FontWeight.Bold, color = PrimaryNeon)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dark", "light", "system").forEach { mode ->
                            FilterChip(
                                selected = theme == mode,
                                onClick = {
                                    theme = mode
                                    repo.setTheme(mode)
                                    onSettingsChanged()
                                },
                                label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("en" to "English", "hi" to "Hindi (हिंदी)").forEach { (code, label) ->
                            FilterChip(
                                selected = language == code,
                                onClick = {
                                    language = code
                                    repo.setLanguage(code)
                                    onSettingsChanged()
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alarms & Assistant", fontWeight = FontWeight.Bold, color = AccentCyan)
                    SettingsToggleRow("Alarm Sound", soundEnabled) {
                        soundEnabled = it
                        repo.setSoundEnabled(it)
                    }
                    SettingsToggleRow("Alarm Vibration", vibrationEnabled) {
                        vibrationEnabled = it
                        repo.setVibrationEnabled(it)
                    }
                    SettingsToggleRow("Assistant Voice Reply (TTS)", ttsEnabled) {
                        ttsEnabled = it
                        repo.setAssistantTtsEnabled(it)
                    }
                    SettingsToggleRow("Wake Word Listener", wakeWordEnabled) {
                        wakeWordEnabled = it
                        repo.setWakeWordEnabled(it)
                    }
                    Text("Default Snooze: ${snoozeMins.toInt()} minutes", fontSize = 14.sp)
                    Slider(
                        value = snoozeMins,
                        onValueChange = { snoozeMins = it },
                        onValueChangeFinished = { repo.setDefaultSnooze(snoozeMins.toInt()) },
                        valueRange = 1f..30f
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Privacy & App Lock", fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Column {
                                Text("Security PIN Lock", fontWeight = FontWeight.Medium)
                                Text(
                                    if (hasPinConfigured) "PIN configured (PBKDF2 hashed)" else "Disabled",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { showPinDialog = true }) {
                                Text(if (hasPinConfigured) "Change" else "Set PIN")
                            }
                            if (hasPinConfigured) {
                                TextButton(onClick = {
                                    repo.setAppLockPinHash(null)
                                    hasPinConfigured = false
                                }) {
                                    Text("Clear", color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                    SettingsToggleRow("Biometric Unlock Preference", biometricEnabled) {
                        biometricEnabled = it
                        repo.setBiometricEnabled(it)
                    }
                    SettingsToggleRow("Screenshot Protection (FLAG_SECURE)", screenshotProtection) {
                        screenshotProtection = it
                        repo.setScreenshotProtectionEnabled(it)
                        onSettingsChanged()
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("RR Reminder v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                    Text("Developed by Ridam Raj", color = PrimaryNeon, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Package: ${BuildConfig.APPLICATION_ID}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configure Security PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 12) newPin = it.filter { c -> c.isDigit() } },
                    label = { Text("Enter 4-12 digit PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length >= 4) {
                            repo.setAppLockPinHash(PasswordHasher.hashPassword(newPin))
                            hasPinConfigured = true
                            showPinDialog = false
                        }
                    },
                    enabled = newPin.length >= 4
                ) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
