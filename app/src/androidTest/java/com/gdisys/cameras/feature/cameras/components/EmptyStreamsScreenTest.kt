package com.gdisys.cameras.feature.cameras.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.R
import com.gdisys.cameras.testing.setThemedContent
import com.gdisys.cameras.testing.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyStreamsScreenTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun showsNoStreamsMessage() {
    composeRule.setThemedContent { EmptyStreamsScreen(onNavigateToConfig = {}) }

    composeRule.onNodeWithText(string(R.string.home_screen_no_streams)).assertIsDisplayed()
  }

  @Test
  fun reconfigureNavigatesToConfig() {
    var navigations = 0
    composeRule.setThemedContent { EmptyStreamsScreen(onNavigateToConfig = { navigations++ }) }

    composeRule.onNodeWithTag(HomeTestTags.EMPTY_STREAMS_RECONFIGURE_BUTTON).performClick()

    assertEquals(1, navigations)
  }
}
