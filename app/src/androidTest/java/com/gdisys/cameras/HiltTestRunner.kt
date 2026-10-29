package com.gdisys.cameras

import android.app.Application
import android.content.Context
import androidx.test.espresso.IdlingRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.gdisys.cameras.testing.TestDispatchers
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Runs every instrumented test inside HiltTestApplication, so @HiltAndroidTest can swap bindings,
 * and registers the [TestDispatchers] with Espresso, so every test waits for them.
 */
class HiltTestRunner : AndroidJUnitRunner() {

  override fun onStart() {
    IdlingRegistry.getInstance().register(*TestDispatchers.all.toTypedArray())
    super.onStart()
  }

  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
    super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
