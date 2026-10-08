package com.networkbroadcast.hospitality.designsystem

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR en blanco y negro, un píxel por módulo: mostrarlo con FilterQuality.None para que se escale
 * sin suavizar. Lo usan el emparejamiento de la TV y los códigos de ubicación del mesero.
 */
fun qrImageBitmap(payload: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0)
    val bitmap = android.graphics.Bitmap.createBitmap(matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
        bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bitmap.asImageBitmap()
}
