package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reference_samples")
data class ReferenceSampleEntity(
    @PrimaryKey val id: Int, // 1 to 6
    val name: String,
    val icumsaValue: Int,
    val description: String,
    val featureVectorJson: String,
    val imagePath: String? = null,
    val version: Int = 1,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedBy: String = "Admin / System",
    val featureVectorSize: Int = 191,
    val medianL: Double = 0.0,
    val medianA: Double = 0.0,
    val medianB: Double = 0.0
)

@Entity(tableName = "reference_versions")
data class ReferenceVersionEntity(
    @PrimaryKey(autoGenerate = true) val versionId: Long = 0,
    val sampleId: Int,
    val versionNumber: Int,
    val name: String,
    val icumsaValue: Int,
    val description: String,
    val featureVectorJson: String,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val operatorName: String = "Admin",
    val changeDescription: String = "Updated reference image and recalculating feature vectors",
    val medianL: Double = 0.0,
    val medianA: Double = 0.0,
    val medianB: Double = 0.0
)

@Entity(tableName = "analysis_history")
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val dateFormatted: String,
    val timeFormatted: String,
    val imagePath: String? = null,
    val matchedSampleId: Int,
    val matchedSampleName: String,
    val confidenceScorePct: Double,
    val confidenceLevel: String,
    val isUnknown: Boolean,
    val euclideanDistance: Double,
    val manhattanDistance: Double,
    val cosineSimilarity: Double,
    val featureVectorJson: String,
    val qualityScore: Double,
    val processingTimeMs: Long,
    val isFavorite: Boolean = false,
    val operatorName: String = "Laboratory Operator",
    val batchId: String? = null,
    val normalizationMethod: String = "STANDARD_SCALER",
    val algorithmVersion: String = "v2.1.0",
    val matchedImagePath: String? = null,
    val estimatedIcumsa: Double? = null,
    val ref1Id: Int? = null,
    val ref1Icumsa: Int? = null,
    val ref2Id: Int? = null,
    val ref2Icumsa: Int? = null,
    val sampleMedianL: Double = 0.0,
    val sampleMedianA: Double = 0.0,
    val sampleMedianB: Double = 0.0,
    val pathDistance: Double = 0.0,
    val interpolationT: Double = 0.0,
    val isOutOfRange: Boolean = false,
    val outOfRangeReason: String? = null
)

@Entity(tableName = "calibration_profiles")
data class CalibrationProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val overallScore: Double,
    val whiteBalanceScore: Double,
    val exposureScore: Double,
    val brightnessScore: Double,
    val contrastScore: Double,
    val sharpnessScore: Double,
    val focusScore: Double,
    val status: String, // Calibrated, Needs Calibration, Calibration Failed
    val deviceModel: String = "Android Laboratory Device"
)
