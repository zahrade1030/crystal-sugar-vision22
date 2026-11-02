package com.example.cv

import kotlin.math.*

/**
 * Pure Kotlin sample mask segmentation.
 * Operates on IntArray pixels without Android Graphics dependencies for JVM testing.
 */
object SampleMask {

    enum class MaskMode {
        BACKGROUND_SEGMENTED,
        FULL_FRAME_CENTER,
        REJECTED_EMPTY
    }

    data class MaskResult(
        val mask: BooleanArray,
        val foregroundCount: Int,
        val areaFraction: Double,
        val maskMode: MaskMode,
        val bgLab: Triple<Double, Double, Double>,
        val isRejected: Boolean = false,
        val rejectionReason: String? = null
    )

    fun computeMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        thresholdDeltaE: Double = ProvisionalConstants.MASK_DELTA_E_BACKGROUND,
        minAreaFraction: Double = ProvisionalConstants.MASK_MIN_AREA_FRACTION
    ): MaskResult {
        val total = width * height
        require(pixels.size == total) { "Pixel array size (${pixels.size}) does not match dimensions ($width x $height)" }

        // 1. Estimate background model from outer ~4% boundary ring
        val ringThicknessX = max(2, (width * 0.04).roundToInt())
        val ringThicknessY = max(2, (height * 0.04).roundToInt())

        val bgLVals = mutableListOf<Double>()
        val bgAVals = mutableListOf<Double>()
        val bgBVals = mutableListOf<Double>()

        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val isOuterRing = (x < ringThicknessX || x >= width - ringThicknessX ||
                        y < ringThicknessY || y >= height - ringThicknessY)
                if (isOuterRing) {
                    val p = pixels[y * width + x]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val (l, a, bLab) = CieLab.rgbToLab(r, g, b)
                    bgLVals.add(l)
                    bgAVals.add(a)
                    bgBVals.add(bLab)
                }
            }
        }

        fun median(list: MutableList<Double>): Double {
            if (list.isEmpty()) return 0.0
            list.sort()
            val m = list.size / 2
            return if (list.size % 2 == 0) (list[m - 1] + list[m]) / 2.0 else list[m]
        }

        val bgL = median(bgLVals)
        val bgA = median(bgAVals)
        val bgB = median(bgBVals)
        val bgLab = Triple(bgL, bgA, bgB)

        // 2. Initial foreground mask: Delta E76 relative to background
        val rawMask = BooleanArray(total)
        var rawCount = 0

        for (i in 0 until total) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val (l, a, bLab) = CieLab.rgbToLab(r, g, b)
            val dE = CieLab.deltaE76(l, a, bLab, bgL, bgA, bgB)
            if (dE >= thresholdDeltaE) {
                rawMask[i] = true
                rawCount++
            }
        }

        // 3. Find largest connected component via iterative BFS queue (no recursion)
        val visited = BooleanArray(total)
        var maxComponentSize = 0
        var bestSeed = -1

        val queue = IntArray(total)
        for (i in 0 until total) {
            if (rawMask[i] && !visited[i]) {
                var head = 0
                var tail = 0
                queue[tail++] = i
                visited[i] = true
                var currentCompSize = 0

                while (head < tail) {
                    val idx = queue[head++]
                    currentCompSize++
                    val x = idx % width
                    val y = idx / width

                    // 4-neighborhood
                    if (x > 0) {
                        val nIdx = idx - 1
                        if (rawMask[nIdx] && !visited[nIdx]) { visited[nIdx] = true; queue[tail++] = nIdx }
                    }
                    if (x < width - 1) {
                        val nIdx = idx + 1
                        if (rawMask[nIdx] && !visited[nIdx]) { visited[nIdx] = true; queue[tail++] = nIdx }
                    }
                    if (y > 0) {
                        val nIdx = idx - width
                        if (rawMask[nIdx] && !visited[nIdx]) { visited[nIdx] = true; queue[tail++] = nIdx }
                    }
                    if (y < height - 1) {
                        val nIdx = idx + width
                        if (rawMask[nIdx] && !visited[nIdx]) { visited[nIdx] = true; queue[tail++] = nIdx }
                    }
                }

                if (currentCompSize > maxComponentSize) {
                    maxComponentSize = currentCompSize
                    bestSeed = i
                }
            }
        }

        val connectedMask = BooleanArray(total)
        if (bestSeed >= 0) {
            // Re-run BFS for the winning component
            val seedVisited = BooleanArray(total)
            var head = 0
            var tail = 0
            queue[tail++] = bestSeed
            seedVisited[bestSeed] = true
            connectedMask[bestSeed] = true

            while (head < tail) {
                val idx = queue[head++]
                val x = idx % width
                val y = idx / width

                if (x > 0) {
                    val nIdx = idx - 1
                    if (rawMask[nIdx] && !seedVisited[nIdx]) { seedVisited[nIdx] = true; connectedMask[nIdx] = true; queue[tail++] = nIdx }
                }
                if (x < width - 1) {
                    val nIdx = idx + 1
                    if (rawMask[nIdx] && !seedVisited[nIdx]) { seedVisited[nIdx] = true; connectedMask[nIdx] = true; queue[tail++] = nIdx }
                }
                if (y > 0) {
                    val nIdx = idx - width
                    if (rawMask[nIdx] && !seedVisited[nIdx]) { seedVisited[nIdx] = true; connectedMask[nIdx] = true; queue[tail++] = nIdx }
                }
                if (y < height - 1) {
                    val nIdx = idx + width
                    if (rawMask[nIdx] && !seedVisited[nIdx]) { seedVisited[nIdx] = true; connectedMask[nIdx] = true; queue[tail++] = nIdx }
                }
            }
        }

        // 4. Morphological erosion with radius max(3, 3% of short side)
        val shortSide = min(width, height)
        val erosionRadius = max(3, (shortSide * 0.03).roundToInt())
        val erodedMask = BooleanArray(total)
        var erodedCount = 0

        for (y in erosionRadius until height - erosionRadius) {
            for (x in erosionRadius until width - erosionRadius) {
                val idx = y * width + x
                if (!connectedMask[idx]) continue

                var fullyInside = true
                // Check diamond/box neighborhood
                checkLoop@ for (dy in -erosionRadius..erosionRadius) {
                    val ny = y + dy
                    val rowOff = ny * width
                    for (dx in -erosionRadius..erosionRadius) {
                        if (dx * dx + dy * dy <= erosionRadius * erosionRadius) {
                            if (!connectedMask[rowOff + (x + dx)]) {
                                fullyInside = false
                                break@checkLoop
                            }
                        }
                    }
                }
                if (fullyInside) {
                    erodedMask[idx] = true
                    erodedCount++
                }
            }
        }

        val areaFraction = erodedCount.toDouble() / total
        val rawFraction = maxComponentSize.toDouble() / total

        // 1. If eroded mask is valid (>= 10% or >= 5% when raw was >= 10%), use segmented foreground
        if (erodedCount > 0 && (areaFraction >= minAreaFraction || (rawFraction >= minAreaFraction && areaFraction >= 0.05))) {
            return MaskResult(
                mask = erodedMask,
                foregroundCount = erodedCount,
                areaFraction = areaFraction,
                maskMode = MaskMode.BACKGROUND_SEGMENTED,
                bgLab = bgLab,
                isRejected = false
            )
        }

        // 2. Check if sample filled the entire frame (low background distinction)
        val cx = width / 2
        val cy = height / 2
        val centerP = pixels[cy * width + cx]
        val (cL, cA, cB) = CieLab.rgbToLab((centerP shr 16) and 0xFF, (centerP shr 8) and 0xFF, centerP and 0xFF)
        val centerDiffFromRing = CieLab.deltaE76(cL, cA, cB, bgL, bgA, bgB)

        if (centerDiffFromRing < 4.0 && rawCount < total * 0.05) {
            // Whole frame is uniform (e.g. sugar filling entire frame) - use central 60%
            val centerMask = BooleanArray(total)
            var centerCount = 0
            val minX = (width * 0.20).toInt()
            val maxX = (width * 0.80).toInt()
            val minY = (height * 0.20).toInt()
            val maxY = (height * 0.80).toInt()

            for (y in minY until maxY) {
                for (x in minX until maxX) {
                    centerMask[y * width + x] = true
                    centerCount++
                }
            }
            return MaskResult(
                mask = centerMask,
                foregroundCount = centerCount,
                areaFraction = centerCount.toDouble() / total,
                maskMode = MaskMode.FULL_FRAME_CENTER,
                bgLab = bgLab,
                isRejected = false
            )
        }

        return MaskResult(
            mask = erodedMask,
            foregroundCount = erodedCount,
            areaFraction = areaFraction,
            maskMode = MaskMode.REJECTED_EMPTY,
            bgLab = bgLab,
            isRejected = true,
            rejectionReason = "Sample not detected (mask area ${(areaFraction * 100).roundToInt()}% < 10% threshold)"
        )

        return MaskResult(
            mask = erodedMask,
            foregroundCount = erodedCount,
            areaFraction = areaFraction,
            maskMode = MaskMode.BACKGROUND_SEGMENTED,
            bgLab = bgLab,
            isRejected = false
        )
    }
}
