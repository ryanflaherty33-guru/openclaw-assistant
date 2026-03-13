package com.openclaw.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.openclaw.assistant.chat.ChatMessage
import com.openclaw.assistant.chat.ChatMessageContent
import com.openclaw.assistant.chat.ChatPendingToolCall
import com.openclaw.assistant.tools.ToolDisplayRegistry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.openclaw.assistant.R

@Composable
fun ChatMessageBubble(message: ChatMessage) {
  val isUser = message.role.lowercase() == "user"

  // Filter to only displayable content parts (text with content, or base64 images)
  val displayableContent = message.content.filter { part ->
    when (part.type) {
      "text" -> !part.text.isNullOrBlank()
      else -> part.base64 != null
    }
  }

  // Skip rendering entirely if no displayable content
  if (displayableContent.isEmpty()) return

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      color = Color.Transparent,
      modifier = Modifier.widthIn(min = 80.dp, max = LocalConfiguration.current.screenWidthDp.dp * 0.85f),
    ) {
      Box(
        modifier =
          Modifier
            .background(bubbleBackground(isUser))
            .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        val textColor = textColorOverBubble(isUser)
        ChatMessageBody(content = displayableContent, textColor = textColor)
      }
    }
  }
}

@Composable
private fun ChatMessageBody(content: List<ChatMessageContent>, textColor: Color) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    for (part in content) {
      when (part.type) {
        "text" -> {
          val text = part.text ?: continue
          ChatMarkdown(text = text, textColor = textColor)
        }
        else -> {
          val b64 = part.base64 ?: continue
          ChatBase64Image(base64 = b64, mimeType = part.mimeType)
        }
      }
    }
  }
}

@Composable
fun ChatTypingIndicatorBubble() {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DotPulse()
        Text(stringResource(R.string.thinking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
fun ChatPendingToolsBubble(toolCalls: List<ChatPendingToolCall>) {
  val context = LocalContext.current
  val displays =
    remember(toolCalls, context) {
      toolCalls.map { ToolDisplayRegistry.resolve(context, it.name, it.args) }
    }
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DotPulse()
          Text(stringResource(R.string.running_tools), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        for (display in displays.take(6)) {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              "${display.emoji} ${display.label}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontFamily = FontFamily.Monospace,
            )
            display.detailLine?.let { detail ->
              Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
        if (toolCalls.size > 6) {
          Text(
            "… +${toolCalls.size - 6} more",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun ChatStreamingAssistantBubble(text: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        ChatMarkdown(text = text, textColor = MaterialTheme.colorScheme.onSurface)
      }
    }
  }
}

@Composable
private fun bubbleBackground(isUser: Boolean): Brush {
  return if (isUser) {
    // Use a solid warm amber that has better readability than pure deep-orange on dark backgrounds
    Brush.linearGradient(
      colors = listOf(Color(0xFFE64A19), Color(0xFFBF360C)),
    )
  } else {
    Brush.linearGradient(
      colors = listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainerHigh),
    )
  }
}

@Composable
private fun textColorOverBubble(isUser: Boolean): Color {
  return if (isUser) {
    MaterialTheme.colorScheme.onPrimary
  } else {
    MaterialTheme.colorScheme.onSurface
  }
}

@Composable
private fun ChatBase64Image(base64: String, mimeType: String?) {
  val state = rememberBase64ImageState(base64)

  if (state.image != null) {
    Image(
      bitmap = state.image!!,
      contentDescription = mimeType ?: "attachment",
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxWidth(),
    )
  } else if (state.failed) {
    Text("Unsupported attachment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun DotPulse() {
  val transition = rememberInfiniteTransition(label = "dot_pulse")

  val alpha0 by transition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(600),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(0)
    ),
    label = "dot_alpha_0"
  )
  val alpha1 by transition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(600),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(200)
    ),
    label = "dot_alpha_1"
  )
  val alpha2 by transition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(600),
      repeatMode = RepeatMode.Reverse,
      initialStartOffset = StartOffset(400)
    ),
    label = "dot_alpha_2"
  )

  Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    PulseDot(alpha = alpha0)
    PulseDot(alpha = alpha1)
    PulseDot(alpha = alpha2)
  }
}

@Composable
private fun PulseDot(alpha: Float) {
  Surface(
    modifier = Modifier.size(6.dp).alpha(alpha),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  ) {}
}

@Composable
fun ChatCodeBlock(code: String, language: String?, textColor: Color = MaterialTheme.colorScheme.onSurface) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = textColor.copy(alpha = 0.15f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = code.trimEnd(),
      modifier = Modifier.padding(10.dp),
      fontFamily = FontFamily.Monospace,
      style = MaterialTheme.typography.bodySmall,
      color = textColor,
    )
  }
}
