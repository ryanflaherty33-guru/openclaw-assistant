package com.openclaw.assistant.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import com.openclaw.assistant.node.JpegSizeLimiter
import java.io.ByteArrayOutputStream

private const val MAX_ATTACHMENT_DIMENSION = 1600

// JpegSizeLimiter works in raw JPEG bytes; base64 adds ~33% overhead.
// 225 000 raw bytes → ~300 000 base64 chars (~300 KB budget).
private const val MAX_JPEG_BYTES = 225_000

data class ImageAttachmentData(val base64: String, val width: Int, val height: Int)

internal object ChatImageCodec {

    // LRU cache keyed by an MD5 hash of the base64 string.
    // Full base64 strings (~200-400 KB per image) are NOT counted in the 4 MB capacity budget,
    // so storing them as keys would silently exceed the intended memory limit.
    private val bitmapCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun cacheKey(base64: String): String =
        java.security.MessageDigest.getInstance("MD5")
            .digest(base64.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Loads an image from [uri], scales it down to [maxDimension] px on the longest edge,
     * and JPEG-compresses it via [JpegSizeLimiter] to stay within [maxBase64Bytes] base64 chars.
     * Returns null if the image cannot be read or decoded.
     */
    fun loadSizedImageAttachment(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int = MAX_ATTACHMENT_DIMENSION,
        maxBase64Bytes: Int = MAX_JPEG_BYTES,
    ): ImageAttachmentData? {
        val rawBytes = resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            out.toByteArray()
        } ?: return null
        if (rawBytes.isEmpty()) return null

        // Cheaply measure dimensions before full decode.
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val sampleSize = computeInSampleSize(boundsOpts, maxDimension, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return null

        return try {
            val result = JpegSizeLimiter.compressToLimit(
                initialWidth = bitmap.width,
                initialHeight = bitmap.height,
                startQuality = 85,
                maxBytes = maxBase64Bytes,
            ) { w, h, q ->
                val toCompress = if (w != bitmap.width || h != bitmap.height) {
                    Bitmap.createScaledBitmap(bitmap, w, h, true)
                } else {
                    bitmap
                }
                try {
                    val out = ByteArrayOutputStream()
                    toCompress.compress(Bitmap.CompressFormat.JPEG, q, out)
                    out.toByteArray()
                } finally {
                    if (toCompress !== bitmap) toCompress.recycle()
                }
            }
            ImageAttachmentData(
                base64 = Base64.encodeToString(result.bytes, Base64.NO_WRAP),
                width = result.width,
                height = result.height,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Decodes a base64-encoded JPEG/PNG to a [Bitmap], sampling down to at most [maxDimension]
     * pixels on the longest edge. Results are cached in an LRU cache.
     */
    fun decodeBase64Bitmap(base64: String, maxDimension: Int = 512): Bitmap? {
        val key = cacheKey(base64)
        bitmapCache.get(key)?.let { return it }

        val bytes = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Throwable) {
            return null
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val sampleSize = computeInSampleSize(boundsOpts, maxDimension, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    /**
     * Calculates the largest power-of-two [BitmapFactory.Options.inSampleSize] that keeps
     * the decoded bitmap within [reqWidth] x [reqHeight].
     */
    private fun computeInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val outHeight = options.outHeight
        val outWidth = options.outWidth
        var inSampleSize = 1
        if (outHeight > reqHeight || outWidth > reqWidth) {
            val halfHeight = outHeight / 2
            val halfWidth = outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight || halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /** Ensures the file name has a recognised image extension, defaulting to `.jpg`. */
    internal fun normalizeAttachmentFileName(fileName: String?): String {
        val name = fileName?.trim()?.takeIf { it.isNotEmpty() } ?: "image"
        val lower = name.lowercase()
        return if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")
        ) {
            name
        } else {
            "$name.jpg"
        }
    }
}
