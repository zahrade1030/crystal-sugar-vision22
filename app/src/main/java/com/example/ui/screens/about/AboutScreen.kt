package com.example.ui.screens.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isPersian = viewModel.language.collectAsState().value == "fa"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isPersian) "درباره برنامه و سازندگان" else "About & Credits") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Logo & Header Card
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Biotech,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "CRYSTAL VISION",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Text(
                text = "Classical Computer Vision Sugar Classifier v2.0",
                fontSize = 12.sp,
                color = Color.Gray
            )

            // Exact Persian Academic Credits Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "گروه هوش مصنوعی کریستال بین",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "دانشگاه آزاد اسلامی واحد همدان",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("• دکتر محمد مهدی شیرمحمدی", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("• دکتر سید معین نظری", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("• مهندس سینا بالائی", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("• مهندس سید علی قاسمی", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("• دانشجو زهرا ده ده سی", fontSize = 13.sp, fontWeight = FontWeight.Medium)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("ایمیل: Mmshirmohammadi@iau.ac.ir", fontSize = 12.sp, color = Color(0xFF38BDF8))
                    Text("ایمیل پشتیبانی: sina.balaei81@gmail.com", fontSize = 12.sp, color = Color(0xFF38BDF8))
                }
            }

            // Exact English Academic Credits Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Crystal Vision Artificial Intelligence Group",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Islamic Azad University, Hamedan Branch",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("• Dr. Mohammad Mehdi Shirmohammadi", fontSize = 13.sp)
                    Text("• Dr. Seyyed Moein Nazari", fontSize = 13.sp)
                    Text("• Engineer Sina Balaei", fontSize = 13.sp)
                    Text("• Engineer Seyyed Ali Ghasemi", fontSize = 13.sp)
                    Text("• Student Zahra Dah Dah Si", fontSize = 13.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Email: Mmshirmohammadi@iau.ac.ir", fontSize = 12.sp, color = Color(0xFF38BDF8))
                    Text("Support Email: sina.balaei81@gmail.com", fontSize = 12.sp, color = Color(0xFF38BDF8))
                }
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Academic Email", "Mmshirmohammadi@iau.ac.ir"))
                    Toast.makeText(context, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPersian) "کپی ایمیل ارتباطی" else "Copy Contact Email")
            }
        }
    }
}
