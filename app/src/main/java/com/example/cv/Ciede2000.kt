package com.example.cv

import kotlin.math.*

/**
 * CIEDE2000 Color-Difference Formula.
 * Conforms to Sharma, Wu, Dalal (2005) "The CIEDE2000 Color-Difference Formula:
 * Implementation Notes, Supplementary Technical Data, and Mathematical Checklist".
 */
object Ciede2000 {

    data class DiffResult(
        val deltaE00: Double,
        val deltaL: Double,
        val deltaA: Double,
        val deltaB: Double,
        val deltaC: Double,
        val deltaH: Double,
        val deltaHueDeg: Double,
        val directionDesc: String = ""
    )

    fun compute(
        l1: Double, a1: Double, b1: Double,
        l2: Double, a2: Double, b2: Double,
        kL: Double = 1.0,
        kC: Double = 1.0,
        kH: Double = 1.0
    ): DiffResult {
        // Step 1: Calculate C*i, a'i, C'i, h'i
        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)
        val cBar = (c1 + c2) / 2.0
        val cBar7 = cBar.pow(7.0)
        val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + 6103515625.0))) // 25^7 = 6103515625

        val a1Prime = (1.0 + g) * a1
        val a2Prime = (1.0 + g) * a2

        val c1Prime = sqrt(a1Prime * a1Prime + b1 * b1)
        val c2Prime = sqrt(a2Prime * a2Prime + b2 * b2)

        var h1Prime = Math.toDegrees(atan2(b1, a1Prime))
        if (h1Prime < 0.0) h1Prime += 360.0

        var h2Prime = Math.toDegrees(atan2(b2, a2Prime))
        if (h2Prime < 0.0) h2Prime += 360.0

        // Step 2: Calculate dL', dC', dH'
        val deltaLPrime = l2 - l1
        val deltaCPrime = c2Prime - c1Prime

        var deltaHPrimeDeg: Double
        if (c1Prime * c2Prime < 1e-12) {
            deltaHPrimeDeg = 0.0
        } else {
            val diff = h2Prime - h1Prime
            deltaHPrimeDeg = when {
                abs(diff) <= 180.0 -> diff
                diff > 180.0 -> diff - 360.0
                else -> diff + 360.0
            }
        }
        val deltaHPrime = 2.0 * sqrt(c1Prime * c2Prime) * sin(Math.toRadians(deltaHPrimeDeg / 2.0))

        // Step 3: Calculate L'Bar, C'Bar, h'Bar, T
        val lBarPrime = (l1 + l2) / 2.0
        val cBarPrime = (c1Prime + c2Prime) / 2.0

        var hBarPrime: Double
        if (c1Prime * c2Prime < 1e-12) {
            hBarPrime = h1Prime + h2Prime
        } else {
            val diff = abs(h1Prime - h2Prime)
            val sum = h1Prime + h2Prime
            hBarPrime = when {
                diff <= 180.0 -> sum / 2.0
                sum < 360.0 -> (sum + 360.0) / 2.0
                else -> (sum - 360.0) / 2.0
            }
        }

        val radHBar = Math.toRadians(hBarPrime)
        val t = 1.0 -
                0.17 * cos(Math.toRadians(hBarPrime - 30.0)) +
                0.24 * cos(Math.toRadians(2.0 * hBarPrime)) +
                0.32 * cos(Math.toRadians(3.0 * hBarPrime + 6.0)) -
                0.20 * cos(Math.toRadians(4.0 * hBarPrime - 63.0))

        // Step 4: Calculate RT, SL, SC, SH
        val deltaTheta = 30.0 * exp(-(((hBarPrime - 275.0) / 25.0).pow(2.0)))
        val cBarPrime7 = cBarPrime.pow(7.0)
        val rc = 2.0 * sqrt(cBarPrime7 / (cBarPrime7 + 6103515625.0))
        val sl = 1.0 + (0.015 * (lBarPrime - 50.0).pow(2.0)) / sqrt(20.0 + (lBarPrime - 50.0).pow(2.0))
        val sc = 1.0 + 0.045 * cBarPrime
        val sh = 1.0 + 0.015 * cBarPrime * t
        val rt = -sin(Math.toRadians(2.0 * deltaTheta)) * rc

        // Step 5: Delta E 00
        val lTerm = deltaLPrime / (kL * sl)
        val cTerm = deltaCPrime / (kC * sc)
        val hTerm = deltaHPrime / (kH * sh)

        val de00Squared = lTerm * lTerm + cTerm * cTerm + hTerm * hTerm + rt * cTerm * hTerm
        val de00 = sqrt(max(0.0, de00Squared))

        // Descriptive direction (Sample 2 relative to Sample 1)
        val lightDesc = when {
            deltaLPrime > 0.5 -> "Lighter (+ΔL*)"
            deltaLPrime < -0.5 -> "Darker (-ΔL*)"
            else -> "Similar Lightness"
        }
        val redGreenDesc = when {
            (a2 - a1) > 0.5 -> "Redder (+Δa*)"
            (a2 - a1) < -0.5 -> "Greener (-Δa*)"
            else -> "Similar a*"
        }
        val yellowBlueDesc = when {
            (b2 - b1) > 0.5 -> "Yellower (+Δb*)"
            (b2 - b1) < -0.5 -> "Bluer (-Δb*)"
            else -> "Similar b*"
        }

        return DiffResult(
            deltaE00 = de00,
            deltaL = deltaLPrime,
            deltaA = a2 - a1,
            deltaB = b2 - b1,
            deltaC = deltaCPrime,
            deltaH = deltaHPrime,
            deltaHueDeg = deltaHPrimeDeg,
            directionDesc = "$lightDesc, $redGreenDesc, $yellowBlueDesc"
        )
    }

    /**
     * Relative similarity index formula:
     * 100 * exp(-deltaE00 / TAU)
     * Monotonic decreasing function in [0, 100].
     * TAU is uncalibrated / provisional.
     */
    fun relativeSimilarityIndex(deltaE00: Double, tau: Double = ProvisionalConstants.TAU_SIMILARITY_DECAY): Double {
        if (deltaE00 <= 0.0) return 100.0
        val score = 100.0 * exp(-deltaE00 / tau)
        return score.coerceIn(0.0, 100.0)
    }
}
