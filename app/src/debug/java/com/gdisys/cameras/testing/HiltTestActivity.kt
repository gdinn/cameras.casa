package com.gdisys.cameras.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/**
 * An empty Hilt activity for instrumented tests, which set `CamerasAppContent` on it themselves.
 *
 * It must behave like `MainActivity` everywhere the content can notice: same theme and
 * `configChanges` (see the debug manifest) and edge-to-edge. It lives in the app's debug build,
 * not in the test APK, because the activity has to be declared in the app's manifest.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
  }
}
