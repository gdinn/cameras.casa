package com.gdisys.cameras.testing

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val SLOW_WORK_MS = 500L

@RunWith(AndroidJUnit4::class)
class IdlingDispatcherTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun busyWhileWorkRunsAndIdleAfter() {
    val dispatcher = IdlingDispatcher(Dispatchers.Default, "under-test")
    val transitionsToIdle = AtomicInteger()
    dispatcher.registerIdleTransitionCallback { transitionsToIdle.incrementAndGet() }
    val release = CountDownLatch(1)
    val finished = CountDownLatch(1)

    assertTrue(dispatcher.isIdleNow)
    CoroutineScope(dispatcher).launch {
      release.await()
      finished.countDown()
    }
    assertFalse(dispatcher.isIdleNow)

    release.countDown()
    assertTrue(finished.await(5, TimeUnit.SECONDS))
    composeRule.waitUntil { dispatcher.isIdleNow }
    assertEquals(1, transitionsToIdle.get())
  }

  /**
   * The point of the dispatchers: main -> test dispatcher -> main, with no `waitUntil`. Without
   * `HiltTestRunner` registering them, the assertion runs before the work ends and fails.
   */
  @Test
  fun composeTestRuleWaitsForTheTestDispatchers() {
    composeRule.setContent {
      var text by remember { mutableStateOf("working") }
      LaunchedEffect(Unit) {
        text = withContext(TestDispatchers.default) {
          Thread.sleep(SLOW_WORK_MS)
          withContext(TestDispatchers.io) {
            Thread.sleep(SLOW_WORK_MS)
            "done"
          }
        }
      }
      Text(text)
    }

    composeRule.onNodeWithText("done").assertExists()
  }
}
