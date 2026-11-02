package com.example.cv

/**
 * Central registry for all provisional, baseline, and heuristic values used in
 * color classification, quality validation, and feature scaling.
 *
 * ATTENTION: All values below are labeled PROVISIONAL / UNCALIBRATED.
 * They represent heuristic engineering defaults and must be calibrated using
 * certified physical ICUMSA reference sets and laboratory spectrocolorimeters
 * under controlled illumination conditions.
 */
object ProvisionalConstants {

    // --- Vector Architecture ---
    /**
     * PROVISIONAL / UNCALIBRATED: Dimension of standard feature vector.
     * Derived from: 9 color channels * 7 statistical measures (63) +
     * 64 RGB histogram bins + 64 LAB histogram bins = 191 features.
     */
    const val EXPECTED_FEATURE_VECTOR_SIZE = 191

    // --- CIEDE2000 & Color Decision ---
    /**
     * PROVISIONAL / UNCALIBRATED: Delta E00 decay scale factor for relative similarity score.
     * Formula: 100 * exp(-deltaE00 / TAU). Default = 5.0.
     * Reason: At ΔE00=5.0 (perceptible difference), similarity index is ~36.8%; at ΔE00=1.0, ~81.9%.
     */
    const val TAU_SIMILARITY_DECAY = 5.0

    /**
     * PROVISIONAL / UNCALIBRATED: Delta E00 threshold for flagging sample as "Unknown / Out-of-Bounds".
     * Reason: Standard JND (Just Noticeable Difference) in industrial grading is 1.0 - 2.0;
     * 5.0 allows forgiving bounds for uncalibrated smartphone cameras without reference cards.
     */
    const val UNKNOWN_DELTA_E_THRESHOLD = 5.0

    /**
     * PROVISIONAL / UNCALIBRATED: Default match threshold percentage (0.0 to 100.0) for legacy secondary vector.
     */
    const val DEFAULT_SIMILARITY_THRESHOLD_PCT = 65.0

    /**
     * PROVISIONAL / UNCALIBRATED: Weight of Pearson mean-centered Cosine Similarity.
     */
    const val WEIGHT_COSINE = 0.40

    /**
     * PROVISIONAL / UNCALIBRATED: Weight of Euclidean distance in normalized feature space.
     */
    const val WEIGHT_EUCLIDEAN = 0.30

    /**
     * PROVISIONAL / UNCALIBRATED: Weight of Bhattacharyya distance between color histograms.
     */
    const val WEIGHT_BHATTACHARYYA = 0.30

    // --- Confidence Level Thresholds ---
    const val CONFIDENCE_VERY_HIGH_THRESHOLD = 88.0
    const val CONFIDENCE_HIGH_THRESHOLD = 75.0
    const val CONFIDENCE_MEDIUM_THRESHOLD = 60.0

    // --- Sample Masking (Segmentation) Heuristics ---
    /**
     * PROVISIONAL / UNCALIBRATED: Delta E76 distance threshold to segment sample foreground
     * from the outer ring background model.
     */
    const val MASK_DELTA_E_BACKGROUND = 4.0

    /**
     * PROVISIONAL / UNCALIBRATED: Minimum fraction of total image area that sample mask must occupy.
     * If sample mask is below 10% (0.10), analysis is REJECTED with "No sample detected".
     */
    const val MASK_MIN_AREA_FRACTION = 0.10

    // --- Sugar-specific LAB Histogram Ranges (Provisional) ---
    const val LAB_HIST_L_MIN = 20.0
    const val LAB_HIST_L_MAX = 100.0
    const val LAB_HIST_A_MIN = -10.0
    const val LAB_HIST_A_MAX = 30.0
    const val LAB_HIST_B_MIN = -5.0
    const val LAB_HIST_B_MAX = 55.0

    // --- Image Quality Validator Heuristics ---
    /**
     * PROVISIONAL / UNCALIBRATED: Minimum acceptable Laplacian variance score for sharpness.
     */
    const val QUALITY_MIN_BLUR_SCORE = 5.0

    /**
     * PROVISIONAL / UNCALIBRATED: Minimum and maximum acceptable average grayscale values.
     */
    const val QUALITY_MIN_BRIGHTNESS = 10.0
    const val QUALITY_MAX_BRIGHTNESS = 248.0

    /**
     * PROVISIONAL / UNCALIBRATED: Minimum standard deviation of grayscale pixels for contrast.
     */
    const val QUALITY_MIN_CONTRAST = 2.0

    /**
     * PROVISIONAL / UNCALIBRATED: Maximum acceptable fraction of pixels clipped at pure black (0) or white (255).
     */
    const val QUALITY_MAX_CLIPPED_FRACTION = 0.20

    /**
     * PROVISIONAL / UNCALIBRATED: Minimum center alignment score (0.0 to 1.0).
     */
    const val QUALITY_MIN_CENTER_ALIGNMENT = 0.10
}
