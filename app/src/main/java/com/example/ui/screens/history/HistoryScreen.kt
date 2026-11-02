package com.example.ui.screens.history

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AnalysisHistoryEntity
import com.example.data.export.ExportManager
import com.example.ui.MainViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val historyList by viewModel.history.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"
    val exportManager = remember { ExportManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var filterFavoritesOnly by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var selectedRecordForComparison by remember { mutableStateOf<AnalysisHistoryEntity?>(null) }

    val filteredList = historyList.filter { record ->
        (!filterFavoritesOnly || record.isFavorite) &&
        (searchQuery.isEmpty() || record.nearestReferenceName.contains(searchQuery, ignoreCase = true) || record.dateFormatted.contains(searchQuery))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "تاریخچه آنالیزها (${historyList.size})" else "Analysis History (${historyList.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteAllConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Filter bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (isPersian) "جستجو در تاریخچه..." else "Search history...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                FilterChip(
                    selected = filterFavoritesOnly,
                    onClick = { filterFavoritesOnly = !filterFavoritesOnly },
                    label = { Text("Favorites") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (isPersian) "هیچ رکوردی یافت نشد" else "No history records found", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList) { record ->
                        HistoryCard(
                            record = record,
                            isPersian = isPersian,
                            onClick = { selectedRecordForComparison = record },
                            onToggleFavorite = { viewModel.toggleFavorite(record.id, !record.isFavorite) },
                            onDelete = { viewModel.deleteHistoryRecord(record.id) }
                        )
                    }
                }
            }
        }

        // Comparison Screen Modal Dialog
        selectedRecordForComparison?.let { record ->
            ComparisonDialog(
                record = record,
                isPersian = isPersian,
                onDismiss = { selectedRecordForComparison = null }
            )
        }

        if (showDeleteAllConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAllConfirm = false },
                title = { Text("Delete All History?") },
                text = { Text("Are you sure you want to permanently clear all saved analysis history records?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearHistory()
                            showDeleteAllConfirm = false
                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Delete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryCard(
    record: AnalysisHistoryEntity,
    isPersian: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val capturedBmp = remember(record.imagePath) {
        record.imagePath?.let { path ->
            if (File(path).exists()) BitmapFactory.decodeFile(path) else null
        }
    }
    val refBmp = remember(record.matchedImagePath) {
        record.matchedImagePath?.let { path ->
            if (File(path).exists()) BitmapFactory.decodeFile(path) else null
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${record.dateFormatted}  ${record.timeFormatted}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = record.algorithmVersion,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (record.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (record.isFavorite) Color(0xFFF59E0B) else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Images Thumbnail Comparison Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedBmp != null) {
                            Image(
                                bitmap = capturedBmp.asImageBitmap(),
                                contentDescription = "Captured Sample",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.LightGray)
                        }
                    }

                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = "vs",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (refBmp != null) {
                            Image(
                                bitmap = refBmp.asImageBitmap(),
                                contentDescription = "Reference",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.LightGray)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ICUMSA: ${String.format(Locale.US, "%.1f", record.estimatedIcumsa)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "${record.nearestReferenceName} (مرجع #${record.nearestReferenceId})",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (record.isUnknown) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "اطمینان: ${String.format(Locale.US, "%.1f", record.confidenceScorePct)}% | پارهخط #${record.segmentRef1Id}→#${record.segmentRef2Id}",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = "L*=${String.format(Locale.US, "%.1f", record.medianL)}, a*=${String.format(Locale.US, "%.1f", record.medianA)}, b*=${String.format(Locale.US, "%.1f", record.medianB)}",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonDialog(
    record: AnalysisHistoryEntity,
    isPersian: Boolean,
    onDismiss: () -> Unit
) {
    val capturedBmp = remember(record.imagePath) {
        record.imagePath?.let { path ->
            if (File(path).exists()) BitmapFactory.decodeFile(path) else null
        }
    }
    val refBmp = remember(record.matchedImagePath) {
        record.matchedImagePath?.let { path ->
            if (File(path).exists()) BitmapFactory.decodeFile(path) else null
        }
    }

    var isZoomed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isPersian) "مقایسه تصویری و آمار آنالیز" else "Image & Metric Comparison",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Side-by-side Images
                Text(
                    if (isPersian) "تصویر ثبت‌شده (چپ) vs نمونه مرجع (راست)" else "Captured Sample (LEFT) vs Matched Reference (RIGHT)",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left: Captured
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Captured Image", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .height(130.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .clickable { isZoomed = !isZoomed },
                            contentAlignment = Alignment.Center
                        ) {
                            if (capturedBmp != null) {
                                Image(
                                    bitmap = capturedBmp.asImageBitmap(),
                                    contentDescription = "Captured Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("No Image", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }

                    // Right: Matched Reference
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Matched Reference", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .height(130.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .clickable { isZoomed = !isZoomed },
                            contentAlignment = Alignment.Center
                        ) {
                            if (refBmp != null) {
                                Image(
                                    bitmap = refBmp.asImageBitmap(),
                                    contentDescription = "Reference Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("No Image", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Text("🔍 Click any image to toggle full view zoom", fontSize = 10.sp, color = Color.Gray)

                Divider()

                // Statistical Metrics Breakdown
                Text("Statistical & ICUMSA Estimation Metrics", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                MetricRow("Estimated ICUMSA", String.format(Locale.US, "%.1f", record.estimatedIcumsa), isHighlight = true)
                MetricRow("Matched Reference", record.nearestReferenceName, isHighlight = true)
                MetricRow("Confidence Score", "${String.format(Locale.US, "%.1f", record.confidenceScorePct)}% (${record.confidenceLevel})")
                MetricRow("Selected Segment", "#${record.segmentRef1Id} → #${record.segmentRef2Id} (t=${String.format(Locale.US, "%.3f", record.interpolationT)})")
                MetricRow("Sample Median LAB", "L*=${String.format(Locale.US, "%.1f", record.medianL)}, a*=${String.format(Locale.US, "%.1f", record.medianA)}, b*=${String.format(Locale.US, "%.1f", record.medianB)}")
                MetricRow("Image Quality Score", "${record.qualityScore.toInt()} / 100 (Blur: ${String.format(Locale.US, "%.1f", record.blurScore)})")
                MetricRow("Algorithm Version", record.algorithmVersion)
                MetricRow("Processing Time", "${record.processingTimeMs} ms")
                MetricRow("Analysis Date & Time", "${record.dateFormatted} ${record.timeFormatted}")

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Difference Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (record.confidenceScorePct >= 80.0)
                                "High spectral and color conformity with standard reference sample. Zero significant divergence observed."
                            else if (record.confidenceScorePct >= 65.0)
                                "Moderate match. Minor color channel variance detected in Lab color space."
                            else
                                "Significant deviation detected. Sample exceeds similarity tolerance limit.",
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun MetricRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = if (isHighlight) MaterialTheme.colorScheme.primary else Color.Gray, fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
