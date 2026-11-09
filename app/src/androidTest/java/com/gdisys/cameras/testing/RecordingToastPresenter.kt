package com.gdisys.cameras.testing

import com.gdisys.cameras.core.components.ToastPresenter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records the toasts instead of showing them, for tests that provide it through
 * `LocalToastPresenter`. [shown] is written on the main thread and read from the test thread.
 */
class RecordingToastPresenter : ToastPresenter {

  val shown: MutableList<Int> = CopyOnWriteArrayList()

  override fun show(resId: Int) {
    shown += resId
  }
}
