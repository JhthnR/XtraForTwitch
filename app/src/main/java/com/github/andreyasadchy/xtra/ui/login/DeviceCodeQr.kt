package com.github.andreyasadchy.xtra.ui.login

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

internal object DeviceCodeQr {
    fun create(url: String, pixels: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, pixels, pixels,
            mapOf(EncodeHintType.MARGIN to 4))
        val colors = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(colors, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}
