package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.cv.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IcumsaEstimationUnitTest {

    private val estimator = ICUMSAEstimator()
    private val featureExtractor = FeatureExtractor()

    // Create 6 calibrated reference standards in ascending ICUMSA order
    private val standardReferences = listOf(
        ReferenceSampleLab(
            id = 1,
            name = "Ref 1 (Ultra Light)",
            actualIcumsa = 45.0,
            medianLab = MedianLab(l = 95.0, a = -0.5, b = 2.0),
            vector191 = DoubleArray(191) { 0.1 },
            rgbHistogram64 = DoubleArray(64) { 1.0 / 64.0 },
            labHistogram64 = DoubleArray(64) { 1.0 / 64.0 }
        ),
        ReferenceSampleLab(
            id = 2,
            name = "Ref 2 (Light)",
            actualIcumsa = 100.0,
            medianLab = MedianLab(l = 90.0, a = 0.0, b = 5.0),
            vector191 = DoubleArray(191) { 0.2 },
            rgbHistogram64 = DoubleArray(64) { 1.0 / 64.0 },
            labHistogram64 = DoubleArray(64) { 1.0 / 64.0 }
        ),
        ReferenceSampleLab(
            id = 3,
            name = "Ref 3 (Medium Light)",
            actualIcumsa = 200.0,
            medianLab = MedianLab(l = 82.0, a = 1.0, b = 10.0),
            vector191 = DoubleArray(191) { 0.3 },
            rgbHistogram64 = DoubleArray(64) { 1.0 / 64.0 },
            labHistogram64 = DoubleArray(64) { 1.0 / 64.0 }
        ),
        ReferenceSampleLab(
            id = 4,
            name = "Ref 4 (Medium Dark)",
            actualIcumsa = 350.0,
            medianLab = MedianLab(l = 70.0, a = 3.0, b = 18.0),
            vector191 = DoubleArray(191) { 0.4 },
            rgbHistogram64 = DoubleArray(64) { 1.0 / 64.0 },
            labHistogram64 = DoubleArray(64) { 1.0 / 64.0 }
        ),
        ReferenceSampleLab(
            id = 5,
            name = "Ref 5 (Dark)",
            actualIcumsa = 500.0,
            medianLab = MedianLab(l = 55.0, a = 6.0, b = 25.0),
            vector191 = DoubleArray(191) { 0.5 },
            rgbHistogram64 = DoubleArray(64) { 1.0 / 64.0 },
            labHistogram64 = DoubleArray(64) { 1.0 / 64.0 }
        ),
        ReferenceSampleLab(
            id = 6,
            name = "Ref 6 (Ultra Dark)",
            actualIcumsa = 800.0,
            medianLab = MedianLab(l = 40.0, a = 10.0, b = 32.0),
            vector191 = DoubleArray(191) { 0.6 },
            rgbHistogram64 = DoubleArray(64) { 1.0 / 64.0 },
            labHistogram64 = DoubleArray(64) { 1.0 / 64.0 }
        )
    )

    private val sampleQualityOk = ImageQualityResult(
        isPassed = true,
        blurScore = 25.0,
        brightnessScore = 120.0,
        contrastScore = 15.0,
        sampleCoverage = 0.85,
        saturatedPixelRatio = 0.02,
        overallScore = 92.0
    )

    @Test
    fun testReferenceSorting() {
        // Pass references in random unsorted order
        val shuffledRefs = standardReferences.shuffled()
        val sorted = shuffledRefs.sortedBy { it.actualIcumsa }

        assertEquals(45.0, sorted[0].actualIcumsa, 0.01)
        assertEquals(100.0, sorted[1].actualIcumsa, 0.01)
        assertEquals(200.0, sorted[2].actualIcumsa, 0.01)
        assertEquals(350.0, sorted[3].actualIcumsa, 0.01)
        assertEquals(500.0, sorted[4].actualIcumsa, 0.01)
        assertEquals(800.0, sorted[5].actualIcumsa, 0.01)
    }

    @Test
    fun testLinearInterpolationMidpoint() {
        // Sample exactly halfway between Ref 1 (ICUMSA 45) and Ref 2 (ICUMSA 100)
        // Midpoint LAB: L = (95 + 90)/2 = 92.5, a = -0.25, b = 3.5
        val sampleLab = MedianLab(l = 92.5, a = -0.25, b = 3.5)

        val result = estimator.estimateIcumsa(
            sampleMedianLab = sampleLab,
            sampleVector191 = DoubleArray(191) { 0.15 },
            sampleRgbHist = DoubleArray(64) { 1.0 / 64.0 },
            sampleLabHist = DoubleArray(64) { 1.0 / 64.0 },
            references = standardReferences,
            qualityResult = sampleQualityOk
        )

        // Expected ICUMSA = 45 + 0.5 * (100 - 45) = 72.5
        assertEquals(72.5, result.estimatedIcumsa, 1.0)
        assertEquals(1, result.interpolation.segmentIndex)
        assertEquals(0.5, result.interpolation.clampedT, 0.05)
        assertEquals(0.0, result.interpolation.perpendicularDistance, 0.1)
        assertFalse(result.interpolation.isOutOfBounds)
    }

    @Test
    fun testSegmentSelectionSegmentThree() {
        // Sample between Ref 3 (200, LAB=(82,1,10)) and Ref 4 (350, LAB=(70,3,18))
        // 25% of the way along segment 3:
        // L = 82 - 0.25*12 = 79.0, a = 1 + 0.25*2 = 1.5, b = 10 + 0.25*8 = 12.0
        val sampleLab = MedianLab(l = 79.0, a = 1.5, b = 12.0)

        val result = estimator.estimateIcumsa(
            sampleMedianLab = sampleLab,
            sampleVector191 = DoubleArray(191) { 0.32 },
            sampleRgbHist = DoubleArray(64) { 1.0 / 64.0 },
            sampleLabHist = DoubleArray(64) { 1.0 / 64.0 },
            references = standardReferences,
            qualityResult = sampleQualityOk
        )

        assertEquals(3, result.interpolation.segmentIndex)
        // Expected ICUMSA = 200 + 0.25 * (350 - 200) = 237.5
        assertEquals(237.5, result.estimatedIcumsa, 2.0)
        assertFalse(result.interpolation.isOutOfBounds)
    }

    @Test
    fun testOutOfBoundsLighterThanLowestReference() {
        // Sample lighter than Ref 1 (L = 98.0, a = -1.0, b = 1.0)
        val ultraWhiteSample = MedianLab(l = 98.0, a = -1.0, b = 0.5)

        val result = estimator.estimateIcumsa(
            sampleMedianLab = ultraWhiteSample,
            sampleVector191 = DoubleArray(191) { 0.05 },
            sampleRgbHist = DoubleArray(64) { 1.0 / 64.0 },
            sampleLabHist = DoubleArray(64) { 1.0 / 64.0 },
            references = standardReferences,
            qualityResult = sampleQualityOk
        )

        assertTrue("Expected out of bounds warning", result.interpolation.isOutOfBounds)
        assertNotNull(result.interpolation.warningMessage)
        // t must be clamped to 0.0, estimating ICUMSA = 45.0 with warning
        assertEquals(45.0, result.estimatedIcumsa, 0.01)
    }

    @Test
    fun testFeatureVector191Dimensions() {
        // Create test bitmap with white sugar crystal circle using setPixels for Robolectric compatibility
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(512 * 512)
        val sugarColor = Color.rgb(240, 235, 220)
        for (y in 0 until 512) {
            val dy = y - 256
            for (x in 0 until 512) {
                val dx = x - 256
                if (dx * dx + dy * dy <= 200 * 200) {
                    pixels[y * 512 + x] = sugarColor
                } else {
                    pixels[y * 512 + x] = Color.BLACK
                }
            }
        }
        bmp.setPixels(pixels, 0, 512, 0, 0, 512, 512)

        val features = featureExtractor.extractFeatures(bmp)

        // Must extract exactly 191 features (63 statistics + 64 RGB hist + 64 LAB hist)
        assertEquals(191, features.concatenatedVector.size)
        assertEquals(64, features.rgbHistogram64.size)
        assertEquals(64, features.labHistogram64.size)

        // Valid sample pixels inside circular mask
        assertTrue(features.validPixelCount > 50000)
        assertTrue(features.medianLab.l > 80.0) // Light sugar
    }

    @Test
    fun testImageQualityValidatorBlurRejection() {
        val validator = ImageQualityValidator()
        // Flat solid image has zero Laplacian variance -> should be rejected as too blurry
        val solidBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(solidBmp)
        canvas.drawColor(Color.rgb(200, 200, 200))

        val result = validator.validate(solidBmp)
        assertFalse(result.isPassed)
        assertEquals("تصویر تار است.", result.rejectionReason)
    }
}
