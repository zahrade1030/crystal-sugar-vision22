package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv.*
import com.example.data.db.*
import com.example.data.preferences.AppPreferences
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val preferences = AppPreferences(application)
    val repository = AppRepository(db, preferences, context = application)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            AppDatabase.ensureSamplesUpdated(application, db)
        }
    }

    // UI States
    val language = repository.languageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fa")
    val theme = repository.themeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK")
    val normalization = repository.normalizationFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "STANDARD_SCALER")
    val similarityThreshold = repository.similarityThresholdFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 65.0)
    val isDeveloperMode = repository.developerModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val securityPin = repository.securityPinFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Asrtkl__4GHsaGT01011")
    val isBiometricEnabled = repository.biometricEnabledFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isScreenshotProtected = repository.screenshotProtectedFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val subActivated = repository.subActivated.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val subStartTime = repository.subStartTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val subExpirationTime = repository.subExpirationTime.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val subDurationDays = repository.subDurationDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val references = repository.allReferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repository.allHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = repository.favoriteHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val calibrationProfiles = repository.calibrationProfiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Capture & Analysis State
    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _classificationResult = MutableStateFlow<ClassificationResult?>(null)
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult.asStateFlow()

    private val _pipelineOutput = MutableStateFlow<PipelineOutput?>(null)
    val pipelineOutput: StateFlow<PipelineOutput?> = _pipelineOutput.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisErrorMessage = MutableStateFlow<String?>(null)
    val analysisErrorMessage: StateFlow<String?> = _analysisErrorMessage.asStateFlow()

    // Batch Analysis list
    private val _batchResults = MutableStateFlow<List<Pair<ClassificationResult, PipelineOutput>>>(emptyList())
    val batchResults: StateFlow<List<Pair<ClassificationResult, PipelineOutput>>> = _batchResults.asStateFlow()

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
                val (classification, output) = repository.analyzeImage(bmp)
                if (!output.qualityResult.isPassed) {
                    _analysisErrorMessage.value = output.qualityResult.rejectionReason
                }
                _classificationResult.value = classification
                _pipelineOutput.value = output

                _batchResults.value = _batchResults.value + Pair(classification, output)
            } catch (e: Exception) {
                _analysisErrorMessage.value = "خطا در پردازش و تخمین: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearBatch() {
        _batchResults.value = emptyList()
    }

    fun updateReferenceSample(id: Int, name: String, icumsa: Double, description: String, newBitmap: Bitmap?) {
        viewModelScope.launch {
            repository.updateReferenceSample(id, name, icumsa, description, newBitmap)
        }
    }

    fun toggleFavorite(id: Long, isFav: Boolean) {
        viewModelScope.launch { repository.toggleFavoriteHistory(id, isFav) }
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch { repository.deleteHistoryRecord(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearAllHistory() }
    }

    fun saveCalibration(profile: CalibrationProfileEntity) {
        viewModelScope.launch { repository.saveCalibrationProfile(profile) }
    }

    fun setLanguage(lang: String) = viewModelScope.launch { repository.setLanguage(lang) }
    fun setTheme(theme: String) = viewModelScope.launch { repository.setTheme(theme) }
    fun setNormalization(method: String) = viewModelScope.launch { repository.setNormalization(method) }
    fun setSimilarityThreshold(thresh: Double) = viewModelScope.launch { repository.setSimilarityThreshold(thresh) }
    fun setDeveloperMode(enabled: Boolean) = viewModelScope.launch { repository.setDeveloperMode(enabled) }
    fun setSecurityPin(pin: String) = viewModelScope.launch { repository.setSecurityPin(pin) }
    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { repository.setBiometricEnabled(enabled) }
    fun setScreenshotProtected(protected: Boolean) = viewModelScope.launch { repository.setScreenshotProtected(protected) }

    fun verifySubscriptionPasswords(pass1: String, pass2: String): Boolean {
        return pass1.trim() == "147852369_CrystalBiN" && pass2.trim() == "14785_Crystal_BiN_2369"
    }

    fun activateSubscription(days: Int) = viewModelScope.launch {
        repository.activateSubscription(days)
    }

    fun extendSubscription(days: Int) = viewModelScope.launch {
        repository.extendSubscription(days, subExpirationTime.value, subDurationDays.value)
    }

    fun isSubscriptionValid(): Boolean {
        return subActivated.value && System.currentTimeMillis() < subExpirationTime.value
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == securityPin.value
    }
}
