package com.gdisys.cameras.feature.config

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QrCodeAnalyzer(
  private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

  private val scanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
      .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
      .build()
  )

  private var isScanned = false

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    if (isScanned) {
      imageProxy.close()
      return
    }

    val mediaImage = imageProxy.image
    if (mediaImage != null) {
      val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

      scanner.process(image)
        .addOnSuccessListener { barcodes ->
          if (barcodes.isNotEmpty()) {
            Log.d("QrCodeAnalyzer", "Barcodes detectados: ${barcodes.size}")
          }

          val barcode = barcodes.firstOrNull { it.rawValue != null }

          if (barcode != null && !isScanned) {
            val rawValue = barcode.rawValue!!
            Log.d("QrCodeAnalyzer", "Conteúdo lido: $rawValue")
            isScanned = true
            onQrCodeScanned(rawValue)
          }
        }
        .addOnFailureListener { e ->
          Log.e("QrCodeAnalyzer", "Erro no scanner: ${e.message}", e)
        }
        .addOnCompleteListener {
          imageProxy.close()
        }
    } else {
      imageProxy.close()
    }
  }
}