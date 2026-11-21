package com.example.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.integration.protection.EmergencyLockdownManager

@Composable
fun EmergencyLockdownOverlay(
    onReleased: () -> Unit
) {
    var unlockKey by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val reason = EmergencyLockdownManager.getLockdownReason()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF450A0A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("emergency_lockdown_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1212)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚨 EMERGENCY LOCKDOWN",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF5252)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (reason.isNotEmpty()) "Reason: $reason" else "Device restricted by remote administrator command.",
                    color = Color(0xFFFECACA),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = unlockKey,
                    onValueChange = { unlockKey = it },
                    label = { Text("Master Admin Unlock Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("emergency_unlock_input")
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMsg ?: "", color = Color(0xFFFF5252), fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val released = EmergencyLockdownManager.releaseLockdown(unlockKey)
                        if (released) {
                            onReleased()
                        } else {
                            errorMsg = "Invalid unlock credentials"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("emergency_release_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Release Lockdown", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
