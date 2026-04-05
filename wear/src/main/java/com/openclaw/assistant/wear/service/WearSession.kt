package com.openclaw.assistant.wear.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.openclaw.assistant.wear.WearApiClient
import com.openclaw.assistant.wear.WearSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff

private enum class SessionState {
    LISTENING, PROCESSING, SPEAKING, ERROR, DONE
}

/**
 * VoiceInteractionSession for Wear OS.
 * Handles the full voice pipeline: listen → API call → TTS.
 * Triggered via VoiceInteractionService (long-press hardware button when set as default).
 */
class WearSession(context: Context) : VoiceInteractionSession(context),
    LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settings by lazy { WearSettingsRepository.getInstance(context) }
    private val apiClient = WearApiClient()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var state by mutableStateOf(SessionState.LISTENING)
    private var displayText by mutableStateOf("")
    private var userQuery by mutableStateOf("")
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        initTts()
    }

    override fun onCreateContentView(): View {
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@WearSession)
            setViewTreeSavedStateRegistryOwner(this@WearSession)
            setContent {
                MaterialTheme {
                    Scaffold(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        ScalingLazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(stateColor(state))
                                        .clickable { if (state == SessionState.ERROR) startListening() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (state) {
                                        SessionState.PROCESSING -> CircularProgressIndicator(
                                            indicatorColor = Color.White,
                                            trackColor = Color.Transparent,
                                            strokeWidth = 3.dp
                                        )
                                        SessionState.ERROR -> Icon(
                                            imageVector = Icons.Default.MicOff,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                        else -> Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                            if (userQuery.isNotBlank()) {
                                item {
                                    Text(
                                        text = userQuery,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.button,
                                        color = Color.LightGray
                                    )
                                }
                            }
                            if (displayText.isNotBlank()) {
                                item {
                                    Text(
                                        text = displayText,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.body1
                                    )
                                }
                            }
                            if (errorMessage != null) {
                                item {
                                    Text(
                                        text = errorMessage!!,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.caption2,
                                        color = Color.Red
                                    )
                                }
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
        return composeView
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        WearSessionForegroundService.start(context)
        startListening()
    }

    override fun onHide() {
        super.onHide()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        WearSessionForegroundService.stop(context)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        speechRecognizer?.destroy()
        tts?.shutdown()
        WearSessionForegroundService.stop(context)
        super.onDestroy()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady && settings.speechLanguage.isNotBlank()) {
                tts?.language = Locale.forLanguageTag(settings.speechLanguage)
            }
            tts?.setSpeechRate(settings.ttsSpeed)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            setError("Speech recognition unavailable")
            return
        }
        state = SessionState.LISTENING
        errorMessage = null
        displayText = ""
        userQuery = ""

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    if (text.isBlank()) {
                        setError("No speech detected")
                    } else {
                        userQuery = text
                        callApi(text)
                    }
                }
                override fun onError(error: Int) {
                    setError("Speech error ($error)")
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { state = SessionState.PROCESSING }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { userQuery = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (settings.speechLanguage.isNotBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.speechLanguage)
                }
            }
            startListening(intent)
        }
    }

    private fun callApi(text: String) {
        state = SessionState.PROCESSING
        scope.launch {
            val result = apiClient.sendMessage(
                chatUrl = settings.getChatUrl(),
                message = text,
                authToken = settings.authToken.takeIf { it.isNotBlank() }
            )
            result.onSuccess { response ->
                displayText = response
                speak(response)
            }.onFailure { e ->
                Log.e(TAG, "API error", e)
                setError(e.message ?: "Network error")
            }
        }
    }

    private fun speak(text: String) {
        if (!settings.ttsEnabled || !ttsReady) {
            finishSession()
            return
        }
        state = SessionState.SPEAKING
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finishSession() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finishSession() }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wear_session")
    }

    private fun setError(message: String) {
        state = SessionState.ERROR
        errorMessage = message
    }

    private fun finishSession() {
        state = SessionState.DONE
        hide()
    }

    private fun stateColor(s: SessionState): Color = when (s) {
        SessionState.LISTENING -> Color(0xFF4CAF50)
        SessionState.PROCESSING -> Color(0xFFFFC107)
        SessionState.SPEAKING -> Color(0xFF2196F3)
        SessionState.ERROR -> Color(0xFFF44336)
        SessionState.DONE -> Color(0xFF9E9E9E)
    }

    companion object {
        private const val TAG = "WearSession"
    }
}
