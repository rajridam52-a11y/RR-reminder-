package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.RRApplication
import com.example.alarm.AlarmScheduler
import com.example.data.model.AlarmEntity
import com.example.stopwatch.StopwatchManager
import com.example.timer.TimerManager
import com.example.timer.TimerService
import com.example.weather.WeatherRepository
import com.example.weather.model.IndianLocations
import com.example.worldclock.WorldClockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

enum class AssistantListeningState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class AssistantMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val actionFeedback: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class AssistantEngine(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AssistantEngine"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _listeningState = MutableStateFlow(AssistantListeningState.IDLE)
    val listeningState: StateFlow<AssistantListeningState> = _listeningState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _messages = MutableStateFlow<List<AssistantMessage>>(
        listOf(
            AssistantMessage(
                isUser = false,
                text = "Hello! I am Hey Reminder, your smart time and weather assistant. How can I help you today? You can speak in English or Hindi!"
            )
        )
    )
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val weatherRepo = WeatherRepository()

    init {
        initSpeechRecognizer()
        initTts()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@AssistantEngine)
            }
        }
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            val lang = if (RRApplication.instance.settingsRepository.getLanguage() == "hi") {
                Locale("hi", "IN")
            } else {
                Locale.ENGLISH
            }
            textToSpeech?.language = lang
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val lang = RRApplication.instance.settingsRepository.getLanguage()
            if (lang == "hi") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
            _listeningState.value = AssistantListeningState.LISTENING
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _listeningState.value = AssistantListeningState.ERROR
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _listeningState.value = AssistantListeningState.IDLE
            _rmsLevel.value = 0f
        } catch (_: Exception) {}
    }

    fun speak(text: String) {
        if (!RRApplication.instance.settingsRepository.isAssistantTtsEnabled()) return
        if (isTtsReady && textToSpeech != null) {
            _listeningState.value = AssistantListeningState.SPEAKING
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rr_assistant_utterance")
            scope.launch {
                val delayTime = (text.length * 75L).coerceIn(1500L, 8000L)
                kotlinx.coroutines.delay(delayTime)
                if (_listeningState.value == AssistantListeningState.SPEAKING) {
                    _listeningState.value = AssistantListeningState.IDLE
                }
            }
        }
    }

    fun processTextInput(input: String, onNavigate: ((String) -> Unit)? = null) {
        if (input.isBlank()) return
        val cleanInput = input.trim()

        _messages.value = _messages.value + AssistantMessage(isUser = true, text = cleanInput)
        _listeningState.value = AssistantListeningState.PROCESSING

        scope.launch {
            handleCommand(cleanInput, onNavigate)
        }
    }

    private suspend fun handleCommand(rawInput: String, onNavigate: ((String) -> Unit)? = null) {
        val lower = rawInput.lowercase().trim()
        val isHindi = rawInput.any { it in '\u0900'..'\u097F' } || lower.contains("बजे") || lower.contains("मौसम") || lower.contains("घड़ी")

        // 1. Time / Date queries
        if (lower.contains("what time") || lower.contains("current time") || lower.contains("समय") || lower.contains("कितने बजे")) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val reply = if (isHindi) "अभी समय है $timeStr." else "It is currently $timeStr."
            respond(reply)
            return
        }

        if (lower.contains("date") || lower.contains("what day") || lower.contains("तारीख") || lower.contains("दिन")) {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            val reply = if (isHindi) "आज की तारीख है $dateStr." else "Today is $dateStr."
            respond(reply)
            return
        }

        // 2. Navigation commands
        if (lower.contains("open settings") || lower.contains("go to settings") || lower.contains("सेटिंग्स")) {
            onNavigate?.invoke("settings")
            respond(if (isHindi) "सेटिंग्स खोल रहा हूँ।" else "Opening Settings screen for you.")
            return
        }
        if (lower.contains("go to home") || lower.contains("home screen") || lower.contains("होम")) {
            onNavigate?.invoke("home")
            respond(if (isHindi) "होम स्क्रीन पर जा रहे हैं।" else "Navigating to Home Dashboard.")
            return
        }
        if (lower.contains("open clock") || lower.contains("show clock") || lower.contains("घड़ी")) {
            onNavigate?.invoke("clock")
            respond(if (isHindi) "क्लॉक स्क्रीन खोल रहा हूँ।" else "Opening Clock.")
            return
        }

        // 3. Alarm Commands
        if (lower.contains("alarm") || lower.contains("अलार्म")) {
            if (lower.contains("cancel") || lower.contains("delete") || lower.contains("हटाओ") || lower.contains("बंद करो")) {
                withContext(Dispatchers.IO) {
                    val alarms = RRApplication.instance.database.alarmDao().getEnabledAlarms()
                    if (alarms.isNotEmpty()) {
                        val first = alarms.first()
                        RRApplication.instance.database.alarmDao().setAlarmEnabled(first.id, false)
                        AlarmScheduler.cancelAlarm(context, first.id)
                    }
                }
                respond(if (isHindi) "आपका अलार्म बंद कर दिया गया है।" else "Your upcoming alarm has been cancelled.")
                return
            }

            val alarmTime = parseAlarmTime(lower)
            if (alarmTime != null) {
                val (hour, minute) = alarmTime
                val newAlarm = AlarmEntity(
                    hour = hour,
                    minute = minute,
                    label = "Hey Reminder Alarm",
                    isEnabled = true,
                    repeatDaysMask = 0,
                    isVibrate = true,
                    soundTitle = "Radar Beep",
                    snoozeMinutes = 5
                )

                withContext(Dispatchers.IO) {
                    val id = RRApplication.instance.database.alarmDao().insertAlarm(newAlarm)
                    val scheduled = newAlarm.copy(id = id)
                    AlarmScheduler.scheduleAlarm(context, scheduled)
                }

                val period = if (hour >= 12) "PM" else "AM"
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                val formatted = String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, period)
                val reply = if (isHindi) "अलार्म $formatted के लिए सेट कर दिया गया है।" else "Alarm set successfully for $formatted."
                respond(reply, actionFeedback = "Alarm scheduled for $formatted")
                return
            }
        }

        // 4. Timer Commands
        if (lower.contains("timer") || lower.contains("टाइमर")) {
            if (lower.contains("pause") || lower.contains("रोक")) {
                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_PAUSE }
                context.startService(intent)
                respond(if (isHindi) "टाइमर रोक दिया गया है।" else "Timer paused.")
                return
            }
            if (lower.contains("resume") || lower.contains("चालू")) {
                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESUME }
                context.startService(intent)
                respond(if (isHindi) "टाइमर फिर से शुरू किया गया।" else "Timer resumed.")
                return
            }
            if (lower.contains("stop") || lower.contains("reset") || lower.contains("बंद")) {
                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESET }
                context.startService(intent)
                respond(if (isHindi) "टाइमर रीसेट कर दिया गया है।" else "Timer stopped and reset.")
                return
            }

            val minutes = extractTimerMinutes(lower)
            if (minutes > 0) {
                TimerManager.setDuration(0, minutes, 0)
                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_START }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                val reply = if (isHindi) "$minutes मिनट का टाइमर शुरू कर दिया गया है।" else "Started a $minutes-minute timer for you."
                respond(reply, actionFeedback = "$minutes min timer started")
                return
            }
        }

        // 5. Stopwatch Commands
        if (lower.contains("stopwatch") || lower.contains("स्टॉपवॉच")) {
            if (lower.contains("pause") || lower.contains("stop") || lower.contains("रोक")) {
                StopwatchManager.pause()
                respond(if (isHindi) "स्टॉपवॉच रोक दी गई है।" else "Stopwatch paused.")
                return
            }
            if (lower.contains("reset") || lower.contains("रीसेट")) {
                StopwatchManager.reset()
                respond(if (isHindi) "स्टॉपवॉच रीसेट हो गई है।" else "Stopwatch reset.")
                return
            }
            if (lower.contains("lap") || lower.contains("लैप")) {
                val lap = StopwatchManager.recordLap()
                if (lap != null) {
                    respond(if (isHindi) "लैप ${lap.lapNumber} रिकॉर्ड हुआ: ${lap.formattedLapTime}." else "Lap ${lap.lapNumber} recorded: ${lap.formattedLapTime}.")
                }
                return
            }
            if (lower.contains("start") || lower.contains("चालू")) {
                StopwatchManager.start()
                respond(if (isHindi) "स्टॉपवॉच शुरू हो गई है।" else "Stopwatch started!")
                return
            }
        }

        // 6. World Clock City query
        for (city in WorldClockRepository.predefinedCities) {
            if (lower.contains(city.cityName.lowercase())) {
                val entity = com.example.data.model.WorldClockEntity(
                    id = city.timeZoneId,
                    cityName = city.cityName,
                    countryName = city.countryName,
                    timeZoneId = city.timeZoneId
                )
                val reply = if (isHindi) {
                    "${city.cityName} में अभी समय है ${entity.formattedTime} (${entity.getTimeDifferenceFromLocal()})."
                } else {
                    "In ${city.cityName}, the current time is ${entity.formattedTime} (${entity.getTimeDifferenceFromLocal()})."
                }
                respond(reply)
                return
            }
        }

        // 7. Weather Commands
        if (lower.contains("weather") || lower.contains("rain") || lower.contains("मौसम") || lower.contains("बारिश")) {
            var targetCity = IndianLocations.citiesList.firstOrNull { lower.contains(it.name.lowercase()) }
            if (targetCity == null) {
                val matchedState = IndianLocations.statesAndUTs.firstOrNull { lower.contains(it.lowercase()) }
                if (matchedState != null) {
                    targetCity = IndianLocations.getCitiesForState(matchedState).firstOrNull()
                }
            }

            if (targetCity != null) {
                val result = weatherRepo.fetchWeather(targetCity)
                result.onSuccess { weather ->
                    val reply = if (isHindi) {
                        "${weather.cityName} में मौसम ${weather.conditionDescription} है, तापमान ${weather.currentTemp} है।"
                    } else {
                        "In ${weather.cityName}, it is currently ${weather.conditionDescription} with a temperature of ${weather.currentTemp}."
                    }
                    respond(reply, actionFeedback = "${weather.cityName}: ${weather.currentTemp}")
                    return
                }
            }
        }

        // Fallback
        val fallback = if (isHindi) {
            "क्षमा करें, मुझे समझ नहीं आया। आप कह सकते हैं: 'अलार्म 6 बजे सेट करो', '5 मिनट का टाइमर लगाओ', या 'मौसम बताओ'।"
        } else {
            "I'm here to help! You can try commands like 'Set alarm for 6 AM', 'Start a 5 minute timer', or 'What's the weather in Mumbai?'"
        }
        respond(fallback)
    }

    private fun parseAlarmTime(text: String): Pair<Int, Int>? {
        val isPm = text.contains("pm") || text.contains("evening") || text.contains("night") || text.contains("शाम") || text.contains("रात")
        val isAm = text.contains("am") || text.contains("morning") || text.contains("सुबह")

        val timePattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?")
        val matcher = timePattern.matcher(text)

        if (matcher.find()) {
            var hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: 0

            if (isPm && hour in 1..11) {
                hour += 12
            } else if (isAm && hour == 12) {
                hour = 0
            }
            if (hour in 0..23 && minute in 0..59) {
                return Pair(hour, minute)
            }
        }
        return null
    }

    private fun extractTimerMinutes(text: String): Int {
        if (text.contains("half hour")) return 30
        if (text.contains("1 hour")) return 60
        val pattern = Pattern.compile("(\\d+)\\s*(?:minute|min|मिनट)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull() ?: 5
        }
        return 5
    }

    private fun respond(text: String, actionFeedback: String? = null) {
        _messages.value = _messages.value + AssistantMessage(
            isUser = false,
            text = text,
            actionFeedback = actionFeedback
        )
        _listeningState.value = AssistantListeningState.IDLE
        speak(text)
    }

    override fun onReadyForSpeech(params: Bundle?) { _listeningState.value = AssistantListeningState.LISTENING }
    override fun onBeginningOfSpeech() { _listeningState.value = AssistantListeningState.LISTENING }
    override fun onRmsChanged(rmsdB: Float) { _rmsLevel.value = ((rmsdB + 2f) * 0.8f).coerceIn(0f, 10f) }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { _listeningState.value = AssistantListeningState.PROCESSING; _rmsLevel.value = 0f }
    override fun onError(error: Int) { _listeningState.value = AssistantListeningState.ERROR; _rmsLevel.value = 0f }
    override fun onResults(results: Bundle?) {
        _rmsLevel.value = 0f
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            processTextInput(text)
        } else {
            _listeningState.value = AssistantListeningState.IDLE
        }
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }
}
