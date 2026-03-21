package com.openclaw.assistant.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Base64ImageState(
    image: ImageBitmap? = null,
    failed: Boolean = false,
) {
    var image by mutableStateOf(image)
    var failed by mutableStateOf(failed)
}

@Composable
fun rememberBase64ImageState(base64: String): Base64ImageState {
    val state = remember(base64) { Base64ImageState() }

    LaunchedEffect(base64) {
        state.failed = false
        state.image =
            withContext(Dispatchers.Default) {
                try {
                    val bitmap = ChatImageCodec.decodeBase64Bitmap(base64, maxDimension = 512)
                        ?: return@withContext null
                    bitmap.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
        if (state.image == null) state.failed = true
    }

    return state
}
