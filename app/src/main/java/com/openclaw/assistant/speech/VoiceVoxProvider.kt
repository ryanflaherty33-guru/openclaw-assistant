package com.openclaw.assistant.speech

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Dummy implementation of VoiceVoxProvider for standard flavor.
 * VOICEVOX functionality is only available in full flavor.
 */
class VoiceVoxProvider(private val context: Context) : TTSProvider {
    
    override fun isAvailable(): Boolean = false
    
    override suspend fun speak(text: String): Boolean = false
    
    override fun stop() {
        // No-op
    }
    
    override fun shutdown() {
        // No-op
    }
    
    override fun getType(): String = TTSProviderType.VOICEVOX
    
    override fun getDisplayName(): String = "VOICEVOX"
    
    override fun isConfigured(): Boolean = false
    
    override fun getConfigurationError(): String? = "VOICEVOX is only available in full flavor"
    
    override fun speakWithProgress(text: String): Flow<TTSState> {
        throw NotImplementedError("VOICEVOX is only available in full flavor")
    }
    
    fun initialize(): Boolean = false
}
