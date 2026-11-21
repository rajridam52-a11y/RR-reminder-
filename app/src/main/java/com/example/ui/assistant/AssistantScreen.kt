package com.example.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
import androidx.core.content.ContextCompat
import com.example.assistant.AssistantEngine
import com.example.assistant.AssistantListeningState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.PrimaryNeon

@Composable
fun AssistantScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val engine = remember { AssistantEngine(context) }

    DisposableEffect(Unit) {
        onDispose { engine.destroy() }
    }

    val messages by engine.messages.collectAsState()
    val listeningState by engine.listeningState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var permissionDeniedNote by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = null
            engine.startListening()
        } else {
            permissionDeniedNote = "Microphone permission denied. You can still type English or Hindi commands below."
        }
    }

    val suggestions = listOf(
        "What time is it?",
        "Set alarm for 6:30 AM",
        "Start a 5 minute timer",
        "Weather in Mumbai",
        "अलार्म 7 बजे सेट करो"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("Hey Reminder Assistant", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Status: ${listeningState.name}",
            fontSize = 12.sp,
            color = if (listeningState == AssistantListeningState.LISTENING) PrimaryNeon else AccentCyan
        )

        if (permissionDeniedNote != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(permissionDeniedNote ?: "", color = Color(0xFFFFB300), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions) { s ->
                SuggestionChip(
                    onClick = { engine.processTextInput(s, onNavigate) },
                    label = { Text(s, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.isUser) Color(0xFF2563EB) else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 310.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(msg.text, color = Color.White, fontSize = 14.sp)
                            if (!msg.actionFeedback.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(msg.actionFeedback, color = PrimaryNeon, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask in English or Hindi...") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("assistant_text_input")
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        engine.processTextInput(inputText, onNavigate)
                        inputText = ""
                    }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("assistant_send_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = AccentCyan)
            }
            FilledIconButton(
                onClick = {
                    if (listeningState == AssistantListeningState.LISTENING) {
                        engine.stopListening()
                    } else {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            engine.startListening()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (listeningState == AssistantListeningState.LISTENING) Color(0xFFEF4444) else PrimaryNeon
                ),
                modifier = Modifier.size(48.dp).testTag("assistant_mic_btn")
            ) {
                Icon(
                    imageVector = if (listeningState == AssistantListeningState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = Color.Black
                )
            }
        }
    }
}
