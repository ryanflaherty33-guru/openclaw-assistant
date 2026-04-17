package com.openclaw.assistant.gateway

import android.content.Context
import android.util.Base64
import android.util.Log
import com.openclaw.assistant.diagnostics.ConnectionDebugLogger
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeySign
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.signature.SignatureConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class DeviceIdentity(private val context: Context) {

    companion object {
        private const val TAG = "DeviceIdentity"
        private const val PREF_FILE_NAME = "device_identity_prefs"
        private const val KEYSET_NAME = "device_identity_keyset_v2"
        private const val MASTER_KEY_URI = "android-keystore://device_identity_master_key"
    }

    private var signer: PublicKeySign? = null
    var deviceId: String? = null
        private set
    var publicKeyBase64Url: String? = null
        private set

    init {
        val L = ConnectionDebugLogger
        L.log("Identity", "=== INIT START ===")

        // ── Step 1: Try pure BouncyCastle first (no Tink, no Android Keystore) ──
        try {
            L.log("Identity", "Step 1: Pure BC keygen...")

            // Ensure BC provider is available
            val bcProviders = java.security.Security.getProviders()
                .filter { it.name.contains("BC", ignoreCase = true) }
            L.log("Identity", "Step 1a: BC providers found: ${bcProviders.map { it.name }}")

            if (bcProviders.isEmpty()) {
                L.log("Identity", "Step 1b: Registering BC provider...")
                val bcProvider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                    .getDeclaredConstructor().newInstance() as java.security.Provider
                java.security.Security.insertProviderAt(bcProvider, 1)
                L.log("Identity", "Step 1b: BC provider registered")
            }

            // Check for existing saved BC keys
            L.log("Identity", "Step 1c: Checking SharedPrefs for saved keys...")
            val prefs = context.getSharedPreferences("anvil_bc_identity", Context.MODE_PRIVATE)
            L.log("Identity", "Step 1c: Got SharedPrefs OK")
            val existingPub = prefs.getString("pub", null)
            val existingPriv = prefs.getString("priv", null)
            L.log("Identity", "Step 1d: existingPub=${existingPub != null}, existingPriv=${existingPriv != null}")

            val finalPubKey: ByteArray
            val finalPrivKey: org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

            if (existingPub != null && existingPriv != null) {
                L.log("Identity", "Step 1e: Restoring saved keys...")
                finalPubKey = Base64.decode(existingPub, Base64.NO_WRAP)
                L.log("Identity", "Step 1e: Decoded pub key, size=${finalPubKey.size}")
                val privBytes = Base64.decode(existingPriv, Base64.NO_WRAP)
                L.log("Identity", "Step 1e: Decoded priv key, size=${privBytes.size}")
                finalPrivKey = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privBytes, 0)
                L.log("Identity", "Step 1e: Restored keys OK")
            } else {
                L.log("Identity", "Step 1e: Generating new Ed25519 keypair...")
                val kpg = org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator()
                L.log("Identity", "Step 1e: Created generator")
                kpg.init(org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters(java.security.SecureRandom()))
                L.log("Identity", "Step 1e: Initialized generator")
                val keyPair = kpg.generateKeyPair()
                L.log("Identity", "Step 1e: Generated keypair")
                val pubKey = keyPair.public as org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
                finalPrivKey = keyPair.private as org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
                finalPubKey = pubKey.encoded
                L.log("Identity", "Step 1e: pubKey size=${finalPubKey.size}")

                // Persist
                L.log("Identity", "Step 1f: Saving keys to SharedPrefs...")
                prefs.edit()
                    .putString("pub", Base64.encodeToString(finalPubKey, Base64.NO_WRAP))
                    .putString("priv", Base64.encodeToString(finalPrivKey.encoded, Base64.NO_WRAP))
                    .apply()
                L.log("Identity", "Step 1f: Keys saved")
            }

            // Set signer
            L.log("Identity", "Step 1g: Creating BC signer...")
            signer = BouncyCastleSigner(finalPrivKey)
            L.log("Identity", "Step 1g: Signer created")

            // Derive identity
            L.log("Identity", "Step 1h: Deriving identity from pub key...")
            publicKeyBase64Url = Base64.encodeToString(
                finalPubKey,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            L.log("Identity", "Step 1h: publicKeyBase64Url=${publicKeyBase64Url?.take(20)}...")

            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(finalPubKey)
            deviceId = bytesToHex(hash)
            L.log("Identity", "Step 1h: deviceId=${deviceId?.take(16)}...")

            // Verify signer works
            L.log("Identity", "Step 1i: Test signing...")
            val testSig = signer?.sign("test".toByteArray())
            L.log("Identity", "Step 1i: Test sign OK, sig size=${testSig?.size}")

            L.log("Identity", "=== INIT SUCCESS (BC) === deviceId=${deviceId?.take(16)}...")

        } catch (e: Throwable) {
            L.log("Identity", "Step 1 FAILED: ${e::class.java.simpleName}: ${e.message}")
            Log.e(TAG, "BC identity init failed", e)

            // ── Step 2: Try Tink as fallback ──
            try {
                L.log("Identity", "Step 2: Trying Tink fallback...")
                SignatureConfig.register()
                L.log("Identity", "Step 2a: SignatureConfig registered")
                val manager = AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                    .withKeyTemplate(KeyTemplates.get("ED25519WithRawOutput"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                L.log("Identity", "Step 2b: KeysetManager built")
                val handle = manager.keysetHandle
                L.log("Identity", "Step 2c: Got handle")
                signer = handle.getPrimitive(PublicKeySign::class.java)
                L.log("Identity", "Step 2d: Got signer")
                extractPublicKeyViaTink(handle)
                L.log("Identity", "=== INIT SUCCESS (Tink) === deviceId=${deviceId?.take(16)}...")
            } catch (e2: Throwable) {
                L.log("Identity", "Step 2 ALSO FAILED: ${e2::class.java.simpleName}: ${e2.message}")
                Log.e(TAG, "All identity init methods failed", e2)
            }
        }

        L.log("Identity", "=== INIT END === deviceId=${deviceId ?: "NULL"}, pubKey=${publicKeyBase64Url?.take(10) ?: "NULL"}, signer=${if (signer != null) "OK" else "NULL"}")
    }

    private class BouncyCastleSigner(
        private val privateKey: org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
    ) : PublicKeySign {
        override fun sign(data: ByteArray): ByteArray {
            val s = org.bouncycastle.crypto.signers.Ed25519Signer()
            s.init(true, privateKey)
            s.update(data, 0, data.size)
            return s.generateSignature()
        }
    }

    private fun extractPublicKeyViaTink(handle: KeysetHandle) {
        val publicHandle = handle.getPublicKeysetHandle()
        val outputStream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(
            publicHandle,
            JsonKeysetWriter.withOutputStream(outputStream)
        )

        val jsonStr = outputStream.toString("UTF-8")
        val json = JSONObject(jsonStr)
        val keys = json.getJSONArray("key")

        if (keys.length() > 0) {
            val primaryKeyId = json.optLong("primaryKeyId")
            var keyObj: JSONObject? = null

            for (i in 0 until keys.length()) {
                val k = keys.getJSONObject(i)
                if (k.optLong("keyId") == primaryKeyId) {
                    keyObj = k
                    break
                }
            }
            if (keyObj == null) keyObj = keys.getJSONObject(0)

            val keyData = keyObj!!.getJSONObject("keyData")
            val valBase64 = keyData.getString("value")
            if (valBase64 != null && valBase64.isNotEmpty()) {
                val protoBytes = Base64.decode(valBase64, Base64.DEFAULT)
                if (protoBytes != null && protoBytes.size >= 32) {
                    val rawKey = protoBytes.copyOfRange(protoBytes.size - 32, protoBytes.size)
                    publicKeyBase64Url = Base64.encodeToString(
                        rawKey,
                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                    )
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hash = digest.digest(rawKey)
                    deviceId = bytesToHex(hash)
                }
            }
        }
    }

    fun sign(data: String): String? {
        return try {
            val signature = signer?.sign(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(
                signature,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sign failed: ${e.message}")
            null
        }
    }

    fun buildAuthPayload(
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String?
    ): String {
        val version = if (nonce != null) "v2" else "v1"
        val scopesStr = scopes.joinToString(",")
        val parts = mutableListOf(
            version,
            deviceId ?: "",
            clientId,
            clientMode,
            role,
            scopesStr,
            signedAtMs.toString(),
            token ?: ""
        )
        if (version == "v2") {
            parts.add(nonce ?: "")
        }
        return parts.joinToString("|")
    }

    fun buildV3(
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String,
        platform: String,
        deviceFamily: String
    ): String {
        val scopesStr = scopes.joinToString(",")
        return listOf(
            "v3",
            deviceId ?: "",
            clientId,
            clientMode,
            role,
            scopesStr,
            signedAtMs.toString(),
            token ?: "",
            nonce,
            normalizeMetadataField(platform),
            normalizeMetadataField(deviceFamily)
        ).joinToString("|")
    }

    private fun normalizeMetadataField(value: String): String {
        return value.filter { it.code in 0x20..0x7E }.lowercase()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val chars = "0123456789abcdef".toCharArray()
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = chars[v ushr 4]
            result[i * 2 + 1] = chars[v and 0x0F]
        }
        return String(result)
    }
}
