package com.example.cv

import kotlin.math.*

/**
 * Pure Kotlin statistical and color metric calculations on masked pixels.
 * Operates on raw IntArray and BooleanArray mask.
 */
object ColorStats {

    data class MaskedColorProfile(
        val count: Int,
        val meanL: Double,
        val trimmedMeanL: Double,
        val medianL: Double,
        val stdL: Double,

        val meanA: Double,
        val trimmedMeanA: Double,
        val medianA: Double,
        val stdA: Double,

        val meanB: Double,
        val trimmedMeanB: Double,
        val medianB: Double,
        val stdB: Double,

        val meanChroma: Double,
        val circularMeanHueDeg: Double,
        val circularHueVariance: Double,
        val hasColorHue: Boolean,

        val labHistogram64: DoubleArray,
        val rgbHistogram64: DoubleArray
    )

    fun computeProfile(pixels: IntArray, mask: BooleanArray): MaskedColorProfile {
        require(pixels.size == mask.size) { "Pixels and mask size mismatch" }

        val lVals = mutableListOf<Double>()
        val aVals = mutableListOf<Double>()
        val bVals = mutableListOf<Double>()

        var sumSinHue = 0.0
        var sumCosHue = 0.0
        var colorfulCount = 0

        val rgbHist = DoubleArray(64)
        val labHist = DoubleArray(64)

        var totalMasked = 0

        for (i in pixels.indices) {
            if (!mask[i]) continue
            totalMasked++

            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            // sRGB 4x4x4 histogram
            val rBin = (r / 64).coerceIn(0, 3)
            val gBin = (g / 64).coerceIn(0, 3)
            val bBin = (b / 64).coerceIn(0, 3)
            rgbHist[rBin * 16 + gBin * 4 + bBin] += 1.0

            // Lab calculation
            val (labL, labA, labB) = CieLab.rgbToLab(r, g, b)
            lVals.add(labL)
            aVals.add(labA)
            bVals.add(labB)

            // Lab 4x4x4 histogram with sugar-specific ranges
            val lNorm = ((labL - ProvisionalConstants.LAB_HIST_L_MIN) /
                    (ProvisionalConstants.LAB_HIST_L_MAX - ProvisionalConstants.LAB_HIST_L_MIN)).coerceIn(0.0, 0.9999)
            val aNorm = ((labA - ProvisionalConstants.LAB_HIST_A_MIN) /
                    (ProvisionalConstants.LAB_HIST_A_MAX - ProvisionalConstants.LAB_HIST_A_MIN)).coerceIn(0.0, 0.9999)
            val bNorm = ((labB - ProvisionalConstants.LAB_HIST_B_MIN) /
                    (ProvisionalConstants.LAB_HIST_B_MAX - ProvisionalConstants.LAB_HIST_B_MIN)).coerceIn(0.0, 0.9999)

            val lBin = (lNorm * 4.0).toInt().coerceIn(0, 3)
            val aBin = (aNorm * 4.0).toInt().coerceIn(0, 3)
            val bLabBin = (bNorm * 4.0).toInt().coerceIn(0, 3)
            labHist[lBin * 16 + aBin * 4 + bLabBin] += 1.0

            // HSV for circular hue
            val maxC = max(r, max(g, b)).toDouble()
            val minC = min(r, min(g, b)).toDouble()
            val delta = maxC - minC
            val sat = if (maxC > 0.0) delta / maxC else 0.0

            if (sat >= 0.10 && delta > 0.0) {
                var hue = when {
                    maxC == r.toDouble() -> 60.0 * (((g - b) / delta) % 6.0)
                    maxC == g.toDouble() -> 60.0 * (((b - r) / delta) + 2.0)
                    else -> 60.0 * (((r - g) / delta) + 4.0)
                }
                if (hue < 0.0) hue += 360.0
                val rad = Math.toRadians(hue)
                sumSinHue += sin(rad)
                sumCosHue += cos(rad)
                colorfulCount++
            }
        }

        if (totalMasked == 0) {
            return MaskedColorProfile(
                count = 0,
                meanL = 0.0, trimmedMeanL = 0.0, medianL = 0.0, stdL = 0.0,
                meanA = 0.0, trimmedMeanA = 0.0, medianA = 0.0, stdA = 0.0,
                meanB = 0.0, trimmedMeanB = 0.0, medianB = 0.0, stdB = 0.0,
                meanChroma = 0.0, circularMeanHueDeg = 0.0, circularHueVariance = 1.0,
                hasColorHue = false,
                labHistogram64 = DoubleArray(64),
                rgbHistogram64 = DoubleArray(64)
            )
        }

        // Normalize histograms strictly to sum = 1.0
        val totalD = totalMasked.toDouble()
        for (i in 0 until 64) {
            rgbHist[i] /= totalD
            labHist[i] /= totalD
        }

        fun stats(values: MutableList<Double>): Quad {
            values.sort()
            val n = values.size
            val sum = values.sum()
            val mean = sum / n

            // Median
            val med = if (n % 2 == 0) (values[n / 2 - 1] + values[n / 2]) / 2.0 else values[n / 2]

            // Trimmed mean 5% to 95%
            val trimCut = (n * 0.05).toInt()
            val trimmedList = if (trimCut > 0 && n > trimCut * 2) {
                values.subList(trimCut, n - trimCut)
            } else {
                values
            }
            val trimmedMean = trimmedList.sum() / trimmedList.size

            // Standard deviation
            var varSum = 0.0
            for (v in values) {
                val diff = v - mean
                varSum += diff * diff
            }
            val std = sqrt(varSum / n)

            return Quad(mean, trimmedMean, med, std)
        }

        val lStats = stats(lVals)
        val aStats = stats(aVals)
        val bStats = stats(bVals)

        val chroma = sqrt(aStats.trimmedMean * aStats.trimmedMean + bStats.trimmedMean * bStats.trimmedMean)

        var circularMeanHue = 0.0
        var circularHueVar = 1.0
        val hasColor = colorfulCount > 0

        if (hasColor) {
            val avgSin = sumSinHue / colorfulCount
            val avgCos = sumCosHue / colorfulCount
            var angleDeg = Math.toDegrees(atan2(avgSin, avgCos))
            if (angleDeg < 0.0) angleDeg += 360.0
            circularMeanHue = angleDeg
            val rLen = sqrt(avgSin * avgSin + avgCos * avgCos)
            circularHueVar = (1.0 - rLen).coerceIn(0.0, 1.0)
        }

        return MaskedColorProfile(
            count = totalMasked,
            meanL = lStats.mean,
            trimmedMeanL = lStats.trimmedMean,
            medianL = lStats.median,
            stdL = lStats.std,

            meanA = aStats.mean,
            trimmedMeanA = aStats.trimmedMean,
            medianA = aStats.median,
            stdA = aStats.std,

            meanB = bStats.mean,
            trimmedMeanB = bStats.trimmedMean,
            medianB = bStats.median,
            stdB = bStats.std,

            meanChroma = chroma,
            circularMeanHueDeg = circularMeanHue,
            circularHueVariance = circularHueVar,
            hasColorHue = hasColor,

            labHistogram64 = labHist,
            rgbHistogram64 = rgbHist
        )
    }

    private data class Quad(val mean: Double, val trimmedMean: Double, val median: Double, val std: Double)
}
