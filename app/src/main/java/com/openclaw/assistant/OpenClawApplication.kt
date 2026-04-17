package com.openclaw.assistant

import android.app.Application
import android.util.Log
import com.openclaw.assistant.node.NodeRuntime
import java.security.Security

class OpenClawApplication : Application() {

    @Volatile private var _nodeRuntime: NodeRuntime? = null

    /**
     * Returns the runtime, initializing it if needed.
     * Call this from Activities/ViewModels to ensure the runtime exists.
     */
    fun ensureRuntime(): NodeRuntime {
        _nodeRuntime?.let { return it }
        return synchronized(this) {
            _nodeRuntime ?: NodeRuntime(this).also { _nodeRuntime = it }
        }
    }

    /**
     * Returns the runtime if already initialized, or null if it has not been created yet.
     * Use this in places that must NOT force initialization (e.g. foreground service).
     */
    fun peekRuntime(): NodeRuntime? = _nodeRuntime

    /**
     * Retained for backwards compatibility with existing call sites.
     * Equivalent to [ensureRuntime].
     */
    val nodeRuntime: NodeRuntime get() = ensureRuntime()

    override fun onCreate() {
        super.onCreate()
        // Register Bouncy Castle as highest-priority provider for Ed25519 support
        try {
            val bcProvider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance() as java.security.Provider
            Security.removeProvider("BC")
            Security.insertProviderAt(bcProvider, 1)
        } catch (e: Throwable) {
            Log.e("OpenClawApp", "Failed to register Bouncy Castle provider", e)
        }
    }
}
