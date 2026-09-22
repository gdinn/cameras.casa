package com.gdisys.cameras

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // The prefix, not the whole id: the debug variant appends ".debug" so that it installs
        // beside a release build (app/build.gradle.kts), and this test runs against whichever
        // variant `testBuildType` selects. Asserting one literal id would tie a stub test to one
        // variant's suffix.
        assertTrue(
            "Expected the package name to be the app's namespace, optionally suffixed by the " +
                "build type, but it was ${appContext.packageName}.",
            appContext.packageName.startsWith("com.gdisys.cameras")
        )
    }
}
