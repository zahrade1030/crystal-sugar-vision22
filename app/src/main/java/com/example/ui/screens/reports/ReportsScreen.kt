package com.example.ui.screens.reports

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.export.ExportManager
import com.example.ui.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val historyList by viewModel.history.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"
    val exportManager = remember { ExportManager(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "گزارش‌های آماری و خروجی اکسل" else "Laboratory Statistical Reports & Export") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isPersian) "خلاصه آمار کنترل کیفیت شکر" else "Sugar Quality Control Summary",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val count = historyList.size
                        val avgScore = if (count > 0) historyList.map { it.confidenceScorePct }.average() else 0.0
                        val avgIcumsa = if (count > 0) historyList.map { it.estimatedIcumsa }.average() else 0.0

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isPersian) "تعداد کل نمونه‌های آنالیزشده:" else "Total Samples Analyzed:", fontSize = 12.sp, color = Color.Gray)
                            Text("$count", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isPersian) "میانگین ICUMSA تخمینی:" else "Mean Estimated ICUMSA:", fontSize = 12.sp, color = Color.Gray)
                            Text(String.format(Locale.US, "%.1f", avgIcumsa), fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isPersian) "میانگین درصد اطمینان تطابق:" else "Mean Confidence Score:", fontSize = 12.sp, color = Color.Gray)
                            Text("${String.format(Locale.US, "%.1f", avgScore)}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Export Actions
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isPersian) "خروجی جامع سوابق به فرمت‌های استاندارد:" else "Comprehensive Export Formats (UTF-8 BOM):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Button(
                            onClick = {
                                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                if (!downloads.exists()) downloads.mkdirs()
                                val file = File(downloads, "All_Sugar_Analyses_History_${System.currentTimeMillis()}.csv")
                                val sb = StringBuilder("\uFEFF") // UTF-8 BOM
                                sb.append("تاریخ,ساعت,تخمین ICUMSA,نزدیکترین مرجع,درصد اطمینان,سطح اطمینان,پارهخط درونیابی,L* میانه,a* میانه,b* میانه,کیفیت تصویر,اپراتور\n")
                                for (h in historyList) {
                                    sb.append("${h.dateFormatted},${h.timeFormatted},${String.format(Locale.US, "%.2f", h.estimatedIcumsa)},\"${h.nearestReferenceName}\",${String.format(Locale.US, "%.1f", h.confidenceScorePct)},${h.confidenceLevel},#${h.segmentRef1Id}->#${h.segmentRef2Id},${String.format(Locale.US, "%.2f", h.medianL)},${String.format(Locale.US, "%.2f", h.medianA)},${String.format(Locale.US, "%.2f", h.medianB)},${h.qualityScore.toInt()}%,${h.operatorName}\n")
                                }
                                file.writeText(sb.toString(), Charsets.UTF_8)
                                Toast.makeText(context, if (isPersian) "فایل CSV تاریخچه در دانلودها ذخیره شد" else "CSV history exported to Downloads", Toast.LENGTH_SHORT).show()
                                exportManager.openFile(file, "text/csv")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPersian) "خروجی کامل تاریخچه اکسل (CSV با UTF-8 BOM)" else "Export All History to Excel/CSV (UTF-8 BOM)")
                        }

                        Button(
                            onClick = {
                                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                if (!downloads.exists()) downloads.mkdirs()
                                val file = File(downloads, "Sugar_Summary_Report_${System.currentTimeMillis()}.csv")
                                val sb = StringBuilder("\uFEFF")
                                sb.append("شاخص,مقدار\n")
                                sb.append("تعداد کل نمونه‌ها,${historyList.size}\n")
                                val avgIcumsa = if (historyList.isNotEmpty()) historyList.map { it.estimatedIcumsa }.average() else 0.0
                                val avgConf = if (historyList.isNotEmpty()) historyList.map { it.confidenceScorePct }.average() else 0.0
                                sb.append("میانگین ICUMSA,${String.format(Locale.US, "%.2f", avgIcumsa)}\n")
                                sb.append("میانگین درصد اطمینان,${String.format(Locale.US, "%.2f", avgConf)}%\n")
                                sb.append("تاریخ گزارش,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
                                file.writeText(sb.toString(), Charsets.UTF_8)
                                Toast.makeText(context, if (isPersian) "گزارش خلاصه در دانلودها ذخیره شد" else "Summary report saved to Downloads", Toast.LENGTH_SHORT).show()
                                exportManager.openFile(file, "text/csv")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPersian) "خروجی گزارش آماری آزمایشگاه (CSV)" else "Export Statistical Summary (CSV)")
                        }
                    }
                }
            }
        }
    }
}
