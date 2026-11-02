package com.example.cv

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

class FeatureExtractor {

    /**
     * Extracts Color and Statistical features for a single ROI bitmap (512x512)
     * Channels: R, G, B, H, S, V, L, A, B (9 channels)
     * Statistics per channel: Mean, Median, Std Dev, Variance, Entropy, Skewness, Kurtosis (7 stats * 9 = 63 features)
     * Color Histograms: 64-bin RGB histogram + 64-bin LAB histogram (128 features)
     * Total Feature Vector Length: 191 features
     */
    fun extractRegionFeatures(bitmap: Bitmap, regionIndex: Int = 0, regionName: String = "Single ROI (Whole Image)"): FeatureVector {
        val w = bitmap.width
        val h = bitmap.height
        val total = w * h
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val rVals = DoubleArray(total)
        val gVals = DoubleArray(total)
        val bVals = DoubleArray(total)
        val hVals = DoubleArray(total)
        val sVals = DoubleArray(total)
        val vVals = DoubleArray(total)
        val lVals = DoubleArray(total)
        val aVals = DoubleArray(total)
        val bLabVals = DoubleArray(total)

        val hsvTmp = FloatArray(3)
        val histogramRgb64 = DoubleArray(64) // 4x4x4 RGB bins
        val histogramLab64 = DoubleArray(64) // 4x4x4 LAB bins

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            rVals[i] = r.toDouble()
            gVals[i] = g.toDouble()
            bVals[i] = b.toDouble()

            // 64-bin RGB Color Histogram (4 bins per channel)
            val rBin = (r / 64).coerceIn(0, 3)
            val gBin = (g / 64).coerceIn(0, 3)
            val bBin = (b / 64).coerceIn(0, 3)
            val rgbBinIdx = rBin * 16 + gBin * 4 + bBin
            histogramRgb64[rgbBinIdx] += 1.0

            // HSV Conversion
            Color.RGBToHSV(r, g, b, hsvTmp)
            hVals[i] = hsvTmp[0].toDouble()
            sVals[i] = hsvTmp[1].toDouble()
            vVals[i] = hsvTmp[2].toDouble()

            // LAB Conversion
            val (labL, labA, labB) = rgbToLab(r, g, b)
            lVals[i] = labL
            aVals[i] = labA
            bLabVals[i] = labB

            // 64-bin LAB Color Histogram (4 bins per channel)
            val lBin = ((labL / 100.0) * 3.99).toInt().coerceIn(0, 3)
            val aBin = (((labA + 128.0) / 256.0) * 3.99).toInt().coerceIn(0, 3)
            val bLabBin = (((labB + 128.0) / 256.0) * 3.99).toInt().coerceIn(0, 3)
            val labBinIdx = lBin * 16 + aBin * 4 + bLabBin
            histogramLab64[labBinIdx] += 1.0
        }

        // Normalize Color Histograms
        for (i in 0 until 64) {
            histogramRgb64[i] /= total.toDouble()
            histogramLab64[i] /= total.toDouble()
        }

        // Feature vector list
        val features = mutableListOf<Double>()

        // 1. Statistical Features across all 9 channels (R, G, B, H, S, V, L, A, B)
        // Each channel contributes: Mean, Median, StdDev, Variance, Entropy, Skewness, Kurtosis (7 stats * 9 = 63 features)
        val channels = listOf(rVals, gVals, bVals, hVals, sVals, vVals, lVals, aVals, bLabVals)
        var medianL = 0.0
        var medianA = 0.0
        var medianB = 0.0

        for ((chIdx, channel) in channels.withIndex()) {
            val stats = computeStats(channel)
            features.add(stats.mean)
            features.add(stats.median)
            features.add(stats.std)
            features.add(stats.variance)
            features.add(stats.entropy)
            features.add(stats.skewness)
            features.add(stats.kurtosis)

            if (chIdx == 6) medianL = stats.median
            if (chIdx == 7) medianA = stats.median
            if (chIdx == 8) medianB = stats.median
        }

