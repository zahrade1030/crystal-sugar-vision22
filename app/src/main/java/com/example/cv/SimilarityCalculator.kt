package com.example.cv

import kotlin.math.exp
import kotlin.math.sqrt

data class StoredReferenceVector(
    val sampleId: Int,
    val sampleName: String,
    val icumsaValue: Int,
    val vector: DoubleArray,
    val labMedian: LabMedian = LabMedian(0.0, 0.0, 0.0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StoredReferenceVector
        return vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        return vector.contentHashCode()
    }
}

class SimilarityCalculator {

    /**
     * Calculates Euclidean distance between two feature vectors
     */
    fun euclideanDistance(v1: DoubleArray, v2: DoubleArray): Double {
        val minLen = minOf(v1.size, v2.size)
        if (minLen == 0) return 999.0
        var sum = 0.0
        for (i in 0 until minLen) {
            val diff = v1[i] - v2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Calculates Cosine Similarity between two feature vectors using mean-centering (Pearson Correlation)
     * Bounded in [-1.0, +1.0]
     */
    fun cosineSimilarity(v1: DoubleArray, v2: DoubleArray): Double {
        val minLen = minOf(v1.size, v2.size)
        if (minLen == 0) return 0.0

        var sum1 = 0.0
        var sum2 = 0.0
        for (i in 0 until minLen) {
            sum1 += v1[i]
            sum2 += v2[i]
        }
        val mean1 = sum1 / minLen
        val mean2 = sum2 / minLen

        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in 0 until minLen) {
            val d1 = v1[i] - mean1
            val d2 = v2[i] - mean2
            dot += d1 * d2
            norm1 += d1 * d1
            norm2 += d2 * d2
        }

        val denominator = sqrt(norm1 * norm2).coerceAtLeast(1e-8)
        return (dot / denominator).coerceIn(-1.0, 1.0)
    }

    /**
     * Calculates Bhattacharyya Distance and Coefficient between two normalized histograms
     */
    fun bhattacharyyaDistance(h1: DoubleArray, h2: DoubleArray): Pair<Double, Double> {
        val minLen = minOf(h1.size, h2.size)
        if (minLen == 0) return Pair(1.0, 0.0)

        var bc = 0.0
        for (i in 0 until minLen) {
            bc += sqrt(maxOf(0.0, h1[i] * h2[i]))
        }
        bc = bc.coerceIn(0.0, 1.0)
        val distance = sqrt((1.0 - bc).coerceAtLeast(0.0))
        return Pair(distance, bc)
    }

    /**
     * Section 5: LAB-Based ICUMSA Interpolation using 6 Reference Samples in CIE-LAB 3D Space
     *
     * Step 1: Sort 6 references ascending by ICUMSA value.
     * Step 2: Form 3D points P_i = (L_i, a_i, b_i) for each reference.
     * Step 3: Form 5 line segments between consecutive points.
     * Step 4: For input sample Q = (L_new, a_new, b_new), project Q onto each segment:
     *         V = P2 - P1
     *         t_raw = dot(Q - P1, V) / dot(V, V)
     *         t = clamp(t_raw, 0, 1)
     *         R = P1 + t * V
     *         d = ||Q - R||
     *         Choose the segment with minimum distance d.
     * Step 5: Linear interpolation: ICUMSA_estimated = ICUMSA_1 + t * (ICUMSA_2 - ICUMSA_1)
     * Step 6: Check range bounds and path distance threshold for Unknown detection.
     */
    fun estimateIcumsa(
        sampleLab: LabMedian,
        references: List<StoredReferenceVector>,
        maxPathDistanceThreshold: Double = 22.0
    ): IcumsaEstimationResult? {
        if (references.size < 2) return null

        // Step 1: Sort 6 active references by ICUMSA value ascending
        val sortedRefs = references
            .map { ReferenceLabPoint(it.sampleId, it.sampleName, it.icumsaValue, it.labMedian) }
            .sortedBy { it.icumsaValue }

        if (sortedRefs.size < 2) return null

        val segmentDetails = mutableListOf<SegmentInterpolationDetail>()
        var bestIndex = -1
        var minDistance = Double.MAX_VALUE

        // Step 2 & 3: Form 5 line segments between consecutive references
        for (i in 0 until sortedRefs.size - 1) {
            val p1 = sortedRefs[i]
            val p2 = sortedRefs[i + 1]

            val vl = p2.lab.medianL - p1.lab.medianL
            val va = p2.lab.medianA - p1.lab.medianA
            val vb = p2.lab.medianB - p1.lab.medianB

            val dotVV = vl * vl + va * va + vb * vb

            // Avoid division by zero if two references are identical in color
            if (dotVV < 1e-6) {
                continue
            }

            val ql = sampleLab.medianL - p1.lab.medianL
            val qa = sampleLab.medianA - p1.lab.medianA
            val qb = sampleLab.medianB - p1.lab.medianB

            val dotQV = ql * vl + qa * va + qb * vb
            val rawT = dotQV / dotVV
            val clampedT = rawT.coerceIn(0.0, 1.0)

            val rL = p1.lab.medianL + clampedT * vl
            val rA = p1.lab.medianA + clampedT * va
            val rB = p1.lab.medianB + clampedT * vb
            val nearestPoint = LabMedian(rL, rA, rB)

            val dist = sampleLab.distanceTo(nearestPoint)

            val detail = SegmentInterpolationDetail(
                segmentIndex = i + 1,
                ref1 = p1,
                ref2 = p2,
                rawT = rawT,
                clampedT = clampedT,
                nearestPoint = nearestPoint,
                distance = dist,
                isSelected = false
            )
            segmentDetails.add(detail)

            if (dist < minDistance) {
                minDistance = dist
                bestIndex = segmentDetails.size - 1
            }
        }

        if (bestIndex == -1 || segmentDetails.isEmpty()) return null

        val chosen = segmentDetails[bestIndex]
        val updatedSegments = segmentDetails.mapIndexed { idx, seg ->
            if (idx == bestIndex) seg.copy(isSelected = true) else seg
        }

        // Step 5: Linear interpolation
        val estimatedIcumsa = chosen.ref1.icumsaValue + chosen.clampedT * (chosen.ref2.icumsaValue - chosen.ref1.icumsaValue)

        // Step 6: Range check & Unknown sample detection
        val isDistanceTooLarge = chosen.distance > maxPathDistanceThreshold
        val isFirstSegment = (bestIndex == 0)
        val isLastSegment = (bestIndex == segmentDetails.size - 1)

        var isOutOfRange = false
        var outOfRangeReason: String? = null

        if (isFirstSegment && chosen.rawT < -0.05) {
            isOutOfRange = true
            outOfRangeReason = "نمونه روشن‌تر از حداقل محدوده مراجع (مرجع ۱) است و نباید بدون اعتبارسنجی برونیابی شود"
        } else if (isLastSegment && chosen.rawT > 1.05) {
            isOutOfRange = true
            outOfRangeReason = "نمونه تیره‌تر از حداکثر محدوده مراجع (مرجع ۶) است و نباید بدون اعتبارسنجی برونیابی شود"
        } else if (isDistanceTooLarge) {
            outOfRangeReason = "فاصله نمونه از مسیر مرجع رنگی آزمایشگاه بیش از حد مجاز است"
        }

        val isUnknown = isDistanceTooLarge

        return IcumsaEstimationResult(
            estimatedIcumsa = estimatedIcumsa,
            selectedRef1 = chosen.ref1,
            selectedRef2 = chosen.ref2,
            sampleLabMedian = sampleLab,
            distanceToPath = chosen.distance,
            relativePositionT = chosen.clampedT,
            isOutOfRange = isOutOfRange,
            outOfRangeReason = outOfRangeReason,
            isUnknown = isUnknown,
            allSegments = updatedSegments
        )
    }

    /**
     * Evaluates input vector against stored 6 reference samples using Euclidean Distance,
     * Cosine Similarity, and Bhattacharyya Histogram Distance, along with LAB-Based ICUMSA Interpolation.
     */
    fun classifySample(
        inputVector: DoubleArray,
        sampleLabMedian: LabMedian = LabMedian(0.0, 0.0, 0.0),
        references: List<StoredReferenceVector>,
        similarityThresholdPct: Double = 65.0,
        startTimeMs: Long = System.currentTimeMillis()
    ): ClassificationResult {
        if (references.isEmpty()) {
            val emptyMatch = ReferenceMatch(
                sampleId = 0,
                sampleName = "Unknown Sample",
                icumsaValue = 0,
                euclideanDistance = 999.0,
                euclideanSimilarityPct = 0.0,
                cosineSimilarity = 0.0,
                cosineSimilarityPct = 0.0,
                bhattacharyyaDistance = 1.0,
                bhattacharyyaSimilarityPct = 0.0,
                finalSimilarityPct = 0.0
            )
            return ClassificationResult(
                nearestSampleId = 0,
                nearestSampleName = "Unknown Sample",
                isUnknown = true,
                confidenceScorePct = 0.0,
                confidenceLevel = ConfidenceLevel.LOW,
                topMatch = emptyMatch,
                allMatches = listOf(emptyMatch),
                processingTimeMs = System.currentTimeMillis() - startTimeMs,
                thresholdUsed = similarityThresholdPct,
                icumsaEstimation = null
            )
        }

        val matches = mutableListOf<ReferenceMatch>()
        val n = inputVector.size.toDouble().coerceAtLeast(1.0)
        val sqrtN = sqrt(n)

        for (ref in references) {
            val eucDist = euclideanDistance(inputVector, ref.vector)
            val cosSim = cosineSimilarity(inputVector, ref.vector)

            // Normalize distance relative to vector dimensionality
            val normalizedEuc = eucDist / sqrtN
            val eucPct = (exp(-normalizedEuc / 0.15) * 100.0).coerceIn(0.0, 100.0)
            val cosPct = (maxOf(0.0, cosSim) * 100.0).coerceIn(0.0, 100.0)

            // Histogram Bhattacharyya Comparison (RGB & LAB Histograms)
            var bhattDistance = 0.0
            var bhattSimPct = cosPct

            if (inputVector.size >= 191 && ref.vector.size >= 191) {
                val inputRgbHist = inputVector.sliceArray(63..126)
                val refRgbHist = ref.vector.sliceArray(63..126)

                val inputLabHist = inputVector.sliceArray(127..190)
                val refLabHist = ref.vector.sliceArray(127..190)

                val (rgbDist, rgbBc) = bhattacharyyaDistance(inputRgbHist, refRgbHist)
                val (labDist, labBc) = bhattacharyyaDistance(inputLabHist, refLabHist)

                val avgBc = (0.5 * rgbBc + 0.5 * labBc).coerceIn(0.0, 1.0)
                bhattDistance = sqrt((1.0 - avgBc).coerceAtLeast(0.0))
                bhattSimPct = (avgBc * 100.0).coerceIn(0.0, 100.0)
            } else if (inputVector.size >= 127 && ref.vector.size >= 127) {
                val inputRgbHist = inputVector.sliceArray(63..126)
                val refRgbHist = ref.vector.sliceArray(63..126)
                val (rgbDist, rgbBc) = bhattacharyyaDistance(inputRgbHist, refRgbHist)
                bhattDistance = rgbDist
                bhattSimPct = (rgbBc * 100.0).coerceIn(0.0, 100.0)
            }

            // Final Combined Similarity Score: Cosine (40%), Euclidean (30%), Bhattacharyya Histogram (30%)
            val finalScore = (0.40 * cosPct + 0.30 * eucPct + 0.30 * bhattSimPct).coerceIn(0.0, 100.0)

            matches.add(
                ReferenceMatch(
                    sampleId = ref.sampleId,
                    sampleName = ref.sampleName,
                    icumsaValue = ref.icumsaValue,
                    euclideanDistance = eucDist,
                    euclideanSimilarityPct = eucPct,
                    cosineSimilarity = cosSim,
                    cosineSimilarityPct = cosPct,
                    bhattacharyyaDistance = bhattDistance,
                    bhattacharyyaSimilarityPct = bhattSimPct,
                    finalSimilarityPct = finalScore
                )
            )
        }

        // Sort descending by final similarity score
        matches.sortByDescending { it.finalSimilarityPct }

        // Perform Section 5 ICUMSA estimation in CIE-LAB space
        val icumsaResult = estimateIcumsa(sampleLabMedian, references)

        val topMatch = matches.first()
        val isSimBelowThreshold = topMatch.finalSimilarityPct < similarityThresholdPct
        val isPathUnknown = icumsaResult?.isUnknown == true
        val isOutOfRange = icumsaResult?.isOutOfRange == true

        val isUnknown = isSimBelowThreshold || isPathUnknown

        val finalName = if (isUnknown) "Unknown Sample" else topMatch.sampleName
        val finalId = if (isUnknown) 0 else topMatch.sampleId

        val confidenceLevel = when {
            topMatch.finalSimilarityPct >= 88.0 && !isPathUnknown && !isOutOfRange -> ConfidenceLevel.VERY_HIGH
            topMatch.finalSimilarityPct >= 75.0 && !isPathUnknown && !isOutOfRange -> ConfidenceLevel.HIGH
            topMatch.finalSimilarityPct >= 60.0 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        return ClassificationResult(
            nearestSampleId = finalId,
            nearestSampleName = finalName,
            isUnknown = isUnknown,
            confidenceScorePct = topMatch.finalSimilarityPct,
            confidenceLevel = confidenceLevel,
            topMatch = topMatch,
            allMatches = matches,
            processingTimeMs = System.currentTimeMillis() - startTimeMs,
            thresholdUsed = similarityThresholdPct,
            icumsaEstimation = icumsaResult
        )
    }
}
