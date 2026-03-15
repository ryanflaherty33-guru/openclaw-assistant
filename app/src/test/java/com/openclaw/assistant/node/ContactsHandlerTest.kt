package com.openclaw.assistant.node

import android.app.Application
import android.content.Context
import com.openclaw.assistant.gateway.GatewaySession
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.provider.ContactsContract
import android.database.MatrixCursor
import android.content.ContentResolver
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContactsHandlerTest {
    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val handler = ContactsHandler(context)

    @Test
    fun `handleSearch returns error when permission missing`() = runBlocking {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) } returns PackageManager.PERMISSION_DENIED

        val result = handler.handleSearch("""{"query":"test"}""")

        assertEquals(false, result.ok)
        assertEquals("CONTACTS_READ_PERMISSION_REQUIRED", result.error?.code)
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `handleSearch returns contacts when permission granted`() = runBlocking {
        mockkStatic(ContextCompat::class)
        every { context.contentResolver } returns contentResolver
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) } returns PackageManager.PERMISSION_GRANTED

        val cursor = MatrixCursor(arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        ))
        cursor.addRow(arrayOf("John Doe", "123456", 1L))

        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor

        val result = handler.handleSearch("""{"query":"John"}""")

        assertEquals(true, result.ok)
        assertEquals(true, result.payloadJson?.contains("John Doe"))
        unmockkStatic(ContextCompat::class)
    }
}
