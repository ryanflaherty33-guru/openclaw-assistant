package com.openclaw.assistant.node

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import com.openclaw.assistant.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@SuppressLint("MissingPermission")
class WifiHandler(
  private val context: Context,
  private val json: Json,
  private val invokeErrorFromThrowable: (Throwable) -> Pair<String, String>,
) {
  private val wifiManager: WifiManager? by lazy {
    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  }

  fun handleWifiStatus(): GatewaySession.InvokeResult {
    return try {
      val wm = wifiManager ?: return GatewaySession.InvokeResult.error("UNAVAILABLE", "WifiManager not available")
      val isEnabled = wm.isWifiEnabled
      val info = wm.connectionInfo

      val payloadObj = if (isEnabled && info != null && info.networkId != -1) {
        // Connected
        val ssid = info.ssid?.removeSurrounding("\"") ?: ""
        val bssid = info.bssid ?: ""
        val rssi = info.rssi
        val linkSpeed = info.linkSpeed
        val ipAddress = info.ipAddress
        val ipString = String.format(
          "%d.%d.%d.%d",
          ipAddress and 0xff,
          ipAddress shr 8 and 0xff,
          ipAddress shr 16 and 0xff,
          ipAddress shr 24 and 0xff
        )

        buildJsonObject {
          put("enabled", true)
          put("connected", true)
          put("ssid", ssid)
          put("bssid", bssid)
          put("rssi", rssi)
          put("linkSpeedMbps", linkSpeed)
          put("ipAddress", ipString)
        }
      } else {
        // Disconnected or disabled
        buildJsonObject {
          put("enabled", isEnabled)
          put("connected", false)
        }
      }
      GatewaySession.InvokeResult.ok(payloadObj.toString())
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }

  fun handleWifiList(): GatewaySession.InvokeResult {
    return try {
      val wm = wifiManager ?: return GatewaySession.InvokeResult.error("UNAVAILABLE", "WifiManager not available")
      if (!wm.isWifiEnabled) {
        return GatewaySession.InvokeResult.error("FAILED_PRECONDITION", "Wi-Fi is disabled")
      }

      // Note: Starting with Android 10, retrieving scan results requires location permission
      // and location services to be enabled. Assuming permissions are mostly granted or handled.
      // Simply retrieving last scan results.
      val results = wm.scanResults
      val payloadObj = buildJsonObject {
        put("networks", buildJsonArray {
          results.forEach { result ->
            add(buildJsonObject {
              put("ssid", result.SSID.removeSurrounding("\""))
              put("bssid", result.BSSID)
              put("rssi", result.level)
            })
          }
        })
      }

      GatewaySession.InvokeResult.ok(payloadObj.toString())
    } catch (e: SecurityException) {
      GatewaySession.InvokeResult.error("PERMISSION_DENIED", "Location permission is required to list Wi-Fi networks.")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }

  fun handleWifiConnect(paramsJson: String?): GatewaySession.InvokeResult {
    return try {
      // connecting programmatically to a specific SSID is complex and restricted on modern Android.
      // Usually requires Q network suggestion API or companion device manager.
      // Setting Wi-Fi enabled/disabled state is deprecated but might still work on older devices, 
      // or we can just mock the basic behavior/intent.
      // For this command, we'll try to just parse the 'enabled' flag if provided, to toggle Wi-Fi.
      
      val root = paramsJson?.let { json.parseToJsonElement(it).jsonObject } ?: return GatewaySession.InvokeResult.error("INVALID_ARGUMENT", "Missing parameters")
      
      // If action is to toggle wifi
      val enabled = root["enabled"]?.jsonPrimitive?.booleanOrNull
      if (enabled != null) {
          @Suppress("DEPRECATION")
          wifiManager?.setWifiEnabled(enabled)
          val payloadObj = buildJsonObject {
            put("enabled", enabled)
          }
          return GatewaySession.InvokeResult.ok(payloadObj.toString())
      }
      
      GatewaySession.InvokeResult.error("UNIMPLEMENTED", "Directly connecting to a specific SSID is currently not fully supported via this node capability.")
    } catch (e: Throwable) {
      val (code, msg) = invokeErrorFromThrowable(e)
      GatewaySession.InvokeResult.error(code, msg)
    }
  }
}
