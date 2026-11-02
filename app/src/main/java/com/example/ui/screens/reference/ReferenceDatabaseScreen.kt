package com.example.ui.screens.reference

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ReferenceSampleEntity
import com.example.ui.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceDatabaseScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val references by viewModel.references.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"

    var selectedSampleForEdit by remember { mutableStateOf<ReferenceSampleEntity?>(null) }
    var selectedSampleForZoom by remember { mutableStateOf<ReferenceSampleEntity?>(null) }

    var editName by remember { mutableStateOf("") }
    var editIcumsa by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editRawBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Dimension & Crop Controls
    var targetResolution by remember { mutableStateOf(512) } // 256, 512, 1024
    var selectedAspectRatio by remember { mutableStateOf("1:1") } // "1:1", "4:3", "Original"

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        editRawBitmap = bmp
                        Toast.makeText(context, if (isPersian) "تصویر مرجع بارگذاری شد" else "Reference image loaded", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Processed Bitmap based on resolution & crop ratio
    val processedBitmap = remember(editRawBitmap, targetResolution, selectedAspectRatio) {
        val raw = editRawBitmap ?: return@remember null
        val cropped = when (selectedAspectRatio) {
            "1:1" -> {
                val side = kotlin.math.min(raw.width, raw.height)
                val x = (raw.width - side) / 2
                val y = (raw.height - side) / 2
                Bitmap.createBitmap(raw, x, y, side, side)
            }
            "4:3" -> {
                val targetW = raw.width
                val targetH = (targetW * 3) / 4
                if (targetH <= raw.height) {
                    val y = (raw.height - targetH) / 2
                    Bitmap.createBitmap(raw, 0, y, targetW, targetH)
                } else raw
            }
            else -> raw
        }
        Bitmap.createScaledBitmap(cropped, targetResolution, targetResolution, true)
    }

    // Helper to load sample bitmap safely from file path or fallback
    fun loadSampleBitmap(sample: ReferenceSampleEntity): Bitmap? {
        val path = sample.imagePath ?: return null
        val directFile = File(path)
        if (directFile.exists()) {
            val b = BitmapFactory.decodeFile(directFile.absolutePath)
            if (b != null) return b
        }
        val refDirFile = File(context.filesDir, "reference_samples/${directFile.name}")
        if (refDirFile.exists()) {
            val b = BitmapFactory.decodeFile(refDirFile.absolutePath)
            if (b != null) return b
        }
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "دیتابیس نمونه‌های مرجع (${references.size} نمونه)" else "Reference Database (${references.size} Samples)") },
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
            items(references) { sample ->
                val sampleBitmap = remember(sample.imagePath, sample.version) {
                    loadSampleBitmap(sample)
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Reference Sample Image Thumbnail with click to zoom
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedSampleForZoom = sample },
                                contentAlignment = Alignment.Center
                            ) {
                                if (sampleBitmap != null) {
                                    Image(
                                        bitmap = sampleBitmap.asImageBitmap(),
                                        contentDescription = sample.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.Science, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sample ID #${sample.id}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text("v${sample.version}", modifier = Modifier.padding(2.dp))
                                    }
                                }

                                Text(
                                    text = sample.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                Text(
                                    text = "ICUMSA Index: ${sample.icumsaValue}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = sample.description,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray.copy(alpha = 0.3f))

                        // Complete Metadata display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Resolution: 512x512", fontSize = 10.sp, color = Color.Gray)
                            Text("Vector Size: ${sample.featureVectorSize}", fontSize = 10.sp, color = Color.Gray)
                            Text("Median LAB: L=${String.format("%.1f", sample.medianL)}, a=${String.format("%.1f", sample.medianA)}, b=${String.format("%.1f", sample.medianB)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedSampleForEdit = sample
                                    editName = sample.name
                                    editIcumsa = sample.icumsaValue.toString()
                                    editDesc = sample.description
                                    editRawBitmap = null
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPersian) "ویرایش و جایگزینی تصویر" else "Edit & Replace Image", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Full Image Preview Zoom Dialog
        selectedSampleForZoom?.let { sample ->
            val bmp = loadSampleBitmap(sample)
            AlertDialog(
                onDismissRequest = { selectedSampleForZoom = null },
                title = { Text(sample.name) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ICUMSA: ${sample.icumsaValue}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        Text(sample.description, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedSampleForZoom = null }) {
                        Text(if (isPersian) "بستن" else "Close")
                    }
                }
            )
        }

        // Edit Reference Sample Dialog
        selectedSampleForEdit?.let { sample ->
            AlertDialog(
                onDismissRequest = { selectedSampleForEdit = null },
                title = { Text(if (isPersian) "ویرایش نمونه مرجع #${sample.id}" else "Edit Reference #${sample.id}") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Sample Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editIcumsa,
                            onValueChange = { editIcumsa = it },
                            label = { Text("ICUMSA Value") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editDesc,
                            onValueChange = { editDesc = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (editRawBitmap == null) (if (isPersian) "بارگذاری تصویر جدید از گالری" else "Select New Image") else (if (isPersian) "تغییر تصویر انتخاب شده" else "Change Selected Image"))
                        }

                        // Preview & Dimension Controls
                        processedBitmap?.let { previewBmp ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(if (isPersian) "پیش‌نمایش تصویر مرجع جدید (${targetResolution}x${targetResolution})" else "New Image Preview (${targetResolution}x${targetResolution})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Image(
                                    bitmap = previewBmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(if (isPersian) "رزولوشن پردازش استاندارد:" else "Output Resolution:", fontSize = 10.sp, color = Color.Gray)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = targetResolution == 256,
                                        onClick = { targetResolution = 256 },
                                        label = { Text("256x256", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = targetResolution == 512,
                                        onClick = { targetResolution = 512 },
                                        label = { Text("512x512", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = targetResolution == 1024,
                                        onClick = { targetResolution = 1024 },
                                        label = { Text("1024x1024", fontSize = 10.sp) }
                                    )
                                }

                                Text(if (isPersian) "نسبت ابعاد برش (Crop Aspect):" else "Crop Aspect Ratio:", fontSize = 10.sp, color = Color.Gray)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = selectedAspectRatio == "1:1",
                                        onClick = { selectedAspectRatio = "1:1" },
                                        label = { Text("1:1 (مربعی)", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedAspectRatio == "4:3",
                                        onClick = { selectedAspectRatio = "4:3" },
                                        label = { Text("4:3", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedAspectRatio == "Original",
                                        onClick = { selectedAspectRatio = "Original" },
                                        label = { Text("اصلی", fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val icumsaInt = editIcumsa.toIntOrNull() ?: sample.icumsaValue
                            viewModel.updateReferenceSample(
                                id = sample.id,
                                name = editName,
                                icumsa = icumsaInt,
                                description = editDesc,
                                newBitmap = processedBitmap
                            )
                            Toast.makeText(context, if (isPersian) "نمونه مرجع ذخیره و ویژگی‌ها استخراج شد" else "Reference saved & features extracted", Toast.LENGTH_SHORT).show()
                            selectedSampleForEdit = null
                        }
                    ) {
                        Text(if (isPersian) "ذخیره و استخراج ویژگی" else "Save & Extract Features")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedSampleForEdit = null }) {
                        Text(if (isPersian) "انصراف" else "Cancel")
                    }
                }
            )
        }
    }
}
