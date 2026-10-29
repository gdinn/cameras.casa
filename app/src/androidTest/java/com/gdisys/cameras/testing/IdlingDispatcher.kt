package com.gdisys.cameras.testing

import androidx.test.espresso.IdlingResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] that runs its work on [delegate] and reports to Espresso, as an
 * [IdlingResource], whether any of that work is running or queued. The Compose test rule syncs
 * through Espresso, so `waitForIdle` and every assertion wait for it as they wait for the main
 * thread.
 *
 * It counts dispatched blocks, not coroutines: a coroutine suspended on something outside this
 * dispatcher (a `delay`, a native callback, a dispatcher that is not wrapped) does not keep it
 * busy. A hop between two counted dispatchers, or back to the main thread, does not open a gap,
 * because the next block is queued before the current one ends.
 */
class IdlingDispatcher(
  private val delegate: CoroutineDispatcher,
  private val resourceName: String
) : CoroutineDispatcher(), IdlingResource {

  private val running = AtomicInteger()

  @Volatile
  private var idleCallback: IdlingResource.ResourceCallback? = null

  override fun isDispatchNeeded(context: CoroutineContext) = delegate.isDispatchNeeded(context)

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    running.incrementAndGet()
    try {
      delegate.dispatch(context) {
        try {
          block.run()
        } finally {
          finishOne()
        }
      }
    } catch (e: Throwable) {
      // The delegate rejected the block, so it will never run.
      finishOne()
      throw e
    }
  }

  private fun finishOne() {
    if (running.decrementAndGet() == 0) idleCallback?.onTransitionToIdle()
  }

  override fun getName() = resourceName

  override fun isIdleNow() = running.get() == 0

  override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
    idleCallback = callback
  }

  override fun toString() = "IdlingDispatcher($resourceName)"
}

/**
 * The dispatchers `TestDispatchersModule` hands to the app in every `@HiltAndroidTest`.
 *
 * Process-wide rather than per test: Espresso's registry is global, and `HiltTestRunner` registers
 * them once, before any test runs.
 */
object TestDispatchers {
  val io = IdlingDispatcher(Dispatchers.IO, "io-dispatcher")
  val default = IdlingDispatcher(Dispatchers.Default, "default-dispatcher")

  val all = listOf(io, default)
}
