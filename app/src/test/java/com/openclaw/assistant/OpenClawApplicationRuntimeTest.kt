package com.openclaw.assistant

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the double-checked-locking (DCL) initialization pattern introduced in PR #359.
 *
 * [OpenClawApplication.ensureRuntime] cannot be tested end-to-end in unit tests because
 * [NodeRuntime] depends on Android KeyStore and system services that are unavailable in
 * the JVM host environment. Instead, we test the exact DCL pattern in a standalone helper
 * that mirrors the production code, verifying:
 *
 *  - Idempotency: the factory lambda is called exactly once across repeated calls.
 *  - peekValue: returns null before the first [ensure] call, non-null after.
 *  - Thread safety: concurrent callers all receive the same instance.
 */
class OpenClawApplicationRuntimeTest {

    /**
     * Mirrors the `@Volatile` + `synchronized` DCL pattern used in [OpenClawApplication]:
     *
     * ```kotlin
     * @Volatile private var _nodeRuntime: NodeRuntime? = null
     *
     * fun ensureRuntime(): NodeRuntime {
     *     _nodeRuntime?.let { return it }
     *     return synchronized(this) {
     *         _nodeRuntime ?: NodeRuntime(this).also { _nodeRuntime = it }
     *     }
     * }
     *
     * fun peekRuntime(): NodeRuntime? = _nodeRuntime
     * ```
     */
    private class LazySubject<T>(private val factory: () -> T) {
        @Volatile private var _value: T? = null
        val createCount = AtomicInteger(0)

        fun ensure(): T {
            _value?.let { return it }
            return synchronized(this) {
                _value ?: factory().also {
                    createCount.incrementAndGet()
                    _value = it
                }
            }
        }

        fun peek(): T? = _value
    }

    private lateinit var subject: LazySubject<String>

    @Before
    fun setUp() {
        subject = LazySubject { "runtime-${System.nanoTime()}" }
    }

    // ---------------------------------------------------------------------------
    // Basic contract
    // ---------------------------------------------------------------------------

    @Test
    fun `peek returns null before ensure`() {
        assertNull("peekRuntime should return null before ensureRuntime is called", subject.peek())
    }

    @Test
    fun `ensure returns non-null value`() {
        assertNotNull(subject.ensure())
    }

    @Test
    fun `ensure returns same instance on repeated calls`() {
        val first = subject.ensure()
        val second = subject.ensure()
        val third = subject.ensure()
        assertSame("All calls must return the same instance", first, second)
        assertSame(first, third)
    }

    @Test
    fun `factory invoked exactly once across multiple ensure calls`() {
        repeat(10) { subject.ensure() }
        assertTrue(
            "factory must be called exactly once, was ${subject.createCount.get()}",
            subject.createCount.get() == 1,
        )
    }

    @Test
    fun `peek returns non-null after ensure`() {
        subject.ensure()
        assertNotNull(subject.peek())
    }

    @Test
    fun `peek returns same instance as ensure`() {
        val runtime = subject.ensure()
        assertSame(runtime, subject.peek())
    }

    // ---------------------------------------------------------------------------
    // Thread safety
    // ---------------------------------------------------------------------------

    @Test
    fun `ensure is thread-safe under concurrent access`() {
        val results = Collections.synchronizedList(mutableListOf<String>())
        val threads = (1..30).map {
            Thread { results.add(subject.ensure()) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }

        org.junit.Assert.assertEquals("All 30 threads must complete", 30, results.size)
        val first = results.first()
        assertTrue(
            "All threads must receive the same instance",
            results.all { it === first },
        )
        assertTrue(
            "Factory must be called exactly once across all threads, was ${subject.createCount.get()}",
            subject.createCount.get() == 1,
        )
    }
}
