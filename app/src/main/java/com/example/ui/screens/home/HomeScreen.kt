package com.example.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cv.ImageQualityValidator
import com.example.ui.MainViewModel
import com.example.ui.components.ExpiredFeatureNoticeDialog
import com.example.ui.components.SubscriptionUnlockDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToResult: () -> Unit,
    onNavigateToReferences: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToDevCenter: () -> Unit,
    onNavigateToAbout: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBitmap by viewModel.currentBitmap.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisError by viewModel.analysisErrorMessage.collectAsState()
    val historyList by viewModel.history.collectAsState()
    val referencesList by viewModel.references.collectAsState()
    val isDevMode by viewModel.isDeveloperMode.collectAsState()
    val isPersian = viewModel.language.collectAsState().value == "fa"

    val subActivated by viewModel.subActivated.collectAsState()
    val subExpirationTime by viewModel.subExpirationTime.collectAsState()
    val subDurationDays by viewModel.subDurationDays.collectAsState()

    val nowMs = System.currentTimeMillis()
    val isExpired = subActivated && nowMs >= subExpirationTime
    val remainingMs = (subExpirationTime - nowMs).coerceAtLeast(0L)
    val totalMs = (subDurationDays.toLong() * 24 * 3600 * 1000L).coerceAtLeast(1L)
    val percentRemaining = ((remainingMs.toFloat() / totalMs.toFloat()) * 100f).coerceIn(0f, 100f)

    var showExpiredNoticeDialog by remember { mutableStateOf(false) }
    var showSubRenewDialog by remember { mutableStateOf(false) }

    fun checkSubscriptionAndExecute(action: () -> Unit) {
        if (!subActivated) {
            return
        }
        if (isExpired) {
            showExpiredNoticeDialog = true
        } else {
            action()
        }
    }

    // Camera / Optical Lightbox simulator controls
    var gridEnabled by remember { mutableStateOf(true) }
    var exposureValue by remember { mutableStateOf(0f) }
    var isCameraActive by remember { mutableStateOf(true) }

    // CameraX ImageCapture instance
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // Quality validation state
    val qualityValidator = remember { ImageQualityValidator() }
    val qualityResult = remember(currentBitmap) {
        currentBitmap?.let { qualityValidator.validate(it) }
    }

    // Required permissions
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permissions launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        hasCameraPermission.value = cameraGranted
        if (!cameraGranted) {
            Toast.makeText(
                context,
                if (isPersian) "برای گرفتن عکس با دوربین نیاز به مجوز دوربین است" else "Camera permission is required to capture photos",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            isCameraActive = true
        }
    }

    // Auto-request permissions on screen launch
    LaunchedEffect(Unit) {
        if (!hasCameraPermission.value) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // In-App Camera Capture Function using CameraX
    fun captureInAppPhoto(onCaptured: (() -> Unit)? = null) {
        val executor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val planeProxy = image.planes[0]
                    val buffer = planeProxy.buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    image.close()

                    if (originalBitmap != null) {
                        val bitmap = if (rotationDegrees != 0) {
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(rotationDegrees.toFloat())
                            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                        } else {
                            originalBitmap
                        }
                        viewModel.setCapturedBitmap(bitmap)
                        isCameraActive = false
                        onCaptured?.invoke()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
                Toast.makeText(
                    context,
                    if (isPersian) "خطا در عکاسی: ${exception.localizedMessage}" else "Capture error: ${exception.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    fun openCamera() {
        if (hasCameraPermission.value) {
            viewModel.setCapturedBitmap(null)
            isCameraActive = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        viewModel.setCapturedBitmap(bmp)
                        isCameraActive = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isPersian) "کریستال بین شکر" else "Crystal Sugar Vision",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (isPersian) "سامانه آنالیز آزمایشگاهی ICUMSA" else "Laboratory Sugar ICUMSA Classifier",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { checkSubscriptionAndExecute { onNavigateToCalibration() } }) {
                        Icon(Icons.Default.Tune, contentDescription = "Calibration")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Default.Info, contentDescription = "About Team")
                    }
                    if (isDevMode) {
                        IconButton(onClick = { checkSubscriptionAndExecute { onNavigateToDevCenter() } }) {
                            Icon(Icons.Default.Code, contentDescription = "Dev Center", tint = Color(0xFFF59E0B))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Subscription Expiration Banner (Active Warning <= 10%)
            if (subActivated && !isExpired && percentRemaining <= 10f) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24))
                            Spacer(modifier = Modifier.width(8.dp))
                            val remDays = (remainingMs / (1000 * 3600 * 24)).toInt()
                            Text(
                                text = if (isPersian)
                                    "هشدار: اشتراک شما در حال اتمام است (باقیمانده: $remDays روز / ${percentRemaining.toInt()}٪). لطفاً نسبت به تمدید اقدام فرمایید."
                                else
                                    "Warning: Subscription expiring soon ($remDays days / ${percentRemaining.toInt()}% left). Please renew.",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showSubRenewDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isPersian) "تمدید" else "Renew", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Subscription Expired Banner
            if (subActivated && isExpired) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFCA5A5))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian)
                                    "زمان اشتراک شما به پایان رسیده است. قابلیت‌های آنالیز قفل می‌باشند (بخش‌های اعضای تیم، تاریخچه، راهنما و تنظیمات فعال هستند)."
                                else
                                    "Subscription expired. Analysis features locked (Team, History, Help, Settings accessible).",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showSubRenewDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isPersian) "تمدید اشتراک" else "Renew", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 1. Dashboard Summary Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                            text = if (isPersian) "داشبورد آزمایشگاه" else "Laboratory Dashboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Badge(containerColor = Color(0xFF10B981)) {
                            Text(if (isPersian) "دیتابیس آماده" else "DB Encrypted", modifier = Modifier.padding(2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(
                            label = if (isPersian) "کل آنالیزها" else "Total Analyses",
                            value = "${historyList.size}"
                        )
                        StatItem(
                            label = if (isPersian) "آخرین تاریخ" else "Last Analysis",
                            value = historyList.firstOrNull()?.dateFormatted ?: "-"
                        )
                        StatItem(
                            label = if (isPersian) "آخرین نمونه" else "Last Sample",
                            value = historyList.firstOrNull()?.matchedSampleName?.take(10) ?: "-"
                        )
                    }
                }
            }

            // 2. Camera Preview Box & Real-time Alignment Guide
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isCameraActive && hasCameraPermission.value) {
                        // Live CameraX Stream directly inside the app
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageCapture
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap!!.asImageBitmap(),
                            contentDescription = "Sample Image",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Empty camera viewfinder placeholder with alignment guide
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Camera",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isPersian) "نمونه شکر را در مرکز قرار دهید" else "Place sugar sample inside alignment circle",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Alignment Crosshairs & Circle Guide Overlay
                    if (gridEnabled) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .align(Alignment.Center)
                                .border(
                                    width = 2.dp,
                                    color = if (qualityResult?.isPassed == true) Color(0xFF10B981) else Color(0xFF38BDF8),
                                    shape = CircleShape
                                )
                        )
                        Divider(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White.copy(alpha = 0.3f),
                            thickness = 1.dp
                        )
                    }

                    // Live Quality Status Indicator Badge
                    if (!isCameraActive && qualityResult != null) {
                        qualityResult.let { q ->
                            Surface(
                                color = if (q.isPassed) Color(0xFF10B981).copy(alpha = 0.9f) else Color(0xFFEF4444).copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (q.isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (q.isPassed) "Quality OK (${q.overallScore.toInt()}%)" else "Quality Rejected",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Optical Controls Bar & In-App Shutter Button
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { gridEnabled = !gridEnabled }) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Grid",
                                tint = if (gridEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }

                        if (isCameraActive && hasCameraPermission.value) {
                            Button(
                                onClick = { captureInAppPhoto() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = CircleShape,
                                modifier = Modifier.size(42.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "Capture Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Exp: ${String.format("%.1f", exposureValue)}",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Quality Rejection Error Banner
            analysisError?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFCA5A5))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(err, color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            // Main Action Buttons (Gallery Import, Camera/Capture, Analyze)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { checkSubscriptionAndExecute { galleryLauncher.launch("image/*") } },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPersian) "گالری" else "Gallery", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        checkSubscriptionAndExecute {
                            if (isCameraActive && hasCameraPermission.value) {
                                captureInAppPhoto()
                            } else {
                                openCamera()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCameraActive && hasCameraPermission.value) {
                            if (isPersian) "عکاسی" else "Capture"
                        } else {
                            if (isPersian) "دوربین" else "Camera"
                        },
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        checkSubscriptionAndExecute {
                            if (currentBitmap == null && isCameraActive && hasCameraPermission.value) {
                                captureInAppPhoto {
                                    viewModel.analyzeCurrentBitmap()
                                    onNavigateToResult()
                                }
                            } else {
                                viewModel.analyzeCurrentBitmap()
                                onNavigateToResult()
                            }
                        }
                    },
                    enabled = (currentBitmap != null || (isCameraActive && hasCameraPermission.value)) && !isAnalyzing,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isPersian) "آنالیز" else "Analyze", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Quick Access Navigation Grid
            Text(
                text = if (isPersian) "دسترسی سریع:" else "Quick Access Modules:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickNavCard(
                    title = if (isPersian) "مرجع" else "References",
                    icon = Icons.Default.Collections,
                    modifier = Modifier.weight(1f),
                    onClick = { checkSubscriptionAndExecute { onNavigateToReferences() } }
                )
                QuickNavCard(
                    title = if (isPersian) "تاریخچه" else "History",
                    icon = Icons.Default.History,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToHistory
                )
                QuickNavCard(
                    title = if (isPersian) "گزارش‌ها" else "Reports",
                    icon = Icons.Default.Assessment,
                    modifier = Modifier.weight(1f),
                    onClick = { checkSubscriptionAndExecute { onNavigateToReports() } }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickNavCard(
                    title = if (isPersian) "کالیبراسیون" else "Calibration",
                    icon = Icons.Default.Tune,
                    modifier = Modifier.weight(1f),
                    onClick = { checkSubscriptionAndExecute { onNavigateToCalibration() } }
                )
                QuickNavCard(
                    title = if (isPersian) "راهنما" else "Help",
                    icon = Icons.Default.HelpOutline,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToHelp
                )
                QuickNavCard(
                    title = if (isPersian) "اعضای تیم" else "Team",
                    icon = Icons.Default.Info,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAbout
                )
            }
        }
    }

    // First Time Subscription Mandatory Setup Dialog
    if (!subActivated) {
        SubscriptionUnlockDialog(
            viewModel = viewModel,
            isFirstTime = true,
            onSuccess = {}
        )
    }

    // Expired Feature Notice Dialog
    if (showExpiredNoticeDialog) {
        ExpiredFeatureNoticeDialog(
            isPersian = isPersian,
            onDismiss = { showExpiredNoticeDialog = false },
            onOpenRenewDialog = { showSubRenewDialog = true }
        )
    }

    // Subscription Renewal Dialog
    if (showSubRenewDialog) {
        SubscriptionUnlockDialog(
            viewModel = viewModel,
            isFirstTime = false,
            onDismiss = { showSubRenewDialog = false },
            onSuccess = { showSubRenewDialog = false }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickNavCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
