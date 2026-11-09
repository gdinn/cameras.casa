package com.gdisys.cameras.feature.qrcode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gdisys.cameras.testing.QrFixture
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

private const val DECODE_TIMEOUT_S = 10L

/**
 * The QR code images of `qr-code-gen/examples/` decode, on the device's ML Kit, to exactly the JSON
 * next to them. This covers the decoder the scanner uses without the emulator's camera, and catches
 * a PNG left behind when its JSON changes (`main.py --examples` regenerates them).
 *
 * Failures name the fixture only: the payloads are fictional, but QR payloads are never printed.
 */
@RunWith(AndroidJUnit4::class)
class QrCodeImageDecodingTest {

  private val scanner: BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
  )

  @After
  fun closeScanner() {
    scanner.close()
  }

  @Test
  fun everyExampleImageDecodesToItsJson() {
    val stale = QrFixture.entries.filter { fixture ->
      val barcodes = Tasks.await(
        scanner.process(InputImage.fromBitmap(fixture.readImage(), 0)),
        DECODE_TIMEOUT_S,
        TimeUnit.SECONDS
      )
      barcodes.singleOrNull()?.rawValue != fixture.read()
    }

    assertTrue("These images do not decode to their JSON (run main.py --examples): $stale", stale.isEmpty())
  }
}
