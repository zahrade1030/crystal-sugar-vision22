package com.example.data.repository

import android.graphics.Bitmap
import com.example.cv.*
import com.example.data.db.*
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppRepository(
    private val db: AppDatabase,
    private val preferences: AppPreferences,
    private val context: android.content.Context? = null,
    private val pipeline: OpenCvPipeline = OpenCvPipeline()
) {

    val allReferences: Flow<List<ReferenceSampleEntity>> = db.referenceSampleDao().getAllSamples()
    val allHistory: Flow<List<AnalysisHistoryEntity>> = db.analysisHistoryDao().getAllHistory()
    val favoriteHistory: Flow<List<AnalysisHistoryEntity>> = db.analysisHistoryDao().getFavorites()
    val calibrationProfiles: Flow<List<CalibrationProfileEntity>> = db.calibrationProfileDao().getAllProfiles()

    val languageFlow = preferences.languageFlow
    val themeFlow = preferences.themeFlow
    val normalizationFlow = preferences.normalizationFlow
    val similarityThresholdFlow = preferences.similarityThresholdFlow
    val developerModeFlow = preferences.developerModeFlow
    val securityPinFlow = preferences.securityPinFlow
    val biometricEnabledFlow = preferences.biometricEnabledFlow
    val screenshotProtectedFlow = preferences.screenshotProtectedFlow

    /**
     * Executes classification analysis on a captured bitmap
     */
    suspend fun analyzeImage(
        bitmap: Bitmap,
        batchId: String? = null
    ): Pair<ClassificationResult, PipelineOutput> = withContext(Dispatchers.Default) {
        val references = db.referenceSampleDao().getAllSamplesDirect()
        val normMethodStr = preferences.normalizationFlow.first()
        val threshold = preferences.similarityThresholdFlow.first()

        val normMethod = if (normMethodStr == "MIN_MAX_SCALER") {
            NormalizationMethod.MIN_MAX_SCALER
        } else {
            NormalizationMethod.STANDARD_SCALER
        }

        val featureScaler = FeatureScaler()
        val storedRefs = references.map { ref ->
            val json = ref.featureVectorJson
            val rawVec = parseVectorJson(json)
            val normVec = featureScaler.scale(rawVec, normMethod)
            StoredReferenceVector(
                sampleId = ref.id,
                sampleName = ref.name,
                icumsaValue = ref.icumsaValue,
                vector = normVec,
                labMedian = LabMedian(ref.medianL, ref.medianA, ref.medianB)
            )
        }

        val (classification, output) = pipeline.processImage(
            originalBitmap = bitmap,
            referenceSamples = storedRefs,
            normalizationMethod = normMethod,
            similarityThresholdPct = threshold
        )

        // Automatically record analysis in local Room database
        val now = Date()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US).format(now)

        val matchedRef = references.find { it.id == classification.nearestSampleId }

        var capturedSavedPath: String? = null
        val ctx = context ?: preferences.context
        try {
            val dir = java.io.File(ctx.filesDir, "analysis_history")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "analysis_${now.time}.jpg")
            java.io.FileOutputStream(file).use { out ->
                output.processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            capturedSavedPath = file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val icumsaEst = classification.icumsaEstimation
        val historyEntity = AnalysisHistoryEntity(
            timestamp = now.time,
            dateFormatted = dateFmt,
            timeFormatted = timeFmt,
            imagePath = capturedSavedPath,
            matchedSampleId = classification.nearestSampleId,
            matchedSampleName = classification.nearestSampleName,
            confidenceScorePct = classification.confidenceScorePct,
            confidenceLevel = classification.confidenceLevel.name,
            isUnknown = classification.isUnknown,
            euclideanDistance = classification.topMatch.euclideanDistance,
            manhattanDistance = 0.0,
            cosineSimilarity = classification.topMatch.cosineSimilarity,
            featureVectorJson = "[" + output.normalizedVector.take(20).joinToString(",") { String.format("%.3f", it) } + "]",
            qualityScore = output.qualityResult.overallScore,
            processingTimeMs = classification.processingTimeMs,
            batchId = batchId,
            normalizationMethod = normMethod.name,
            algorithmVersion = "v2.1.0",
            matchedImagePath = matchedRef?.imagePath,
            estimatedIcumsa = icumsaEst?.estimatedIcumsa,
            ref1Id = icumsaEst?.selectedRef1?.sampleId,
            ref1Icumsa = icumsaEst?.selectedRef1?.icumsaValue,
            ref2Id = icumsaEst?.selectedRef2?.sampleId,
            ref2Icumsa = icumsaEst?.selectedRef2?.icumsaValue,
            sampleMedianL = output.completeFeatureSet.labMedian.medianL,
            sampleMedianA = output.completeFeatureSet.labMedian.medianA,
            sampleMedianB = output.completeFeatureSet.labMedian.medianB,
            pathDistance = icumsaEst?.distanceToPath ?: 0.0,
            interpolationT = icumsaEst?.relativePositionT ?: 0.0,
            isOutOfRange = icumsaEst?.isOutOfRange ?: false,
            outOfRangeReason = icumsaEst?.outOfRangeReason
        )

        db.analysisHistoryDao().insert(historyEntity)

        Pair(classification, output)
    }

    /**
     * Re-calculates and updates reference sample features upon image update
     */
    suspend fun updateReferenceSample(
        id: Int,
        name: String,
        icumsa: Int,
        description: String,
        newBitmap: Bitmap?,
        operatorName: String = "Administrator"
    ) = withContext(Dispatchers.IO) {
        val existing = db.referenceSampleDao().getSampleById(id)
        val newVersionNum = (existing?.version ?: 1) + 1

        var savedPath = existing?.imagePath

        var updatedMedianL = existing?.medianL ?: 0.0
        var updatedMedianA = existing?.medianA ?: 0.0
        var updatedMedianB = existing?.medianB ?: 0.0

        val (newVectorJson, vectorSize) = if (newBitmap != null) {
            if (context != null) {
                try {
                    val dir = java.io.File(context.filesDir, "reference_samples")
                    if (!dir.exists()) dir.mkdirs()
                    val file = java.io.File(dir, "sample_${id}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        newBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    savedPath = file.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val (_, output) = pipeline.processImage(newBitmap, emptyList())
            val complete = output.completeFeatureSet
            val labMed = complete.labMedian
            updatedMedianL = labMed.medianL
            updatedMedianA = labMed.medianA
            updatedMedianB = labMed.medianB
            Pair("[" + complete.concatenatedVector.joinToString(",") { String.format("%.4f", it) } + "]", complete.concatenatedVector.size)
        } else {
            Pair(existing?.featureVectorJson ?: "[]", existing?.featureVectorSize ?: 191)
        }

        val updatedEntity = ReferenceSampleEntity(
            id = id,
            name = name,
            icumsaValue = icumsa,
            description = description,
            featureVectorJson = newVectorJson,
            imagePath = savedPath,
            version = newVersionNum,
            lastModifiedTimestamp = System.currentTimeMillis(),
            lastModifiedBy = operatorName,
            featureVectorSize = vectorSize,
            medianL = updatedMedianL,
            medianA = updatedMedianA,
            medianB = updatedMedianB
        )

        db.referenceSampleDao().insertOrUpdate(updatedEntity)

        // Log version history
        db.referenceVersionDao().insertVersion(
            ReferenceVersionEntity(
                sampleId = id,
                versionNumber = newVersionNum,
                name = name,
                icumsaValue = icumsa,
                description = description,
                featureVectorJson = newVectorJson,
                imagePath = savedPath,
                operatorName = operatorName,
                changeDescription = "Updated reference sample image & re-extracted features",
                medianL = updatedMedianL,
                medianA = updatedMedianA,
                medianB = updatedMedianB
            )
        )
    }

    suspend fun getReferenceVersions(sampleId: Int) = db.referenceVersionDao().getVersionsForSample(sampleId)

    suspend fun toggleFavoriteHistory(id: Long, isFavorite: Boolean) {
        db.analysisHistoryDao().setFavorite(id, isFavorite)
    }

    suspend fun deleteHistoryRecord(id: Long) {
        db.analysisHistoryDao().deleteById(id)
    }

    suspend fun clearAllHistory() {
        db.analysisHistoryDao().deleteAll()
    }

    suspend fun saveCalibrationProfile(profile: CalibrationProfileEntity) {
        db.calibrationProfileDao().insert(profile)
    }

    val subActivated: Flow<Boolean> = preferences.subActivatedFlow
    val subStartTime: Flow<Long> = preferences.subStartTimeFlow
    val subExpirationTime: Flow<Long> = preferences.subExpirationTimeFlow
    val subDurationDays: Flow<Int> = preferences.subDurationDaysFlow

    suspend fun activateSubscription(durationDays: Int) {
        val startTime = System.currentTimeMillis()
        val expirationTime = startTime + (durationDays.toLong() * 24 * 3600 * 1000)
        preferences.setSubscriptionData(
            activated = true,
            startTime = startTime,
            expirationTime = expirationTime,
            durationDays = durationDays
        )
    }

    suspend fun extendSubscription(additionalDays: Int, currentExpTime: Long, currentDuration: Int) {
        val startTime = System.currentTimeMillis()
        val baseTime = if (currentExpTime > startTime) currentExpTime else startTime
        val expirationTime = baseTime + (additionalDays.toLong() * 24 * 3600 * 1000)
        preferences.setSubscriptionData(
            activated = true,
            startTime = startTime,
            expirationTime = expirationTime,
            durationDays = currentDuration + additionalDays
        )
    }

    // Preferences setters
    suspend fun setLanguage(lang: String) = preferences.setLanguage(lang)
    suspend fun setTheme(theme: String) = preferences.setTheme(theme)
    suspend fun setNormalization(method: String) = preferences.setNormalization(method)
    suspend fun setSimilarityThreshold(threshold: Double) = preferences.setSimilarityThreshold(threshold)
    suspend fun setDeveloperMode(enabled: Boolean) = preferences.setDeveloperMode(enabled)
    suspend fun setSecurityPin(pin: String) = preferences.setSecurityPin(pin)
    suspend fun setBiometricEnabled(enabled: Boolean) = preferences.setBiometricEnabled(enabled)
    suspend fun setScreenshotProtected(protected: Boolean) = preferences.setScreenshotProtected(protected)

    private fun parseVectorJson(json: String): DoubleArray {
        return try {
            val clean = json.replace("[", "").replace("]", "").trim()
            if (clean.isEmpty()) DoubleArray(191) { 0.0 }
            else clean.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }.toDoubleArray()
        } catch (e: Exception) {
            DoubleArray(191) { 0.0 }
        }
    }
}
