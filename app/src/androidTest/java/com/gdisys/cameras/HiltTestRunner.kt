package com.gdisys.cameras

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Runs every instrumented test inside HiltTestApplication, so @HiltAndroidTest can swap bindings. */
class HiltTestRunner : AndroidJUnitRunner() {

  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
    super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
