package com.example.cv

import kotlin.math.*

/**
 * Unified Quality Policy for image validation.
 * Categorizes sample input into OK, WARN, or REJECT with specific reasons.
 */
object QualityPolicy {

    enum class Status {
        OK,
        WARN,
        REJECT
    }

    data class Evaluation(
        val status: Status,
        val isPassed: Boolean,
        val blurScore: Double,
        val brightness: Double,
        val contrast: Double,
        val clippedFraction: Double,
        val maskAreaFraction: Double,
        val primaryReason: String,
        val recommendations: List<String>
    )

    fun evaluate(
        pixels: IntArray,
        width: Int,
        height: Int,
        maskResult: SampleMask.MaskResult? = null
    ): Evaluation {
        val total = width * height
        require(pixels.size == total) { "Pixel array size does not match width * height" }

        var sumGray = 0.0
        var clippedCount = 0
        val grayPixels = DoubleArray(total)

        for (i in 0 until total) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            grayPixels[i] = lum
            sumGray += lum
            if (lum <= 2.0 || lum >= 253.0) {
                clippedCount++
            }
        }

        val avgBrightness = sumGray / total
        val clippedFraction = clippedCount.toDouble() / total

        // Contrast (Standard Deviation)
        var varSum = 0.0
        for (lum in grayPixels) {
            val d = lum - avgBrightness
            varSum += d * d
        }
        val contrast = sqrt(varSum / total)

        // Laplacian Variance for Sharpness
        // Use replicate borders to avoid fake border edges!
        var lapSum = 0.0
        var lapSqSum = 0.0
        var lapCount = 0

        for (y in 1 until height - 1) {
            val rowOff = y * width
            for (x in 1 until width - 1) {
                val center = grayPixels[rowOff + x]
                val top = grayPixels[rowOff - width + x]
                val bottom = grayPixels[rowOff + width + x]
                val left = grayPixels[rowOff + x - 1]
                val right = grayPixels[rowOff + x + 1]

                val lap = (top + bottom + left + right) - (4.0 * center)
                lapSum += lap
                lapSqSum += lap * lap
                lapCount++
            }
        }

        val lapMean = if (lapCount > 0) lapSum / lapCount else 0.0
        val blurScore = if (lapCount > 0) (lapSqSum / lapCount) - (lapMean * lapMean) else 0.0

        val maskArea = maskResult?.areaFraction ?: 1.0
        val isMaskRejected = maskResult?.isRejected ?: false

        // Check REJECT conditions
        val reasons = mutableListOf<String>()
        val tips = mutableListOf<String>()

        if (isMaskRejected || maskArea < ProvisionalConstants.MASK_MIN_AREA_FRACTION) {
            reasons.add("No sugar sample detected or mask area too small (< 10%)")
            tips.add("Place the sugar sample centrally on a contrasting background or fill the frame")
        }

        if (avgBrightness < ProvisionalConstants.QUALITY_MIN_BRIGHTNESS) {
            reasons.add("Image is severely underexposed (too dark)")
            tips.add("Increase ambient lighting or move closer to standard light box")
        } else if (avgBrightness > ProvisionalConstants.QUALITY_MAX_BRIGHTNESS) {
            reasons.add("Image is severely overexposed (blown out)")
            tips.add("Reduce glare, reflections, or decrease camera exposure")
        }

        if (clippedFraction > ProvisionalConstants.QUALITY_MAX_CLIPPED_FRACTION) {
            reasons.add("Excessive pixel clipping (${(clippedFraction * 100).roundToInt()}%)")
            tips.add("Avoid direct harsh reflections on sugar crystals")
        }

        if (contrast < ProvisionalConstants.QUALITY_MIN_CONTRAST) {
            reasons.add("Severely low contrast (flat/blank image)")
            tips.add("Ensure optical focus on crystalline structure")
        }

        if (blurScore < ProvisionalConstants.QUALITY_MIN_BLUR_SCORE) {
            reasons.add("Severe optical blur (Laplacian score: ${String.format(java.util.Locale.ROOT, "%.1f", blurScore)})")
            tips.add("Hold the camera steady and tap to refocus")
        }

        if (reasons.isNotEmpty()) {
            return Evaluation(
                status = Status.REJECT,
                isPassed = false,
                blurScore = blurScore,
                brightness = avgBrightness,
                contrast = contrast,
                clippedFraction = clippedFraction,
                maskAreaFraction = maskArea,
                primaryReason = reasons.first(),
                recommendations = tips
            )
        }

        // Check WARN conditions
        val warnReasons = mutableListOf<String>()
        if (blurScore < ProvisionalConstants.QUALITY_MIN_BLUR_SCORE * 2.5) {
            warnReasons.add("Moderate blur detected; results may have reduced precision")
        }
        if (clippedFraction > 0.08) {
            warnReasons.add("Minor highlight clipping detected on crystals")
        }
        if (avgBrightness < 40.0 || avgBrightness > 215.0) {
            warnReasons.add("Sub-optimal lighting level")
        }

        if (warnReasons.isNotEmpty()) {
            return Evaluation(
                status = Status.WARN,
                isPassed = true,
                blurScore = blurScore,
                brightness = avgBrightness,
                contrast = contrast,
                clippedFraction = clippedFraction,
                maskAreaFraction = maskArea,
                primaryReason = warnReasons.first(),
                recommendations = listOf("Lighting or sharpness is sub-optimal but passable for provisional check")
            )
        }

        return Evaluation(
            status = Status.OK,
            isPassed = true,
            blurScore = blurScore,
            brightness = avgBrightness,
            contrast = contrast,
            clippedFraction = clippedFraction,
            maskAreaFraction = maskArea,
            primaryReason = "Image optical quality passed",
            recommendations = emptyList()
        )
    }
}
