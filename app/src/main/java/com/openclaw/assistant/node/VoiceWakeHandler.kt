package com.openclaw.assistant.node

import com.openclaw.assistant.VoiceWakeMode
import com.openclaw.assistant.gateway.GatewaySession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class VoiceWakeHandler(
  private val json: Json,
  private val voiceWakeMode: () -> VoiceWakeMode,
  private val setVoiceWakeMode: (VoiceWakeMode) -> Unit,
  private val voiceWakeStatusText: () -> String,
  private val invokeErrorFromThrowable: (Throwable) -> Pair<String, String>,
) {

  fun handleVoiceWakeGetMode(): GatewaySession.InvokeResult {
    return try {
      val mode = voiceWakeMode().name.lowercase()
      GatewaySession.InvokeResult.ok("""{"mode":"$mode"}""")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }

  fun handleVoiceWakeSetMode(paramsJson: String?): GatewaySession.InvokeResult {
    return try {
      val root = paramsJson?.let { json.parseToJsonElement(it).jsonObject }
        ?: return GatewaySession.InvokeResult.error("INVALID_ARGUMENT", "Missing parameters")

      val modeStr = root["mode"]?.jsonPrimitive?.content?.lowercase()
        ?: return GatewaySession.InvokeResult.error("INVALID_ARGUMENT", "Mode must be provided (off, foreground, always)")

      val newMode = when (modeStr) {
        "off" -> VoiceWakeMode.Off
        "foreground" -> VoiceWakeMode.Foreground
        "always" -> VoiceWakeMode.Always
        else -> return GatewaySession.InvokeResult.error("INVALID_ARGUMENT", "Invalid mode: $modeStr")
      }

      setVoiceWakeMode(newMode)
      GatewaySession.InvokeResult.ok("""{"success":true,"mode":"$modeStr"}""")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }

  fun handleVoiceWakeStatus(): GatewaySession.InvokeResult {
    return try {
      val status = voiceWakeStatusText()
      GatewaySession.InvokeResult.ok("""{"status":"$status"}""")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }
}
