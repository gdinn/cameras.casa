package com.gdisys.cameras.testing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The fictional QR payloads of `qr-code-gen/examples/`, which the build adds to the test APK's
 * assets. They are the single source of truth: nothing keeps a copy of them.
 */
enum class QrFixture(private val fileName: String) {
  /** Complete and well-typed: the app accepts it. */
  VALID("1_validqr_bogus_info.json"),

  /** Valid JSON with a required field missing. */
  INCOMPLETE("2_validqr_incomplete_info.json"),

  /** Valid JSON with a field of the wrong type. */
  WRONG_TYPE("3_validqr_info_dif_type.json"),

  /** Valid JSON with a shape the app does not know. */
  UNKNOWN_SHAPE("4_validqr_dif_type_2.json");

  /** The raw JSON, as the scanner would hand it over. */
  fun read(): String = open(fileName).bufferedReader().use { it.readText() }

  /** The QR code image next to the JSON, generated from it by `qr-code-gen/main.py --examples`. */
  fun readImage(): Bitmap =
    open(fileName.replace(".json", ".png")).use { checkNotNull(BitmapFactory.decodeStream(it)) { "$this: unreadable PNG" } }

  private fun open(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(name)
}
