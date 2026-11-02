package com.example.cv

import kotlin.math.sqrt

enum class NormalizationMethod {
    STANDARD_SCALER,
    MIN_MAX_SCALER
}

class FeatureScaler {

    /**
     * Normalizes a feature vector using domain-aware feature scaling.
     * Preserves exact color and statistical differences between reference samples
     * and newly analyzed samples without distortion.
     */
    fun domainScale(vector: DoubleArray): DoubleArray {
        if (vector.isEmpty()) return vector
        val scaled = DoubleArray(vector.size)

        // 191 features: 63 stats (9 channels * 7 stats) + 64 RGB Hist + 64 LAB Hist
        if (vector.size == ProvisionalConstants.EXPECTED_FEATURE_VECTOR_SIZE) {
            val channelDomains = doubleArrayOf(
                255.0, // R
                255.0, // G
                255.0, // B
                360.0, // H
                1.0,   // S
                1.0,   // V
                100.0, // L
                255.0, // A (shifted)
                255.0  // B (shifted)
            )

            for (ch in 0 until 9) {
                val base = ch * 7
                val maxDomain = channelDomains[ch]

                val meanVal = vector[base + 0]
                val medVal = vector[base + 1]
                val stdVal = vector[base + 2]
                val varVal = vector[base + 3]
                val entVal = vector[base + 4]
                val skewVal = vector[base + 5]
                val kurtVal = vector[base + 6]

                if (ch == 7 || ch == 8) { // LAB A and B channels (-128 to +127)
                    scaled[base + 0] = ((meanVal + 128.0) / 256.0).coerceIn(0.0, 1.0)
                    scaled[base + 1] = ((medVal + 128.0) / 256.0).coerceIn(0.0, 1.0)
                } else {
                    scaled[base + 0] = (meanVal / maxDomain).coerceIn(0.0, 1.0)
                    scaled[base + 1] = (medVal / maxDomain).coerceIn(0.0, 1.0)
                }
                scaled[base + 2] = (stdVal / (maxDomain / 2.0)).coerceIn(0.0, 1.0)
                scaled[base + 3] = (varVal / ((maxDomain / 2.0) * (maxDomain / 2.0))).coerceIn(0.0, 1.0)
                scaled[base + 4] = (entVal / 5.0).coerceIn(0.0, 1.0)
                scaled[base + 5] = ((skewVal + 5.0) / 10.0).coerceIn(0.0, 1.0)
                scaled[base + 6] = (kurtVal / 20.0).coerceIn(0.0, 1.0)
            }

            // Histograms (63 to 190) are already normalized frequency values in [0, 1]
            for (i in 63 until vector.size) {
                scaled[i] = vector[i].coerceIn(0.0, 1.0)
            }
            return scaled
        }

        // Explicit fallback for non-standard or legacy vectors (logs warning)
        android.util.Log.w("FeatureScaler", "Vector size ${vector.size} does not match standard 191; falling back to minMaxScale")
        return minMaxScale(vector)
    }

    fun scale(vector: DoubleArray, method: NormalizationMethod = NormalizationMethod.STANDARD_SCALER): DoubleArray {
        return when (method) {
            NormalizationMethod.STANDARD_SCALER -> domainScale(vector)
            NormalizationMethod.MIN_MAX_SCALER -> minMaxScale(vector)
        }
    }

    fun minMaxScale(vector: DoubleArray): DoubleArray {
        if (vector.isEmpty()) return vector
        val scaled = DoubleArray(vector.size)
        var minVal = Double.MAX_VALUE
        var maxVal = -Double.MAX_VALUE
        for (v in vector) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        val range = (maxVal - minVal).coerceAtLeast(1e-6)
        for (i in vector.indices) {
            scaled[i] = (vector[i] - minVal) / range
        }
        return scaled
    }
}
