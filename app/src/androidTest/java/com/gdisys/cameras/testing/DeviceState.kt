package com.gdisys.cameras.testing

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry

/**
 * System state a flow test changes on the device. The VPN consent is not here: it is faked by
 * [FakeVpnPermissionRepository], because its app op survives the orchestrator's `pm clear`.
 */
object DeviceState {

  /**
   * Grants the camera permission. Every test starts without it (`pm clear` revokes it). There is
   * no revoke: revoking a runtime permission kills the app's process, and the test with it.
   */
  fun grantCameraPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
      instrumentation.targetContext.packageName,
      Manifest.permission.CAMERA
    )
  }
}
