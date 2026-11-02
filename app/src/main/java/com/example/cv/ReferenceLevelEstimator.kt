package com.example.cv

import kotlin.math.abs
import kotlin.math.round

/**
 * Pure Kotlin estimator for relative Reference Level (1.0 to 6.0).
 * Strictly relative spectrum position between consecutive reference samples.
 * NOT an ICUMSA IU value or statistical probability.
 */
object ReferenceLevelEstimator {

    data class ReferenceTarget(
        val id: Int,
        val name: String,
        val lab: Triple<Double, Double, Double>,
        val source: String = "SYNTHETIC_DEMO",
        val isValid: Boolean = true
    )

    data class ReferenceMatch(
        val reference: ReferenceTarget,
        val deltaE00: Double,
        val similarityPct: Double
    )

    data class LevelEstimationResult(
        val estimatedLevel: Double?,
        val nearestReferenceId: Int,
        val secondNearestReferenceId: Int?,
        val distanceBetweenTopTwo: Double?,
        val explanation: String,
        val allMatches: List<ReferenceMatch>,
        val isSyntheticDemo: Boolean
    )

    /**
     * Pure testable interpolation function between two reference points.
     * Given reference IDs idA and idB (where idA < idB and idB == idA + 1),
     * and their respective Delta E00 distances dA and dB:
     * t = dA / (dA + dB)
     * estimatedLevel = idA + t
     */
    fun interpolateLevel(
        idA: Int,
        idB: Int,
        dA: Double,
        dB: Double,
        unknownThreshold: Double = ProvisionalConstants.UNKNOWN_DELTA_E_THRESHOLD
    ): Pair<Double?, String> {
        val lowId = minOf(idA, idB)
        val highId = maxOf(idA, idB)
        val dLow = if (lowId == idA) dA else dB
        val dHigh = if (lowId == idA) dB else dA

        // 1. Must be consecutive
        if (highId != lowId + 1) {
            return Pair(null, "References #$lowId and #$highId are not consecutive")
        }

        // 2. Both distances must be non-negative and finite
        if (dLow.isNaN() || dHigh.isNaN() || dLow.isInfinite() || dHigh.isInfinite() || dLow < 0.0 || dHigh < 0.0) {
            return Pair(null, "Invalid distance values (NaN or Infinite)")
        }

        // 3. Nearest must be within unknown threshold
        val minD = minOf(dLow, dHigh)
        if (minD > unknownThreshold) {
            return Pair(null, "Closest distance (${String.format(java.util.Locale.ROOT, "%.2f", minD)}) exceeds Unknown threshold ($unknownThreshold)")
        }

        // 4. Denominator check (dLow + dHigh must be distinct > 1e-6)
        val denom = dLow + dHigh
        if (denom < 1e-6) {
            return Pair(null, "Both reference samples are indistinguishable (dLow + dHigh ≈ 0)")
        }

        val t = (dLow / denom).coerceIn(0.0, 1.0)
        val rawLevel = lowId.toDouble() + t

        // Round to 2 decimal places
        val rounded = round(rawLevel * 100.0) / 100.0
        return Pair(rounded, "Interpolated between #$lowId and #$highId")
    }

    /**
     * Estimates reference level across all valid reference samples.
     */
    fun estimate(
        sampleLab: Triple<Double, Double, Double>,
        references: List<ReferenceTarget>,
        qualityPassed: Boolean = true,
        tau: Double = ProvisionalConstants.TAU_SIMILARITY_DECAY,
        unknownThreshold: Double = ProvisionalConstants.UNKNOWN_DELTA_E_THRESHOLD
    ): LevelEstimationResult {
        if (!qualityPassed) {
            return LevelEstimationResult(
                estimatedLevel = null,
                nearestReferenceId = 0,
                secondNearestReferenceId = null,
                distanceBetweenTopTwo = null,
                explanation = "Image quality check failed; level estimation aborted",
                allMatches = emptyList(),
                isSyntheticDemo = false
            )
        }

        val validRefs = references.filter { it.isValid }
        if (validRefs.isEmpty()) {
            return LevelEstimationResult(
                estimatedLevel = null,
                nearestReferenceId = 0,
                secondNearestReferenceId = null,
                distanceBetweenTopTwo = null,
                explanation = "No valid reference samples available",
                allMatches = emptyList(),
                isSyntheticDemo = false
            )
        }

        val matches = validRefs.map { ref ->
            val de00 = Ciede2000.compute(
                sampleLab.first, sampleLab.second, sampleLab.third,
                ref.lab.first, ref.lab.second, ref.lab.third
            ).deltaE00
            val sim = Ciede2000.relativeSimilarityIndex(de00, tau)
            ReferenceMatch(ref, de00, sim)
        }.sortedBy { it.deltaE00 }

        val best = matches.first()
        val second = if (matches.size > 1) matches[1] else null
        val distBetweenTopTwo = if (second != null) abs(best.deltaE00 - second.deltaE00) else null
        val isSynthetic = validRefs.any { it.source == "SYNTHETIC_DEMO" }

        if (second == null) {
            return LevelEstimationResult(
                estimatedLevel = best.reference.id.toDouble(),
                nearestReferenceId = best.reference.id,
                secondNearestReferenceId = null,
                distanceBetweenTopTwo = null,
                explanation = "Single reference available: #${best.reference.id}",
                allMatches = matches,
                isSyntheticDemo = isSynthetic
            )
        }

        // Interpolation
        val (level, explanation) = interpolateLevel(
            best.reference.id,
            second.reference.id,
            best.deltaE00,
            second.deltaE00,
            unknownThreshold
        )

        return LevelEstimationResult(
            estimatedLevel = level,
            nearestReferenceId = best.reference.id,
            secondNearestReferenceId = second.reference.id,
            distanceBetweenTopTwo = distBetweenTopTwo,
            explanation = explanation,
            allMatches = matches,
            isSyntheticDemo = isSynthetic
        )
    }
}
