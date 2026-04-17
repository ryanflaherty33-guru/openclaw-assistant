package com.openclaw.assistant.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.assistant.OpenClawApplication
import com.openclaw.assistant.ui.theme.OpenClawAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

class DiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenClawAssistantTheme {
                DiagnosticsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OpenClawApplication
    val runtime = remember { app.peekRuntime() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionInfoPanel(runtime)
            NetworkDiagnosticsPanel(runtime)
            DeviceIdentityPanel(runtime)
            LiveLogPanel(context)
        }
    }
}

@Composable
private fun ConnectionInfoPanel(runtime: com.openclaw.assistant.node.NodeRuntime?) {
    val isConnected by runtime?.isConnected?.collectAsState() ?: remember { mutableStateOf(false) }
    val statusText by runtime?.statusText?.collectAsState() ?: remember { mutableStateOf("N/A") }
    val serverName by runtime?.serverName?.collectAsState() ?: remember { mutableStateOf(null) }
    val remoteAddress by runtime?.remoteAddress?.collectAsState() ?: remember { mutableStateOf(null) }
    val serverVersion by runtime?.serverVersion?.collectAsState() ?: remember { mutableStateOf(null) }

    val manualEnabled by runtime?.prefs?.manualEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val manualHost by runtime?.prefs?.manualHost?.collectAsState() ?: remember { mutableStateOf("") }
    val manualPort by runtime?.prefs?.manualPort?.collectAsState() ?: remember { mutableStateOf(18789) }
    val manualTls by runtime?.prefs?.manualTls?.collectAsState() ?: remember { mutableStateOf(true) }

    SectionCard(title = "Connection") {
        InfoRow("Status", if (isConnected) "Connected" else statusText)
        serverName?.let { InfoRow("Server", it) }
        remoteAddress?.let { InfoRow("Address", it) }
        serverVersion?.let { InfoRow("Version", it) }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Endpoint Config", fontWeight = FontWeight.Medium, fontSize = 13.sp)
        InfoRow("Mode", if (manualEnabled) "Manual" else "Discovery (mDNS)")
        if (manualEnabled) {
            InfoRow("Host", manualHost.ifBlank { "(not set)" })
            InfoRow("Port", manualPort.toString())
            InfoRow("TLS", if (manualTls) "Enabled" else "Disabled")
        }
    }
}

@Composable
private fun NetworkDiagnosticsPanel(runtime: com.openclaw.assistant.node.NodeRuntime?) {
    val scope = rememberCoroutineScope()
    var tcpResult by remember { mutableStateOf<String?>(null) }
    var dnsResult by remember { mutableStateOf<String?>(null) }
    var networkInterfaces by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    val manualHost by runtime?.prefs?.manualHost?.collectAsState() ?: remember { mutableStateOf("") }
    val manualPort by runtime?.prefs?.manualPort?.collectAsState() ?: remember { mutableStateOf(18789) }

    SectionCard(title = "Network") {
        Button(
            onClick = {
                testing = true
                tcpResult = null
                dnsResult = null
                networkInterfaces = null
                scope.launch {
                    // Network interfaces (detect Tailscale)
                    networkInterfaces = withContext(Dispatchers.IO) {
                        try {
                            NetworkInterface.getNetworkInterfaces()?.toList()
                                ?.flatMap { iface ->
                                    iface.inetAddresses.toList().map { addr ->
                                        "${iface.displayName}: ${addr.hostAddress}"
                                    }
                                }
                                ?.filter { !it.contains("127.0.0.1") && !it.contains("::1") }
                                ?.joinToString("\n")
                                ?: "No interfaces"
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                    }

                    if (manualHost.isNotBlank()) {
                        // DNS resolution
                        dnsResult = withContext(Dispatchers.IO) {
                            try {
                                val addresses = InetAddress.getAllByName(manualHost)
                                addresses.joinToString(", ") { it.hostAddress ?: "?" }
                            } catch (e: Exception) {
                                "Failed: ${e.message}"
                            }
                        }

                        // TCP connectivity
                        tcpResult = withContext(Dispatchers.IO) {
                            try {
                                val socket = Socket()
                                socket.connect(InetSocketAddress(manualHost, manualPort), 5000)
                                socket.close()
                                "OK (connected in <5s)"
                            } catch (e: Exception) {
                                "Failed: ${e::class.java.simpleName}: ${e.message}"
                            }
                        }
                    } else {
                        dnsResult = "No host configured"
                        tcpResult = "No host configured"
                    }
                    testing = false
                }
            },
            enabled = !testing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (testing) "Testing…" else "Run Network Test")
        }

        networkInterfaces?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Interfaces", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            val hasTailscale = it.contains("tun") || it.contains("tailscale") || it.contains("100.")
            if (hasTailscale) {
                Text("Tailscale detected", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
        }

        dnsResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("DNS ($manualHost)", it)
        }

        tcpResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("TCP ($manualHost:$manualPort)", it)
        }
    }
}

@Composable
private fun DeviceIdentityPanel(runtime: com.openclaw.assistant.node.NodeRuntime?) {
    val deviceId = runtime?.deviceId

    SectionCard(title = "Device Identity") {
        InfoRow("Device ID", deviceId?.take(16)?.plus("…") ?: "Not initialized")
        deviceId?.let {
            Text(
                it,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LiveLogPanel(context: Context) {
    val logs by ConnectionDebugLogger.logs.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    SectionCard(title = "Connection Log (${logs.size} entries)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ConnectionDebugLogger.clear() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
            OutlinedButton(
                onClick = {
                    val clip = logs.joinToString("\n")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Anvil Diagnostics", clip))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 300.dp)
                .verticalScroll(scrollState)
                .horizontalScroll(rememberScrollState())
        ) {
            if (logs.isEmpty()) {
                Text(
                    "No events yet. Connect to a gateway to see logs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    logs.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
