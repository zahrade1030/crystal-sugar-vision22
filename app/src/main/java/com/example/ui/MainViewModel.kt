package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv.*
import com.example.data.db.*
import com.example.data.preferences.AppPreferences
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val preferences = AppPreferences(application)
    val repository = AppRepository(db, preferences, context = application)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.ensureSamplesUpdated(application, db)
        }
    }

    // UI States
    val language = repository.languageFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "fa"
    )

    val theme = repository.themeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK"
    )

    val normalization = repository.normalizationFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "STANDARD_SCALER"
    )

    val similarityThreshold = repository.similarityThresholdFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 65.0
    )

    val isDeveloperMode = repository.developerModeFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val securityPin = repository.securityPinFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        "Asrtkl__4GHsaGT01011"
    )

    val isBiometricEnabled = repository.biometricEnabledFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val isScreenshotProtected = repository.screenshotProtectedFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val subActivated = repository.subActivated.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val subStartTime = repository.subStartTime.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0L
    )

    val subExpirationTime = repository.subExpirationTime.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0L
    )

    val subDurationDays = repository.subDurationDays.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 30
    )

    val references = repository.allReferences.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val history = repository.allHistory.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val favorites = repository.favoriteHistory.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val calibrationProfiles = repository.calibrationProfiles.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Main image analysis
    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _classificationResult =
        MutableStateFlow<ClassificationResult?>(null)

    val classificationResult: StateFlow<ClassificationResult?> =
        _classificationResult.asStateFlow()

    private val _pipelineOutput = MutableStateFlow<PipelineOutput?>(null)
    val pipelineOutput: StateFlow<PipelineOutput?> =
        _pipelineOutput.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisErrorMessage = MutableStateFlow<String?>(null)
    val analysisErrorMessage: StateFlow<String?> =
        _analysisErrorMessage.asStateFlow()

    private val _batchResults =
        MutableStateFlow<List<Pair<ClassificationResult, PipelineOutput>>>(
            emptyList()
        )

    val batchResults: StateFlow<List<Pair<ClassificationResult, PipelineOutput>>> =
        _batchResults.asStateFlow()

    // ------------------------------------------------
    // TWO SAMPLE COMPARISON
    // ------------------------------------------------

    private val _twoSampleState =
        MutableStateFlow(TwoSampleComparisonState())

    val twoSampleState: StateFlow<TwoSampleComparisonState> =
        _twoSampleState.asStateFlow()

    fun setTwoSampleA(bitmap: Bitmap) {
        _twoSampleState.update {
            it.copy(
                bitmapA = bitmap,
                diffResult = null,
                profileA = null,
                profileB = null,
                errorMessage = null
            )
        }
    }

    fun setTwoSampleB(bitmap: Bitmap) {
        _twoSampleState.update {
            it.copy(
                bitmapB = bitmap,
                diffResult = null,
                profileA = null,
                profileB = null,
                errorMessage = null
            )
        }
    }

    fun compareTwoSamples() {
        val current = _twoSampleState.value
        val bitmapA = current.bitmapA
        val bitmapB = current.bitmapB

        if (bitmapA == null || bitmapB == null) {
            _twoSampleState.update {
                it.copy(errorMessage = "لطفاً هر دو تصویر را انتخاب کنید.")
            }
            return
        }

        viewModelScope.launch {
            _twoSampleState.update {
                it.copy(
                    isComparing = true,
                    errorMessage = null,
                    diffResult = null
                )
            }

            try {
                val result = withContext(Dispatchers.Default) {
                    val profileA = calculateColorProfile(bitmapA)
                    val profileB = calculateColorProfile(bitmapB)

                    val difference = calculateCiede2000(
                        profileA,
                        profileB
                    )

                    Triple(profileA, profileB, difference)
                }

                _twoSampleState.update {
                    it.copy(
                        isComparing = false,
                        profileA = result.first,
                        profileB = result.second,
                        diffResult = result.third,
                        errorMessage = null
                    )
                }

            } catch (e: Exception) {
                _twoSampleState.update {
                    it.copy(
                        isComparing = false,
                        errorMessage = "خطا در مقایسه تصاویر: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // ------------------------------------------------
    // COLOR PROFILE
    // ------------------------------------------------

    private fun calculateColorProfile(bitmap: Bitmap): TwoSampleColorProfile {

        val width = bitmap.width
        val height = bitmap.height

        require(width > 0 && height > 0) {
            "تصویر معتبر نیست."
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val left = width / 10
        val right = width * 9 / 10
        val top = height / 10
        val bottom = height * 9 / 10

        val area = (right - left) * (bottom - top)
        val step = max(1, sqrt(area / 40000.0).toInt())

        val lightness = mutableListOf<Double>()
        val aValues = mutableListOf<Double>()
        val bValues = mutableListOf<Double>()

        for (y in top until bottom step step) {
            for (x in left until right step step) {

                val color = pixels[y * width + x]

                val red = (color shr 16) and 0xFF
                val green = (color shr 8) and 0xFF
                val blue = color and 0xFF

                val luminance =
                    0.299 * red + 0.587 * green + 0.114 * blue

                if (luminance < 18.0) continue

                val lab = rgbToLab(red, green, blue)

                lightness.add(lab.first)
                aValues.add(lab.second)
                bValues.add(lab.third)
            }
        }

        require(lightness.size >= 10) {
            "تعداد پیکسل‌های معتبر تصویر کافی نیست."
        }

        val meanL = trimmedMean(lightness)
        val meanA = trimmedMean(aValues)
        val meanB = trimmedMean(bValues)

        val variance = lightness.map {
            (it - meanL).pow(2)
        }.average()

        return TwoSampleColorProfile(
            trimmedMeanL = meanL,
            trimmedMeanA = meanA,
            trimmedMeanB = meanB,
            stdL = sqrt(variance)
        )
    }

    private fun trimmedMean(values: List<Double>): Double {
        val sorted = values.sorted()
        val trimCount = (sorted.size * 0.10).toInt()

        val selected = sorted.subList(
            trimCount,
            sorted.size - trimCount
        )

        return selected.average()
    }

    // RGB to CIELAB, D65
    private fun rgbToLab(
        red: Int,
        green: Int,
        blue: Int
    ): Triple<Double, Double, Double> {

        fun linearize(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.04045) {
                v / 12.92
            } else {
                ((v + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = linearize(red)
        val g = linearize(green)
        val b = linearize(blue)

        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

        fun labFunction(t: Double): Double {
            return if (t > 0.008856451679) {
                t.pow(1.0 / 3.0)
            } else {
                7.787037037 * t + 16.0 / 116.0
            }
        }

        val fx = labFunction(x)
        val fy = labFunction(y)
        val fz = labFunction(z)

        val l = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val labB = 200.0 * (fy - fz)

        return Triple(l, a, labB)
    }

    // ------------------------------------------------
    // CIEDE2000
    // ------------------------------------------------

    private fun calculateCiede2000(
        first: TwoSampleColorProfile,
        second: TwoSampleColorProfile
    ): TwoSampleColorDifference {

        val l1 = first.trimmedMeanL
        val a1 = first.trimmedMeanA
        val b1 = first.trimmedMeanB

        val l2 = second.trimmedMeanL
        val a2 = second.trimmedMeanA
        val b2 = second.trimmedMeanB

        val c1 = sqrt(a1 * a1 + b1 * b1)
        val c2 = sqrt(a2 * a2 + b2 * b2)

        val cBar = (c1 + c2) / 2.0
        val cBar7 = cBar.pow(7.0)
        val twentyFive7 = 25.0.pow(7.0)

        val g = 0.5 * (
            1.0 - sqrt(cBar7 / (cBar7 + twentyFive7))
        )

        val a1p = (1.0 + g) * a1
        val a2p = (1.0 + g) * a2

        val c1p = sqrt(a1p * a1p + b1 * b1)
        val c2p = sqrt(a2p * a2p + b2 * b2)

        fun hueAngle(a: Double, b: Double): Double {
            if (a == 0.0 && b == 0.0) return 0.0
            return normalizeAngle(Math.toDegrees(atan2(b, a)))
        }

        val h1p = hueAngle(a1p, b1)
        val h2p = hueAngle(a2p, b2)

        val deltaL = l2 - l1
        val deltaC = c2p - c1p

        var deltaHue = h2p - h1p

        if (c1p * c2p == 0.0) {
            deltaHue = 0.0
        } else if (deltaHue > 180.0) {
            deltaHue -= 360.0
        } else if (deltaHue < -180.0) {
            deltaHue += 360.0
        }

        val deltaH = 2.0 * sqrt(c1p * c2p) *
            sin(Math.toRadians(deltaHue / 2.0))

        val lBar = (l1 + l2) / 2.0
        val cBarP = (c1p + c2p) / 2.0

        val hBar = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180.0 -> (h1p + h2p) / 2.0
            h1p + h2p < 360.0 -> (h1p + h2p + 360.0) / 2.0
            else -> (h1p + h2p - 360.0) / 2.0
        }

        val t =
            1.0 -
            0.17 * cos(Math.toRadians(hBar - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * hBar)) +
            0.32 * cos(Math.toRadians(3.0 * hBar + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * hBar - 63.0))

        val deltaTheta = 30.0 * exp(
            -(((hBar - 275.0) / 25.0).pow(2.0))
        )

        val rc = 2.0 * sqrt(
            cBarP.pow(7.0) /
                (cBarP.pow(7.0) + twentyFive7)
        )

        val sl = 1.0 +
            (0.015 * (lBar - 50.0).pow(2.0)) /
            sqrt(20.0 + (lBar - 50.0).pow(2.0))

        val sc = 1.0 + 0.045 * cBarP
        val sh = 1.0 + 0.015 * cBarP * t

        val rt = -sin(Math.toRadians(2.0 * deltaTheta)) * rc

        val termL = deltaL / sl
        val termC = deltaC / sc
        val termH = deltaH / sh

        val deltaE = sqrt(
            max(
                0.0,
                termL * termL +
                    termC * termC +
                    termH * termH +
                    rt * termC * termH
            )
        )

        val originalC1 = sqrt(a1 * a1 + b1 * b1)
        val originalC2 = sqrt(a2 * a2 + b2 * b2)

        val originalHue1 = hueAngle(a1, b1)
        val originalHue2 = hueAngle(a2, b2)

        var hueDifference = originalHue2 - originalHue1

        if (hueDifference > 180.0) hueDifference -= 360.0
        if (hueDifference < -180.0) hueDifference += 360.0

        val direction = when {
            deltaL > 0.5 -> "نمونه B روشن‌تر است"
            deltaL < -0.5 -> "نمونه B تیره‌تر است"
            deltaC > 0.5 -> "نمونه B اشباع رنگی بیشتری دارد"
            deltaC < -0.5 -> "نمونه B اشباع رنگی کمتری دارد"
            else -> "روشنایی دو نمونه تقریباً یکسان است"
        }

        return TwoSampleColorDifference(
            deltaE00 = deltaE,
            deltaL = deltaL,
            deltaA = a2 - a1,
            deltaB = b2 - b1,
            deltaC = originalC2 - originalC1,
            deltaHueDeg = hueDifference,
            directionDesc = direction
        )
    }

    private fun normalizeAngle(angle: Double): Double {
        return ((angle % 360.0) + 360.0) % 360.0
    }

    // ------------------------------------------------
    // EXISTING FUNCTIONS
    // ------------------------------------------------

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _currentBitmap.value = bitmap
        _analysisErrorMessage.value = null
    }

    fun analyzeCurrentBitmap() {
        val bmp = _currentBitmap.value ?: return

        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisErrorMessage.value = null

            try {
                val (classification, output) =
                    repository.analyzeImage(bmp)

                if (!output.qualityResult.isPassed) {
                    _analysisErrorMessage.value =
                        output.qualityResult.rejectionReason
                }

                _classificationResult.value = classification
                _pipelineOutput.value = output

                _batchResults.value =
                    _batchResults.value + Pair(classification, output)

            } catch (e: Exception) {
                _analysisErrorMessage.value =
                    "خطا در پردازش و تخمین: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearBatch() {
        _batchResults.value = emptyList()
    }

    fun updateReferenceSample(
        id: Int,
        name: String,
        icumsa: Double,
        description: String,
        newBitmap: Bitmap?
    ) {
        viewModelScope.launch {
            repository.updateReferenceSample(
                id,
                name,
                icumsa.toInt(),
                description,
                newBitmap
            )
        }
    }

    fun toggleFavorite(id: Long, isFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavoriteHistory(id, isFav)
        }
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryRecord(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun saveCalibration(profile: CalibrationProfileEntity) {
        viewModelScope.launch {
            repository.saveCalibrationProfile(profile)
        }
    }

    fun setLanguage(lang: String) =
        viewModelScope.launch { repository.setLanguage(lang) }

    fun setTheme(theme: String) =
        viewModelScope.launch { repository.setTheme(theme) }

    fun setNormalization(method: String) =
        viewModelScope.launch { repository.setNormalization(method) }

    fun setSimilarityThreshold(thresh: Double) =
        viewModelScope.launch {
            repository.setSimilarityThreshold(thresh)
        }

    fun setDeveloperMode(enabled: Boolean) =
        viewModelScope.launch {
            repository.setDeveloperMode(enabled)
        }

    fun setSecurityPin(pin: String) =
        viewModelScope.launch {
            repository.setSecurityPin(pin)
        }

    fun setBiometricEnabled(enabled: Boolean) =
        viewModelScope.launch {
            repository.setBiometricEnabled(enabled)
        }

    fun setScreenshotProtected(protected: Boolean) =
        viewModelScope.launch {
            repository.setScreenshotProtected(protected)
        }

    fun verifySubscriptionPasswords(
        pass1: String,
        pass2: String
    ): Boolean {
        return pass1.trim() == "147852369_CrystalBiN" &&
            pass2.trim() == "14785_Crystal_BiN_2369"
    }

    fun activateSubscription(days: Int) = viewModelScope.launch {
        repository.activateSubscription(days)
    }

    fun extendSubscription(days: Int) = viewModelScope.launch {
        repository.extendSubscription(
            days,
            subExpirationTime.value,
            subDurationDays.value
        )
    }

    fun isSubscriptionValid(): Boolean {
        return subActivated.value &&
            System.currentTimeMillis() < subExpirationTime.value
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == securityPin.value
    }
}

// ------------------------------------------------
// COMPARISON DATA MODELS
// ------------------------------------------------

data class TwoSampleComparisonState(
    val bitmapA: Bitmap? = null,
    val bitmapB: Bitmap? = null,
    val isComparing: Boolean = false,
    val errorMessage: String? = null,
    val diffResult: TwoSampleColorDifference? = null,
    val profileA: TwoSampleColorProfile? = null,
    val profileB: TwoSampleColorProfile? = null
)

data class TwoSampleColorProfile(
    val trimmedMeanL: Double,
    val trimmedMeanA: Double,
    val trimmedMeanB: Double,
    val stdL: Double
)

data class TwoSampleColorDifference(
    val deltaE00: Double,
    val deltaL: Double,
    val deltaA: Double,
    val deltaB: Double,
    val deltaC: Double,
    val deltaHueDeg: Double,
    val directionDesc: String
)
