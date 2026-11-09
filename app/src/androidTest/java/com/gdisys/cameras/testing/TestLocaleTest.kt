package com.gdisys.cameras.testing

import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Guards the `testLocale` argument: what a screen renders and what `string()` resolves must agree,
 * and both must be in the requested language. Without the argument, both follow the device.
 */
@RunWith(AndroidJUnit4::class)
class TestLocaleTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun screensAndStringRenderInTheSameLocale() {
    lateinit var renderedLocale: Locale
    composeRule.setThemedContent {
      renderedLocale = LocalConfiguration.current.locales[0]
      Text(stringResource(R.string.home_screen_reconfigure))
    }

    composeRule.onNodeWithText(string(R.string.home_screen_reconfigure)).assertExists()
    testLocale?.let { requested -> composeRule.runOnIdle { assertEquals(requested, renderedLocale) } }
  }
}
