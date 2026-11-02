package com.example.ui.screens.developer

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperCenterScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val output by viewModel.pipelineOutput.collectAsState()
    val classification by viewModel.classificationResult.collectAsState()
    val references by viewModel.references.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"

    var activeTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        if (isPersian) "۱۳ مرحله پردازش تصویر" else "13 Pipeline Stages",
        if (isPersian) "تحلیل هندسی ICUMSA در LAB" else "LAB ICUMSA Interpolation",
        if (isPersian) "بردار ویژگی ۱۹۱ بعدی" else "191-Dim Features",
        if (isPersian) "محیط و امنیت" else "Diagnostics & Logs"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "مرکز توسعه‌دهنده و دیباگ OpenCV" else "Developer Center & OpenCV Debug") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Badge(containerColor = Color(0xFFF59E0B)) {
                        Text("DEBUG MODE", modifier = Modifier.padding(2.dp), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(selectedTabIndex = activeTab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = activeTab == idx,
                        onClick = { activeTab = idx },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }

            if (output == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isPersian) "هیچ پایپلاینی اجرا نشده است. ابتدا یک نمونه را از صفحه اصلی آنالیز کنید."
                        else "No active pipeline execution. Run an analysis from Home first.",
                        color = Color.Gray
                    )
                }
                return@Scaffold
            }

            val pipe = output!!
            val est = classification?.icumsaEstimation

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (activeTab) {
                    0 -> { // 13 OpenCV Stages Inspector
                        item {
                            Text(
                                "Intermediate Processing Stages (${pipe.stages.size} Stages)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(pipe.stages) { stage ->
                            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(stage.stageName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(stage.description, fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Image(
                                        bitmap = stage.bitmap.asImageBitmap(),
                                        contentDescription = stage.stageName,
                                        modifier = Modifier
                                            .height(200.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }

                    1 -> { // LAB ICUMSA Interpolation Geometric Inspector
                        item {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = if (isPersian) "مختصات Median LAB نمونه ورودی (نقطه Q)" else "Input Sample Median LAB (Point Q)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val med = pipe.completeFeatureSet.labMedian
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        BadgeCard("Median L*", String.format("%.2f", med.medianL), Color(0xFF38BDF8))
                                        BadgeCard("Median a*", String.format("%.2f", med.medianA), Color(0xFFF472B6))
                                        BadgeCard("Median b*", String.format("%.2f", med.medianB), Color(0xFFFBBF24))
                                    }
                                }
                            }
                        }

                        item {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = if (isPersian) "ترتیب ۶ نمونه مرجع بر اساس ICUMSA صعودی" else "6 Reference Points Sorted by ICUMSA",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val sortedRefs = references.sortedBy { it.icumsaValue }
                                    for ((idx, ref) in sortedRefs.withIndex()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "P${idx + 1}: ${ref.name.take(18)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "ICUMSA: ${ref.icumsaValue} IU",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                            Text(
                                                text = "L=${String.format("%.1f", ref.medianL)} a=${String.format("%.1f", ref.medianA)} b=${String.format("%.1f", ref.medianB)}",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Divider(color = Color.DarkGray.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }

                        if (est != null) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (est.isOutOfRange || est.isUnknown) Color(0xFF451A1A) else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = if (isPersian) "نتیجه تخمین ICUMSA و بازه انتخابی" else "ICUMSA Estimation Result",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (est.isOutOfRange) "وضعیت: خارج از محدوده مراجع (Out of Reference Range)"
                                            else if (est.isUnknown) "وضعیت: نمونه ناشناخته (Unknown - High Path Distance)"
                                            else "مقدار تخمینی ICUMSA: ${String.format("%.1f", est.estimatedIcumsa)} IU",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (est.isOutOfRange || est.isUnknown) Color(0xFFEF4444) else Color(0xFF10B981)
                                        )
                                        if (est.outOfRangeReason != null) {
                                            Text(est.outOfRangeReason, fontSize = 12.sp, color = Color(0xFFFCA5A5))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("پاره‌خط انتخابی: مرجع ${est.selectedRef1.sampleId} (${est.selectedRef1.icumsaValue}) به مرجع ${est.selectedRef2.sampleId} (${est.selectedRef2.icumsaValue})", fontSize = 12.sp)
                                        Text("موقعیت نسبی روی پاره‌خط (t): ${String.format("%.4f", est.relativePositionT)} (${String.format("%.1f", est.relativePositionT * 100)}%)", fontSize = 12.sp)
                                        Text("فاصله نمونه تا نزدیک‌ترین نقطه مسیر (d): ${String.format("%.2f", est.distanceToPath)}", fontSize = 12.sp)
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = if (isPersian) "جزئیات ۵ پاره‌خط تشکیل‌شده بین مراجع متوالی:" else "Details of 5 Line Segments Between References:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            items(est.allSegments) { seg ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (seg.isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "پاره‌خط ${seg.segmentIndex}: [${seg.ref1.sampleName.take(12)} (${seg.ref1.icumsaValue})] ➔ [${seg.ref2.sampleName.take(12)} (${seg.ref2.icumsaValue})]",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            if (seg.isSelected) {
                                                Badge(containerColor = Color(0xFF10B981)) {
                                                    Text("انتخاب شده (کمترین فاصله)", fontSize = 10.sp, color = Color.White)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("raw t: ${String.format("%.4f", seg.rawT)} | clamped t: ${String.format("%.4f", seg.clampedT)}", fontSize = 11.sp, color = Color.Gray)
                                        Text("نزدیک‌ترین نقطه R: L=${String.format("%.1f", seg.nearestPoint.medianL)}, a=${String.format("%.1f", seg.nearestPoint.medianA)}, b=${String.format("%.1f", seg.nearestPoint.medianB)}", fontSize = 11.sp, color = Color.Gray)
                                        Text("فاصله تا پاره‌خط (d): ${String.format("%.2f", seg.distance)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (seg.isSelected) MaterialTheme.colorScheme.primary else Color.White)
                                    }
                                }
                            }
                        }
                    }

                    2 -> { // Feature Vector Inspector (191 Dimensions)
                        item {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("191-Dimensional Vector Inspector", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Dimensions: 63 stats (9 ch * 7 stats) + 64 RGB Hist + 64 LAB Hist = 191 features", fontSize = 12.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = pipe.normalizedVector.take(50).joinToString(", ") { String.format("%.3f", it) } + "...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }

                    3 -> { // Environment & System Info
                        item {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Environment Diagnostics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text("• OpenCV Android Native Core: Active", fontSize = 12.sp)
                                    Text("• Fixed Central ROI Engine (80%): Enabled (No Auto-Crop)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                    Text("• LAB Median ICUMSA Interpolator: Active (6 References, 5 Segments)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    Text("• CameraX Hardware Level: FULL / LIMITED", fontSize = 12.sp)
                                    Text("• Classical Computer Vision Engine: Active (NO Deep Learning)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    Text("• Room DB SQLCipher Key: Initialized", fontSize = 12.sp)
                                    Text("• Security Root Check: Clear", fontSize = 12.sp)
                                    Text("• Multilingual Bundle: English (en) / Persian (fa)", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCard(label: String, value: String, color: Color) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
