package com.example.cv

import android.graphics.Bitmap

/**
 * Image Quality Validation Result
 */
data class ImageQualityResult(
    val isPassed: Boolean,
    val blurScore: Double,          // Laplacian variance (higher = sharper)
    val brightnessScore: Double,    // Mean luminance (0 - 255)
    val contrastScore: Double,      // Std dev of luminance
    val centerAlignmentScore: Double,// 0.0 - 1.0 (closeness to center)
    val overallScore: Double,       // 0 - 100%
    val rejectionReason: String? = null
)

/**
 * 3D Color coordinates for CIE-LAB Median
 * P = (Median_L, Median_a, Median_b)
 */
data class LabMedian(
    val medianL: Double,
    val medianA: Double,
    val medianB: Double
) {
    fun distanceTo(other: LabMedian): Double {
        val dl = medianL - other.medianL
        val da = medianA - other.medianA
        val db = medianB - other.medianB
        return kotlin.math.sqrt(dl * dl + da * da + db * db)
    }
}

/**
 * Reference Sample point in CIE-LAB space with assigned ICUMSA value
 */
data class ReferenceLabPoint(
    val sampleId: Int,
    val sampleName: String,
    val icumsaValue: Int,
    val lab: LabMedian
)

/**
 * Geometric calculation details for each of the 5 reference path segments
 */
data class SegmentInterpolationDetail(
    val segmentIndex: Int,
    val ref1: ReferenceLabPoint,
    val ref2: ReferenceLabPoint,
    val rawT: Double,
    val clampedT: Double,
    val nearestPoint: LabMedian,
    val distance: Double,
    val isSelected: Boolean
)

/**
 * Complete ICUMSA estimation result calculated via CIE-LAB Median path interpolation
 */
data class IcumsaEstimationResult(
    val estimatedIcumsa: Double,
    val selectedRef1: ReferenceLabPoint,
    val selectedRef2: ReferenceLabPoint,
    val sampleLabMedian: LabMedian,
    val distanceToPath: Double,
    val relativePositionT: Double,
    val isOutOfRange: Boolean,
    val outOfRangeReason: String? = null,
    val isUnknown: Boolean,
    val allSegments: List<SegmentInterpolationDetail>
)

/**
 * Feature vector representation for Single ROI
 */
data class FeatureVector(
    val regionIndex: Int,
    val regionName: String,
    val values: DoubleArray,
    val labMedian: LabMedian = LabMedian(0.0, 0.0, 0.0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FeatureVector
        if (regionIndex != other.regionIndex) return false
        if (regionName != other.regionName) return false
        if (!values.contentEquals(other.values)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = regionIndex
        result = 31 * result + regionName.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

/**
 * Extracted color and statistical feature set for Single ROI (Whole Image)
 */
data class CompleteFeatureSet(
    val regions: List<FeatureVector>,
    val concatenatedVector: DoubleArray,
    val extractionTimeMs: Long,
    val colorHistogram: FloatArray,
    val rgbMeans: Triple<Double, Double, Double>,
    val hsvMeans: Triple<Double, Double, Double>,
    val labMeans: Triple<Double, Double, Double>,
    val labMedian: LabMedian = LabMedian(0.0, 0.0, 0.0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompleteFeatureSet
        return concatenatedVector.contentEquals(other.concatenatedVector)
    }

    override fun hashCode(): Int {
        return concatenatedVector.contentHashCode()
    }
}

/**
 * Individual metric match result against a reference sample
 * Using Euclidean Distance, Cosine Similarity, and Bhattacharyya Histogram Distance
 */
data class ReferenceMatch(
    val sampleId: Int,
    val sampleName: String,
    val icumsaValue: Int,
    val euclideanDistance: Double,
    val euclideanSimilarityPct: Double,
    val cosineSimilarity: Double,
    val cosineSimilarityPct: Double,
    val bhattacharyyaDistance: Double = 0.0,
    val bhattacharyyaSimilarityPct: Double = 0.0,
    val finalSimilarityPct: Double
)

/**
 * Confidence Level Enum
 */
enum class ConfidenceLevel {
    VERY_HIGH,
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Final Classification and ICUMSA Estimation Result
 */
data class ClassificationResult(
    val nearestSampleId: Int,
    val nearestSampleName: String,
    val isUnknown: Boolean,
    val confidenceScorePct: Double,
    val confidenceLevel: ConfidenceLevel,
    val topMatch: ReferenceMatch,
    val allMatches: List<ReferenceMatch>,
    val processingTimeMs: Long,
    val thresholdUsed: Double,
    val icumsaEstimation: IcumsaEstimationResult? = null
)

/**
 * Debug Stage Bitmap representation for Developer Mode
 */
data class ProcessingStage(
    val stageName: String,
    val description: String,
    val bitmap: Bitmap
)
