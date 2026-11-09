package com.gdisys.cameras.testing

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

// The system dialogs open in another process, outside Compose's idle tracking, so they are awaited.
private const val DIALOG_TIMEOUT_MS = 10_000L

private const val VPN_DIALOG_PACKAGE = "com.android.vpndialogs"

/**
 * The system windows a level C test answers, through UI Automator: the VPN consent dialog and the
 * runtime permission dialog. Their buttons are found by resource id, not by text, so the device's
 * language does not matter. The permission controller's package differs between AOSP and Google
 * builds, so its ids are matched on any package.
 */
object SystemDialogs {

  private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
  private val device: UiDevice get() = UiDevice.getInstance(instrumentation)

  /** The VPN consent dialog (`VpnService.prepare`'s Intent). */
  object VpnConsent {
    fun accept() = tap(By.pkg(VPN_DIALOG_PACKAGE).res("android:id/button1"), "VPN consent OK")
    fun cancel() = tap(By.pkg(VPN_DIALOG_PACKAGE).res("android:id/button2"), "VPN consent Cancel")

    /**
     * Forgets the consent, so `VpnService.prepare` asks again. `pm clear` does not reset the
     * `ACTIVATE_VPN` app op, so without this a test would inherit the previous one's answer.
     */
    fun reset() = shell("appops set ${instrumentation.targetContext.packageName} ACTIVATE_VPN default")
  }

  /** The runtime permission dialog. */
  object Permission {
    fun allowWhileInUse() = tap(permissionButton("permission_allow_foreground_only_button"), "permission Allow")
    // From the second request on, "Don't allow" is final and the button takes another id.
    fun deny() = tap(permissionButton("permission_deny(_and_dont_ask_again)?_button"), "permission Don't allow")

    private fun permissionButton(idPattern: String): BySelector = By.res(Pattern.compile(".*:id/$idPattern"))
  }

  private fun tap(selector: BySelector, name: String) {
    val button = checkNotNull(device.wait(Until.findObject(selector), DIALOG_TIMEOUT_MS)) {
      // Name what is on screen instead, so a renamed button is fixed from the failure alone.
      val onScreen = device.findObjects(By.clickable(true)).map { "${it.resourceName} \"${it.text}\"" }
      "The system dialog's \"$name\" button did not show up. Clickable on screen: $onScreen"
    }
    button.click()
    // The dialog closes and hands the result back to the activity, which resumes.
    device.wait(Until.gone(selector), DIALOG_TIMEOUT_MS)
    device.waitForIdle()
  }

  private fun shell(command: String) {
    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
      .use { it.readBytes() }
  }
}
