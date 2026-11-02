package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.MainViewModel
import com.example.ui.components.SubscriptionUnlockDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.language.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val normalization by viewModel.normalization.collectAsState()
    val threshold by viewModel.similarityThreshold.collectAsState()
    val isDevMode by viewModel.isDeveloperMode.collectAsState()
    val isBiometric by viewModel.isBiometricEnabled.collectAsState()
    val isScreenshotProtected by viewModel.isScreenshotProtected.collectAsState()
    val isPersian = language == "fa"

    val subActivated by viewModel.subActivated.collectAsState()
    val subStartTime by viewModel.subStartTime.collectAsState()
    val subExpirationTime by viewModel.subExpirationTime.collectAsState()
    val subDurationDays by viewModel.subDurationDays.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var showSubDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "تنظیمات سامانه" else "System Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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

            // 0. Subscription Management Section
            val nowMs = System.currentTimeMillis()
            val remainingMs = (subExpirationTime - nowMs).coerceAtLeast(0L)
            val remainingDays = (remainingMs / (1000 * 3600 * 24)).toInt()
            val remainingHours = ((remainingMs % (1000 * 3600 * 24)) / (1000 * 3600)).toInt()
            val totalMs = (subDurationDays.toLong() * 24 * 3600 * 1000L).coerceAtLeast(1L)
            val percentRemaining = ((remainingMs.toFloat() / totalMs.toFloat()) * 100f).coerceIn(0f, 100f)
            val isExpired = subActivated && remainingMs <= 0L

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        !subActivated -> MaterialTheme.colorScheme.surfaceVariant
                        isExpired -> Color(0xFF450A0A)
                        percentRemaining <= 10f -> Color(0xFF451A03)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "وضعیت اشتراک سامانه" else "Subscription Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            color = when {
                                !subActivated -> Color.Gray
                                isExpired -> MaterialTheme.colorScheme.error
                                percentRemaining <= 10f -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = when {
                                    !subActivated -> if (isPersian) "غیرفعال" else "Not Activated"
                                    isExpired -> if (isPersian) "منقضی شده" else "Expired"
                                    percentRemaining <= 10f -> if (isPersian) "رو به اتمام (${percentRemaining.toInt()}%)" else "Expiring Soon (${percentRemaining.toInt()}%)"
                                    else -> if (isPersian) "فعال (${percentRemaining.toInt()}%)" else "Active (${percentRemaining.toInt()}%)"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (subActivated && !isExpired) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isPersian) "زمان باقیمانده اشتراک:" else "Remaining Subscription Time:",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                            Text(
                                text = if (isPersian)
                                    "$remainingDays روز و $remainingHours ساعت (${percentRemaining.toInt()}٪)"
                                else
                                    "$remainingDays days $remainingHours hrs (${percentRemaining.toInt()}%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = percentRemaining / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = if (percentRemaining <= 10f) Color(0xFFF59E0B) else Color(0xFF10B981),
                            trackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    } else if (isExpired) {
                        Text(
                            text = if (isPersian)
                                "زمان اشتراک شما به پایان رسیده است. جهت تمدید رمزهای عبور امنیتی را وارد نمایید."
                            else
                                "Your subscription has expired. Enter passwords to extend access.",
                            fontSize = 12.sp,
                            color = Color(0xFFFCA5A5)
                        )
                    } else {
                        Text(
                            text = if (isPersian)
                                "سامانه هنوز فعالسازی اولیه نشده است. جهت فعالسازی رمزهای عبور را وارد نمایید."
                            else
                                "System initial activation pending. Enter keys to activate.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showSubDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!subActivated) {
                                if (isPersian) "فعالسازی اولیه اشتراک" else "Initial Activation"
                            } else {
                                if (isPersian) "مدیریت و تمدید اشتراک" else "Manage & Extend Subscription"
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 1. Language & Localization Section
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isPersian) "زبان و محلی‌سازی (Multilingual)" else "Language & Localization",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isPersian) "زبان برنامه" else "App Language", fontSize = 13.sp)
                        Row {
                            FilterChip(
                                selected = language == "fa",
                                onClick = { viewModel.setLanguage("fa") },
                                label = { Text("فارسی") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = language == "en",
                                onClick = { viewModel.setLanguage("en") },
                                label = { Text("English") }
                            )
                        }
                    }
                }
            }

            // 2. Computer Vision Classification Settings
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isPersian) "تنظیمات بینایی ماشین (Computer Vision)" else "Computer Vision Engine Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(if (isPersian) "روش نرمال‌سازی بردار ویژگی:" else "Feature Vector Normalization:", fontSize = 12.sp, color = Color.Gray)
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setNormalization("STANDARD_SCALER") },
                            label = { Text("Fixed-domain (Domain Scaling)", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Similarity Threshold: ${threshold.toInt()}% (${if (isPersian) "زیر این مقدار = نمونه ناشناخته" else "Below = Unknown Sample"})",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { viewModel.setSimilarityThreshold(it.toDouble()) },
                        valueRange = 40f..85f
                    )
                }
            }

            // 3. Security & Privacy Settings
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isPersian) "امنیت و حفاظت (Security)" else "Security & Privacy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isPersian) "حفاظت اسکرین‌شات (FLAG_SECURE)" else "Screenshot Protection (FLAG_SECURE)", fontSize = 13.sp)
                        Switch(
                            checked = isScreenshotProtected,
                            onCheckedChange = { viewModel.setScreenshotProtected(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isPersian) "احراز هویت بیومتریک" else "Biometric Authentication", fontSize = 13.sp)
                        Switch(
                            checked = isBiometric,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) }
                        )
                    }
                }
            }

            // 4. Developer Center Access Toggle
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPersian) "حالت توسعه‌دهنده (Developer Center)" else "Developer Mode & Debug Tools",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFF59E0B)
                            )
                            Text(
                                text = if (isPersian) "مشاهده مراحل پردازش تصویر و بردار ویژگی‌ها" else "Inspect pipeline processing stages & feature vectors",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isDevMode,
                            onCheckedChange = { check ->
                                if (check) {
                                    showPinDialog = true
                                } else {
                                    viewModel.setDeveloperMode(false)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Admin PIN Verification Prompt
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text(if (isPersian) "تأیید دسترسی توسعه‌دهنده" else "Developer Access Verification") },
                text = {
                    Column {
                        Text(if (isPersian) "کد امنیتی مدیریت را وارد کنید" else "Enter Admin Security PIN", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = { enteredPin = it },
                            label = { Text(if (isPersian) "کد رمز" else "PIN Code") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.verifyAdminPin(enteredPin)) {
                                viewModel.setDeveloperMode(true)
                                showPinDialog = false
                                enteredPin = ""
                                Toast.makeText(context, "Developer Mode Activated", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Verify")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showSubDialog) {
            SubscriptionUnlockDialog(
                viewModel = viewModel,
                isFirstTime = !subActivated,
                onDismiss = { showSubDialog = false },
                onSuccess = { showSubDialog = false }
            )
        }
    }
}
