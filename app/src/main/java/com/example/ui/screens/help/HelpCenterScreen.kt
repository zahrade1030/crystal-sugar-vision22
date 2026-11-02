package com.example.ui.screens.help

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpCenterScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isPersian = viewModel.language.collectAsState().value == "fa"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "راهنما و پشتیبانی علمی" else "Help & Support Center") },
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

            // 1. Step-by-Step Workflow Guide
            HelpSectionCard(
                title = if (isPersian) "۱. گام به گام آنالیز نمونه شکر" else "1. Step-by-Step Analysis Guide",
                description = if (isPersian)
                    "• گام ۱: نمونه شکر را در یک ظرف ساده یا روی زمینه تیره و مسطح قرار دهید.\n" +
                    "• گام ۲: از طریق دکمه «دوربین» عکس بگیرید یا از «گالری» عکس نمونه را انتخاب کنید.\n" +
                    "• گام ۳: بررسی کنید که نشانگر کیفیت تصویر در بالای کادر به رنگ سبز (Quality OK) باشد.\n" +
                    "• گام ۴: روی دکمه «آنالیز» بزنید تا شاخص ICUMSA، درجه کیفیت و آنالیز رنگی محاسبه شود."
                else
                    "• Step 1: Place the sugar sample on a clean, dark surface.\n" +
                    "• Step 2: Take a photo using 'Capture' or pick an existing image from 'Gallery'.\n" +
                    "• Step 3: Ensure the image quality badge shows green (Quality OK).\n" +
                    "• Step 4: Tap 'Analyze' to obtain ICUMSA grade, color parameters, and quality reports."
            )

            // 2. Comprehensive Option & Button Guide
            HelpSectionCard(
                title = if (isPersian) "۲. راهنمای کاربرد تمامی دکمه‌ها و ابزارها" else "2. Every Button & Feature Explained",
                description = if (isPersian)
                    "📷 دکمه و پیش‌نمایش دوربین (In-App Camera Viewfinder):\n" +
                    "• کی استفاده کنیم؟ پیش‌نمایش زنده دوربین مستقیماً درون کادر برنامه نمایش داده می‌شود تا بدون خروج از برنامه، نمونه شکر را تنظیم و عکاسی کنید.\n" +
                    "• چطور استفاده کنیم؟ نمونه شکر را درون دایره راهنما تنظیم کرده و دکمه عکاسی را فشار دهید.\n\n" +
                    "🖼️ دکمه گالری (Gallery):\n" +
                    "• کی استفاده کنیم؟ وقتی عکس نمونه شکر از قبل در گالری گوشی شما ذخیره شده است.\n\n" +
                    "🎯 توری و دایره راهنما (Grid Guide):\n" +
                    "• کی استفاده کنیم؟ برای تنظیم دقیق نمونه شکر درست در مرکز تصویر جهت افزایش دقت آنالیز.\n\n" +
                    "☀️ میزان نوردهی (Exposure Value):\n" +
                    "• کی استفاده کنیم؟ میزان روشنایی تصویر ثبت‌شده را نشان می‌دهد تا مطمئن شوید تصویر خیلی تاریک یا روشن نیست.\n\n" +
                    "📊 دکمه آنالیز (Analyze):\n" +
                    "• کی استفاده کنیم؟ پس از ثبت یا بارگذاری عکس و سبز شدن وضعیت کیفیت، برای محاسبه درجه ICUMSA و ثبت آن.\n\n" +
                    "🔍 سایر بخش‌های برنامه:\n" +
                    "• 📚 مرجع‌ها (References): مشاهده نمونه‌های استاندارد ثبت‌شده در دیتابیس جهت مقایسه.\n" +
                    "• 📜 تاریخچه (History): مشاهده و بررسی نتایج آنالیزهای قبلی انجام‌شده.\n" +
                    "• 📈 گزارش‌ها (Reports): بررسی آمار و نمودارهای تحلیلی کلی.\n" +
                    "• 🎯 کالیبراسیون (Calibration): تنظیم دقیق معیارها و خطاهای سنجش."
                else
                    "📷 In-App Camera Viewfinder:\n" +
                    "• Live camera feed streams directly inside the application frame so you can align and capture photos without leaving the app.\n\n" +
                    "🖼️ Gallery: Select a pre-existing photo of a sugar sample from your device storage.\n" +
                    "🎯 Grid Guide: Toggle alignment circle to center the sugar sample properly.\n" +
                    "☀️ Exposure Value: Displays captured brightness level for lighting evaluation.\n" +
                    "📊 Analyze: Computes ICUMSA color index and saves results once quality badge is green.\n\n" +
                    "🔍 Other Application Sections:\n" +
                    "• 📚 References: View calibrated reference database samples.\n" +
                    "• 📜 History: Review past test records and measurements.\n" +
                    "• 📈 Reports: Inspect analytical stats and quality charts.\n" +
                    "• 🎯 Calibration: Adjust measurement factors and calibration curves."
            )

            // 3. Quality Indicator Explanation
            HelpSectionCard(
                title = if (isPersian) "۳. وضعیت کیفیت تصویر (Quality Status)" else "3. Image Quality Indicators",
                description = if (isPersian)
                    "🟢 وضعیت سبز (Quality OK):\n" +
                    "تصویر دارای نور مناسب، کنتراست خوب و فوکوس شفاف است و آمادگی کامل برای آنالیز دقیق دارد.\n\n" +
                    "🔴 وضعیت قرمز (Quality Rejected):\n" +
                    "تصویر خیلی تاریک، تار یا خارج از کادر است. کافیست نور محیط را افزایش داده یا نمونه را در مرکز دایره راهنما بگذارید."
                else
                    "🟢 Green (Quality OK): Ideal lighting and sharp contrast. Ready for analysis.\n" +
                    "🔴 Red (Quality Rejected): Image is blurry or too dark. Adjust lighting or center the sample."
            )

            // 4. Contact Academic Support Button
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(if (isPersian) "تماس با گروه علمی و پشتیبانی:" else "Academic Support Contact:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Islamic Azad University, Hamedan Branch", fontSize = 12.sp, color = Color.Gray)
                    Text("Email: Mmshirmohammadi@iau.ac.ir", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Support Email", "Mmshirmohammadi@iau.ac.ir"))
                            Toast.makeText(context, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isPersian) "کپی ایمیل پشتیبانی" else "Copy Support Email")
                    }
                }
            }
        }
    }
}

@Composable
fun HelpSectionCard(title: String, description: String) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, fontSize = 12.sp, color = Color.White)
        }
    }
}
