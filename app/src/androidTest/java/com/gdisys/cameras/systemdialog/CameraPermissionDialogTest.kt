package com.gdisys.cameras.systemdialog

import com.gdisys.cameras.R
import com.gdisys.cameras.feature.config.ConfigButtonState
import com.gdisys.cameras.testing.FlowTest
import com.gdisys.cameras.testing.SystemDialogs
import com.gdisys.cameras.testing.robot.ConfigStep
import com.gdisys.cameras.testing.robot.config
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * The real camera permission dialog, answered through UI Automator. The suite already uses the
 * production camera repository, and every test starts with the permission revoked (`pm clear`).
 */
@HiltAndroidTest
class CameraPermissionDialogTest : FlowTest() {

  @Before
  fun openConfig() {
    launchApp()
  }

  @Test
  fun allowingCompletesTheStep() {
    config { tapStep(ConfigStep.CAMERA) }
    SystemDialogs.Permission.allowWhileInUse()

    config { assertStep(ConfigStep.CAMERA, ConfigButtonState.Done) }
  }

  @Test
  fun denyingOnceKeepsTheStepReady() {
    config { tapStep(ConfigStep.CAMERA) }
    SystemDialogs.Permission.deny()

    config { assertStep(ConfigStep.CAMERA, ConfigButtonState.Ready) }
    // After a first denial the app may still ask, so it does not report a permanent denial.
    assertFalse(R.string.config_route_camera_permanently_denied in toasts.shown)
  }

  @Test
  fun denyingTwiceReportsPermanentDenial() {
    config { tapStep(ConfigStep.CAMERA) }
    SystemDialogs.Permission.deny()
    config { tapStep(ConfigStep.CAMERA) }
    SystemDialogs.Permission.deny()

    // A second denial is final (Android 11+): the system will not show the dialog again.
    config { assertStep(ConfigStep.CAMERA, ConfigButtonState.Ready) }
    // The first run also toasts that there are no credentials yet, so only the last toast is read.
    assertEquals(R.string.config_route_camera_permanently_denied, toasts.shown.last())
  }
}
