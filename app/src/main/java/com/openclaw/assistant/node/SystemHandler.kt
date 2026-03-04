package com.openclaw.assistant.node

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.openclaw.assistant.R
import com.openclaw.assistant.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import android.media.AudioManager
import android.provider.Settings

class SystemHandler(private val appContext: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun handleNotify(paramsJson: String?): GatewaySession.InvokeResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return GatewaySession.InvokeResult.error("PERMISSION_REQUIRED", "POST_NOTIFICATIONS permission required")
            }
        }

        val params = paramsJson?.let {
            try {
                json.parseToJsonElement(it).jsonObject
            } catch (e: Exception) {
                null
            }
        } ?: return GatewaySession.InvokeResult.error("INVALID_REQUEST", "Expected JSON object")

        val title = (params["title"] as? JsonPrimitive)?.content ?: "OpenClaw Assistant"
        val message = (params["message"] as? JsonPrimitive)?.content ?: ""

        if (message.isEmpty()) {
            return GatewaySession.InvokeResult.error("INVALID_REQUEST", "Message is required")
        }

        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "openclaw_system"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "OpenClaw System Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)

        return GatewaySession.InvokeResult.ok("""{"ok":true}""")
    }

    fun handleVolume(paramsJson: String?): GatewaySession.InvokeResult {
        return try {
            val params = paramsJson?.let {
                try { json.parseToJsonElement(it).jsonObject } catch (e: Exception) { null }
            } ?: return GatewaySession.InvokeResult.error("INVALID_REQUEST", "Expected JSON object")

            val level = (params["level"] as? JsonPrimitive)?.intOrNull

            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            if (level != null) {
                // Set volume
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val safeLevel = level.coerceIn(0, 100)
                val newVolume = (safeLevel / 100f * maxVolume).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                GatewaySession.InvokeResult.ok("""{"level":$safeLevel}""")
            } else {
                // Get volume
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val percentage = if (maxVolume > 0) (currentVolume * 100 / maxVolume) else 0
                GatewaySession.InvokeResult.ok("""{"level":$percentage}""")
            }
        } catch (e: Exception) {
            GatewaySession.InvokeResult.error("INTERNAL_ERROR", e.message ?: "Unknown error")
        }
    }

    fun handleBrightness(paramsJson: String?): GatewaySession.InvokeResult {
        return try {
            val params = paramsJson?.let {
                try { json.parseToJsonElement(it).jsonObject } catch (e: Exception) { null }
            } ?: return GatewaySession.InvokeResult.error("INVALID_REQUEST", "Expected JSON object")

            val level = (params["level"] as? JsonPrimitive)?.intOrNull

            if (level != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(appContext)) {
                    return GatewaySession.InvokeResult.error("PERMISSION_DENIED", "WRITE_SETTINGS permission is not granted")
                }
                // Set brightness
                val safeLevel = level.coerceIn(0, 100)
                val newBrightness = (safeLevel / 100f * 255).toInt()
                
                // Disable auto-brightness if setting manually
                Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
                
                GatewaySession.InvokeResult.ok("""{"level":$safeLevel}""")
            } else {
                // Get brightness
                val currentBrightness = Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                val percentage = (currentBrightness * 100 / 255f).toInt()
                GatewaySession.InvokeResult.ok("""{"level":$percentage}""")
            }
        } catch (e: Settings.SettingNotFoundException) {
             GatewaySession.InvokeResult.error("INTERNAL_ERROR", "Could not read brightness setting")
        } catch (e: Exception) {
            GatewaySession.InvokeResult.error("INTERNAL_ERROR", e.message ?: "Unknown error")
        }
    }
}
