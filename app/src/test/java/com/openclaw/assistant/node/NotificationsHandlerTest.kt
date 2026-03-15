package com.openclaw.assistant.node

import android.app.Application
import android.app.Notification
import android.content.Context
import com.openclaw.assistant.gateway.GatewaySession
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import android.service.notification.StatusBarNotification
import android.provider.Settings
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationsHandlerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val notificationManager = mockk<NotificationManager>()
    private val handler = NotificationsHandler(context, notificationManager)

    @Test
    fun `handleList returns error when service disabled`() = runBlocking {
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")

        val result = handler.handleList()

        assertEquals(false, result.ok)
        assertEquals("NOTIFICATIONS_PERMISSION_REQUIRED", result.error?.code)
    }

    @Test
    fun `handleList returns notifications when service enabled`() = runBlocking {
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            context.packageName
        )

        val sbn = mockk<StatusBarNotification>()
        val notification = Notification.Builder(context, "test-channel")
            .setContentTitle("Title")
            .setContentText("Text")
            .build()
        every { sbn.key } returns "test_key"
        every { sbn.packageName } returns "com.test"
        every { sbn.postTime } returns 12345L
        every { sbn.notification } returns notification

        every { notificationManager.getActiveNotifications() } returns listOf(sbn)

        val result = handler.handleList()

        assertEquals(true, result.ok)
        val json = result.payloadJson ?: ""
        assertEquals(true, json.contains("test_key"))
    }
}
