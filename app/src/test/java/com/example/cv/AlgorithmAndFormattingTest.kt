package com.example.cv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.repository.AppRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlgorithmAndFormattingTest {

    private fun getRepository(): AppRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val prefs = AppPreferences(context)
        return AppRepository(db, prefs, context)
    }

    @Test
    fun testVectorFormattingWithLocaleUS() {
        val testNumber = 12.3456
        val formatted = String.format(Locale.US, "%.4f", testNumber)
        assertEquals("12.3456", formatted)
        assertFalse(formatted.contains(","))
    }

    @Test
    fun testParseVectorJsonStandard() {
        val repo = getRepository()
        // TEST FIXTURE: 191 sample features
        val testVector = DoubleArray(191) { it * 0.1 }
        val json = "[" + testVector.joinToString(",") { String.format(Locale.US, "%.4f", it) } + "]"

        val parsed = repo.parseVectorJson(json)
        assertEquals(191, parsed.size)
        assertEquals(0.0, parsed[0], 0.0001)
        assertEquals(0.1, parsed[1], 0.0001)
        assertEquals(19.0, parsed[190], 0.0001)
    }

    @Test
    fun testParseVectorJsonPersianAndArabicDigits() {
        val repo = getRepository()
        // Persian digits representation of [1.5, 2.75, 3.0]
        val persianJson = "[۱.۵, ۲.۷۵, ۳.۰]"
        val parsed = repo.parseVectorJson(persianJson)
        assertEquals(3, parsed.size)
        assertEquals(1.5, parsed[0], 0.001)
        assertEquals(2.75, parsed[1], 0.001)
        assertEquals(3.0, parsed[2], 0.001)

        // Arabic digits with Persian slash decimal: [٠/٥, ١/٢٥]
        val arabicJson = "[٠/٥, ١/٢٥]"
        val parsedArabic = repo.parseVectorJson(arabicJson)
        assertEquals(2, parsedArabic.size)
        assertEquals(0.5, parsedArabic[0], 0.001)
        assertEquals(1.25, parsedArabic[1], 0.001)
    }

    @Test
    fun testParseVectorJsonRejectsEmptyOrCorrupt() {
        val repo = getRepository()
        try {
            repo.parseVectorJson("")
            fail("Expected IllegalArgumentException for empty vector JSON")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("empty") == true)
        }

        try {
            repo.parseVectorJson("[1.0, invalid_token, 3.0]")
            fail("Expected IllegalArgumentException for non-numeric token")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid numeric token") == true)
        }
    }

    @Test
    fun testSimilarityCalculatorRejectsDimensionMismatch() {
        val calc = SimilarityCalculator()
        val v191 = DoubleArray(191) { 1.0 }
        val v100 = DoubleArray(100) { 1.0 }

        try {
            calc.euclideanDistance(v191, v100)
            fail("Expected IllegalArgumentException for dimension mismatch in euclideanDistance")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Vector dimension mismatch") == true)
        }

        try {
            calc.cosineSimilarity(v191, v100)
            fail("Expected IllegalArgumentException for dimension mismatch in cosineSimilarity")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Vector dimension mismatch") == true)
        }

        try {
            calc.bhattacharyyaDistance(DoubleArray(64), DoubleArray(32))
            fail("Expected IllegalArgumentException for dimension mismatch in bhattacharyyaDistance")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Histogram dimension mismatch") == true)
        }
    }

    @Test
    fun testSimilarityCalculatorComputesCorrectDistancesOnValidVectors() {
        val calc = SimilarityCalculator()
        val v1 = DoubleArray(191) { it.toDouble() }
        val v2 = DoubleArray(191) { it.toDouble() }

        // Identical vectors should have 0 Euclidean distance and 1.0 Cosine correlation
        val dist = calc.euclideanDistance(v1, v2)
        assertEquals(0.0, dist, 0.0001)

        val cos = calc.cosineSimilarity(v1, v2)
        assertEquals(1.0, cos, 0.0001)
    }

    @Test
    fun testFeatureScalerDomainScaleRange() {
        val scaler = FeatureScaler()
        val input = DoubleArray(191) { 128.0 }
        // Set specific channel stats
        val scaled = scaler.domainScale(input)
        assertEquals(191, scaled.size)
        for (v in scaled) {
            assertTrue("Scaled feature $v must be between 0.0 and 1.0", v in 0.0..1.0)
        }
    }

    @Test
    fun testClassifySampleFiltersOutMismatchedDimensionReferences() {
        val calc = SimilarityCalculator()
        val input = DoubleArray(191) { (it + 1) * 0.005 }
        val badRef = StoredReferenceVector(1, "Bad Ref", 100, DoubleArray(50) { 0.5 })
        val goodRef = StoredReferenceVector(2, "Good Ref", 200, DoubleArray(191) { (it + 1) * 0.005 })

        val result = calc.classifySample(input, listOf(badRef, goodRef), similarityThresholdPct = 10.0)
        // Bad ref with size 50 must be filtered out, matching only goodRef
        assertEquals(2, result.topMatch.sampleId)
        assertEquals("Good Ref", result.topMatch.sampleName)
        assertEquals(1, result.allMatches.size)
        assertEquals(2, result.nearestSampleId)
    }
}
