package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.data.SettingsRepository
import com.example.security.crypto.PasswordHasher
import com.example.security.events.SecurityAction
import com.example.security.events.SecurityEventManager

@Composable
fun AppLockOverlay(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLockout by remember { mutableStateOf(SecurityEventManager.isTemporaryLockoutActive()) }
    var lockoutSec by remember { mutableStateOf(SecurityEventManager.getRemainingLockoutSeconds()) }

    LaunchedEffect(isLockout) {
        while (isLockout) {
            kotlinx.coroutines.delay(1000L)
            isLockout = SecurityEventManager.isTemporaryLockoutActive()
            lockoutSec = SecurityEventManager.getRemainingLockoutSeconds()
            if (!isLockout) {
                errorMessage = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120).copy(alpha = 0.98f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .testTag("app_lock_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "App Lock",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "RR Secure Lock",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLockout) {
                        "Too many failed attempts. Locked for ${lockoutSec}s"
                    } else {
                        "Enter your security PIN to continue"
                    },
                    fontSize = 14.sp,
                    color = if (isLockout) Color(0xFFFF5252) else Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 12 && !isLockout) pin = it },
                    label = { Text("Security PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLockout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_lock_pin_input")
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val storedHash = settingsRepo.getAppLockPinHash()
                        val valid = if (storedHash.isNullOrBlank()) {
                            pin == "1234"
                        } else {
                            PasswordHasher.verifyPassword(pin, storedHash)
                        }
                        if (valid) {
                            SecurityEventManager.reportSuccessfulLogin("AppLockOverlay", "owner")
                            onUnlocked()
                        } else {
                            val action = SecurityEventManager.reportFailedLoginAttempt("AppLockOverlay", "owner")
                            isLockout = SecurityEventManager.isTemporaryLockoutActive()
                            lockoutSec = SecurityEventManager.getRemainingLockoutSeconds()
                            errorMessage = if (action == SecurityAction.BLOCKED || isLockout) {
                                "Too many attempts. Temporarily locked."
                            } else {
                                "Incorrect security PIN"
                            }
                            pin = ""
                        }
                    },
                    enabled = pin.isNotEmpty() && !isLockout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("app_lock_unlock_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("Unlock", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
