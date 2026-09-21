package com.gdisys.cameras.core.vpn.data

import android.content.Context
import android.content.Intent
import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class VpnLifecycleControllerImplTest {

  private val context = mockk<Context>(relaxed = true)
  private lateinit var controller: VpnLifecycleControllerImpl

  @Before
  fun setUp() {
    // Really constructing Intent(context, Class) does not populate its fields under the test
    // android.jar (the mockable jar), so the target component is verified through
    // mockkConstructor rather than by inspecting the returned Intent.
    mockkConstructor(Intent::class)
    controller = VpnLifecycleControllerImpl(context)
  }

  @After
  fun tearDown() {
    unmockkConstructor(Intent::class)
  }

  @Test
  fun `start starts the VpnLifecycleService`() {
    every { context.startService(any()) } returns null

    controller.start()

    verify(exactly = 1) {
      constructedWith<Intent>(EqMatcher(context), EqMatcher(VpnLifecycleService::class.java))
    }
    verify(exactly = 1) { context.startService(any()) }
  }

  @Test
  fun `stop stops the VpnLifecycleService`() {
    every { context.stopService(any()) } returns true

    controller.stop()

    verify(exactly = 1) {
      constructedWith<Intent>(EqMatcher(context), EqMatcher(VpnLifecycleService::class.java))
    }
    verify(exactly = 1) { context.stopService(any()) }
  }
}
