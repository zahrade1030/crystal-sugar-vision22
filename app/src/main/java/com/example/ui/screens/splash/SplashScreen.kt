package com.example.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "alpha"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scaleAnim)
                .alpha(alphaAnim)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF0284C7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Biotech,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CRYSTAL VISION",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Text(
                text = "SUGAR COLOR & ICUMSA ANALYZER",
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Islamic Azad University, Hamedan Branch",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Classical Computer Vision Engine",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
