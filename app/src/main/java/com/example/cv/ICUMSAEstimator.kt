package com.example.cv

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ICUMSAEstimator implementing Section 5:
 * 1. Exactly 6 Reference Slots sorted ascending by ICUMSA value.
 * 2. 3D representation in CIE-LAB space: P_i = (L_i, a_i, b_i) from Median LAB.
 * 3. Linear interpolation along nearest line segment:
 *    t = dot(Q - P1, P2 - P1) / ||P2 - P1||^2
 *    ICUMSA_estimated = ICUMSA_1 + t * (ICUMSA_2 - ICUMSA_1)
 * 4. Selection of segment with minimum perpendicular distance from Q.
 * 5. Out-of-bounds & extrapolation detection and warnings.
 * 6. Auxiliary Euclidean, Cosine, and Bhattacharyya comparisons.
 * 7. Unknown status when similarity < 65%.
 */
class ICUMSAEstimator {

    /**
     * Calculates Euclidean distance between two vectors
     */
    fun euclideanDistance(v1: DoubleArray, v2: DoubleArray): Double {
        val minLen = min(v1.size, v2.size)
        if (minLen == 0) return 999.0
        var sum = 0.0
        for (i in 0 until minLen) {
            val diff = v1[i] - v2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Calculates mathematical Cosine Similarity between two feature vectors:
     * cos(theta) = dot(u, v) / (||u|| * ||v||)
     * Range: [-1.0, 1.0], bounded and distinct from Pearson Correlation.
     */
    fun cosineSimilarity(v1: DoubleArray, v2: DoubleArray): Double {
        val minLen = min(v1.size, v2.size)
        if (minLen == 0) return 0.0

        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in 0 until minLen) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }

        val denominator = sqrt(norm1 * norm2).coerceAtLeast(1e-9)
        return (dot / denominator).coerceIn(-1.0, 1.0)
    }

    /**
     * Calculates Bhattacharyya Distance & Coefficient between two histograms:
     * BC = sum(sqrt(h1 * h2))
     * Distance = sqrt(max(0, 1 - BC))
     */
    fun bhattacharyya(h1: DoubleArray, h2: DoubleArray): Pair<Double, Double> {
        val minLen = min(h1.size, h2.size)
        if (minLen == 0) return Pair(1.0, 0.0)

        var bc = 0.0
        for (i in 0 until minLen) {
            bc += sqrt(max(0.0, h1[i] * h2[i]))
        }
        bc = bc.coerceIn(0.0, 1.0)
        val dist = sqrt((1.0 - bc).coerceAtLeast(0.0))
        return Pair(dist, bc)
    }

