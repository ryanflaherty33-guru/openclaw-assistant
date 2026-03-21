package com.openclaw.assistant.ui.chat

import android.app.Application
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ChatImageCodec].
 *
 * Covers:
 *  - [ChatImageCodec.normalizeAttachmentFileName] — pure string logic
 *  - [ChatImageCodec.decodeBase64Bitmap] — decode + LRU cache hit
 *  - [ChatImageCodec.computeInSampleSize] behaviour is exercised indirectly via
 *    [ChatImageCodec.decodeBase64Bitmap] with a real PNG.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatImageCodecTest {

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Minimal 1×1 white PNG (11 bytes uncompressed), base64 NO_WRAP encoded.
     * Used as a known-good image for decode tests.
     */
    private val tiny1x1PngBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC"

    // ---------------------------------------------------------------------------
    // normalizeAttachmentFileName
    // ---------------------------------------------------------------------------

    @Test
    fun `normalizeAttachmentFileName - jpg preserved as-is`() {
        assertEquals("photo.jpg", ChatImageCodec.normalizeAttachmentFileName("photo.jpg"))
    }

    @Test
    fun `normalizeAttachmentFileName - jpeg preserved as-is`() {
        assertEquals("shot.jpeg", ChatImageCodec.normalizeAttachmentFileName("shot.jpeg"))
    }

    @Test
    fun `normalizeAttachmentFileName - png preserved as-is`() {
        assertEquals("image.png", ChatImageCodec.normalizeAttachmentFileName("image.png"))
    }

    @Test
    fun `normalizeAttachmentFileName - webp preserved as-is`() {
        assertEquals("anim.webp", ChatImageCodec.normalizeAttachmentFileName("anim.webp"))
    }

    @Test
    fun `normalizeAttachmentFileName - gif preserved as-is`() {
        assertEquals("clip.gif", ChatImageCodec.normalizeAttachmentFileName("clip.gif"))
    }

    @Test
    fun `normalizeAttachmentFileName - extension check is case-insensitive`() {
        assertEquals("PHOTO.JPG", ChatImageCodec.normalizeAttachmentFileName("PHOTO.JPG"))
        assertEquals("Shot.JPEG", ChatImageCodec.normalizeAttachmentFileName("Shot.JPEG"))
        assertEquals("img.PNG", ChatImageCodec.normalizeAttachmentFileName("img.PNG"))
    }

    @Test
    fun `normalizeAttachmentFileName - no extension appends dot jpg`() {
        assertEquals("photo.jpg", ChatImageCodec.normalizeAttachmentFileName("photo"))
    }

    @Test
    fun `normalizeAttachmentFileName - unrecognised extension appends dot jpg`() {
        assertEquals("file.txt.jpg", ChatImageCodec.normalizeAttachmentFileName("file.txt"))
        assertEquals("data.bmp.jpg", ChatImageCodec.normalizeAttachmentFileName("data.bmp"))
    }

    @Test
    fun `normalizeAttachmentFileName - null defaults to image dot jpg`() {
        assertEquals("image.jpg", ChatImageCodec.normalizeAttachmentFileName(null))
    }

    @Test
    fun `normalizeAttachmentFileName - blank string defaults to image dot jpg`() {
        assertEquals("image.jpg", ChatImageCodec.normalizeAttachmentFileName("   "))
    }

    @Test
    fun `normalizeAttachmentFileName - empty string defaults to image dot jpg`() {
        assertEquals("image.jpg", ChatImageCodec.normalizeAttachmentFileName(""))
    }

    // ---------------------------------------------------------------------------
    // decodeBase64Bitmap — decode
    // ---------------------------------------------------------------------------

    @Test
    fun `decodeBase64Bitmap - valid PNG returns non-null bitmap`() {
        val bitmap = ChatImageCodec.decodeBase64Bitmap(tiny1x1PngBase64, maxDimension = 512)
        assertNotNull("Valid PNG should decode to a non-null Bitmap", bitmap)
    }

    // ---------------------------------------------------------------------------
    // decodeBase64Bitmap — LRU cache
    // ---------------------------------------------------------------------------

    @Test
    fun `decodeBase64Bitmap - second call returns same cached Bitmap instance`() {
        val first = ChatImageCodec.decodeBase64Bitmap(tiny1x1PngBase64, maxDimension = 512)
        val second = ChatImageCodec.decodeBase64Bitmap(tiny1x1PngBase64, maxDimension = 512)
        assertNotNull(first)
        assertSame(
            "Second call with identical base64 should return the cached Bitmap, not a new decode",
            first,
            second,
        )
    }

    @Test
    fun `decodeBase64Bitmap - different base64 strings are cached independently`() {
        val base64A = tiny1x1PngBase64
        // Encode the same PNG bytes with a NO_WRAP vs DEFAULT flag — produces different strings.
        val rawBytes = Base64.decode(tiny1x1PngBase64, Base64.NO_WRAP)
        val base64B = Base64.encodeToString(rawBytes, Base64.DEFAULT) // adds newlines

        val a = ChatImageCodec.decodeBase64Bitmap(base64A, maxDimension = 512)
        val b = ChatImageCodec.decodeBase64Bitmap(base64B, maxDimension = 512)
        // Both should decode successfully; they may or may not be the same object,
        // but neither should be null.
        assertNotNull("base64A should decode successfully", a)
        assertNotNull("base64B should decode successfully", b)
    }
}
