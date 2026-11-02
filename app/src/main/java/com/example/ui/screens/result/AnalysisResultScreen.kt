package com.example.ui.screens.result

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cv.ClassificationResult
import com.example.cv.ConfidenceLevel
import com.example.data.export.ExportManager
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    viewModel: MainViewModel,
    onNavigateToAdvanced: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val classification by viewModel.classificationResult.collectAsState()
    val pipelineOutput by viewModel.pipelineOutput.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"
    val exportManager = remember { ExportManager(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "نتیجه آنالیز" else "Analysis Result") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        classification?.let {
                            val pdf = exportManager.exportToPdf(it)
                            Toast.makeText(context, "PDF saved: ${pdf.name}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                }
            )
        }
    ) { padding ->
        if (classification == null || pipelineOutput == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (isPersian) "هیچ نتیجه‌ای موجود نیست" else "No active analysis result")
            }
            return@Scaffold
        }

        val result = classification!!
        val output = pipelineOutput!!

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Dedicated ICUMSA Estimation Card (LAB 3D Interpolation)
            val est = result.icumsaEstimation
            if (est != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                est.isOutOfRange -> Color(0xFF451A1A)
                                est.isUnknown -> Color(0xFF422006)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isPersian) "تخمین عددی ICUMSA (درونیابی در فضای LAB)" else "ICUMSA Estimation (LAB 3D Space)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Badge(
                                    containerColor = when {
                                        est.isOutOfRange -> Color(0xFFEF4444)
                                        est.isUnknown -> Color(0xFFF59E0B)
                                        else -> Color(0xFF10B981)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            est.isOutOfRange -> if (isPersian) "خارج از محدوده مراجع" else "Out of Range"
                                            est.isUnknown -> if (isPersian) "نمونه ناشناخته" else "Unknown Sample"
                                            else -> if (isPersian) "در محدوده مراجع" else "In Reference Range"
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (est.isOutOfRange) {
                                Text(
                                    text = "OUT OF RANGE",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = est.outOfRangeReason ?: "نمونه خارج از طیف ۶ نمونه مرجع قرار دارد",
                                    fontSize = 13.sp,
                                    color = Color(0xFFFCA5A5)
                                )
                            } else if (est.isUnknown) {
                                Text(
                                    text = "UNKNOWN SAMPLE",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFF59E0B)
                                )
                                Text(
                                    text = "فاصله رنگی نمونه از مسیر مرجع (${String.format("%.2f", est.distanceToPath)}) بیش از حد آستانه آزمایشگاه است.",
                                    fontSize = 13.sp,
                                    color = Color(0xFFFDE68A)
                                )
                            } else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format("%.1f", est.estimatedIcumsa),
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ICUMSA (IU)",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Reference interval card & interpolation progress
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "مرجع ${est.selectedRef1.sampleId} (${est.selectedRef1.icumsaValue} IU)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "موقعیت t: ${String.format("%.1f", est.relativePositionT * 100)}%",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "مرجع ${est.selectedRef2.sampleId} (${est.selectedRef2.icumsaValue} IU)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { est.relativePositionT.toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.DarkGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Median LAB: L*=${String.format("%.1f", est.sampleLabMedian.medianL)}, a*=${String.format("%.1f", est.sampleLabMedian.medianA)}, b*=${String.format("%.1f", est.sampleLabMedian.medianB)}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "فاصله از مسیر: ${String.format("%.2f", est.distanceToPath)}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ نکته علمی: تخمین ICUMSA با درونیابی در فضای CIE-LAB انجام شده و دقت آن باید با نمونه‌های دارای برگه آزمایشگاهی اعتبارسنجی شود.",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 1. Primary Classification Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.isUnknown) Color(0xFF7F1D1D) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPersian) "شناسایی نزدیک‌ترین مرجع (بردار ۱۹۱ بعد):" else "Nearest Reference (191-dim Vector):",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Badge(
                                containerColor = when (result.confidenceLevel) {
                                    ConfidenceLevel.VERY_HIGH -> Color(0xFF10B981)
                                    ConfidenceLevel.HIGH -> Color(0xFF0284C7)
                                    ConfidenceLevel.MEDIUM -> Color(0xFFF59E0B)
                                    ConfidenceLevel.LOW -> Color(0xFFEF4444)
                                }
                            ) {
                                Text(result.confidenceLevel.name, modifier = Modifier.padding(2.dp))
                            }
                        }

                        Text(
                            text = result.nearestSampleName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Combined Match: ${String.format("%.1f", result.confidenceScorePct)}%",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Time: ${result.processingTimeMs} ms",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 2. Captured & Processed Image Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Original Image", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Image(
                            bitmap = output.originalBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Processed 512x512", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Image(
                            bitmap = output.processedBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            // 3. Similarity Metrics Table
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isPersian) "جدول مقایسه شباهت‌ها:" else "Similarity Metrics Breakdown:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        for (match in result.allMatches) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text(match.sampleName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("ICUMSA: ${match.icumsaValue}", fontSize = 10.sp, color = Color.Gray)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text("Euc: ${String.format("%.1f", match.euclideanSimilarityPct)}%", fontSize = 11.sp)
                                    Text("Cos: ${String.format("%.1f", match.cosineSimilarityPct)}%", fontSize = 11.sp)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${String.format("%.1f", match.finalSimilarityPct)}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (match.sampleId == result.nearestSampleId) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                            }
                            Divider(color = Color.DarkGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }

            // 4. Dedicated Advanced Analysis Center Button
            item {
                Button(
                    onClick = onNavigateToAdvanced,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isPersian) "ورود به مرکز آنالیز پیشرفته" else "Open Advanced Analysis Center",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5. Quick Export Actions
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isPersian) "ذخیره و خروجی گزارش‌ها (در پوشه دانلودها):" else "Export & Save Reports (In Downloads):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val pdf = exportManager.exportToPdf(result)
                                Toast.makeText(context, if (isPersian) "فایل PDF در دانلودها ذخیره شد" else "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                                exportManager.openFile(pdf, "application/pdf")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "گزارش PDF" else "PDF Report", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val excel = exportManager.exportToExcel(listOf(result))
                                Toast.makeText(context, if (isPersian) "فایل اکسل در دانلودها ذخیره شد" else "Excel saved to Downloads", Toast.LENGTH_SHORT).show()
                                exportManager.openFile(excel, "text/csv")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "خروجی اکسل" else "Excel Export", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val pdf = exportManager.exportToPdf(result)
                                exportManager.shareFile(pdf, "application/pdf")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPersian) "اشتراک" else "Share", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
