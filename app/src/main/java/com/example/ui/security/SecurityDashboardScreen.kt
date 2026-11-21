package com.example.ui.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.integration.firebase.GoogleAuthHelper
import com.example.integration.firebase.authStateFlow
import com.example.integration.protection.DeviceRegistrationManager
import com.example.integration.protection.EmergencyLockdownManager
import com.example.integration.protection.LockdownState
import com.example.integration.worker.CommandSyncScheduler
import com.example.security.AuditStatus
import com.example.security.FullSecurityAuditReport
import com.example.security.SecurityAuditEngine
import com.example.security.backend.OwnerSecurityBridge
import com.example.security.events.SecurityEvent
import com.example.security.events.SecurityEventManager
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.PrimaryNeon
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SecurityDashboardScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val lockdownState by EmergencyLockdownManager.lockdownState.collectAsStateWithLifecycle()

    var isBusy by remember { mutableStateOf(false) }
    var isAdminVerified by remember { mutableStateOf<Boolean?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var auditReport by remember { mutableStateOf<FullSecurityAuditReport?>(null) }
    var recentEvents by remember { mutableStateOf<List<SecurityEvent>>(emptyList()) }

    LaunchedEffect(Unit) {
        auditReport = SecurityAuditEngine(context).runFullAudit()
        recentEvents = SecurityEventManager.getRecentEvents(20)
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        isAdminVerified = if (uid != null) {
            OwnerSecurityBridge.isCallerAuthorizedAdmin(context, uid)
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Protection") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (currentUser != null) PrimaryNeon else Color(0xFFFFB300)
                            )
                            Text("Firebase Identity & UID Admin Verification", fontWeight = FontWeight.Bold)
                        }

                        val user = currentUser
                        if (user == null) {
                            Text(
                                "Sign in with Google to bind this device to your Firebase UID, sync FCM push tokens, and verify server-controlled administrator privileges.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        isBusy = true
                                        GoogleAuthHelper.signInWithGoogle(context)
                                            .onSuccess { signedIn ->
                                                DeviceRegistrationManager.registerOrSyncDevice(context)
                                                statusMessage = "Signed in cleanly (${signedIn.uid.take(8)}...)"
                                            }
                                            .onFailure { err ->
                                                statusMessage = err.message ?: "Sign-in unavailable"
                                            }
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("google_signin_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                            ) {
                                Text("Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Account: ${user.email ?: "Google Account"}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Firebase UID: ${user.uid}", fontSize = 12.sp, color = AccentCyan)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = if (isAdminVerified == true) PrimaryNeon else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when (isAdminVerified) {
                                        true -> "Authorized Admin (UID matched in /admin_roles/config)"
                                        false -> "Standard Device Owner (not listed in /admin_roles/config)"
                                        null -> "Checking administrator permissions..."
                                    },
                                    fontSize = 12.sp,
                                    color = if (isAdminVerified == true) PrimaryNeon else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            DeviceRegistrationManager.registerOrSyncDevice(context)
                                                .onSuccess {
                                                    statusMessage = "Device & FCM token synced to Firestore (${it.deviceId})"
                                                }
                                                .onFailure {
                                                    statusMessage = "Device sync failed: ${it.message}"
                                                }
                                            isBusy = false
                                        }
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier.weight(1f).testTag("register_device_btn")
                                ) {
                                    Text("Sync Device")
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isBusy = true
                                            GoogleAuthHelper.signOut(context)
                                            statusMessage = "Signed out"
                                            isBusy = false
                                        }
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier.testTag("sign_out_btn")
                                ) {
                                    Text("Sign Out")
                                }
                            }
                        }

                        if (statusMessage != null) {
                            Text(statusMessage ?: "", fontSize = 12.sp, color = AccentCyan)
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Command Recovery & Lockdown Controls", fontWeight = FontWeight.Bold)
                        Text(
                            "Device ID: ${DeviceRegistrationManager.getDeviceId()} • Lockdown: ${lockdownState.name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    CommandSyncScheduler.triggerImmediateSync(context)
                                    statusMessage = "WorkManager command recovery triggered"
                                    scope.launch { recentEvents = SecurityEventManager.getRecentEvents(20) }
                                },
                                modifier = Modifier.weight(1f).testTag("sync_commands_btn")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Commands")
                            }
                            OutlinedButton(
                                onClick = {
                                    EmergencyLockdownManager.triggerLockdown("Manual Lockdown test from Security Dashboard")
                                },
                                modifier = Modifier.testTag("test_lockdown_btn")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lockdown", color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }

            val report = auditReport
            if (report != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryNeon)
                                    Text("Device Security Audit", fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "${report.score}/100 (${report.status.name})",
                                    fontWeight = FontWeight.Bold,
                                    color = when (report.status) {
                                        AuditStatus.SECURE -> PrimaryNeon
                                        AuditStatus.WARNING -> Color(0xFFFFB300)
                                        AuditStatus.CRITICAL -> Color(0xFFEF4444)
                                    }
                                )
                            }
                            report.checks.forEach { check ->
                                Text("• ${check.title}: ${check.description}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Text("Recent Security Journal (${recentEvents.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            if (recentEvents.isEmpty()) {
                item {
                    Text("No security events recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                items(recentEvents, key = { it.eventId }) { ev ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(ev.eventType, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(ev.severity.name, fontSize = 12.sp, color = AccentCyan)
                            }
                            Text(
                                "Source: ${ev.detectionSource} • Action: ${ev.actionTaken.name}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
