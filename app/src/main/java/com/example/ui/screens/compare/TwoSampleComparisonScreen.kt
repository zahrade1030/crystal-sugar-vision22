package com.example.ui.screens.compare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoSampleComparisonScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val isPersian = language == "fa"
    val state by viewModel.twoSampleState.collectAsState()

    var activePickerTarget by remember { mutableStateOf<String?>(null) } // "A" or "B"

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                var sampleSize = 1
                val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                while (maxSide / (sampleSize * 2) >= 512) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
                if (bmp != null) {
                    if (activePickerTarget == "A") viewModel.setTwoSampleA(bmp)
                    else if (activePickerTarget == "B") viewModel.setTwoSampleB(bmp)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "مقایسه مستقیم دو نمونه" else "Direct Two-Sample Comparison") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prominent Notice Banner (Mandatory Requirement)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isPersian)
                            "توجه: این مقایسه فقط زمانی معتبر است که هر دو عکس با همان دستگاه، نور و زاویه گرفته شده باشند. این اختلاف، تفاوت رنگ ظاهری (Apparent Color) است؛ نه عدد ICUMSA و نه شاخص خلوص شیمیایی."
                        else
                            "Notice: Valid only if both images are captured under identical lighting, camera angle, and device settings. This measures apparent optical colour difference, not laboratory ICUMSA or chemical purity.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Image Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sample A
                SampleCard(
                    title = if (isPersian) "نمونه اول (A)" else "Sample A",
                    bitmap = state.bitmapA,
                    onPickGallery = {
                        activePickerTarget = "A"
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier.weight(1f),
                    isPersian = isPersian
                )

                // Sample B
                SampleCard(
                    title = if (isPersian) "نمونه دوم (B)" else "Sample B",
                    bitmap = state.bitmapB,
                    onPickGallery = {
                        activePickerTarget = "B"
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier.weight(1f),
                    isPersian = isPersian
                )
            }

            // Action Button
            Button(
                onClick = { viewModel.compareTwoSamples() },
                enabled = state.bitmapA != null && state.bitmapB != null && !state.isComparing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isComparing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPersian) "در حال پردازش رنگ..." else "Processing Color...")
                } else {
                    Icon(Icons.Default.CompareArrows, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPersian) "محاسبه اختلاف رنگ (CIEDE2000)" else "Compute Color Difference (CIEDE2000)")
                }
            }

            state.errorMessage?.let { err ->
                Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            // Comparison Results
            state.diffResult?.let { diff ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = if (isPersian) "نتیجه تفاضل رنگ بین نمونه B و A" else "Color Difference (Sample B vs Sample A)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        // Delta E 00 Highlight
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "ΔE00 (CIEDE2000)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = String.format(Locale.US, "%.3f", diff.deltaE00),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (diff.deltaE00 < 1.0)
                                        (if (isPersian) "تفاوت رنگ برای چشم انسان نامحسوس است (< 1.0 JND)" else "Imperceptible difference to human eye (< 1.0 JND)")
                                    else if (diff.deltaE00 < 2.5)
                                        (if (isPersian) "تفاوت رنگ بسیار جزئی و در آستانه تشخیص" else "Very slight perceptual difference")
                                    else
                                        (if (isPersian) "تفاوت رنگ آشکار" else "Clearly noticeable perceptual color difference"),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Direction Description Chip
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isPersian) "جهت تغییر: ${diff.directionDesc}" else "Direction: ${diff.directionDesc}",
                                modifier = Modifier.padding(10.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Detailed Metric Breakdown Table
                        Divider()
                        MetricRow(label = "ΔL* (Lightness)", value = String.format(Locale.US, "%+.2f", diff.deltaL))
                        MetricRow(label = "Δa* (Green/Red)", value = String.format(Locale.US, "%+.2f", diff.deltaA))
                        MetricRow(label = "Δb* (Blue/Yellow)", value = String.format(Locale.US, "%+.2f", diff.deltaB))
                        MetricRow(label = "ΔC* (Chroma)", value = String.format(Locale.US, "%+.2f", diff.deltaC))
                        MetricRow(label = "Δh° (Hue Angle)", value = String.format(Locale.US, "%+.2f°", diff.deltaHueDeg))

                        // Stats Comparison
                        state.profileA?.let { pA ->
                            state.profileB?.let { pB ->
                                Divider()
                                Text(
                                    text = if (isPersian) "مقادیر میانگین پیراسته (Trimmed Mean Lab D65)" else "Trimmed Mean Lab D65 Comparison",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "A: L*=${String.format(Locale.US, "%.1f", pA.trimmedMeanL)}, a*=${String.format(Locale.US, "%.1f", pA.trimmedMeanA)}, b*=${String.format(Locale.US, "%.1f", pA.trimmedMeanB)} (std L: ${String.format(Locale.US, "%.1f", pA.stdL)})",
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "B: L*=${String.format(Locale.US, "%.1f", pB.trimmedMeanL)}, a*=${String.format(Locale.US, "%.1f", pB.trimmedMeanA)}, b*=${String.format(Locale.US, "%.1f", pB.trimmedMeanB)} (std L: ${String.format(Locale.US, "%.1f", pB.stdL)})",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleCard(
    title: String,
    bitmap: Bitmap?,
    onPickGallery: () -> Unit,
    modifier: Modifier = Modifier,
    isPersian: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = if (isPersian) "تصویر انتخاب نشده" else "No image",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            OutlinedButton(
                onClick = onPickGallery,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isPersian) "انتخاب عکس" else "Gallery", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
