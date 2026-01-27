package me.ikate.findmy.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码生成工具类
 * 使用 ZXing 库生成二维码 Bitmap
 */
object QrCodeUtils {

    /**
     * 生成二维码 Bitmap
     *
     * @param content 要编码的内容
     * @param size 二维码尺寸（宽高相同）
     * @param foregroundColor 前景色（二维码颜色），默认黑色
     * @param backgroundColor 背景色，默认白色
     * @return 生成的 Bitmap，失败返回 null
     */
    fun generateBitmap(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )

            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) {
                        foregroundColor
                    } else {
                        backgroundColor
                    }
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成带透明背景的二维码 Bitmap
     *
     * @param content 要编码的内容
     * @param size 二维码尺寸
     * @param foregroundColor 前景色
     * @return 生成的 Bitmap，失败返回 null
     */
    fun generateTransparentBitmap(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK
    ): Bitmap? {
        return generateBitmap(
            content = content,
            size = size,
            foregroundColor = foregroundColor,
            backgroundColor = Color.TRANSPARENT
        )
    }
}
