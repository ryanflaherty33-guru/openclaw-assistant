package com.openclaw.assistant.node

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import com.openclaw.assistant.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AppHandler(
  private val context: Context,
  private val json: Json,
  private val invokeErrorFromThrowable: (Throwable) -> Pair<String, String>,
) {
  fun handleAppLaunch(paramsJson: String?): GatewaySession.InvokeResult {
    return try {
      val root = paramsJson?.let { json.parseToJsonElement(it).jsonObject }
        ?: return GatewaySession.InvokeResult.error("INVALID_ARGUMENT", "Missing parameters")

      val packageName = root["packageName"]?.jsonPrimitive?.content
        ?: return GatewaySession.InvokeResult.error("INVALID_ARGUMENT", "packageName is required")

      val pm = context.packageManager
      val intent = pm.getLaunchIntentForPackage(packageName)
      if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        GatewaySession.InvokeResult.ok("""{"success":true}""")
      } else {
        GatewaySession.InvokeResult.error("NOT_FOUND", "App not found or cannot be launched: $packageName")
      }
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }

  fun handleAppList(): GatewaySession.InvokeResult {
    return try {
      val pm = context.packageManager
      // Note: Querying all packages requires QUERY_ALL_PACKAGES permission in Android 11+
      // For now, get installed packages, though it might be limited by package visibility rules.
      val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
      val apps = packages.filter { 
          (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName.contains("com.google.android") 
      }.joinToString(",") { appInfo ->
        val label = pm.getApplicationLabel(appInfo).toString().replace("\"", "\\\"")
        val pkg = appInfo.packageName
        """{"packageName":"$pkg","name":"$label"}"""
      }

      GatewaySession.InvokeResult.ok("""{"apps":[$apps]}""")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }
}