        val labMedian = LabMedian(medianL = medianL, medianA = medianA, medianB = medianB)

        // 2. Add Normalized 64-Bin RGB Color Histogram (64 features)
        for (binVal in histogramRgb64) {
            features.add(binVal)
        }

        // 3. Add Normalized 64-Bin LAB Color Histogram (64 features)
        for (binVal in histogramLab64) {
            features.add(binVal)
        }

        return FeatureVector(
            regionIndex = regionIndex,
            regionName = regionName,
            values = features.toDoubleArray(),
            labMedian = labMedian
        )
    }

    data class ChannelStats(
        val mean: Double,
        val median: Double,
        val std: Double,
        val variance: Double,
        val entropy: Double,
        val skewness: Double,
        val kurtosis: Double
    )

    private fun computeStats(data: DoubleArray): ChannelStats {
        if (data.isEmpty()) return ChannelStats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val n = data.size
        var sum = 0.0
        var minVal = Double.MAX_VALUE
        var maxVal = -Double.MAX_VALUE

        for (v in data) {
            sum += v
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        val mean = sum / n

        var varSum = 0.0
        var m3Sum = 0.0
        var m4Sum = 0.0

        for (v in data) {
            val diff = v - mean
            val diff2 = diff * diff
            varSum += diff2
            m3Sum += diff2 * diff
            m4Sum += diff2 * diff2
        }

        val variance = varSum / n
        val std = sqrt(variance)

        val skewness = if (std > 1e-6) (m3Sum / n) / (std * std * std) else 0.0
        val kurtosis = if (std > 1e-6) (m4Sum / n) / (variance * variance) else 0.0

        // Median
        val sorted = data.clone()
        sorted.sort()
        val median = if (n % 2 == 0) (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0 else sorted[n / 2]

        // Entropy (32-bin histogram)
        val bins = IntArray(32)
        val range = (maxVal - minVal).coerceAtLeast(1e-5)
        for (v in data) {
            val bIdx = (((v - minVal) / range) * 31.99).toInt().coerceIn(0, 31)
            bins[bIdx]++
        }
        var entropy = 0.0
        for (count in bins) {
            if (count > 0) {
                val p = count.toDouble() / n
                entropy -= p * (ln(p) / ln(2.0))
            }
        }

        return ChannelStats(
            mean = mean,
            median = median,
            std = std,
            variance = variance,
            entropy = entropy,
            skewness = skewness,
            kurtosis = kurtosis
        )
    }

    private fun rgbToLab(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        var varR = r / 255.0
        var varG = g / 255.0
        var varB = b / 255.0

        varR = if (varR > 0.04045) ((varR + 0.055) / 1.055).pow(2.4) else varR / 12.92
        varG = if (varG > 0.04045) ((varG + 0.055) / 1.055).pow(2.4) else varG / 12.92
        varB = if (varB > 0.04045) ((varB + 0.055) / 1.055).pow(2.4) else varB / 12.92

        varR *= 100
        varG *= 100
        varB *= 100

        val x = varR * 0.4124 + varG * 0.3576 + varB * 0.1805
        val y = varR * 0.2126 + varG * 0.7152 + varB * 0.0722
        val z = varR * 0.0193 + varG * 0.1192 + varB * 0.9505

        val refX = 95.047
        val refY = 100.000
        val refZ = 108.883

        var varX = x / refX
        var varY = y / refY
        var varZ = z / refZ

        varX = if (varX > 0.008856) varX.pow(1.0 / 3.0) else (7.787 * varX) + (16.0 / 116.0)
        varY = if (varY > 0.008856) varY.pow(1.0 / 3.0) else (7.787 * varY) + (16.0 / 116.0)
        varZ = if (varZ > 0.008856) varZ.pow(1.0 / 3.0) else (7.787 * varZ) + (16.0 / 116.0)

        val l = (116 * varY) - 16
        val a = 500 * (varX - varY)
        val labB = 200 * (varY - varZ)

        return Triple(l, a, labB)
    }
}
