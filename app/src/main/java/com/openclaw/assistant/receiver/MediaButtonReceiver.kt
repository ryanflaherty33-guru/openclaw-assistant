package com.openclaw.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import com.openclaw.assistant.data.SettingsRepository
import com.openclaw.assistant.service.OpenClawAssistantService

/**
 * Handles media button events from Bluetooth headsets and wired headphones.
 * When the headset button (KEYCODE_HEADSETHOOK) or play/pause button is pressed,
 * triggers the voice assistant if media button trigger is enabled in settings.
 */
class MediaButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val settings = SettingsRepository.getInstance(context)
        if (!settings.mediaButtonEnabled || !settings.hotwordEnabled) return

        val event: KeyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return

        // Only act on key-down to avoid double-triggering
        if (event.action != KeyEvent.ACTION_DOWN) return

        when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                Log.d(TAG, "Media button pressed (keyCode=${event.keyCode}) — triggering assistant")
                val serviceIntent = Intent(context, OpenClawAssistantService::class.java).apply {
                    action = OpenClawAssistantService.ACTION_SHOW_ASSISTANT
                }
                try {
                    context.startService(serviceIntent)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Background start failed, falling back to broadcast", e)
                    val broadcastIntent = Intent(OpenClawAssistantService.ACTION_SHOW_ASSISTANT).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcastIntent)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Background start failed, falling back to broadcast", e)
                    val broadcastIntent = Intent(OpenClawAssistantService.ACTION_SHOW_ASSISTANT).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcastIntent)
                }
                // Absorb the event so it doesn't reach the media player
                abortBroadcast()
            }
            else -> {
                Log.d(TAG, "Ignoring media button keyCode=${event.keyCode}")
            }
        }
    }
}
