package com.example.ui.screens.calibration

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CalibrationProfileEntity
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCalibrationScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val profiles by viewModel.calibrationProfiles.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"

    var manualExposure by remember { mutableStateOf(0f) } // -2.0 to +2.0 EV
    var manualFocus by remember { mutableStateOf(50f) } // 10mm to 200mm
    var manualWb by remember { mutableStateOf(5500f) } // 2800K to 8000K
    var brightnessOffset by remember { mutableStateOf(0f) } // -50 to +50

    var isCalibrating by remember { mutableStateOf(false) }
    var activeProfile by remember { mutableStateOf<CalibrationProfileEntity?>(profiles.firstOrNull()) }

    val currentBmp by viewModel.currentBitmap.collectAsState()

    // Live Optical Evaluation function
    fun evaluateOptics(): CalibrationProfileEntity {
        val bmp = currentBmp
        val scores = if (bmp != null) {
            val w = bmp.width
            val h = bmp.height
            val total = w * h
            val pixels = IntArray(total)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)

            var sumR = 0.0; var sumG = 0.0; var sumB = 0.0
            var clipped = 0
            var sumGray = 0.0

            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sumR += r; sumG += g; sumB += b
                if (r < 5 || r > 250 || g < 5 || g > 250 || b < 5 || b > 250) clipped++
                val gray = 0.299 * r + 0.587 * g + 0.114 * b
                sumGray += gray
            }

            val meanR = sumR / total
            val meanG = sumG / total
            val meanB = sumB / total
            val meanGray = (sumGray / total) + brightnessOffset

            val wbDiff = kotlin.math.abs(meanR - meanG) + kotlin.math.abs(meanG - meanB) + kotlin.math.abs(meanR - meanB)
            val baseWb = (100.0 - (wbDiff / 3.0) - kotlin.math.abs(manualWb - 5500.0) / 100.0).coerceIn(50.0, 99.0)

            val baseExp = (100.0 - (clipped.toDouble() / total) * 200.0 - kotlin.math.abs(manualExposure) * 10.0).coerceIn(50.0, 99.0)
            val baseBright = (100.0 - (kotlin.math.abs(meanGray - 128.0) / 128.0) * 80.0).coerceIn(50.0, 99.0)

            var varSum = 0.0
            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val gray = 0.299 * r + 0.587 * g + 0.114 * b
                val diff = gray - meanGray
                varSum += diff * diff
            }
            val stdDev = kotlin.math.sqrt(varSum / total)
            val baseCont = ((stdDev / 64.0) * 100.0).coerceIn(10.0, 100.0)

            // Dynamic Laplacian sharpness calculation (replicated borders)
            val qEval = com.example.cv.QualityPolicy.evaluate(pixels, w, h)
            val baseSharp = (qEval.blurScore * 3.0).coerceIn(10.0, 100.0)
            val baseFocus = (100.0 - kotlin.math.abs(manualFocus - 50.0) * 0.5).coerceIn(20.0, 100.0)

            listOf(baseWb, baseExp, baseBright, baseCont, baseSharp, baseFocus)
        } else {
            val baseWb = (100.0 - kotlin.math.abs(manualWb - 5500.0) / 60.0).coerceIn(20.0, 100.0)
            val baseExp = (100.0 - kotlin.math.abs(manualExposure) * 12.0).coerceIn(20.0, 100.0)
            val baseBright = (100.0 - kotlin.math.abs(brightnessOffset) * 0.8).coerceIn(20.0, 100.0)
            val baseCont = 80.0
            val baseSharp = 75.0
            val baseFocus = (100.0 - kotlin.math.abs(manualFocus - 50.0) * 0.5).coerceIn(20.0, 100.0)
            listOf(baseWb, baseExp, baseBright, baseCont, baseSharp, baseFocus)
        }

        val wbScore = scores[0]
        val expScore = scores[1]
        val brightScore = scores[2]
        val contScore = scores[3]
        val sharpScore = scores[4]
        val focScore = scores[5]

        val overall = (wbScore * 0.25 + expScore * 0.20 + brightScore * 0.15 + contScore * 0.15 + sharpScore * 0.15 + focScore * 0.10).coerceIn(10.0, 100.0)

        return CalibrationProfileEntity(
            profileName = if (bmp != null) "Camera Live Analysis Profile" else "Manual Control Target Profile",
            overallScore = overall,
            whiteBalanceScore = wbScore,
            exposureScore = expScore,
            brightnessScore = brightScore,
            contrastScore = contScore,
            sharpnessScore = sharpScore,
            focusScore = focScore,
            status = if (overall >= 75) "Optimal" else "Sub-optimal"
        )
    }

    // Dynamic Live Profile based on sliders or auto run
    val currentProfile = remember(manualExposure, manualFocus, manualWb, brightnessOffset, activeProfile, currentBmp) {
        activeProfile ?: evaluateOptics()
    }

    fun runAutoCalibration() {
        isCalibrating = true
        // Auto reset manual sliders to optimal laboratory defaults
        manualExposure = 0.0f
        manualWb = 5500.0f
        manualFocus = 50.0f
        brightnessOffset = 0.0f

        val evaluated = evaluateOptics()
        viewModel.saveCalibration(evaluated)
        activeProfile = evaluated
        isCalibrating = false

        val msg = if (isPersian) "کالیبراسیون نوری بر اساس تصویر دوربین محاسبه و ذخیره شد" else "Camera optics calibration evaluated & saved"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "راهنمای ارزیابی کیفیت تصویر و نور" else "Optical Image Quality & Lighting Assessment") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Overall Calibration Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentProfile.status == "Optimal") Color(0xFF064E3B) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isPersian) "وضعیت نوری و اپتیکی:" else "Current Optics Status:", fontSize = 12.sp, color = Color.LightGray)
                            Badge(containerColor = if (currentProfile.status == "Optimal") Color(0xFF10B981) else Color(0xFFF59E0B)) {
                                Text(if (currentProfile.status == "Optimal") (if (isPersian) "مناسب" else "Optimal") else (if (isPersian) "نیازمند تنظیم" else "Sub-optimal"), modifier = Modifier.padding(2.dp))
                            }
                        }

                        Text(
                            text = "${if (isPersian) "امتیاز ارزیابی نوری:" else "Optics Assessment Score:"} ${currentProfile.overallScore.toInt()}%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        // Live Recommendation logic
                        val recText = when {
                            currentProfile.overallScore >= 80 -> if (isPersian) "نور و وضوح تصویر مناسب است. آماده مقایسه رنگ." else "Optimal optical lighting. Ready for color comparison."
                            currentProfile.whiteBalanceScore < 75 -> if (isPersian) "تعادل سفیدی نامتوازن است. دمای رنگ را روی 5500K تنظیم کنید." else "White balance imbalance detected. Set color temperature to 5500K."
                            currentProfile.exposureScore < 75 -> if (isPersian) "تصویر اشباع یا تاریک است. تنظیمات نور را اصلاح کنید." else "Exposure clipped or underexposed. Adjust lighting."
                            else -> if (isPersian) "ارزیابی قابل قبول است." else "Acceptable optical conditions."
                        }

                        Text(
                            text = recText,
                            fontSize = 12.sp,
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = { runAutoCalibration() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isCalibrating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isPersian) "ارزیابی و تنظیم اتوماتیک با دوربین" else "Run Automatic Camera Check")
                            }
                        }
                    }
                }
            }

            // 2. Optical Component Metrics Breakdown
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(if (isPersian) "شاخص‌های نوری تصویر دوربین" else "Optical Component Scores", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        ScoreBar(if (isPersian) "تعادل سفیدی (White Balance)" else "White Balance", currentProfile.whiteBalanceScore)
                        ScoreBar(if (isPersian) "موج منفی نورگیری (Exposure)" else "Exposure", currentProfile.exposureScore)
                        ScoreBar(if (isPersian) "روشنایی میانگین (Brightness)" else "Brightness", currentProfile.brightnessScore)
                        ScoreBar(if (isPersian) "کنتراست (Contrast)" else "Contrast", currentProfile.contrastScore)
                        ScoreBar(if (isPersian) "وضوح (Sharpness)" else "Sharpness", currentProfile.sharpnessScore)
                        ScoreBar(if (isPersian) "دقت فوکوس (Focus Accuracy)" else "Focus Accuracy", currentProfile.focusScore)
                    }
                }
            }

            // 3. Manual Calibration Sliders (حالت دستی)
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isPersian) "کنترل و تنظیمات دستی (Manual Controls)" else "Manual Optics Controls", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            TextButton(onClick = {
                                manualExposure = 0.0f
                                manualFocus = 50.0f
                                manualWb = 5500.0f
                                brightnessOffset = 0.0f
                            }) {
                                Text(if (isPersian) "بازنشانی" else "Reset", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("${if (isPersian) "جبران نورگیری" else "Exposure Bias"} (${String.format("%.1f", manualExposure)} EV)", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = manualExposure,
                            onValueChange = {
                                manualExposure = it
                                activeProfile = null
                            },
                            valueRange = -2f..2f
                        )

                        Text("${if (isPersian) "دمای تعادل سفیدی" else "White Balance Temp"} (${manualWb.toInt()} K)", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = manualWb,
                            onValueChange = {
                                manualWb = it
                                activeProfile = null
                            },
                            valueRange = 2800f..8000f
                        )

                        Text("${if (isPersian) "فاصله فوکوس دستی" else "Manual Focus Target"} (${manualFocus.toInt()} mm)", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = manualFocus,
                            onValueChange = {
                                manualFocus = it
                                activeProfile = null
                            },
                            valueRange = 10f..200f
                        )

                        Text("${if (isPersian) "تصحیح روشنایی" else "Brightness Offset"} (${brightnessOffset.toInt()})", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = brightnessOffset,
                            onValueChange = {
                                brightnessOffset = it
                                activeProfile = null
                            },
                            valueRange = -50f..50f
                        )
                    }
                }
            }

            // 4. Saved Profiles Log
            item {
                Text(if (isPersian) "تاریخچه پروفایل‌های کالیبراسیون:" else "Calibration Profile Logs:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            items(profiles) { log ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { activeProfile = log }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(log.profileName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Device: ${log.deviceModel}", fontSize = 11.sp, color = Color.Gray)
                        }
                        Text("${log.overallScore.toInt()}%", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(label: String, score: Double) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp)
            Text("${score.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = (score / 100.0).toFloat().coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                score >= 85 -> Color(0xFF10B981)
                score >= 70 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
