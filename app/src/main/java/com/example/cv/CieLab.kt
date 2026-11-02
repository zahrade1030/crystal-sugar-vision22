package com.example.cv

import kotlin.math.*

/**
 * Pure Kotlin sRGB <-> CIELAB (D65) color space conversion.
 * Completely decoupled from Android Bitmap/Graphics APIs to allow JVM unit testing.
 * Reference White Point: D65 (Xn=95.047, Yn=100.000, Zn=108.883)
 */
object CieLab {

    const val REF_X = 95.047
    const val REF_Y = 100.000
    const val REF_Z = 108.883

    /**
     * Converts an 8-bit sRGB triplet to CIELAB (L*, a*, b*) under D65 illuminant.
     */
    fun rgbToLab(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val (x, y, z) = rgbToXyz(r, g, b)
        return xyzToLab(x, y, z)
    }

    /**
     * Converts sRGB [0..255] to CIE XYZ [0..100] with sRGB companding inverse (gamma).
     */
    fun rgbToXyz(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        var vr = r.coerceIn(0, 255) / 255.0
        var vg = g.coerceIn(0, 255) / 255.0
        var vb = b.coerceIn(0, 255) / 255.0

        vr = if (vr > 0.04045) ((vr + 0.055) / 1.055).pow(2.4) else vr / 12.92
        vg = if (vg > 0.04045) ((vg + 0.055) / 1.055).pow(2.4) else vg / 12.92
        vb = if (vb > 0.04045) ((vb + 0.055) / 1.055).pow(2.4) else vb / 12.92

        vr *= 100.0
        vg *= 100.0
        vb *= 100.0

        // Standard sRGB to XYZ matrix (D65)
        val x = vr * 0.4124564 + vg * 0.3575761 + vb * 0.1804375
        val y = vr * 0.2126729 + vg * 0.7151522 + vb * 0.0721750
        val z = vr * 0.0193339 + vg * 0.1191920 + vb * 0.9503041

        return Triple(x, y, z)
    }

    /**
     * Converts CIE XYZ [0..100] to CIELAB (L*, a*, b*).
     */
    fun xyzToLab(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        var vx = x / REF_X
        var vy = y / REF_Y
        var vz = z / REF_Z

        val epsilon = 216.0 / 24389.0 // 0.008856
        val kappa = 24389.0 / 27.0     // 903.3

        vx = if (vx > epsilon) cbrt(vx) else (kappa * vx + 16.0) / 116.0
        vy = if (vy > epsilon) cbrt(vy) else (kappa * vy + 16.0) / 116.0
        vz = if (vz > epsilon) cbrt(vz) else (kappa * vz + 16.0) / 116.0

        val l = (116.0 * vy) - 16.0
        val a = 500.0 * (vx - vy)
        val b = 200.0 * (vy - vz)

        return Triple(l, a, b)
    }

    /**
     * Converts CIELAB back to sRGB [0..255] for display or rendering purposes.
     */
    fun labToRgb(l: Double, a: Double, b: Double): Triple<Int, Int, Int> {
        val vy = (l + 16.0) / 116.0
        val vx = a / 500.0 + vy
        val vz = vy - b / 200.0

        val epsilon = 216.0 / 24389.0
        val kappa = 24389.0 / 27.0

        val x = (if (vx.pow(3.0) > epsilon) vx.pow(3.0) else (116.0 * vx - 16.0) / kappa) * REF_X
        val y = (if (l > (kappa * epsilon)) ((l + 16.0) / 116.0).pow(3.0) else l / kappa) * REF_Y
        val z = (if (vz.pow(3.0) > epsilon) vz.pow(3.0) else (116.0 * vz - 16.0) / kappa) * REF_Z

        var vr = (x * 3.2404542 + y * -1.5371385 + z * -0.4985314) / 100.0
        var vg = (x * -0.9692660 + y * 1.8760108 + z * 0.0415560) / 100.0
        var vb = (x * 0.0556434 + y * -0.2040259 + z * 1.0572252) / 100.0

        vr = if (vr > 0.0031308) 1.055 * vr.pow(1.0 / 2.4) - 0.055 else 12.92 * vr
        vg = if (vg > 0.0031308) 1.055 * vg.pow(1.0 / 2.4) - 0.055 else 12.92 * vg
        vb = if (vb > 0.0031308) 1.055 * vb.pow(1.0 / 2.4) - 0.055 else 12.92 * vb

        val ir = (vr * 255.0).roundToInt().coerceIn(0, 255)
        val ig = (vg * 255.0).roundToInt().coerceIn(0, 255)
        val ib = (vb * 255.0).roundToInt().coerceIn(0, 255)

        return Triple(ir, ig, ib)
    }

    /**
     * Euclidean Delta E (CIE76)
     */
    fun deltaE76(l1: Double, a1: Double, b1: Double, l2: Double, a2: Double, b2: Double): Double {
        val dl = l1 - l2
        val da = a1 - a2
        val db = b1 - b2
        return sqrt(dl * dl + da * da + db * db)
    }
}
