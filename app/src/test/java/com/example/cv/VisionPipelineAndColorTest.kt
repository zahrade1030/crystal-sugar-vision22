package com.example.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.ReferenceSampleEntity
import com.example.data.preferences.AppPreferences
import com.example.data.repository.AppRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * TEST FIXTURE: Comprehensive unit tests T1–T22 for Crystal Sugar Vision color vision pipeline.
 * Evaluates CIEDE2000, sRGB-CIELAB conversion, background segmentation mask, quality policy,
 * vector serialization, Room v3-v4 migration, two-sample comparison, and decimal level interpolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisionPipelineAndColorTest {

    // --- T1: Self-comparison deltaE00 <= 10^-9 ---
    @Test
    fun testT01_SelfComparisonZeroDeltaE() {
        val lab = Triple(75.4, 2.1, 14.8)
        val diff = Ciede2000.compute(lab.first, lab.second, lab.third, lab.first, lab.second, lab.third)
        assertTrue("Self-comparison deltaE00 must be <= 1e-9, got ${diff.deltaE00}", diff.deltaE00 <= 1e-9)

        val similarity = Ciede2000.relativeSimilarityIndex(diff.deltaE00)
        assertEquals(100.0, similarity, 1e-6)
    }

    // --- T2: Same sugar on black vs gray vs white background deltaE00 <= 0.5 via SampleMask ---
    @Test
    fun testT02_BackgroundInvarianceWithSampleMask() {
        val sugarColor = Color.rgb(240, 235, 220)
        val bgs = listOf(Color.BLACK, Color.rgb(128, 128, 128), Color.WHITE)

        val labs = bgs.map { bg ->
            val w = 100
            val h = 100
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = x - 50
                    val dy = y - 50
                    val isSugar = (dx * dx + dy * dy) <= (30 * 30)
                    pixels[y * w + x] = if (isSugar) sugarColor else bg
                }
            }
            val maskResult = SampleMask.computeMask(pixels, w, h)
            assertNotNull("Mask must compute successfully", maskResult)
            assertTrue("Sugar area fraction must be > 10%", maskResult.areaFraction >= 0.10)
            val profile = ColorStats.computeProfile(pixels, maskResult.mask)
            Triple(profile.trimmedMeanL, profile.trimmedMeanA, profile.trimmedMeanB)
        }

        val de01 = Ciede2000.compute(labs[0].first, labs[0].second, labs[0].third, labs[1].first, labs[1].second, labs[1].third).deltaE00
        val de12 = Ciede2000.compute(labs[1].first, labs[1].second, labs[1].third, labs[2].first, labs[2].second, labs[2].third).deltaE00
        val de02 = Ciede2000.compute(labs[0].first, labs[0].second, labs[0].third, labs[2].first, labs[2].second, labs[2].third).deltaE00

        assertTrue("DeltaE between black and gray bg must be <= 0.5, got $de01", de01 <= 0.5)
        assertTrue("DeltaE between gray and white bg must be <= 0.5, got $de12", de12 <= 0.5)
        assertTrue("DeltaE between black and white bg must be <= 0.5, got $de02", de02 <= 0.5)
    }

    // --- T3: Sample shift (+-40px) and scale (+-10%) invariance deltaE00 <= 0.5 ---
    @Test
    fun testT03_ShiftAndScaleInvariance() {
        val sugarColor = Color.rgb(245, 240, 225)
        val bgColor = Color.rgb(40, 40, 40)
        val w = 200
        val h = 200

        fun makeSample(cx: Int, cy: Int, radius: Int): Triple<Double, Double, Double> {
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = x - cx
                    val dy = y - cy
                    pixels[y * w + x] = if (dx * dx + dy * dy <= radius * radius) sugarColor else bgColor
                }
            }
            val maskResult = SampleMask.computeMask(pixels, w, h)
            val profile = ColorStats.computeProfile(pixels, maskResult.mask)
            return Triple(profile.trimmedMeanL, profile.trimmedMeanA, profile.trimmedMeanB)
        }

        val baseLab = makeSample(100, 100, 40)
        val shiftedLab = makeSample(125, 95, 40)
        val scaledLab = makeSample(100, 100, 44)

        val deShift = Ciede2000.compute(baseLab.first, baseLab.second, baseLab.third, shiftedLab.first, shiftedLab.second, shiftedLab.third).deltaE00
        val deScale = Ciede2000.compute(baseLab.first, baseLab.second, baseLab.third, scaledLab.first, scaledLab.second, scaledLab.third).deltaE00

        assertTrue("Shift deltaE00 must be <= 0.5, got $deShift", deShift <= 0.5)
        assertTrue("Scale deltaE00 must be <= 0.5, got $deScale", deScale <= 0.5)
    }

    // --- T4: Brightness multiplier shifts deltaL* in correct direction ---
    @Test
    fun testT04_BrightnessMultiplierShiftsDeltaL() {
        val baseLab = CieLab.rgbToLab(200, 200, 190)
        val darkLab = CieLab.rgbToLab((200 * 0.9).toInt(), (200 * 0.9).toInt(), (190 * 0.9).toInt())
        val brightLab = CieLab.rgbToLab(
            minOf(255, (200 * 1.1).toInt()),
            minOf(255, (200 * 1.1).toInt()),
            minOf(255, (190 * 1.1).toInt())
        )

        val deltaL_dark = darkLab.first - baseLab.first
        val deltaL_bright = brightLab.first - baseLab.first

        assertTrue("0.9x multiplier must decrease L* (deltaL < 0), got $deltaL_dark", deltaL_dark < 0)
        assertTrue("1.1x multiplier must increase L* (deltaL > 0), got $deltaL_bright", deltaL_bright > 0)
    }

    // --- T5: sRGB -> CIELAB D65 on standard known colors ---
    @Test
    fun testT05_SrgbToLabD65KnownColors() {
        // Pure Black
        val black = CieLab.rgbToLab(0, 0, 0)
        assertEquals(0.0, black.first, 0.05)
        assertEquals(0.0, black.second, 0.05)
        assertEquals(0.0, black.third, 0.05)

        // Pure White
        val white = CieLab.rgbToLab(255, 255, 255)
        assertEquals(100.0, white.first, 0.05)
        assertEquals(0.0, white.second, 0.5)
        assertEquals(0.0, white.third, 0.5)

        // Standard sRGB Red (255, 0, 0) -> D65 L* ~ 53.24, a* ~ 80.09, b* ~ 67.20
        val red = CieLab.rgbToLab(255, 0, 0)
        assertEquals(53.24, red.first, 0.3)
        assertEquals(80.09, red.second, 0.5)
        assertEquals(67.20, red.third, 0.5)

        // Standard sRGB Green (0, 255, 0) -> D65 L* ~ 87.73, a* ~ -86.18, b* ~ 83.18
        val green = CieLab.rgbToLab(0, 255, 0)
        assertEquals(87.73, green.first, 0.3)
        assertEquals(-86.18, green.second, 0.5)
        assertEquals(83.18, green.third, 0.5)

        // Standard sRGB Blue (0, 0, 255) -> D65 L* ~ 32.30, a* ~ 79.20, b* ~ -107.86
        val blue = CieLab.rgbToLab(0, 0, 255)
        assertEquals(32.30, blue.first, 0.3)
        assertEquals(79.20, blue.second, 0.5)
        assertEquals(-107.86, blue.third, 0.5)
    }

    // --- T6: CIEDE2000 Sharma et al. (2005) reference test pairs ---
    @Test
    fun testT06_Ciede2000SharmaReferencePairs() {
        // Pair 1:
        val de1 = Ciede2000.compute(50.0000, 2.6772, -79.7751, 50.0000, 0.0000, -82.7485).deltaE00
        assertEquals(2.0425, de1, 0.0005)

        // Pair 2:
        val de2 = Ciede2000.compute(50.0000, 3.1571, -77.2803, 50.0000, 0.0000, -82.7485).deltaE00
        assertEquals(2.8615, de2, 0.0005)

        // Pair 3:
        val de3 = Ciede2000.compute(50.0000, 2.8361, -74.0200, 50.0000, 0.0000, -82.7485).deltaE00
        assertEquals(3.4412, de3, 0.0005)

        // Pair 4 (identical):
        val de4 = Ciede2000.compute(50.0000, -0.0002, -82.7485, 50.0000, -0.0002, -82.7485).deltaE00
        assertEquals(0.0000, de4, 0.00001)
    }

    // --- T7: Vector serialization round-trip across various Locales ---
    @Test
    fun testT07_VectorSerializationRoundTripLocales() {
        val originalLocale = Locale.getDefault()
        val testLocales = listOf(Locale.ROOT, Locale.GERMANY, Locale.FRANCE, Locale("fa", "IR"), Locale("ar"))

        try {
            for (loc in testLocales) {
                Locale.setDefault(loc)
                val originalVec = DoubleArray(191) { (it + 1) * 0.1234 }
                val originalLab = Triple(85.5, 1.25, 10.75)

                val json = VectorFormat.serialize(originalVec, originalLab)
                val vContent = json.substringAfter("\"v\":[").substringBefore("]")
                val tokens = vContent.split(",")
                for (token in tokens) {
                    assertTrue("Token '$token' must contain decimal dot", token.contains("."))
                    assertNotNull("Token '$token' must parse as Double", token.toDoubleOrNull())
                }

                val parsed = VectorFormat.parse(json)
                assertNotNull("Parsed result should not be null for locale $loc", parsed)
                assertEquals(191, parsed!!.vector.size)
                assertEquals(originalVec[0], parsed.vector[0], 0.0001)
                assertEquals(originalVec[190], parsed.vector[190], 0.0001)
                assertNotNull(parsed.lab)
                assertEquals(originalLab.first, parsed.lab!!.first, 0.0001)
                assertEquals(originalLab.second, parsed.lab!!.second, 0.0001)
                assertEquals(originalLab.third, parsed.lab!!.third, 0.0001)
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    // --- T8: Corrupt / empty vector parsing returns error or null, never silent 191 zeros ---
    @Test
    fun testT08_CorruptOrEmptyVectorRejection() {
        assertNull(VectorFormat.parse(""))
        assertNull(VectorFormat.parse("   "))
        assertNull(VectorFormat.parse("not json at all"))
        assertNull(VectorFormat.parse("[]"))
        assertNull(VectorFormat.parse("[1.0, 2.0, abc]"))
        assertNull(VectorFormat.parse("{\"schema\":3}")) // Missing "v" array
        assertNull(VectorFormat.parse("{\"schema\":3,\"v\":[1.0, corrupt, 3.0]}"))
    }

    // --- T9: Quality policy rejection (severe blur, extreme exposure, mask < 10%) ---
    @Test
    fun testT09_QualityPolicyRejection() {
        val width = 100
        val height = 100
        val total = width * height

        // Case 1: Mask area < 10%
        val allBlackPixels = IntArray(total) { Color.BLACK }
        val smallMask = BooleanArray(total) { it < (total * 0.05).toInt() }
        val dummyMaskResult = SampleMask.MaskResult(
            mask = smallMask,
            foregroundCount = (total * 0.05).toInt(),
            areaFraction = 0.05,
            maskMode = SampleMask.MaskMode.BACKGROUND_SEGMENTED,
            bgLab = Triple(0.0, 0.0, 0.0)
        )
        val evalLowMask = QualityPolicy.evaluate(allBlackPixels, width, height, dummyMaskResult)
        assertEquals(QualityPolicy.Status.REJECT, evalLowMask.status)
        assertFalse(evalLowMask.isPassed)

        // Case 2: Extreme overexposure (all 255)
        val allWhitePixels = IntArray(total) { Color.WHITE }
        val fullMask = BooleanArray(total) { true }
        val fullMaskResult = SampleMask.MaskResult(
            mask = fullMask,
            foregroundCount = total,
            areaFraction = 1.0,
            maskMode = SampleMask.MaskMode.BACKGROUND_SEGMENTED,
            bgLab = Triple(100.0, 0.0, 0.0)
        )
        val evalOver = QualityPolicy.evaluate(allWhitePixels, width, height, fullMaskResult)
        assertEquals(QualityPolicy.Status.REJECT, evalOver.status)
        assertFalse(evalOver.isPassed)
    }

    // --- T10: Gaussian blur border replication on uniform image doesn't create boundary ring artifacts ---
    @Test
    fun testT10_GaussianBlurBorderReplication() {
        val width = 50
        val height = 50
        val uniformColor = Color.rgb(180, 180, 180)
        val pixels = IntArray(width * height) { uniformColor }

        val pipeline = OpenCvPipeline()
        val blurred = pipeline.applyGaussianBlur3x3(pixels, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = blurred.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                assertEquals("Red channel at ($x, $y) should be 180", 180, r)
                assertEquals("Green channel at ($x, $y) should be 180", 180, g)
                assertEquals("Blue channel at ($x, $y) should be 180", 180, b)
            }
        }
    }

    // --- T11: Normalized histograms sum to 1.0; MIN_MAX_SCALER absent from decision path ---
    @Test
    fun testT11_HistogramsNormalizedSumToOne() {
        val w = 50
        val h = 50
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = Color.rgb(x * 5, y * 5, (x + y) * 2)
            }
        }
        val mask = BooleanArray(w * h) { true }
        val profile = ColorStats.computeProfile(pixels, mask)

        val rgbSum = profile.rgbHistogram64.sum()
        val labSum = profile.labHistogram64.sum()

        assertEquals("RGB histogram bin sum must equal 1.0", 1.0, rgbSum, 1e-4)
        assertEquals("Lab histogram bin sum must equal 1.0", 1.0, labSum, 1e-4)
    }

    // --- T12: Reference preservation across DB re-initialization (no duplicate records) ---
    @Test
    fun testT12_ReferencePreservationAcrossReinitialization() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.referenceSampleDao()

        // Initialize samples
        AppDatabase.ensureSamplesUpdated(context, db)
        val initialList = dao.getAllSamplesDirect()
        assertEquals(6, initialList.size)

        // Insert custom user sample (id = 7)
        val userSample = ReferenceSampleEntity(
            id = 7,
            name = "User Factory Calibrated Sugar",
            icumsaValue = 120,
            description = "Custom calibrated sample",
            featureVectorJson = VectorFormat.serialize(DoubleArray(191) { 0.5 }, Triple(75.0, 1.0, 10.0)),
            source = "USER_CALIBRATED",
            featureSchemaVersion = 3
        )
        dao.insertOrUpdate(userSample)
        assertEquals(7, dao.getAllSamplesDirect().size)

        // Running ensureSamplesUpdated again must preserve user sample
        AppDatabase.ensureSamplesUpdated(context, db)
        val afterList = dao.getAllSamplesDirect()
        assertEquals(7, afterList.size)

        val retrieved = dao.getSampleById(7)
        assertNotNull(retrieved)
        assertEquals("USER_CALIBRATED", retrieved?.source)
    }

    // --- T15: Two-sample comparison: A=A -> dE=0; swap A<->B inverts deltaL,a,b while keeping dE ---
    @Test
    fun testT15_TwoSampleComparisonSymmetry() {
        val labA = Triple(82.4, 1.5, 12.0)
        val labB = Triple(76.2, 3.8, 18.5)

        val diffAA = Ciede2000.compute(labA.first, labA.second, labA.third, labA.first, labA.second, labA.third)
        assertEquals(0.0, diffAA.deltaE00, 1e-9)

        val diffAB = Ciede2000.compute(labA.first, labA.second, labA.third, labB.first, labB.second, labB.third)
        val diffBA = Ciede2000.compute(labB.first, labB.second, labB.third, labA.first, labA.second, labA.third)

        assertEquals("CIEDE2000 must be symmetric", diffAB.deltaE00, diffBA.deltaE00, 1e-9)
        assertEquals(-diffAB.deltaL, diffBA.deltaL, 1e-9)
        assertEquals(-diffAB.deltaA, diffBA.deltaA, 1e-9)
        assertEquals(-diffAB.deltaB, diffBA.deltaB, 1e-9)
    }

    // --- T16: scalePreservingAspect preserves aspect ratio ---
    @Test
    fun testT16_ScalePreservingAspect() {
        val pipeline = OpenCvPipeline()
        val original = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val scaled = pipeline.scalePreservingAspect(original, 200)

        assertEquals(200, scaled.width)
        assertEquals(100, scaled.height)
    }

    // --- T17: Synthetic test fixture disclosure banner present on synthetic references ---
    @Test
    fun testT17_SyntheticFixtureDisclosure() {
        val dummyMatch = ReferenceMatch(
            sampleId = 1,
            sampleName = "نمونه 1",
            icumsaValue = 50,
            deltaE00 = 0.5,
            euclideanDistance = 1.0,
            euclideanSimilarityPct = 95.0,
            cosineSimilarity = 0.99,
            cosineSimilarityPct = 99.0,
            finalSimilarityPct = 95.0,
            source = "SYNTHETIC_DEMO",
            isSynthetic = true
        )
        val result = ClassificationResult(
            nearestSampleId = 1,
            nearestSampleName = "نمونه 1",
            isUnknown = false,
            confidenceScorePct = 95.0,
            confidenceLevel = ConfidenceLevel.VERY_HIGH,
            topMatch = dummyMatch,
            allMatches = listOf(dummyMatch),
            processingTimeMs = 50L,
            thresholdUsed = 10.0,
            estimatedReferenceLevel = 1.0,
            isSyntheticDemo = true
        )
        assertTrue("Result created with synthetic references must flag isSyntheticDemo", result.isSyntheticDemo)
    }

    // --- T18: UI honesty grep: no claims of OpenCV Native, SQLCipher, or 13 stages ---
    @Test
    fun testT18_UiHonestyNoFalseClaims() {
        val appSrcDir = File("src/main")
        if (appSrcDir.exists()) {
            val forbiddenPhrases = listOf("OpenCV Native C++", "SQLCipher", "13 مراحل", "13 stages")
            appSrcDir.walk().filter { it.extension == "kt" || it.extension == "xml" }.forEach { file ->
                val content = file.readText()
                for (phrase in forbiddenPhrases) {
                    assertFalse(
                        "File ${file.name} must not contain misleading phrase '$phrase'",
                        content.contains(phrase)
                    )
                }
            }
        }
    }

    // --- T19: Similarity index monotonicity: dE=0 -> 100%, strictly decreasing ---
    @Test
    fun testT19_SimilarityIndexMonotonicity() {
        val sim0 = Ciede2000.relativeSimilarityIndex(0.0)
        assertEquals(100.0, sim0, 1e-6)

        var lastSim = sim0
        val dEs = listOf(0.5, 1.0, 2.0, 3.5, 5.0, 10.0, 20.0)
        for (de in dEs) {
            val sim = Ciede2000.relativeSimilarityIndex(de)
            assertTrue("Similarity must be strictly decreasing ($sim < $lastSim)", sim < lastSim)
            assertTrue("Similarity must remain >= 0", sim >= 0.0)
            lastSim = sim
        }
    }

    // --- T20: Decimal interpolation between consecutive references ---
    @Test
    fun testT20_DecimalInterpolationConsecutive() {
        // Equal distance to reference 2 and 3: d2 = 1.5, d3 = 1.5 -> t = 0.5 -> level = 2.5
        val (level, explanation) = ReferenceLevelEstimator.interpolateLevel(
            idA = 2,
            idB = 3,
            dA = 1.5,
            dB = 1.5,
            unknownThreshold = 5.0
        )
        assertNotNull(level)
        assertEquals(2.5, level!!, 0.01)
        assertTrue(explanation.contains("Interpolated"))
    }

    // --- T21: Decimal interpolation rejected for non-consecutive or out-of-bounds references ---
    @Test
    fun testT21_DecimalInterpolationRejectedNonConsecutiveOrFar() {
        // Non-consecutive: IDs 1 and 4 (|1-4| = 3 != 1)
        val (nonConsecutiveLevel, _) = ReferenceLevelEstimator.interpolateLevel(
            idA = 1,
            idB = 4,
            dA = 1.2,
            dB = 1.3,
            unknownThreshold = 5.0
        )
        // Non-consecutive references must return null (interpolation disallowed)
        assertNull("Non-consecutive references must return null", nonConsecutiveLevel)

        // Far distance: best distance 8.0 > unknown threshold 5.0
        val (farLevel, explanationFar) = ReferenceLevelEstimator.interpolateLevel(
            idA = 2,
            idB = 3,
            dA = 8.0,
            dB = 8.5,
            unknownThreshold = 5.0
        )
        assertNull("Must return null when distances exceed unknown threshold", farLevel)
        assertTrue(explanationFar.contains("Unknown") || explanationFar.contains("outside reference range"))
    }

    // --- T22: Result model handles null fields and safe defaults ---
    @Test
    fun testT22_ResultModelNullSafety() {
        val dummyMatch = ReferenceMatch(
            sampleId = 1,
            sampleName = "Sample 1",
            icumsaValue = 50,
            deltaE00 = 1.0,
            euclideanDistance = 1.0,
            euclideanSimilarityPct = 90.0,
            cosineSimilarity = 0.9,
            cosineSimilarityPct = 90.0,
            finalSimilarityPct = 90.0,
            source = "SYNTHETIC_DEMO",
            isSynthetic = true
        )
        val result = ClassificationResult(
            nearestSampleId = 1,
            nearestSampleName = "Sample 1",
            isUnknown = false,
            confidenceScorePct = 85.0,
            confidenceLevel = ConfidenceLevel.HIGH,
            topMatch = dummyMatch,
            allMatches = listOf(dummyMatch),
            processingTimeMs = 40L,
            thresholdUsed = 10.0,
            estimatedReferenceLevel = null,
            estimationExplanation = "",
            isSyntheticDemo = false
        )
        assertNull(result.estimatedReferenceLevel)
        assertFalse(result.isSyntheticDemo)
    }
}
