package com.openclaw.assistant.shared.utils

import android.net.Uri

object NetworkUtils {
    /**
     * Checks whether a given URL is secure.
     * A URL is considered secure if it uses HTTPS/WSS, or if it uses HTTP/WS but targets a local network IP address (e.g. localhost, 127.0.0.1, 192.168.x.x, etc.).
     * Public HTTP/WS URLs are considered insecure.
     */
    fun isUrlSecure(urlString: String?): Boolean {
        if (urlString.isNullOrBlank()) return false
        val uri = try {
            val normalized = if (urlString.contains("://")) urlString else "https://$urlString"
            Uri.parse(normalized)
        } catch (e: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme == "https" || scheme == "wss") return true
        if (scheme != "http" && scheme != "ws") return false

        val host = uri.host ?: return false
        return isLocalHost(host)
    }

    private fun isLocalHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host.endsWith(".local", ignoreCase = true)) return true

        // IPv4
        val ipv4Parts = host.split(".")
        if (ipv4Parts.size == 4) {
            val p1 = ipv4Parts[0].toIntOrNull() ?: return false
            val p2 = ipv4Parts[1].toIntOrNull() ?: return false
            val p3 = ipv4Parts[2].toIntOrNull() ?: return false
            val p4 = ipv4Parts[3].toIntOrNull() ?: return false

            if (p1 !in 0..255 || p2 !in 0..255 || p3 !in 0..255 || p4 !in 0..255) return false

            if (p1 == 127) return true // Loopback: 127.x.x.x
            if (p1 == 10) return true  // Class A Private: 10.x.x.x
            if (p1 == 172 && p2 in 16..31) return true // Class B Private: 172.16.x.x - 172.31.x.x
            if (p1 == 192 && p2 == 168) return true // Class C Private: 192.168.x.x
        }

        // IPv6
        if (host == "::1" || host == "0:0:0:0:0:0:0:1") return true

        return false
    }
}
