package com.openclaw.assistant.speech

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stub implementation of VoiceVoxModelManager for standard flavor.
 * VOICEVOX functionality is only available in the full flavor.
 */
class VoiceVoxModelManager(private val context: Context) {

    fun isDictionaryReady(): Boolean = false

    fun isVvmModelReady(vvmFileName: String): Boolean = false

    fun getDownloadedVvmFiles(): List<String> = emptyList()

    fun getVvmFileSizeMB(vvmFileName: String): String = "N/A"

    fun deleteVvmModel(vvmFileName: String): Boolean = false

    suspend fun copyDictionaryFromAssets(): Flow<CopyProgress> = flow {
        emit(CopyProgress.Error("VOICEVOX is only available in the full flavor"))
    }

    fun downloadVvmModel(vvmFileName: String): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Error("VOICEVOX is only available in the full flavor"))
    }

    sealed class CopyProgress {
        data class Copying(val percent: Int) : CopyProgress()
        object Success : CopyProgress()
        data class Error(val message: String) : CopyProgress()
    }

    sealed class DownloadProgress {
        data class Downloading(val percent: Int) : DownloadProgress()
        object Success : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }
}
