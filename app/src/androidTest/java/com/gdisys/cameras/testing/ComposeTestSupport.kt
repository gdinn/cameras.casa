package com.gdisys.cameras.testing

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.gdisys.cameras.ui.theme.CamerasTheme

/** Renders [content] inside the app theme, the way every screen is shown in production. */
fun ComposeContentTestRule.setThemedContent(content: @Composable () -> Unit) =
  setContent { CamerasTheme { content() } }

/** Resolves a string in the device locale, so assertions on text do not hardcode a language. */
fun string(@StringRes resId: Int): String =
  InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
