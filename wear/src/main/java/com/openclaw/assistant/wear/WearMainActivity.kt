package com.openclaw.assistant.wear

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text

/**
 * Settings and status screen for the Wear OS app.
 * Allows the user to configure the server URL and auth token directly on the watch.
 */
class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = WearSettingsRepository.getInstance(this)

        setContent {
            var urlText by remember { mutableStateOf(settings.httpUrl) }
            var tokenText by remember { mutableStateOf(settings.authToken) }
            var ttsEnabled by remember { mutableStateOf(settings.ttsEnabled) }

            val inputTextStyle = TextStyle(color = Color.White, fontSize = 12.sp)
            val inputBoxModifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)

            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            Text(
                                text = getString(R.string.settings_title),
                                style = MaterialTheme.typography.title3,
                                textAlign = TextAlign.Center,
                                color = Color.White
                            )
                        }

                        // Status chip
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        if (settings.isConfigured())
                                            getString(R.string.status_configured)
                                        else
                                            getString(R.string.status_not_configured),
                                        style = MaterialTheme.typography.caption1
                                    )
                                },
                                onClick = {},
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = if (settings.isConfigured())
                                        Color(0xFF1B5E20) else Color(0xFF4E342E)
                                )
                            )
                        }

                        // Server URL label
                        item {
                            Text(
                                text = getString(R.string.server_url_label),
                                style = MaterialTheme.typography.caption2,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        item {
                            Box(modifier = inputBoxModifier) {
                                BasicTextField(
                                    value = urlText,
                                    onValueChange = { urlText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = inputTextStyle,
                                    cursorBrush = SolidColor(Color.White),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true
                                )
                            }
                        }

                        // Auth Token label
                        item {
                            Text(
                                text = getString(R.string.auth_token_label),
                                style = MaterialTheme.typography.caption2,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        item {
                            Box(modifier = inputBoxModifier) {
                                BasicTextField(
                                    value = tokenText,
                                    onValueChange = { tokenText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = inputTextStyle,
                                    cursorBrush = SolidColor(Color.White),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    singleLine = true
                                )
                            }
                        }

                        // TTS toggle (Chip that toggles on click)
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { ttsEnabled = !ttsEnabled },
                                label = {
                                    Text(
                                        "${getString(R.string.tts_enabled_label)}: ${if (ttsEnabled) "ON" else "OFF"}",
                                        style = MaterialTheme.typography.caption1
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = if (ttsEnabled) Color(0xFF1B5E20) else Color(0xFF4E342E)
                                )
                            )
                        }

                        // Save button
                        item {
                            Button(
                                onClick = {
                                    val trimmedUrl = urlText.trim()
                                    if (trimmedUrl.isNotBlank() && !com.openclaw.assistant.shared.utils.NetworkUtils.isUrlSecure(trimmedUrl)) {
                                        android.widget.Toast.makeText(this@WearMainActivity, "Insecure URL: Only HTTPS or local HTTP allowed.", android.widget.Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    settings.httpUrl = trimmedUrl
                                    settings.authToken = tokenText.trim()
                                    settings.ttsEnabled = ttsEnabled
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(getString(R.string.save_button))
                            }
                        }

                        // Open system assistant settings
                        item {
                            Button(
                                onClick = {
                                    startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    getString(R.string.open_assistant_settings),
                                    style = MaterialTheme.typography.caption1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
