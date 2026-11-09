package com.gdisys.cameras.testing

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val NO_FRAMES = VideoSink { }
private const val CAM_1 = "http://10.0.0.1:8889/cam_1/whep"
private const val CAM_2 = "http://10.0.0.1:8889/cam_2/whep"

@RunWith(AndroidJUnit4::class)
class FakeStreamConnectionsTest {

  private val recorder = FakeStreamConnections()

  @Test
  fun connectAndDisconnectAreRecordedInOrder() {
    val repository = FakeStreamConnectionRepository(recorder)

    repository.connect(CAM_1, NO_FRAMES)
    repository.connect(CAM_2, NO_FRAMES)
    repository.disconnect(CAM_1)

    assertEquals(setOf(CAM_2), recorder.connected)
    assertEquals(
      listOf(StreamEvent.Connect(CAM_1), StreamEvent.Connect(CAM_2), StreamEvent.Disconnect(CAM_1)),
      recorder.events
    )
  }

  @Test
  fun closeAllClosesOnlyItsOwnConnections() {
    val home = FakeStreamConnectionRepository(recorder)
    val other = FakeStreamConnectionRepository(recorder)
    home.connect(CAM_1, NO_FRAMES)
    other.connect(CAM_2, NO_FRAMES)

    home.closeAll()

    assertEquals(setOf(CAM_2), recorder.connected)
    assertEquals(StreamEvent.CloseAll, recorder.events.last())
  }

  @Test
  fun aFailingUrlStaysClosedAndReportsOnTheMainThread() {
    val repository = FakeStreamConnectionRepository(recorder)
    repository.connect(CAM_1, NO_FRAMES)
    recorder.failingUrls += CAM_1
    val reported = CountDownLatch(1)
    var reportedOnMain = false

    // Called off the main thread, so the check below proves the error is posted, not run inline.
    repository.connect(CAM_1, NO_FRAMES) {
      reportedOnMain = Looper.myLooper() == Looper.getMainLooper()
      reported.countDown()
    }

    assertTrue("onError was never called", reported.await(5, TimeUnit.SECONDS))
    assertTrue(reportedOnMain)
    assertEquals(emptySet<String>(), recorder.connected)
  }

  @Test
  fun aRetryAfterTheFailureIsClearedConnects() {
    val repository = FakeStreamConnectionRepository(recorder)
    recorder.failingUrls += CAM_1
    repository.connect(CAM_1, NO_FRAMES)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    recorder.failingUrls -= CAM_1
    repository.connect(CAM_1, NO_FRAMES)

    assertEquals(setOf(CAM_1), recorder.connected)
  }
}