    /**
     * Estimates ICUMSA via 3D LAB Interpolation and compares against the 6 active references
     */
    fun estimateIcumsa(
        sampleMedianLab: MedianLab,
        sampleVector191: DoubleArray,
        sampleRgbHist: DoubleArray,
        sampleLabHist: DoubleArray,
        references: List<ReferenceSampleLab>,
        qualityResult: ImageQualityResult,
        similarityThresholdPct: Double = 65.0,
        startTimeMs: Long = System.currentTimeMillis()
    ): ClassificationResult {
        // Fallback when references are not yet populated
        if (references.isEmpty()) {
            val emptyLab = MedianLab(0.0, 0.0, 0.0)
            val emptySegment = InterpolationSegmentResult(
                segmentIndex = 0,
                ref1Id = 0, ref1Name = "None", ref1Icumsa = 0.0, ref1Lab = emptyLab,
                ref2Id = 0, ref2Name = "None", ref2Icumsa = 0.0, ref2Lab = emptyLab,
                rawT = 0.0, clampedT = 0.0, projectedLab = emptyLab,
                perpendicularDistance = 999.0, isOutOfBounds = true,
                warningMessage = "هیچ نمونه مرجعی تعریف نشده است."
            )
            val emptyMatch = ReferenceMatch(
                sampleId = 0, sampleName = "Unknown Sample", icumsaValue = 0.0,
                medianLab = emptyLab, euclideanDistance = 999.0, euclideanSimilarityPct = 0.0,
                cosineSimilarity = 0.0, cosineSimilarityPct = 0.0,
                bhattacharyyaDistance = 1.0, bhattacharyyaSimilarityPct = 0.0,
                finalSimilarityPct = 0.0
            )
            return ClassificationResult(
                estimatedIcumsa = 0.0,
                sampleMedianLab = sampleMedianLab,
                interpolation = emptySegment,
                nearestSampleId = 0,
                nearestSampleName = "Unknown Sample",
                isUnknown = true,
                confidenceScorePct = 0.0,
                confidenceLevel = ConfidenceLevel.LOW,
                topMatch = emptyMatch,
                allMatches = listOf(emptyMatch),
                processingTimeMs = System.currentTimeMillis() - startTimeMs,
                qualityResult = qualityResult,
                validPixelCount = 0,
                totalMaskPixelCount = 0,
                sampleCoveragePct = 0.0,
                thresholdUsed = similarityThresholdPct
            )
        }

        // 1. Sort active references ascending by actual ICUMSA value
        val sortedRefs = references.sortedBy { it.actualIcumsa }

        // 2. Query point in 3D LAB space: Q = (Lq, aq, bq)
        val lq = sampleMedianLab.l
        val aq = sampleMedianLab.a
        val bq = sampleMedianLab.b

        // 3. Interpolate along the 5 line segments connecting consecutive references
        var bestSegmentIndex = 0
        var minPerpendicularDist = Double.MAX_VALUE
        var bestRef1 = sortedRefs[0]
        var bestRef2 = sortedRefs[min(1, sortedRefs.size - 1)]
        var bestRawT = 0.0
        var bestClampedT = 0.0
        var bestProjectedLab = MedianLab(lq, aq, bq)

        val numSegments = sortedRefs.size - 1

        for (i in 0 until numSegments) {
            val r1 = sortedRefs[i]
            val r2 = sortedRefs[i + 1]

            val p1 = r1.medianLab
            val p2 = r2.medianLab

            val vl = p2.l - p1.l
            val va = p2.a - p1.a
            val vb = p2.b - p1.b

            val lenSq = vl * vl + va * va + vb * vb

            val rawT: Double
            val clampedT: Double
            val projL: Double
            val projA: Double
            val projB: Double

            if (lenSq < 1e-6) {
                // Points are essentially coincident, prevent division by zero
                rawT = 0.0
                clampedT = 0.0
                projL = p1.l
                projA = p1.a
                projB = p1.b
            } else {
                val dot = (lq - p1.l) * vl + (aq - p1.a) * va + (bq - p1.b) * vb
                rawT = dot / lenSq
                clampedT = rawT.coerceIn(0.0, 1.0)
                projL = p1.l + clampedT * vl
                projA = p1.a + clampedT * va
                projB = p1.b + clampedT * vb
            }

            val dL = lq - projL
            val dA = aq - projA
            val dB = bq - projB
            val dist = sqrt(dL * dL + dA * dA + dB * dB)

            if (dist < minPerpendicularDist) {
                minPerpendicularDist = dist
                bestSegmentIndex = i + 1 // 1-indexed (1 to 5)
                bestRef1 = r1
                bestRef2 = r2
                bestRawT = rawT
                bestClampedT = clampedT
                bestProjectedLab = MedianLab(projL, projA, projB)
            }
        }

        // 4. Calculate estimated ICUMSA using linear interpolation
        val estimatedIcumsa = bestRef1.actualIcumsa + bestClampedT * (bestRef2.actualIcumsa - bestRef1.actualIcumsa)

        // 5. Out-of-bounds & extrapolation check
        var isOutOfBounds = false
        var outOfBoundsWarning: String? = null

        if (bestSegmentIndex == 1 && bestRawT < 0.0) {
            isOutOfBounds = true
            outOfBoundsWarning = "نمونه روشن‌تر از کمترین مرجع موجود است (خارج از محدوده پایین)."
        } else if (bestSegmentIndex == numSegments && bestRawT > 1.0) {
            isOutOfBounds = true
            outOfBoundsWarning = "نمونه تیره‌تر از بالاترین مرجع موجود است (خارج از محدوده بالا)."
        } else if (minPerpendicularDist > 25.0) {
            isOutOfBounds = true
            outOfBoundsWarning = "فاصله رنگی نمونه از مسیر مراجع زیاد است (انحراف رنگی بالا)."
        }

        val interpolationResult = InterpolationSegmentResult(
            segmentIndex = bestSegmentIndex,
            ref1Id = bestRef1.id,
            ref1Name = bestRef1.name,
            ref1Icumsa = bestRef1.actualIcumsa,
            ref1Lab = bestRef1.medianLab,
            ref2Id = bestRef2.id,
            ref2Name = bestRef2.name,
            ref2Icumsa = bestRef2.actualIcumsa,
            ref2Lab = bestRef2.medianLab,
            rawT = bestRawT,
            clampedT = bestClampedT,
            projectedLab = bestProjectedLab,
            perpendicularDistance = minPerpendicularDist,
            isOutOfBounds = isOutOfBounds,
            warningMessage = outOfBoundsWarning
        )

        // 6. Auxiliary Similarity Calculations against all references
        val matches = mutableListOf<ReferenceMatch>()
        val sqrtDim = sqrt(sampleVector191.size.toDouble().coerceAtLeast(1.0))

        for (ref in sortedRefs) {
            val eucDist = euclideanDistance(sampleVector191, ref.vector191)
            val cosSim = cosineSimilarity(sampleVector191, ref.vector191)

            val normEuc = eucDist / sqrtDim
            val eucPct = (exp(-normEuc / 0.15) * 100.0).coerceIn(0.0, 100.0)
            val cosPct = (max(0.0, cosSim) * 100.0).coerceIn(0.0, 100.0)

            val (_, rgbBc) = bhattacharyya(sampleRgbHist, ref.rgbHistogram64)
            val (labDist, labBc) = bhattacharyya(sampleLabHist, ref.labHistogram64)
            val avgBc = (0.5 * rgbBc + 0.5 * labBc).coerceIn(0.0, 1.0)
            val bhattSimPct = (avgBc * 100.0).coerceIn(0.0, 100.0)

            // Combined Similarity Score: Cosine (40%), Euclidean (30%), Bhattacharyya (30%)
            val finalScore = (0.40 * cosPct + 0.30 * eucPct + 0.30 * bhattSimPct).coerceIn(0.0, 100.0)

            matches.add(
                ReferenceMatch(
                    sampleId = ref.id,
                    sampleName = ref.name,
                    icumsaValue = ref.actualIcumsa,
                    medianLab = ref.medianLab,
                    euclideanDistance = eucDist,
                    euclideanSimilarityPct = eucPct,
                    cosineSimilarity = cosSim,
                    cosineSimilarityPct = cosPct,
                    bhattacharyyaDistance = labDist,
                    bhattacharyyaSimilarityPct = bhattSimPct,
                    finalSimilarityPct = finalScore
                )
            )
        }

        // Sort matches descending by similarity
        matches.sortByDescending { it.finalSimilarityPct }
        val topMatch = matches.first()

        // 7. Unknown Status checking
        val isUnknown = topMatch.finalSimilarityPct < similarityThresholdPct || isOutOfBounds

        val confidenceLevel = when {
            topMatch.finalSimilarityPct >= 88.0 && !isOutOfBounds -> ConfidenceLevel.VERY_HIGH
            topMatch.finalSimilarityPct >= 75.0 && !isOutOfBounds -> ConfidenceLevel.HIGH
            topMatch.finalSimilarityPct >= 60.0 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        val nearestId = if (isUnknown) 0 else topMatch.sampleId
        val nearestName = if (isUnknown) "Unknown Sample" else topMatch.sampleName

        return ClassificationResult(
            estimatedIcumsa = estimatedIcumsa,
            estimationLabel = "تخمین ICUMSA مبتنی بر تصویر",
            sampleMedianLab = sampleMedianLab,
            interpolation = interpolationResult,
            nearestSampleId = nearestId,
            nearestSampleName = nearestName,
            isUnknown = isUnknown,
            confidenceScorePct = topMatch.finalSimilarityPct,
            confidenceLevel = confidenceLevel,
            topMatch = topMatch,
            allMatches = matches,
            processingTimeMs = System.currentTimeMillis() - startTimeMs,
            qualityResult = qualityResult,
            validPixelCount = 0,
            totalMaskPixelCount = 0,
            sampleCoveragePct = qualityResult.sampleCoverage * 100.0,
            thresholdUsed = similarityThresholdPct
        )
    }
}
