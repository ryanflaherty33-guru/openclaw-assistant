package com.openclaw.assistant.node

import com.openclaw.assistant.gateway.GatewaySession
import com.openclaw.assistant.protocol.OpenClawCameraCommand
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InvokeDispatcherTest {
  private val canvas = mockk<CanvasController>()
  private val cameraHandler = mockk<CameraHandler>()
  private val locationHandler = mockk<LocationHandler>()
  private val screenHandler = mockk<ScreenHandler>()
  private val smsHandler = mockk<SmsHandler>()
  private val a2uiHandler = mockk<A2UIHandler>()
  private val debugHandler = mockk<DebugHandler>()
  private val appUpdateHandler = mockk<AppUpdateHandler>()
  private val deviceHandler = mockk<DeviceHandler>()

  private fun createDispatcher(
    isForeground: Boolean = true,
    cameraEnabled: Boolean = true,
    locationEnabled: Boolean = true
  ) = InvokeDispatcher(
    canvas = canvas,
    cameraHandler = cameraHandler,
    locationHandler = locationHandler,
    screenHandler = screenHandler,
    smsHandler = smsHandler,
    a2uiHandler = a2uiHandler,
    debugHandler = debugHandler,
    appUpdateHandler = appUpdateHandler,
    deviceHandler = deviceHandler,
    isForeground = { isForeground },
    cameraEnabled = { cameraEnabled },
    locationEnabled = { locationEnabled }
  )

  @Test
  fun `camera list is dispatched to handler`() = runTest {
    val dispatcher = createDispatcher()
    coEvery { cameraHandler.handleList(null) } returns GatewaySession.InvokeResult.ok("{}")

    val result = dispatcher.handleInvoke(OpenClawCameraCommand.List.rawValue, null)

    assertEquals(true, result.ok)
    assertEquals("{}", result.payloadJson)
  }

  @Test
  fun `camera list returns error when camera disabled`() = runTest {
    val dispatcher = createDispatcher(cameraEnabled = false)

    val result = dispatcher.handleInvoke(OpenClawCameraCommand.List.rawValue, null)

    assertEquals(false, result.ok)
    assertEquals("CAMERA_DISABLED", result.error?.code)
  }

  @Test
  fun `camera list returns error when app in background`() = runTest {
    val dispatcher = createDispatcher(isForeground = false)

    val result = dispatcher.handleInvoke(OpenClawCameraCommand.List.rawValue, null)

    assertEquals(false, result.ok)
    assertEquals("NODE_BACKGROUND_UNAVAILABLE", result.error?.code)
  }
}
