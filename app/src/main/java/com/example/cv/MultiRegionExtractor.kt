package com.example.cv

import android.graphics.Bitmap
import android.graphics.Color

class MultiRegionExtractor(private val extractor: FeatureExtractor = FeatureExtractor()) {

    /**
     * Extracts features from a single ROI (512x512 image) without multi-region splitting
     */
    fun extractAllRegions(bitmap: Bitmap): CompleteFeatureSet {
        val startTime = System.currentTimeMillis()

        // Ensure fixed resolution 512x512
        val targetBmp = if (bitmap.width != 512 || bitmap.height != 512) {
            Bitmap.createScaledBitmap(bitmap, 512, 512, true)
        } else {
            bitmap
        }

        // Single ROI region (Whole Image)
        val singleRegion = extractor.extractRegionFeatures(targetBmp, 0, "Single ROI (Whole Image)")
        val regions = listOf(singleRegion)
        val concat = singleRegion.values

        val extractionTime = System.currentTimeMillis() - startTime

        // Compute 64-bin Color Histogram (4x4x4 RGB)
        val histogram = FloatArray(64)
        val pixels = IntArray(512 * 512)
        targetBmp.getPixels(pixels, 0, 512, 0, 0, 512, 512)
        for (p in pixels) {
            val r = ((p shr 16) and 0xFF) / 64
            val g = ((p shr 8) and 0xFF) / 64
            val b = (p and 0xFF) / 64
            val bin = (r * 16 + g * 4 + b).coerceIn(0, 63)
            histogram[bin] += 1.0f
        }
        val totalPix = (512 * 512).toFloat()
        for (i in histogram.indices) histogram[i] /= totalPix

        // Representative Means for RGB, HSV, LAB
        var sumR = 0.0; var sumG = 0.0; var sumB = 0.0
        val hsvTmp = FloatArray(3)
        var sumH = 0.0; var sumS = 0.0; var sumV = 0.0

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sumR += r; sumG += g; sumB += b
            Color.RGBToHSV(r, g, b, hsvTmp)
            sumH += hsvTmp[0]; sumS += hsvTmp[1]; sumV += hsvTmp[2]
        }
        val count = pixels.size.toDouble()
        val meanR = sumR / count
        val meanG = sumG / count
        val meanB = sumB / count

        val labL = 0.2126 * meanR + 0.7152 * meanG + 0.0722 * meanB
        val labA = 0.5 * (meanR - meanG) + 128.0
        val labB = 0.5 * (meanG - meanB) + 128.0

        return CompleteFeatureSet(
            regions = regions,
            concatenatedVector = concat,
            extractionTimeMs = extractionTime,
            colorHistogram = histogram,
            rgbMeans = Triple(meanR, meanG, meanB),
            hsvMeans = Triple(sumH / count, sumS / count, sumV / count),
            labMeans = Triple(labL, labA, labB),
            labMedian = singleRegion.labMedian
        )
    }
}
