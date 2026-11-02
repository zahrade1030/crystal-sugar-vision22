package com.example.cv

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Image Quality Validator based on Section 2 specifications:
 * 1. Blur Detection: Variance of Laplacian >= 5.0
 * 2. Brightness: Mean luminance inside ROI mask (10.0 .. 248.0)
 * 3. Contrast: Std dev of luminance inside mask >= 2.0
 * 4. Sample Coverage: Ratio of valid sample pixels in mask
 * 5. Light Reflection: Ratio of saturated pixels (lum >= 250) in mask
 */
class ImageQualityValidator {

    data class MaskPixelStats(
        val totalMaskPixels: Int,
        val validPixels: Int,
        val saturatedPixels: Int,
        val darkPixels: Int,
        val meanLuminance: Double,
        val contrastStdDev: Double
    )

    fun validate(
        bitmap: Bitmap,
        minBlurThreshold: Double = 5.0,
        minBrightness: Double = 10.0,
        maxBrightness: Double = 248.0,
        minContrast: Double = 2.0,
        minCoverageRatio: Double = 0.25,
        maxSaturatedRatio: Double = 0.15
    ): ImageQualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Compute Laplacian variance for blur detection
        val grayArray = DoubleArray(totalPixels)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            grayArray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }
        val blurScore = computeLaplacianVariance(grayArray, width, height)

        // 2. Circular Mask geometry: Center (width/2, height/2), radius = 0.45 * width
        val centerX = width / 2.0
        val centerY = height / 2.0
        val radius = 0.45 * width
        val radiusSq = radius * radius

        var maskPixelCount = 0
        var validPixelCount = 0
        var saturatedPixelCount = 0
        var darkPixelCount = 0
        var sumLuminanceValid = 0.0
        var sumValidX = 0.0
        var sumValidY = 0.0

        val validLuminances = ArrayList<Double>(totalPixels / 2)

        for (y in 0 until height) {
            val dy = y - centerY
            val dySq = dy * dy
            for (x in 0 until width) {
                val dx = x - centerX
                if (dx * dx + dySq <= radiusSq) {
                    maskPixelCount++
                    val idx = y * width + x
                    val lum = grayArray[idx]

                    if (lum >= 250.0) {
                        saturatedPixelCount++
                    } else if (lum <= 18.0) {
                        darkPixelCount++
                    } else {
                        // Valid sample pixel
                        validPixelCount++
                        sumLuminanceValid += lum
                        sumValidX += x
                        sumValidY += y
                        validLuminances.add(lum)
                    }
                }
            }
        }

        val totalMask = maxOf(1, maskPixelCount)
        val sampleCoverage = validPixelCount.toDouble() / totalMask.toDouble()
        val saturatedRatio = saturatedPixelCount.toDouble() / totalMask.toDouble()

        val meanBrightness = if (validPixelCount > 0) sumLuminanceValid / validPixelCount else 0.0

        // Contrast: Standard deviation of valid pixel luminance
        var varianceSum = 0.0
        for (lum in validLuminances) {
            val diff = lum - meanBrightness
            varianceSum += diff * diff
        }
        val contrastStd = if (validPixelCount > 1) sqrt(varianceSum / validPixelCount) else 0.0
        val centerAlignmentScore = if (validPixelCount > 0) {
            val sampleCenterX = sumValidX / validPixelCount
            val sampleCenterY = sumValidY / validPixelCount
            
            val distance = sqrt(
                (sampleCenterX - centerX).pow(2) +
                    (sampleCenterY - centerY).pow(2)
            )
            (1.0 - distance / radius).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        // Validation Rules checking in Persian
        var rejectionReason: String? = null

        if (blurScore < minBlurThreshold) {
            rejectionReason = "تصویر تار است."
        } else if (sampleCoverage < minCoverageRatio) {
            rejectionReason = "نمونه بهاندازه کافی در محدوده مشخصشده قرار نگرفته است."
        } else if (saturatedRatio > maxSaturatedRatio) {
            rejectionReason = "بازتاب نور زیاد است."
        } else if (meanBrightness < minBrightness) {
            rejectionReason = "نور تصویر کم است."
        } else if (meanBrightness > maxBrightness) {
            rejectionReason = "نور بیش از حد زیاد است."
        } else if (contrastStd < minContrast) {
            rejectionReason = "کنتراست تصویر کم است."
        }

        val isPassed = rejectionReason == null

        // Quality score normalization (0 - 100%)
        val blurFactor = (blurScore / 100.0).coerceIn(0.0, 1.0) * 30.0
        val brightnessFactor = (1.0 - abs(meanBrightness - 128.0) / 128.0).coerceIn(0.0, 1.0) * 25.0
        val contrastFactor = (contrastStd / 60.0).coerceIn(0.0, 1.0) * 20.0
        val coverageFactor = (sampleCoverage / 0.85).coerceIn(0.0, 1.0) * 25.0
        val penalty = (saturatedRatio * 50.0).coerceAtMost(30.0)

        val overallScore = if (isPassed) {
            (blurFactor + brightnessFactor + contrastFactor + coverageFactor - penalty).coerceIn(40.0, 100.0)
        } else {
            (blurFactor + brightnessFactor + contrastFactor + coverageFactor - penalty).coerceIn(10.0, 59.0)
        }

        return ImageQualityResult(
            isPassed = isPassed,
            blurScore = blurScore,
            brightnessScore = meanBrightness,
            contrastScore = contrastStd,
            centerAlignmentScore = centerAlignmentScore,
            overallScore = overallScore,
            rejectionReason = rejectionReason
        )
    }

    private fun computeLaplacianVariance(gray: DoubleArray, w: Int, h: Int): Double {
        var lapSum = 0.0
        var count = 0
        val lapValues = DoubleArray((w - 2) * (h - 2))

        // 3x3 Laplacian Kernel: [[0, 1, 0], [1, -4, 1], [0, 1, 0]]
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val center = gray[idx]
                val top = gray[(y - 1) * w + x]
                val bottom = gray[(y + 1) * w + x]
                val left = gray[y * w + (x - 1)]
                val right = gray[y * w + (x + 1)]

                val lap = top + bottom + left + right - 4.0 * center
                lapValues[count++] = lap
                lapSum += lap
            }
        }

        if (count == 0) return 0.0
        val lapMean = lapSum / count

        var varSum = 0.0
        for (i in 0 until count) {
            val d = lapValues[i] - lapMean
            varSum += d * d
        }

        return varSum / count
    }
}
