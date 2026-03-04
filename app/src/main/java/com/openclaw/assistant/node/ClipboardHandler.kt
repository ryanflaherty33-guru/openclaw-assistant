package com.openclaw.assistant.node

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.openclaw.assistant.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClipboardHandler(
  private val context: Context,
  private val json: Json,
  private val invokeErrorFromThrowable: (Throwable) -> Pair<String, String>,
) {
  private val clipboardManager: ClipboardManager? by lazy {
    context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  }

  fun handleClipboardRead(): GatewaySession.InvokeResult {
    return try {
      val clipMgr = clipboardManager ?: return GatewaySession.InvokeResult.error(
        "UNAVAILABLE",
        "ClipboardManager not available"
      )
      
      val clipData = clipMgr.primaryClip
      val text = if (clipData != null && clipData.itemCount > 0) {
        clipData.getItemAt(0).text?.toString() ?: ""
      } else {
        ""
      }

      val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n")
      GatewaySession.InvokeResult.ok("""{"text":"$escapedText"}""")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }

  fun handleClipboardWrite(paramsJson: String?): GatewaySession.InvokeResult {
    return try {
      val clipMgr = clipboardManager ?: return GatewaySession.InvokeResult.error(
        "UNAVAILABLE",
        "ClipboardManager not available"
      )

      val root = paramsJson?.let { json.parseToJsonElement(it).jsonObject }
        ?: return GatewaySession.InvokeResult.error("INVALID_ARGUMENT", "Missing parameters")

      val text = root["text"]?.jsonPrimitive?.content ?: ""
      val clip = ClipData.newPlainText("OpenClawAssistant", text)
      clipMgr.setPrimaryClip(clip)

      GatewaySession.InvokeResult.ok("""{"success":true}""")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }
}
