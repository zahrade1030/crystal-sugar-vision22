package com.example.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

class OpenCvPipeline(
    private val qualityValidator: ImageQualityValidator = ImageQualityValidator(),
    private val regionExtractor: MultiRegionExtractor = MultiRegionExtractor(),
    private val featureScaler: FeatureScaler = FeatureScaler(),
    private val similarityCalculator: SimilarityCalculator = SimilarityCalculator()
) {

    /**
     * Executes Simplified Classical Computer Vision Pipeline for Single ROI Sugar Sample Analysis
     */
    fun processImage(
        originalBitmap: Bitmap,
        referenceSamples: List<StoredReferenceVector>,
        normalizationMethod: NormalizationMethod = NormalizationMethod.STANDARD_SCALER,
        similarityThresholdPct: Double = 65.0
    ): Pair<ClassificationResult, PipelineOutput> {
        val startTime = System.currentTimeMillis()

        // 1. Fixed Central ROI (Auto-Crop is completely removed as per Section 1)
        val fixedRoi = extractFixedRoi(originalBitmap)

        // 2. Background Removal (< 18 luminance -> Color.BLACK)
        val bgRemoved = removeBackground(fixedRoi)

        // 3. Resize & Standardization to 512x512
        val resized512 = Bitmap.createScaledBitmap(bgRemoved, 512, 512, true)

        // 4. White Balance & Gaussian Blur
        val whiteBalanced = applyWhiteBalance(resized512)
        val gaussianBlurred = applyGaussianBlur3x3(whiteBalanced)

        // 5. Color Spaces & Visualization Channels for Developer Mode (13 Stages)
        val rgbBitmap = gaussianBlurred
        val redChannelBitmap = extractChannelBitmap(gaussianBlurred, 0)
        val greenChannelBitmap = extractChannelBitmap(gaussianBlurred, 1)
        val blueChannelBitmap = extractChannelBitmap(gaussianBlurred, 2)
        val hsvBitmap = convertToHsvBitmap(gaussianBlurred)
        val labBitmap = convertToLabBitmap(gaussianBlurred)
        val laplacianBitmap = generateLaplacianBitmap(gaussianBlurred)
        val analysisMaskBitmap = generateAnalysisMaskBitmap(gaussianBlurred)

        // 6. Image Quality Assessment
        val qualityResult = qualityValidator.validate(gaussianBlurred)

        // 7. Feature Vector Extraction for Single ROI (Color & Statistical Features + LAB Median)
        val completeFeatures = regionExtractor.extractAllRegions(gaussianBlurred)

        // 8. Feature Vector Normalization
        val normalizedVector = featureScaler.scale(completeFeatures.concatenatedVector, normalizationMethod)

        // 9. Classification against 6 Reference Samples & LAB-Based ICUMSA Interpolation
        val classification = similarityCalculator.classifySample(
            inputVector = normalizedVector,
            sampleLabMedian = completeFeatures.labMedian,
            references = referenceSamples,
            similarityThresholdPct = similarityThresholdPct,
            startTimeMs = startTime
        )

        // Developer Debug Stages List (13 Stages as defined in Section 8)
        val stages = listOf(
            ProcessingStage("1. Original Image", "Raw laboratory sample photo (${originalBitmap.width}x${originalBitmap.height} px)", originalBitmap),
            ProcessingStage("2. Fixed Central ROI (80%)", "Central square crop: S = 0.8 * min(W,H) = ${fixedRoi.width}x${fixedRoi.height} px", fixedRoi),
            ProcessingStage("3. Background Removal (<18)", "Light box background pixels (< 18 luminance) converted to pure black", bgRemoved),
            ProcessingStage("4. Standard 512x512 ROI", "Standardized laboratory resolution 512x512 px", resized512),
            ProcessingStage("5. White Balance (0.96-1.04)", "Color temperature corrected with controlled gains", whiteBalanced),
            ProcessingStage("6. 3x3 Gaussian Blur", "Noise reduction using 3x3 Gaussian kernel (/16)", gaussianBlurred),
            ProcessingStage("7. Red (R) Channel", "Red spectrum intensity grayscale representation", redChannelBitmap),
            ProcessingStage("8. Green (G) Channel", "Green spectrum intensity grayscale representation", greenChannelBitmap),
            ProcessingStage("9. Blue (B) Channel", "Blue spectrum intensity grayscale representation", blueChannelBitmap),
            ProcessingStage("10. HSV Color Space", "Hue-Saturation-Value color space representation", hsvBitmap),
            ProcessingStage("11. CIE-LAB Color Space", "CIE-LAB perceptual color space representation", labBitmap),
            ProcessingStage("12. Grayscale & Laplacian", "3x3 Laplacian edge variance and sharpness map", laplacianBitmap),
            ProcessingStage("13. Analysis Region & Mask", "Sample measurement zone and 191-dim feature mask", analysisMaskBitmap)
        )

        val output = PipelineOutput(
            originalBitmap = originalBitmap,
            processedBitmap = gaussianBlurred,
            qualityResult = qualityResult,
            completeFeatureSet = completeFeatures,
            normalizedVector = normalizedVector,
            stages = stages
        )

        return Pair(classification, output)
    }

    /**
     * Section 1: Fixed ROI Crop (Auto-Crop completely removed)
     * W = original width, H = original height
     * S = 0.8 * min(W, H)
     * x = (W - S) / 2, y = (H - S) / 2
     * x2 = x + S, y2 = y + S
     * Exactly crops an 80% square from the image center regardless of object placement.
     */
    private fun extractFixedRoi(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val minDim = minOf(w, h)
        val s = (0.8 * minDim).toInt().coerceIn(1, minDim)
        val x = ((w - s) / 2).coerceIn(0, w - s)
        val y = ((h - s) / 2).coerceIn(0, h - s)

        return Bitmap.createBitmap(src, x, y, s, s)
    }

    private fun removeBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            if (lum < 18.0) { // Black light box background threshold
                pixels[i] = Color.BLACK
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applyWhiteBalance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var sumR = 0.0; var sumG = 0.0; var sumB = 0.0
        var count = 0

        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r > 20 || g > 20 || b > 20) {
                sumR += r; sumG += g; sumB += b
                count++
            }
        }

        if (count == 0) return src
        val avgR = sumR / count
        val avgG = sumG / count
        val avgB = sumB / count
        val avgGray = (avgR + avgG + avgB) / 3.0

        val gainR = (avgGray / avgR).coerceIn(0.96, 1.04)
        val gainG = (avgGray / avgG).coerceIn(0.96, 1.04)
        val gainB = (avgGray / avgB).coerceIn(0.96, 1.04)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (((c shr 16) and 0xFF) * gainR).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) * gainG).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * gainB).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(r, g, b)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applyGaussianBlur3x3(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val kernel = arrayOf(
            intArrayOf(1, 2, 1),
            intArrayOf(2, 4, 2),
            intArrayOf(1, 2, 1)
        )
        val kernelWeight = 16

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var rSum = 0; var gSum = 0; var bSum = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val c = pixels[(y + ky) * w + (x + kx)]
                        val wK = kernel[ky + 1][kx + 1]
                        rSum += ((c shr 16) and 0xFF) * wK
                        gSum += ((c shr 8) and 0xFF) * wK
                        bSum += (c and 0xFF) * wK
                    }
                }
                outPixels[y * w + x] = Color.rgb(rSum / kernelWeight, gSum / kernelWeight, bSum / kernelWeight)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun convertToHsvBitmap(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            Color.RGBToHSV(r, g, b, hsv)
            val hVal = (hsv[0] / 360f * 255).toInt()
            val sVal = (hsv[1] * 255).toInt()
            val vVal = (hsv[2] * 255).toInt()
            pixels[i] = Color.rgb(hVal, sVal, vVal)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun convertToLabBitmap(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val labL = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt().coerceIn(0, 255)
            val labA = (0.5 * (r - g) + 128).toInt().coerceIn(0, 255)
            val labB = (0.5 * (g - b) + 128).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(labL, labA, labB)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun extractChannelBitmap(src: Bitmap, channelIndex: Int): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val v = when (channelIndex) {
                0 -> r
                1 -> g
                else -> b
            }
            pixels[i] = Color.rgb(v, v, v)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun generateLaplacianBitmap(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                fun getGray(px: Int): Double {
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    return 0.299 * r + 0.587 * g + 0.114 * b
                }
                val center = getGray(pixels[y * w + x])
                val top = getGray(pixels[(y - 1) * w + x])
                val bottom = getGray(pixels[(y + 1) * w + x])
                val left = getGray(pixels[y * w + (x - 1)])
                val right = getGray(pixels[y * w + (x + 1)])

                val lap = Math.abs(top + bottom + left + right - 4.0 * center).toInt().coerceIn(0, 255)
                outPixels[y * w + x] = Color.rgb(lap, lap, lap)
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun generateAnalysisMaskBitmap(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val bmp = Bitmap.createBitmap(src)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            color = Color.CYAN
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        // Draw circular sample guide ROI
        val radius = (minOf(w, h) * 0.45f)
        canvas.drawCircle(w / 2f, h / 2f, radius, paint)

        // Draw crosshair
        canvas.drawLine(w / 2f - 20f, h / 2f, w / 2f + 20f, h / 2f, paint)
        canvas.drawLine(w / 2f, h / 2f - 20f, w / 2f, h / 2f + 20f, paint)
        return bmp
    }
}

data class PipelineOutput(
    val originalBitmap: Bitmap,
    val processedBitmap: Bitmap,
    val qualityResult: ImageQualityResult,
    val completeFeatureSet: CompleteFeatureSet,
    val normalizedVector: DoubleArray,
    val stages: List<ProcessingStage>
)
